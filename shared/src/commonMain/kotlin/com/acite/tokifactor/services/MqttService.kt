package com.acite.tokifactor.services

import com.acite.tokifactor.model.MqttBroker
import com.acite.tokifactor.model.MqttBrokerProbe
import com.acite.tokifactor.model.MqttMessage
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

enum class MqttDeliveryQos(val code: Int) {
    AtMostOnce(0),
    AtLeastOnce(1),
}

@SingleIn(AppScope::class)
class MqttService @Inject constructor(
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val SESSION_EXPIRY_SECONDS = 30L * 24L * 60L * 60L
    }

    private val lock = Mutex()
    private var client: Mqtt5AsyncClient? = null
    // Stale reconnect listeners must not change the state of a newer client.
    @Volatile private var connectionGeneration = 0L

    // Store the maximum packet size negotiated with the broker
    private var maxPacketSize: Int? = null
    private val subscriptionLock = Any()
    private val listeners = mutableMapOf<String, MutableList<(MqttMessage) -> Unit>>()
    private val pendingMessages = ArrayDeque<MqttMessage>()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    suspend fun connect(broker: MqttBroker = settingsStore.mqttBroker): Boolean {
        return lock.withLock {
            disconnectUnlocked()
            val generation = ++connectionGeneration
            var built: Mqtt5AsyncClient? = null
            try {
                val clientId = stableClientId(broker)
                built = buildClient(broker, clientId, autoReconnect = true) { live ->
                    if (generation == connectionGeneration) _connected.value = live
                }
                built.publishes(MqttGlobalPublishFilter.ALL) { publish -> dispatch(publish) }
                val connack = built.connectWith()
                    .cleanStart(false)
                    .sessionExpiryInterval(SESSION_EXPIRY_SECONDS)
                    .send()
                    .await()
                if (connack?.reasonCode?.toString() == "SUCCESS") {
                    client = built
                    maxPacketSize = connack.restrictions.maximumPacketSize
                    _connected.value = true
                    true
                } else {
                    disconnectClient(built)
                    _connected.value = false
                    false
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    built?.let { disconnectClient(it) }
                    clearConnection(generation)
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(NonCancellable) {
                    built?.let { disconnectClient(it) }
                    clearConnection(generation)
                }
                false
            }
        }
    }

    suspend fun probe(broker: MqttBroker, timeoutMs: Long = 8_000L): MqttBrokerProbe {
        val clientId = "tf-probe-${UUID.randomUUID().toString().take(8)}"
        val probeClient = buildClient(broker, clientId, autoReconnect = false)
        val start = System.nanoTime()
        return try {
            withTimeout(timeoutMs) {
                val connack = probeClient.connect().await()
                val ms = (System.nanoTime() - start) / 1_000_000L
                disconnectQuietly(probeClient)
                if (connack.reasonCode.toString() == "SUCCESS") {
                    MqttBrokerProbe(latencyMs = ms)
                } else {
                    MqttBrokerProbe(error = "refused")
                }
            }
        } catch (_: TimeoutCancellationException) {
            disconnectQuietly(probeClient)
            MqttBrokerProbe(error = "timeout")
        } catch (e: CancellationException) {
            disconnectQuietly(probeClient)
            throw e
        } catch (_: Exception) {
            disconnectQuietly(probeClient)
            MqttBrokerProbe(error = "failed")
        }
    }

    // Expose the maximum packet size
    fun getMaximumPacketSize(): Int? {
        return maxPacketSize
    }

    suspend fun publish(
        topic: String,
        payload: String,
        qos: MqttDeliveryQos = MqttDeliveryQos.AtLeastOnce,
        retain: Boolean = false,
    ) {
        val asyncClient = client ?: throw IllegalStateException("MQTT Not Connected")

        asyncClient.publishWith()
            .topic(topic)
            .qos(com.hivemq.client.mqtt.datatypes.MqttQos.fromCode(qos.code)!!)
            .retain(retain)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .send()
            .await()
    }

    fun subscribe(
        topic: String,
        qos: MqttDeliveryQos = MqttDeliveryQos.AtLeastOnce,
    ): Flow<MqttMessage> = callbackFlow {
        val asyncClient = client ?: run {
            close(IllegalStateException("MQTT Not Connected"))
            return@callbackFlow
        }

        val listener: (MqttMessage) -> Unit = { trySend(it) }
        val queued = synchronized(subscriptionLock) {
            listeners.getOrPut(topic) { mutableListOf() }.add(listener)
            pendingMessages
                .filter { it.topic == topic }
                .also { buffered -> pendingMessages.removeAll { it.topic == topic } }
        }
        queued.forEach(listener)

        try {
            asyncClient.subscribeWith()
                .topicFilter(topic)
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.fromCode(qos.code)!!)
                // Set NoLocal to true to instruct the broker not to echo back our own messages
                .noLocal(true)
                .send()
                .await()
        } catch (e: Exception) {
            synchronized(subscriptionLock) {
                listeners[topic]?.remove(listener)
            }
            close(e)
            return@callbackFlow
        }

        awaitClose {
            synchronized(subscriptionLock) {
                listeners[topic]?.remove(listener)
            }
            try {
                asyncClient.unsubscribeWith()
                    .topicFilter(topic)
                    .send()
            } catch (_: Exception) {
            }
        }
    }.buffer(Channel.UNLIMITED)

    suspend fun disconnect() {
        withContext(NonCancellable) {
            lock.withLock { disconnectUnlocked() }
        }
    }

    fun isConnected(): Boolean = _connected.value

    private suspend fun disconnectUnlocked() {
        val current = client
        client = null
        maxPacketSize = null
        connectionGeneration++
        _connected.value = false
        synchronized(subscriptionLock) {
            pendingMessages.clear()
        }
        if (current != null) disconnectClient(current)
    }

    private fun clearConnection(generation: Long) {
        if (generation != connectionGeneration) return
        client = null
        maxPacketSize = null
        _connected.value = false
    }

    private suspend fun disconnectClient(target: Mqtt5AsyncClient) {
        try {
            withContext(NonCancellable) {
                withTimeoutOrNull(3_000L) {
                    target.disconnectWith()
                        .sessionExpiryInterval(SESSION_EXPIRY_SECONDS)
                        .send()
                        .await()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun stableClientId(broker: MqttBroker): String {
        val identity = buildString {
            append(settingsStore.deviceId)
            append('\u0000')
            append(broker.endpointKey())
            append('\u0000')
            append(settingsStore.magicString)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
        return "tf-" + digest.take(10).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun dispatch(publish: Mqtt5Publish) {
        val content = publish.payload.orElse(null)?.let { buffer ->
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            String(bytes, StandardCharsets.UTF_8)
        } ?: ""
        val message = MqttMessage(topic = publish.topic.toString(), payload = content)
        synchronized(subscriptionLock) {
            val matching = listeners[message.topic]
            if (matching.isNullOrEmpty()) {
                pendingMessages.addLast(message)
            } else {
                matching.toList().forEach { listener -> listener(message) }
            }
        }
    }

    private fun disconnectQuietly(target: Mqtt5AsyncClient) {
        try {
            target.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun buildClient(
        broker: MqttBroker,
        clientId: String,
        autoReconnect: Boolean,
        onConnectionState: ((Boolean) -> Unit)? = null,
    ): Mqtt5AsyncClient {
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(clientId)
            .serverHost(broker.host)
            .serverPort(broker.port)
        if (broker.username.isNotEmpty()) {
            val auth = builder.simpleAuth().username(broker.username)
            builder = if (broker.password.isEmpty()) {
                auth.applySimpleAuth()
            } else {
                auth.password(broker.password.toByteArray(StandardCharsets.UTF_8)).applySimpleAuth()
            }
        }
        if (autoReconnect) {
            builder = builder.automaticReconnectWithDefaultConfig()
            if (onConnectionState != null) {
                builder = builder
                    .addConnectedListener { onConnectionState(true) }
                    .addDisconnectedListener { onConnectionState(false) }
            }
        }
        return if (broker.tls) {
            builder.sslWithDefaultConfig().buildAsync()
        } else {
            builder.buildAsync()
        }
    }
}

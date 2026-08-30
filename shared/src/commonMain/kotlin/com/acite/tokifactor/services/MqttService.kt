package com.acite.tokifactor.services

import com.acite.tokifactor.model.MqttBroker
import com.acite.tokifactor.model.MqttBrokerProbe
import com.acite.tokifactor.model.MqttMessage
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.nio.charset.StandardCharsets
import java.util.UUID

@SingleIn(AppScope::class)
class MqttService @Inject constructor(
    private val settingsStore: SettingsStore,
) {
    private val lock = Mutex()
    private var client: Mqtt5AsyncClient? = null

    // Store the maximum packet size negotiated with the broker
    private var maxPacketSize: Int? = null

    suspend fun connect(broker: MqttBroker = settingsStore.mqttBroker): Boolean {
        return lock.withLock {
            disconnectUnlocked()
            var built: Mqtt5AsyncClient? = null
            try {
                val clientId = "compose-metro-${UUID.randomUUID().toString().take(8)}"
                built = buildClient(broker, clientId)
                val connack = built.connect().await()
                if (connack?.reasonCode?.toString() == "SUCCESS") {
                    client = built
                    maxPacketSize = connack.restrictions.maximumPacketSize
                    true
                } else {
                    try {
                        built.disconnect()
                    } catch (_: Exception) {
                    }
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    built?.disconnect()
                } catch (_: Exception) {
                }
                disconnectUnlocked()
                false
            }
        }
    }

    suspend fun probe(broker: MqttBroker, timeoutMs: Long = 8_000L): MqttBrokerProbe {
        val clientId = "tf-probe-${UUID.randomUUID().toString().take(8)}"
        val probeClient = buildClient(broker, clientId)
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

    suspend fun publish(topic: String, payload: String, qos: Int = 1) {
        val asyncClient = client ?: throw IllegalStateException("MQTT Not Connected")

        asyncClient.publishWith()
            .topic(topic)
            .qos(com.hivemq.client.mqtt.datatypes.MqttQos.fromCode(qos)!!)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .send()
            .await()
    }

    fun subscribe(topic: String): Flow<MqttMessage> = callbackFlow {
        val asyncClient = client ?: run {
            close(IllegalStateException("MQTT Not Connected"))
            return@callbackFlow
        }

        asyncClient.subscribeWith()
            .topicFilter(topic)
            // Set NoLocal to true to instruct the broker not to echo back our own messages
            .noLocal(true)
            .callback { publish: Mqtt5Publish ->
                val content = publish.payload.orElse(null)?.let { buffer ->
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    String(bytes, StandardCharsets.UTF_8)
                } ?: ""

                trySend(MqttMessage(topic = publish.topic.toString(), payload = content))
            }
            .send()
            .await()

        awaitClose {
            try {
                asyncClient.unsubscribeWith()
                    .topicFilter(topic)
                    .send()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun disconnect() {
        lock.withLock { disconnectUnlocked() }
    }

    fun isConnected(): Boolean {
        return client != null
    }

    private fun disconnectUnlocked() {
        val current = client
        client = null
        maxPacketSize = null
        if (current != null) {
            try {
                current.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun disconnectQuietly(target: Mqtt5AsyncClient) {
        try {
            target.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun buildClient(broker: MqttBroker, clientId: String): Mqtt5AsyncClient {
        val builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(clientId)
            .serverHost(broker.host)
            .serverPort(broker.port)
        return if (broker.tls) {
            builder.sslWithDefaultConfig().buildAsync()
        } else {
            builder.buildAsync()
        }
    }
}

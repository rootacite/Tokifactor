package com.acite.tokifactor.services

import com.acite.tokifactor.appAvatarFile
import com.acite.tokifactor.appConfigFile
import com.acite.tokifactor.appMqttProbesFile
import com.acite.tokifactor.defaultDisplayName
import com.acite.tokifactor.model.MqttBroker
import com.acite.tokifactor.model.MqttBrokerProbe
import com.acite.tokifactor.model.MqttBrokers
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.UUID

@SingleIn(AppScope::class)
class SettingsStore @Inject constructor() {

    companion object {
        const val DEFAULT_MAGIC_STRING = "TOKIFACTOR"
        const val MAX_AVATAR_BYTES = 4 * 1024 * 1024
        private const val KEY_MAGIC = "magicString"
        private const val KEY_DISPLAY_NAME = "displayName"
        private const val KEY_MQTT_BROKER = "mqttBrokerId"
        private const val KEY_DEVICE_ID = "deviceId"
    }

    var magicString: String = DEFAULT_MAGIC_STRING
        private set

    var displayName: String = defaultDisplayName()
        private set

    var mqttBrokerId: String = MqttBrokers.DEFAULT_ID
        private set

    var deviceId: String = ""
        private set

    val mqttBroker: MqttBroker
        get() = MqttBrokers.byId(mqttBrokerId)

    var avatarBytes: ByteArray? = null
        private set

    var mqttProbes: Map<String, MqttBrokerProbe> = emptyMap()
        private set

    init {
        val file = appConfigFile()
        val parsed = if (file.exists()) parseToml(file.readText()) else emptyMap()
        magicString = parsed[KEY_MAGIC]?.ifBlank { null } ?: DEFAULT_MAGIC_STRING
        displayName = parsed[KEY_DISPLAY_NAME]?.ifBlank { null } ?: defaultDisplayName()
        mqttBrokerId = MqttBrokers.byId(parsed[KEY_MQTT_BROKER]).id
        deviceId = parsed[KEY_DEVICE_ID]?.ifBlank { null } ?: UUID.randomUUID().toString()
        avatarBytes = loadAvatar()
        mqttProbes = loadMqttProbes()
        if (!file.exists() ||
            parsed[KEY_DISPLAY_NAME].isNullOrBlank() ||
            parsed[KEY_MQTT_BROKER].isNullOrBlank() ||
            parsed[KEY_DEVICE_ID].isNullOrBlank()
        ) {
            persist()
        }
    }

    fun updateMagicString(value: String) {
        magicString = value.ifBlank { DEFAULT_MAGIC_STRING }
        persist()
    }

    fun updateDisplayName(value: String) {
        displayName = value.ifBlank { defaultDisplayName() }
        persist()
    }

    fun updateMqttBroker(id: String) {
        mqttBrokerId = MqttBrokers.byId(id).id
        persist()
    }

    @Synchronized
    fun updateMqttProbe(id: String, probe: MqttBrokerProbe) {
        mqttProbes = mqttProbes + (id to probe)
        persistMqttProbes()
    }

    fun updateAvatar(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) {
            avatarBytes = null
            val file = appAvatarFile()
            if (file.exists()) file.delete()
        } else {
            avatarBytes = bytes.copyOf()
            val file = appAvatarFile()
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    private fun loadAvatar(): ByteArray? {
        val file = appAvatarFile()
        if (!file.exists() || file.length() <= 0L) return null
        return try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    private fun loadMqttProbes(): Map<String, MqttBrokerProbe> {
        val file = appMqttProbesFile()
        if (!file.exists()) return emptyMap()
        val parsed = try {
            parseToml(file.readText())
        } catch (_: Exception) {
            return emptyMap()
        }
        val known = MqttBrokers.ALL.map { it.id }.toSet()
        val out = mutableMapOf<String, MqttBrokerProbe>()
        for ((id, raw) in parsed) {
            if (id !in known) continue
            val ms = raw.toLongOrNull()
            out[id] = if (ms != null) {
                MqttBrokerProbe(latencyMs = ms)
            } else {
                MqttBrokerProbe(error = raw.ifBlank { "failed" })
            }
        }
        return out
    }

    @Synchronized
    private fun persistMqttProbes() {
        val file = appMqttProbesFile()
        file.parentFile?.mkdirs()
        val body = buildString {
            append("# MQTT connect probes: milliseconds or error token\n")
            for (broker in MqttBrokers.ALL) {
                val probe = mqttProbes[broker.id] ?: continue
                val value = probe.latencyMs?.toString() ?: (probe.error ?: "failed")
                append("${broker.id} = \"${escapeTomlString(value)}\"\n")
            }
        }
        file.writeText(body)
    }

    private fun persist() {
        val file = appConfigFile()
        file.parentFile?.mkdirs()
        file.writeText(
            "$KEY_MAGIC = \"${escapeTomlString(magicString)}\"\n" +
                "$KEY_DISPLAY_NAME = \"${escapeTomlString(displayName)}\"\n" +
                "$KEY_MQTT_BROKER = \"${escapeTomlString(mqttBrokerId)}\"\n" +
                "$KEY_DEVICE_ID = \"${escapeTomlString(deviceId)}\"\n"
        )
    }

    private fun parseToml(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) continue
            val eq = line.indexOf('=')
            if (eq < 0) continue
            val key = line.substring(0, eq).trim()
            val value = unescapeTomlString(line.substring(eq + 1).trim())
            out[key] = value
        }
        return out
    }

    private fun escapeTomlString(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private fun unescapeTomlString(raw: String): String {
        val inner = if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
        val out = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c == '\\' && i + 1 < inner.length) {
                when (val n = inner[i + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

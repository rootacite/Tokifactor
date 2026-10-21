package com.acite.tokifactor.services

import com.acite.tokifactor.appAvatarFile
import com.acite.tokifactor.appConfigFile
import com.acite.tokifactor.appMqttProbesFile
import com.acite.tokifactor.defaultDisplayName
import com.acite.tokifactor.model.AppearanceIds
import com.acite.tokifactor.model.CustomBrokerParse
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
        const val DEFAULT_FONT_SCALE = 1f
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 1.6f
        const val DEFAULT_APPEARANCE_ID = AppearanceIds.Default
        private const val KEY_MAGIC = "magicString"
        private const val KEY_DISPLAY_NAME = "displayName"
        private const val KEY_FONT_SCALE = "fontScale"
        private const val KEY_APPEARANCE = "appearanceId"
        private const val KEY_MQTT_BROKER = "mqttBrokerId"
        private const val KEY_CUSTOM_HOST = "mqttCustomHost"
        private const val KEY_CUSTOM_PORT = "mqttCustomPort"
        private const val KEY_CUSTOM_TLS = "mqttCustomTls"
        private const val KEY_CUSTOM_USERNAME = "mqttCustomUsername"
        private const val KEY_CUSTOM_PASSWORD = "mqttCustomPassword"
        private const val KEY_DEVICE_ID = "deviceId"
    }

    var magicString: String = DEFAULT_MAGIC_STRING
        private set

    var displayName: String = defaultDisplayName()
        private set

    var fontScale: Float = DEFAULT_FONT_SCALE
        private set

    var appearanceId: String = DEFAULT_APPEARANCE_ID
        private set

    var mqttBrokerId: String = MqttBrokers.DEFAULT_ID
        private set

    var mqttCustomHost: String = ""
        private set

    var mqttCustomPort: String = "8883"
        private set

    var mqttCustomTls: Boolean = true
        private set

    var mqttCustomUsername: String = ""
        private set

    var mqttCustomPassword: String = ""
        private set

    var deviceId: String = ""
        private set

    val mqttBroker: MqttBroker
        get() {
            if (mqttBrokerId == MqttBrokers.CUSTOM_ID) {
                customBroker()?.let { return it }
            }
            val id = if (mqttBrokerId == MqttBrokers.CUSTOM_ID) {
                MqttBrokers.DEFAULT_ID
            } else {
                mqttBrokerId
            }
            return MqttBrokers.byId(id)
        }

    fun customEndpointKey(): String? = customBroker()?.endpointKey()

    var avatarBytes: ByteArray? = null
        private set

    var mqttProbes: Map<String, MqttBrokerProbe> = emptyMap()
        private set

    init {
        val file = appConfigFile()
        val parsed = if (file.exists()) parseToml(file.readText()) else emptyMap()
        magicString = parsed[KEY_MAGIC]?.ifBlank { null } ?: DEFAULT_MAGIC_STRING
        displayName = parsed[KEY_DISPLAY_NAME]?.ifBlank { null } ?: defaultDisplayName()
        fontScale = parsed[KEY_FONT_SCALE]
            ?.toFloatOrNull()
            ?.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
            ?: DEFAULT_FONT_SCALE
        appearanceId = AppearanceIds.normalize(parsed[KEY_APPEARANCE])
        mqttCustomHost = parsed[KEY_CUSTOM_HOST].orEmpty()
        mqttCustomPort = parsed[KEY_CUSTOM_PORT]?.takeIf { it.isNotBlank() } ?: "8883"
        mqttCustomTls = !parsed[KEY_CUSTOM_TLS].equals("false", ignoreCase = true)
        mqttCustomUsername = parsed[KEY_CUSTOM_USERNAME].orEmpty()
        mqttCustomPassword = parsed[KEY_CUSTOM_PASSWORD].orEmpty()
        val storedBroker = parsed[KEY_MQTT_BROKER]
        mqttBrokerId = when (storedBroker) {
            MqttBrokers.CUSTOM_ID -> MqttBrokers.CUSTOM_ID
            null, "" -> MqttBrokers.DEFAULT_ID
            else -> MqttBrokers.byId(storedBroker).id
        }
        val customWasUnusable = mqttBrokerId == MqttBrokers.CUSTOM_ID && customBroker() == null
        if (customWasUnusable) mqttBrokerId = MqttBrokers.DEFAULT_ID
        deviceId = parsed[KEY_DEVICE_ID]?.ifBlank { null } ?: UUID.randomUUID().toString()
        avatarBytes = loadAvatar()
        mqttProbes = loadMqttProbes()
        if (!file.exists() ||
            parsed[KEY_DISPLAY_NAME].isNullOrBlank() ||
            parsed[KEY_MQTT_BROKER].isNullOrBlank() ||
            parsed[KEY_DEVICE_ID].isNullOrBlank() ||
            customWasUnusable
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

    fun updateFontScale(value: Float) {
        fontScale = value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        persist()
    }

    fun updateAppearanceId(value: String) {
        appearanceId = AppearanceIds.normalize(value)
        persist()
    }

    fun updateMqttSelection(selectedId: String, draft: CustomBrokerParse) {
        val previousKey = customEndpointKey()
        val broker = draft.broker
        if (broker != null) {
            mqttCustomHost = broker.host
            mqttCustomPort = broker.port.toString()
            mqttCustomTls = broker.tls
            mqttCustomUsername = broker.username
            mqttCustomPassword = broker.password
        } else {
            mqttCustomHost = draft.host
            mqttCustomPort = draft.portText.ifBlank { mqttCustomPort }
            mqttCustomTls = draft.tls
            mqttCustomUsername = draft.username
            mqttCustomPassword = draft.password
        }
        mqttBrokerId = when {
            selectedId == MqttBrokers.CUSTOM_ID && broker != null -> MqttBrokers.CUSTOM_ID
            selectedId == MqttBrokers.CUSTOM_ID ->
                if (mqttBrokerId == MqttBrokers.CUSTOM_ID) MqttBrokers.DEFAULT_ID else mqttBrokerId
            else -> MqttBrokers.byId(selectedId).id
        }
        if (previousKey != customEndpointKey()) {
            mqttProbes = mqttProbes - MqttBrokers.CUSTOM_ID
            persistMqttProbes()
        }
        persist()
    }

    private fun customBroker(): MqttBroker? =
        MqttBrokers.parseCustom(
            mqttCustomHost,
            mqttCustomPort,
            mqttCustomTls,
            mqttCustomUsername,
            mqttCustomPassword,
        ).broker

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
        val known = MqttBrokers.ALL.map { it.id }.toSet() + MqttBrokers.CUSTOM_ID
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
            val ids = MqttBrokers.ALL.map { it.id } + MqttBrokers.CUSTOM_ID
            for (id in ids) {
                val probe = mqttProbes[id] ?: continue
                val value = probe.latencyMs?.toString() ?: (probe.error ?: "failed")
                append("$id = \"${escapeTomlString(value)}\"\n")
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
                "$KEY_FONT_SCALE = \"${fontScale}\"\n" +
                "$KEY_APPEARANCE = \"${escapeTomlString(appearanceId)}\"\n" +
                "$KEY_MQTT_BROKER = \"${escapeTomlString(mqttBrokerId)}\"\n" +
                "$KEY_CUSTOM_HOST = \"${escapeTomlString(mqttCustomHost)}\"\n" +
                "$KEY_CUSTOM_PORT = \"${escapeTomlString(mqttCustomPort)}\"\n" +
                "$KEY_CUSTOM_TLS = \"${mqttCustomTls}\"\n" +
                "$KEY_CUSTOM_USERNAME = \"${escapeTomlString(mqttCustomUsername)}\"\n" +
                "$KEY_CUSTOM_PASSWORD = \"${escapeTomlString(mqttCustomPassword)}\"\n" +
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

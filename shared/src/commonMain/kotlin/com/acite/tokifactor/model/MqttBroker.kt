package com.acite.tokifactor.model

data class MqttBroker(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val region: String,
    val username: String = "",
    val password: String = "",
) {
    val endpoint: String
        get() = "$host:$port"

    val transportLabel: String
        get() = if (tls) "TLS" else "TCP"

    /** Identity of the server and credentials. Used to drop a stale latency sample. */
    fun endpointKey(): String =
        listOf(host, port.toString(), tls.toString(), username, password).joinToString("\u0000")

    /** Identity of the active session, including which list entry is selected. */
    fun connectionKey(): String = id + "\u0000" + endpointKey()
}

data class CustomBrokerParse(
    val host: String,
    val portText: String,
    val tls: Boolean,
    val username: String,
    val password: String,
    val broker: MqttBroker?,
    val error: String?,
)

data class MqttBrokerProbe(
    val latencyMs: Long? = null,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = latencyMs != null

    fun isSlow(thresholdMs: Long = MqttBrokers.SLOW_MS): Boolean {
        if (error != null) return true
        val ms = latencyMs ?: return false
        return ms > thresholdMs
    }
}

object MqttBrokers {
    const val DEFAULT_ID = "hivemq-tls"
    const val CUSTOM_ID = "custom"
    const val SLOW_MS = 800L

    val ALL: List<MqttBroker> = listOf(
        MqttBroker(
            id = "emqx-cn",
            name = "EMQX China",
            host = "broker-cn.emqx.io",
            port = 8883,
            tls = true,
            region = "China",
        ),
        MqttBroker(
            id = "emqx-cn-tcp",
            name = "EMQX China",
            host = "broker-cn.emqx.io",
            port = 1883,
            tls = false,
            region = "China",
        ),
        MqttBroker(
            id = "mqttx",
            name = "MQTTX Public",
            host = "broker.mqttx.io",
            port = 8883,
            tls = true,
            region = "China",
        ),
        MqttBroker(
            id = "mqttx-tcp",
            name = "MQTTX Public",
            host = "broker.mqttx.io",
            port = 1883,
            tls = false,
            region = "China",
        ),
        MqttBroker(
            id = "p2hp",
            name = "P2HP",
            host = "mqtt.p2hp.com",
            port = 1883,
            tls = false,
            region = "China",
        ),
        MqttBroker(
            id = "ranye",
            name = "Ranye IoT",
            host = "test.ranye-iot.net",
            port = 1883,
            tls = false,
            region = "China",
        ),
        MqttBroker(
            id = "jmqtt",
            name = "JMQTT",
            host = "test.jmqtt.cn",
            port = 1883,
            tls = false,
            region = "China",
        ),
        MqttBroker(
            id = "hivemq-tls",
            name = "HiveMQ Public",
            host = "broker.hivemq.com",
            port = 8883,
            tls = true,
            region = "Europe",
        ),
        MqttBroker(
            id = "hivemq",
            name = "HiveMQ Public",
            host = "broker.hivemq.com",
            port = 1883,
            tls = false,
            region = "Europe",
        ),
        MqttBroker(
            id = "emqx",
            name = "EMQX Global",
            host = "broker.emqx.io",
            port = 8883,
            tls = true,
            region = "Global",
        ),
        MqttBroker(
            id = "emqx-tcp",
            name = "EMQX Global",
            host = "broker.emqx.io",
            port = 1883,
            tls = false,
            region = "Global",
        ),
        MqttBroker(
            id = "mosquitto",
            name = "Eclipse Mosquitto",
            host = "test.mosquitto.org",
            port = 8883,
            tls = true,
            region = "Europe",
        ),
        MqttBroker(
            id = "mosquitto-tcp",
            name = "Eclipse Mosquitto",
            host = "test.mosquitto.org",
            port = 1883,
            tls = false,
            region = "Europe",
        ),
        MqttBroker(
            id = "eclipse",
            name = "Eclipse Projects",
            host = "mqtt.eclipseprojects.io",
            port = 8883,
            tls = true,
            region = "Europe",
        ),
        MqttBroker(
            id = "eclipse-tcp",
            name = "Eclipse Projects",
            host = "mqtt.eclipseprojects.io",
            port = 1883,
            tls = false,
            region = "Europe",
        ),
        MqttBroker(
            id = "mqtthq",
            name = "MQTTHQ",
            host = "public.mqtthq.com",
            port = 1883,
            tls = false,
            region = "Global",
        ),
        MqttBroker(
            id = "hivemq-dashboard",
            name = "MQTT Dashboard",
            host = "www.mqtt-dashboard.com",
            port = 1883,
            tls = false,
            region = "Europe",
        ),
    )

    fun byId(id: String?): MqttBroker =
        ALL.find { it.id == id } ?: ALL.first { it.id == DEFAULT_ID }

    /**
     * Pulls a scheme or ":port" out of a host field while the user is typing or pasting.
     * Returns null when the text should stay as a plain host.
     */
    fun peelEndpoint(input: String): PeeledEndpoint? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        var tls: Boolean? = null
        var raw = trimmed
        when {
            raw.startsWith("mqtts://", ignoreCase = true) ||
                raw.startsWith("ssl://", ignoreCase = true) -> {
                tls = true
                raw = raw.substring(raw.indexOf("://") + 3)
            }
            raw.startsWith("mqtt://", ignoreCase = true) ||
                raw.startsWith("tcp://", ignoreCase = true) -> {
                tls = false
                raw = raw.substring(raw.indexOf("://") + 3)
            }
        }
        raw = raw.substringBefore('/').substringBefore('?').trim()
        if (tls == null && !raw.contains(':') && !raw.startsWith('[')) return null
        if (raw.startsWith('[')) {
            val end = raw.indexOf(']')
            if (end < 0) return null
            val host = raw.substring(0, end + 1)
            val rest = raw.substring(end + 1)
            if (rest.isEmpty()) return PeeledEndpoint(host, null, tls)
            if (!rest.startsWith(':')) return null
            val portPart = rest.substring(1)
            if (portPart.isEmpty()) return PeeledEndpoint(host, null, tls)
            val port = portPart.toIntOrNull() ?: return null
            return PeeledEndpoint(host, port, tls)
        }
        val colon = raw.lastIndexOf(':')
        if (colon < 0) {
            if (raw.isEmpty()) return null
            return PeeledEndpoint(raw, null, tls)
        }
        val hostPart = raw.substring(0, colon).trim()
        val portPart = raw.substring(colon + 1).trim()
        if (hostPart.isEmpty() || hostPart.contains(':')) return null
        if (portPart.isEmpty()) return PeeledEndpoint(hostPart, null, tls)
        if (portPart.any { !it.isDigit() }) return null
        return PeeledEndpoint(hostPart, portPart.toIntOrNull(), tls)
    }

    fun parseCustom(
        hostInput: String,
        portInput: String,
        tls: Boolean,
        username: String = "",
        password: String = "",
    ): CustomBrokerParse {
        val peeled = peelEndpoint(hostInput)
        val tlsFlag = peeled?.tls ?: tls
        var host = (peeled?.host ?: hostInput).trim()
        host = host.substringBefore('/').substringBefore('?').trim()
        if (host.startsWith('[') && host.endsWith(']') && host.length >= 2) {
            host = host.substring(1, host.length - 1)
        }
        host = host.removeSuffix(".")
        val user = username.trim()
        val portText = when {
            portInput.isNotBlank() -> portInput.trim()
            peeled?.port != null -> peeled.port.toString()
            else -> if (tlsFlag) "8883" else "1883"
        }
        fun fail(message: String) = CustomBrokerParse(
            host = host,
            portText = portText,
            tls = tlsFlag,
            username = user,
            password = password,
            broker = null,
            error = message,
        )
        if (host.isEmpty()) return fail("Enter a broker host.")
        if (!isAcceptableHost(host)) {
            return fail("Host can only contain a hostname or an IP address.")
        }
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) return fail("Port must be between 1 and 65535.")
        return CustomBrokerParse(
            host = host,
            portText = port.toString(),
            tls = tlsFlag,
            username = user,
            password = password,
            broker = MqttBroker(
                id = CUSTOM_ID,
                name = "Custom",
                host = host,
                port = port,
                tls = tlsFlag,
                region = "Custom",
                username = user,
                password = password,
            ),
            error = null,
        )
    }

    private fun isAcceptableHost(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        val ipv6 = host.contains(':')
        if (ipv6) {
            return host.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
        }
        val labels = host.split('.')
        if (labels.isEmpty()) return false
        return labels.all { label ->
            label.isNotEmpty() &&
                label.length <= 63 &&
                label.all { it.isLetterOrDigit() || it == '-' || it == '_' } &&
                !label.startsWith('-') &&
                !label.endsWith('-')
        }
    }
}

data class PeeledEndpoint(
    val host: String,
    val port: Int?,
    val tls: Boolean?,
)

package com.acite.tokifactor.model

data class MqttBroker(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val region: String,
) {
    val endpoint: String
        get() = "$host:$port"

    val transportLabel: String
        get() = if (tls) "TLS" else "TCP"
}

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
}

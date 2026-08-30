package com.acite.tokifactor.model

data class MqttMessage(
    val topic: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)
package com.acite.tokifactor.model

data class ChatAttachment(
    val id: String,
    val name: String,
    val mime: String = "",
    val filePath: String? = null,
    val size: Long = 0,
    val isTransferring: Boolean = false,
    val chunksReceived: Int = 0,
    val chunksTotal: Int = 0,
) {
    val progress: Float
        get() = if (chunksTotal <= 0) 1f else chunksReceived.toFloat() / chunksTotal.toFloat()
}

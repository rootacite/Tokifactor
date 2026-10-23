package com.acite.tokifactor.model

data class AvatarEnvelope(
    val type: String,
    val id: String,
    val hash: String,
    val data: String = "",
    val source: String = "",
) {
    fun encode(): String = WireJson.encodeObject(
        "type" to type,
        "id" to id,
        "hash" to hash,
        "data" to data,
        "source" to source,
    )

    companion object {
        const val HAVE = "have"
        const val WANT = "want"

        fun have(id: String, hash: String, data: String, source: String = ""): AvatarEnvelope =
            AvatarEnvelope(type = HAVE, id = id, hash = hash, data = data, source = source)

        fun want(id: String, hash: String): AvatarEnvelope =
            AvatarEnvelope(type = WANT, id = id, hash = hash)

        fun decode(payload: String): AvatarEnvelope? {
            val type = WireJson.extractString(payload, "type") ?: return null
            val id = WireJson.extractString(payload, "id") ?: return null
            if (id.isEmpty()) return null
            if (type != HAVE && type != WANT) return null
            val hash = WireJson.extractString(payload, "hash") ?: ""
            val data = WireJson.extractString(payload, "data") ?: ""
            val source = WireJson.extractString(payload, "source") ?: ""
            return AvatarEnvelope(type = type, id = id, hash = hash, data = data, source = source)
        }
    }
}

package com.acite.tokifactor.model

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val SOLO_START = "solo_start"
const val SOLO_READY = "solo_ready"
const val SOLO_FAILED = "solo_failed"
const val SOLO_ACTIVE = "solo_active"
const val SOLO_END = "solo_end"

const val GROUP_CONVERSATION = "group"

fun soloConversationId(memberId: String): String = "solo:$memberId"

fun deriveSoloMagic(groupMagic: String, memberId: String): String {
    require(groupMagic.isNotEmpty()) { "group magic must not be empty" }
    require(memberId.isNotEmpty()) { "member id must not be empty" }
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(groupMagic.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val digest = mac.doFinal("aki-solo-v1\u0000$memberId".toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

data class SoloState(
    val active: Boolean = false,
    val phase: String = "idle",
    val sessionId: String = "",
    val targetId: String = "",
    val targetName: String = "",
    val startedAt: String = "",
    val error: String = "",
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val memberId: String = "",
    val lastMessageAt: Long = 0L,
)

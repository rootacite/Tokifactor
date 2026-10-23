package com.acite.tokifactor.model

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

const val LEGACY_MEMBER_ID_NAMESPACE = "tokifactor-member-v1\u0000"
private val memberIdPattern = Regex("^(?:mem_[0-9a-f]{32}|legacy_[0-9a-f]{32})$")

fun canonicalMemberName(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim { it.isWhitespace() }
        .lowercase(Locale.ROOT)

fun legacyMemberId(name: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest((LEGACY_MEMBER_ID_NAMESPACE + canonicalMemberName(name)).encodeToByteArray())
    val hex = digest.joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
    return "legacy_${hex.take(32)}"
}

fun newMemberId(): String = "mem_${UUID.randomUUID().toString().replace("-", "")}"

fun isMemberId(value: String): Boolean = memberIdPattern.matches(value.trim())

data class MemberIdentity(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val kind: String = MEMBER_KIND_AGENT,
    val createdAt: String = "",
) {
    fun labels(): List<String> {
        val out = linkedMapOf<String, String>()
        (listOf(name) + aliases).forEach { raw ->
            val label = raw.trim()
            val key = canonicalMemberName(label)
            if (label.isNotEmpty() && key.isNotEmpty()) out.putIfAbsent(key, label)
        }
        return out.values.toList()
    }

    fun renamed(value: String): MemberIdentity {
        val target = value.trim()
        val nextAliases = buildList {
            addAll(aliases)
            if (name.isNotBlank() && canonicalMemberName(name) != canonicalMemberName(target)) {
                add(name)
            }
            add(target)
        }.distinctBy(::canonicalMemberName)
        return copy(
            name = target,
            aliases = nextAliases.filter {
                canonicalMemberName(it) != canonicalMemberName(target)
            },
        )
    }
}

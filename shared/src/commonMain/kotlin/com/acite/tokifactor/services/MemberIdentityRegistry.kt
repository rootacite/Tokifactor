package com.acite.tokifactor.services

import com.acite.tokifactor.appIdentitiesFile
import com.acite.tokifactor.model.MemberIdentity
import com.acite.tokifactor.model.canonicalMemberName
import com.acite.tokifactor.model.isMemberId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class IdentityLabelConflict(val label: String, val ownerId: String) :
    IllegalArgumentException("label $label already belongs to $ownerId")

@SingleIn(AppScope::class)
class MemberIdentityRegistry @Inject constructor() {
    private val json = Json { prettyPrint = true }

    fun get(memberId: String): MemberIdentity? = read().members[memberId]

    fun identities(): Map<String, MemberIdentity> = read().members

    fun resolve(label: String): String {
        val key = canonicalMemberName(label)
        if (key.isEmpty()) return ""
        val snapshot = read()
        return snapshot.labels[key] ?: ""
    }

    fun owner(label: String): MemberIdentity? = get(resolve(label))

    fun sourceFor(memberId: String): String = read().sources[memberId].orEmpty()

    /** Merge an observed identity, never stealing a label from its current owner. */
    fun observe(identity: MemberIdentity, source: String = ""): Boolean =
        mutate { data -> merge(data, identity, source, strict = false, claim = true).first }

    fun register(identity: MemberIdentity, source: String = "", strict: Boolean = false): Boolean =
        mutate { data -> merge(data, identity, source, strict, claim = true).first }

    fun rename(memberId: String, name: String): MemberIdentity = mutate { data ->
        val current = data.members[memberId] ?: error("unknown member id: $memberId")
        val renamed = current.renamed(name)
        if (renamed.name.isBlank()) error("member name must not be empty")
        val target = canonicalMemberName(renamed.name)
        val owner = data.labels[target]
        if (owner != null && owner != memberId) throw IdentityLabelConflict(renamed.name, owner)
        for (label in renamed.labels()) {
            val key = canonicalMemberName(label)
            val existing = data.labels[key]
            if (existing != null && existing != memberId) throw IdentityLabelConflict(label, existing)
        }
        renamed.labels().forEach { label -> data.labels[canonicalMemberName(label)] = memberId }
        data.members[memberId] = renamed
        renamed
    }

    private data class RegistryData(
        val members: MutableMap<String, MemberIdentity>,
        val labels: MutableMap<String, String>,
        val sources: MutableMap<String, String>,
        val conflicts: MutableList<JsonObject>,
    )

    private fun read(): RegistryData = locked { load() }

    private fun <T> mutate(block: (RegistryData) -> T): T = locked {
        val data = load()
        val result = block(data)
        save(data)
        result
    }

    private fun <T> locked(block: () -> T): T {
        val path = appIdentitiesFile()
        path.parentFile?.mkdirs()
        val lock = File(path.parentFile, "${path.name}.lock")
        FileChannel.open(
            lock.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use {
                return block()
            }
        }
    }

    private fun load(): RegistryData {
        val file = appIdentitiesFile()
        if (!file.isFile) {
            return RegistryData(mutableMapOf(), mutableMapOf(), mutableMapOf(), mutableListOf())
        }
        val root = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (_: Exception) {
            return RegistryData(mutableMapOf(), mutableMapOf(), mutableMapOf(), mutableListOf())
        }
        val members = mutableMapOf<String, MemberIdentity>()
        (root["members"] as? JsonObject)?.forEach { (id, raw) ->
            val obj = raw as? JsonObject ?: return@forEach
            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!isMemberId(id) || name.isBlank()) return@forEach
            val aliases = (obj["aliases"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            }.orEmpty()
            val createdAt = obj["createdAt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                .ifBlank { obj["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            members[id] = MemberIdentity(
                id = id,
                name = name,
                aliases = aliases,
                kind = obj["kind"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "agent" },
                createdAt = createdAt,
            )
        }
        val labels = mutableMapOf<String, String>()
        (root["labels"] as? JsonObject)?.forEach { (key, raw) ->
            val owner = raw.jsonPrimitive.contentOrNull.orEmpty()
            if (key.isNotBlank() && owner in members) labels[key] = owner
        }
        rebuildLabels(members).forEach { (key, owner) -> labels.putIfAbsent(key, owner) }
        val conflicts = (root["conflicts"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.takeLast(100)
            ?.toMutableList()
            ?: mutableListOf()
        val sources = mutableMapOf<String, String>()
        (root["sources"] as? JsonObject)?.forEach { (id, raw) ->
            val source = raw.jsonPrimitive.contentOrNull.orEmpty()
            if (id in members && source.isNotBlank()) sources[id] = source
        }
        return RegistryData(members, labels, sources, conflicts)
    }

    private fun merge(
        data: RegistryData,
        incoming: MemberIdentity,
        source: String,
        strict: Boolean,
        claim: Boolean,
    ): Pair<Boolean, List<JsonObject>> {
        val current = data.members[incoming.id]
        val identity = if (current != null) {
            incoming.copy(
                aliases = (current.aliases + incoming.aliases)
                    .distinctBy(::canonicalMemberName),
                createdAt = current.createdAt,
            )
        } else {
            incoming
        }
        val conflicts = mutableListOf<JsonObject>()
        var claimed = true
        identity.labels().forEach { label ->
            val key = canonicalMemberName(label)
            if (key.isEmpty()) return@forEach
            val owner = data.labels[key]
            if (owner != null && owner != identity.id) {
                claimed = false
                if (strict) throw IdentityLabelConflict(label, owner)
                val conflict = buildJsonObject {
                    put("label", label)
                    put("owner", owner)
                    put("member_id", identity.id)
                    put("source", source)
                    put("at", Instant.now().toString())
                }
                conflicts += conflict
                data.conflicts += conflict
                return@forEach
            }
            if (claim) data.labels[key] = identity.id
        }
        data.members[identity.id] = identity
        if (source.isNotBlank()) data.sources[identity.id] = source
        while (data.conflicts.size > 100) data.conflicts.removeAt(0)
        return claimed to conflicts
    }

    private fun rebuildLabels(members: Map<String, MemberIdentity>): Map<String, String> {
        val labels = linkedMapOf<String, String>()
        members.forEach { (id, identity) ->
            identity.labels().forEach { label ->
                val key = canonicalMemberName(label)
                if (key.isNotEmpty()) labels.putIfAbsent(key, id)
            }
        }
        return labels
    }

    private fun save(data: RegistryData) {
        val path = appIdentitiesFile()
        path.parentFile?.mkdirs()
        val members = buildJsonObject {
            data.members.forEach { (id, identity) ->
                put(id, buildJsonObject {
                    put("id", identity.id)
                    put("name", identity.name)
                    put("aliases", buildJsonArray {
                        identity.aliases.forEach { add(JsonPrimitive(it)) }
                    })
                    put("kind", identity.kind)
                    put("createdAt", identity.createdAt.ifBlank { Instant.now().toString() })
                })
            }
        }
        val labels = buildJsonObject {
            data.labels.forEach { (key, owner) -> put(key, owner) }
        }
        val sources = buildJsonObject {
            data.sources.forEach { (id, source) -> put(id, source) }
        }
        val root = buildJsonObject {
            put("version", 1)
            put("members", members)
            put("labels", labels)
            put("sources", sources)
            put("conflicts", JsonArray(data.conflicts.toList()))
        }
        val tmp = File(path.parentFile, ".${path.name}.tmp")
        tmp.writeText(json.encodeToString(JsonElement.serializer(), root) + "\n")
        try {
            Files.move(
                tmp.toPath(),
                path.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(tmp.toPath(), path.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

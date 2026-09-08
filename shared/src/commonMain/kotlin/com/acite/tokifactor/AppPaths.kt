package com.acite.tokifactor

import java.io.File

expect fun appConfigFile(): File

expect fun defaultDisplayName(): String

fun appAvatarFile(): File = File(requireNotNull(appConfigFile().parentFile), "avatar")

fun appMqttProbesFile(): File = File(requireNotNull(appConfigFile().parentFile), "mqtt-probes.toml")

fun appPeerAvatarsDir(): File = File(requireNotNull(appConfigFile().parentFile), "peer-avatars")

fun appHistoryDir(): File = File(requireNotNull(appConfigFile().parentFile), "history")

fun appHistoryIndexFile(): File = File(appHistoryDir(), "index.jsonl")

fun appHistoryBlobsDir(): File = File(appHistoryDir(), "blobs")

expect suspend fun copyFileToPlatformFile(source: File, dest: io.github.vinceglb.filekit.core.PlatformFile)

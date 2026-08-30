package com.acite.tokifactor

import java.io.File

expect fun appConfigFile(): File

expect fun defaultDisplayName(): String

fun appAvatarFile(): File = File(requireNotNull(appConfigFile().parentFile), "avatar")

fun appMqttProbesFile(): File = File(requireNotNull(appConfigFile().parentFile), "mqtt-probes.toml")

expect suspend fun copyFileToPlatformFile(source: File, dest: io.github.vinceglb.filekit.core.PlatformFile)

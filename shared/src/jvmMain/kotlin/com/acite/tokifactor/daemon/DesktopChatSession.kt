package com.acite.tokifactor.daemon

import com.acite.tokifactor.services.ChatEngine
import com.acite.tokifactor.services.ChatSession
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopChatSession @Inject constructor(
    daemonChatSession: DaemonChatSession,
    embeddedEngine: ChatEngine,
) : ChatSession by if (isLinux()) daemonChatSession else embeddedEngine

private fun isLinux(): Boolean = System.getProperty("os.name")
    .orEmpty()
    .lowercase()
    .contains("linux")

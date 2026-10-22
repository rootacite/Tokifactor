package com.acite.tokifactor

import com.acite.tokifactor.services.ChatEngine
import com.acite.tokifactor.services.ChatSession
import com.acite.tokifactor.services.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    val chatEngine: ChatEngine
    val chatSession: ChatSession
    val settingsStore: SettingsStore
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidChatSession @Inject constructor(
    private val chatEngine: ChatEngine,
) : ChatSession by chatEngine

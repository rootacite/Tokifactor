package com.acite.tokifactor

import com.acite.tokifactor.services.ChatEngine
import com.acite.tokifactor.services.ChatSession
import com.acite.tokifactor.services.MqttService
import com.acite.tokifactor.services.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    val chatSession: ChatSession
    val settingsStore: SettingsStore
}

@DependencyGraph(AppScope::class)
interface DaemonGraph {
    val chatEngine: ChatEngine
    val settingsStore: SettingsStore
    val mqttService: MqttService
}

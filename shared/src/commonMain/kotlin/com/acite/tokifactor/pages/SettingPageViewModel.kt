package com.acite.tokifactor.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acite.tokifactor.model.MqttBrokerProbe
import com.acite.tokifactor.model.MqttBrokers
import com.acite.tokifactor.services.MqttService
import com.acite.tokifactor.services.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SettingPageViewModel(
    private val settingsStore: SettingsStore,
    private val mqttService: MqttService,
) : ViewModel() {

    var magicString by mutableStateOf(settingsStore.magicString)

    var displayName by mutableStateOf(settingsStore.displayName)

    var selectedBrokerId by mutableStateOf(settingsStore.mqttBrokerId)

    var avatarBytes by mutableStateOf(settingsStore.avatarBytes)

    var avatarError by mutableStateOf<String?>(null)
        private set

    var probing by mutableStateOf(false)
        private set

    val probeById = mutableStateMapOf<String, MqttBrokerProbe>().apply {
        putAll(settingsStore.mqttProbes)
    }

    val brokers get() = MqttBrokers.ALL

    fun reload() {
        magicString = settingsStore.magicString
        displayName = settingsStore.displayName
        selectedBrokerId = settingsStore.mqttBrokerId
        avatarBytes = settingsStore.avatarBytes
        avatarError = null
    }

    fun clearAvatar() {
        avatarBytes = null
        avatarError = null
    }

    fun pickAvatar(file: PlatformFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = try {
                file.readBytes()
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    avatarError = "Could not read that image."
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                when {
                    bytes.isEmpty() -> avatarError = "That file is empty."
                    bytes.size > SettingsStore.MAX_AVATAR_BYTES -> {
                        avatarError =
                            "Avatar must be under ${SettingsStore.MAX_AVATAR_BYTES / (1024 * 1024)} MB."
                    }
                    else -> {
                        avatarBytes = bytes
                        avatarError = null
                    }
                }
            }
        }
    }

    fun testAllBrokers() {
        if (probing) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { probing = true }
            coroutineScope {
                val gate = Semaphore(PROBE_CONCURRENCY)
                MqttBrokers.ALL.map { broker ->
                    async {
                        gate.withPermit {
                            val result = mqttService.probe(broker)
                            withContext(Dispatchers.Main) {
                                probeById[broker.id] = result
                            }
                            settingsStore.updateMqttProbe(broker.id, result)
                        }
                    }
                }.awaitAll()
            }
            withContext(Dispatchers.Main) {
                probing = false
            }
        }
    }

    fun save() {
        settingsStore.updateMagicString(magicString)
        settingsStore.updateDisplayName(displayName)
        settingsStore.updateMqttBroker(selectedBrokerId)
        settingsStore.updateAvatar(avatarBytes)
        reload()
    }

    companion object {
        private const val PROBE_CONCURRENCY = 6
    }
}

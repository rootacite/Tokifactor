package com.acite.tokifactor.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acite.tokifactor.model.MqttBroker
import com.acite.tokifactor.model.MqttBrokerProbe
import com.acite.tokifactor.model.MqttBrokers
import com.acite.tokifactor.services.ChatEngine
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
    private val chatEngine: ChatEngine,
) : ViewModel() {

    var magicString by mutableStateOf(settingsStore.magicString)

    var displayName by mutableStateOf(settingsStore.displayName)

    var selectedBrokerId by mutableStateOf(settingsStore.mqttBrokerId)

    var customHost by mutableStateOf(settingsStore.mqttCustomHost)

    var customPort by mutableStateOf(settingsStore.mqttCustomPort)

    var customTls by mutableStateOf(settingsStore.mqttCustomTls)

    var customUsername by mutableStateOf(settingsStore.mqttCustomUsername)

    var customPassword by mutableStateOf(settingsStore.mqttCustomPassword)

    var avatarBytes by mutableStateOf(settingsStore.avatarBytes)

    var avatarError by mutableStateOf<String?>(null)
        private set

    var probing by mutableStateOf(false)
        private set

    var customProbing by mutableStateOf(false)
        private set

    val probeById = mutableStateMapOf<String, MqttBrokerProbe>().apply {
        putAll(settingsStore.mqttProbes)
    }

    private var customProbeSnapshot: MqttBrokerProbe? = settingsStore.mqttProbes[MqttBrokers.CUSTOM_ID]
    private var customProbeKey: String? =
        settingsStore.customEndpointKey()?.takeIf { customProbeSnapshot != null }

    val brokers get() = MqttBrokers.ALL

    val customFormError: String?
        get() {
            if (selectedBrokerId != MqttBrokers.CUSTOM_ID) return null
            if (customHost.isBlank()) return null
            return currentParse().error
        }

    val canTestCustom: Boolean
        get() = !probing && !customProbing && currentParse().broker != null

    fun reload() {
        magicString = settingsStore.magicString
        displayName = settingsStore.displayName
        selectedBrokerId = settingsStore.mqttBrokerId
        customHost = settingsStore.mqttCustomHost
        customPort = settingsStore.mqttCustomPort
        customTls = settingsStore.mqttCustomTls
        customUsername = settingsStore.mqttCustomUsername
        customPassword = settingsStore.mqttCustomPassword
        avatarBytes = settingsStore.avatarBytes
        avatarError = null
        syncCustomProbeDisplay()
    }

    fun selectCustom() {
        selectedBrokerId = MqttBrokers.CUSTOM_ID
        syncCustomProbeDisplay()
    }

    fun onCustomHostChange(value: String) {
        val peeled = MqttBrokers.peelEndpoint(value)
        if (peeled != null) {
            customHost = peeled.host
            if (peeled.port != null) customPort = peeled.port.toString()
            if (peeled.tls != null) customTls = peeled.tls
        } else {
            customHost = value
        }
        selectedBrokerId = MqttBrokers.CUSTOM_ID
        syncCustomProbeDisplay()
    }

    fun onCustomPortChange(value: String) {
        customPort = value.filter { it.isDigit() }.take(5)
        selectedBrokerId = MqttBrokers.CUSTOM_ID
        syncCustomProbeDisplay()
    }

    fun onCustomTlsChange(enabled: Boolean) {
        val current = customPort.trim()
        customTls = enabled
        customPort = when {
            enabled && (current.isEmpty() || current == "1883") -> "8883"
            !enabled && (current.isEmpty() || current == "8883") -> "1883"
            else -> customPort
        }
        selectedBrokerId = MqttBrokers.CUSTOM_ID
        syncCustomProbeDisplay()
    }

    fun onCustomUsernameChange(value: String) {
        customUsername = value
        selectedBrokerId = MqttBrokers.CUSTOM_ID
        syncCustomProbeDisplay()
    }

    fun onCustomPasswordChange(value: String) {
        customPassword = value
        selectedBrokerId = MqttBrokers.CUSTOM_ID
        syncCustomProbeDisplay()
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
                val custom = currentParse().broker
                val targets = if (custom != null) MqttBrokers.ALL + custom else MqttBrokers.ALL
                targets.map { broker ->
                    async {
                        gate.withPermit {
                            val result = mqttService.probe(broker)
                            withContext(Dispatchers.Main) {
                                recordProbe(broker, result)
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

    fun testCustom() {
        if (!canTestCustom) return
        val broker = currentParse().broker ?: return
        selectedBrokerId = MqttBrokers.CUSTOM_ID
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { customProbing = true }
            val result = mqttService.probe(broker)
            withContext(Dispatchers.Main) {
                recordProbe(broker, result)
                customProbing = false
            }
            settingsStore.updateMqttProbe(broker.id, result)
        }
    }

    /** Persists the form. Returns false when a custom broker was chosen but the address is invalid. */
    fun save(): Boolean {
        settingsStore.updateMagicString(magicString)
        settingsStore.updateDisplayName(displayName)
        settingsStore.updateAvatar(avatarBytes)
        val parsed = currentParse()
        if (selectedBrokerId == MqttBrokers.CUSTOM_ID &&
            customHost.isNotBlank() &&
            parsed.broker == null
        ) {
            return false
        }
        val selected = if (selectedBrokerId == MqttBrokers.CUSTOM_ID && customHost.isBlank()) {
            settingsStore.mqttBrokerId.takeUnless { it == MqttBrokers.CUSTOM_ID }
                ?: MqttBrokers.DEFAULT_ID
        } else {
            selectedBrokerId
        }
        val previousProbe = customProbeSnapshot
        val previousProbeKey = customProbeKey
        settingsStore.updateMqttSelection(selected, parsed)
        val savedKey = settingsStore.customEndpointKey()
        if (previousProbe != null && savedKey != null && savedKey == previousProbeKey) {
            settingsStore.updateMqttProbe(MqttBrokers.CUSTOM_ID, previousProbe)
        } else if (savedKey != previousProbeKey) {
            probeById.remove(MqttBrokers.CUSTOM_ID)
            customProbeSnapshot = null
            customProbeKey = null
        }
        reload()
        return true
    }

    fun clearChatHistory() {
        chatEngine.clearHistory()
    }

    private fun currentParse() = MqttBrokers.parseCustom(
        customHost,
        customPort,
        customTls,
        customUsername,
        customPassword,
    )

    private fun recordProbe(broker: MqttBroker, result: MqttBrokerProbe) {
        probeById[broker.id] = result
        if (broker.id == MqttBrokers.CUSTOM_ID) {
            customProbeSnapshot = result
            customProbeKey = broker.endpointKey()
        }
    }

    private fun syncCustomProbeDisplay() {
        val key = currentParse().broker?.endpointKey()
        val snapshot = customProbeSnapshot
        if (key != null && key == customProbeKey && snapshot != null) {
            probeById[MqttBrokers.CUSTOM_ID] = snapshot
        } else {
            probeById.remove(MqttBrokers.CUSTOM_ID)
        }
    }

    companion object {
        private const val PROBE_CONCURRENCY = 6
    }
}

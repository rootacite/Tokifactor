package com.acite.tokifactor.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.acite.tokifactor.model.MqttBroker
import com.acite.tokifactor.model.MqttBrokerProbe
import com.acite.tokifactor.model.MqttBrokers
import com.acite.tokifactor.services.SettingsStore
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPage(
    onBack: () -> Unit,
    viewModel: SettingPageViewModel = metroViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.reload()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.save() }
    }

    var clearStep by remember { mutableStateOf(0) }
    var historyCleared by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberFilePickerLauncher(
        type = PickerType.Image
    ) { file: PlatformFile? ->
        if (file != null) viewModel.pickAvatar(file)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.save()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.save()
                        onBack()
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { avatarPickerLauncher.launch() },
                    contentAlignment = Alignment.Center
                ) {
                    val photo = viewModel.avatarBytes
                    if (photo != null) {
                        ByteImage(
                            imageBytes = photo,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Avatar", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Shown next to your messages on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(onClick = { avatarPickerLauncher.launch() }) {
                            Text("Choose photo")
                        }
                        if (viewModel.avatarBytes != null) {
                            TextButton(onClick = { viewModel.clearAvatar() }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
            val error = viewModel.avatarError
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            OutlinedTextField(
                value = viewModel.displayName,
                onValueChange = { viewModel.displayName = it },
                label = { Text("Display name") },
                supportingText = { Text("Shown to other devices. Blank restores the device name.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.magicString,
                onValueChange = { viewModel.magicString = it },
                label = { Text("Magic string") },
                placeholder = { Text(SettingsStore.DEFAULT_MAGIC_STRING) },
                supportingText = {
                    Text("Shared secret used to encrypt messages. Must match on both devices. Default: ${SettingsStore.DEFAULT_MAGIC_STRING}")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            BrokerSelector(
                brokers = viewModel.brokers,
                selectedId = viewModel.selectedBrokerId,
                probing = viewModel.probing,
                probeById = viewModel.probeById,
                onSelect = { viewModel.selectedBrokerId = it },
                onTestAll = { viewModel.testAllBrokers() },
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Chat history", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Messages, photos, and files on this device. Clearing does not tell other peers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { clearStep = 1 }) {
                    Text(
                        text = "Clear chat history",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (historyCleared) {
                    Text(
                        text = "Chat history cleared.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (clearStep == 1) {
        AlertDialog(
            onDismissRequest = { clearStep = 0 },
            title = { Text("Clear chat history?") },
            text = {
                Text("This deletes every message, photo, and file stored on this device.")
            },
            confirmButton = {
                TextButton(onClick = { clearStep = 2 }) {
                    Text("Continue", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearStep = 0 }) {
                    Text("Cancel")
                }
            },
        )
    } else if (clearStep == 2) {
        AlertDialog(
            onDismissRequest = { clearStep = 0 },
            title = { Text("This cannot be undone") },
            text = {
                Text("Clear all chat history now?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChatHistory()
                        historyCleared = true
                        clearStep = 0
                    },
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearStep = 0 }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BrokerSelector(
    brokers: List<MqttBroker>,
    selectedId: String,
    probing: Boolean,
    probeById: Map<String, MqttBrokerProbe>,
    onSelect: (String) -> Unit,
    onTestAll: () -> Unit,
) {
    var showSlow by remember { mutableStateOf(false) }
    val folded = brokers.filter { broker ->
        broker.id != selectedId && (probeById[broker.id]?.isSlow() == true)
    }
    val visible = brokers.filter { broker -> folded.none { it.id == broker.id } }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("MQTT broker", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Same node on both devices. >${MqttBrokers.SLOW_MS} ms folds away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (probing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            TextButton(onClick = onTestAll, enabled = !probing) {
                Text(if (probing) "Testing…" else "Test all")
            }
        }
        visible.forEach { broker ->
            BrokerRow(
                broker = broker,
                selected = broker.id == selectedId,
                probing = probing,
                probe = probeById[broker.id],
                onSelect = { onSelect(broker.id) },
            )
        }
        if (folded.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSlow = !showSlow }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (showSlow) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showSlow) "Hide slow nodes" else "Show slow nodes",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${folded.size} slow or unreachable",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            AnimatedVisibility(visible = showSlow) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    folded.forEach { broker ->
                        BrokerRow(
                            broker = broker,
                            selected = false,
                            probing = probing,
                            probe = probeById[broker.id],
                            onSelect = { onSelect(broker.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrokerRow(
    broker: MqttBroker,
    selected: Boolean,
    probing: Boolean,
    probe: MqttBrokerProbe?,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(background)
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp, end = 6.dp)
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent
                )
        )
        Text(
            text = broker.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = broker.transportLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        QualityMark(
            probing = probing,
            probe = probe,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun QualityMark(
    probing: Boolean,
    probe: MqttBrokerProbe?,
    modifier: Modifier = Modifier,
) {
    val quality = qualityOf(probe, darkTheme = isSystemInDarkTheme())
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            quality != null -> {
                SignalBars(level = quality.level, color = quality.color)
                Text(
                    text = quality.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = quality.color,
                )
            }
            probing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                )
            }
        }
    }
}

private data class LinkQuality(
    val level: Int,
    val color: Color,
    val label: String,
)

private fun qualityOf(probe: MqttBrokerProbe?, darkTheme: Boolean): LinkQuality? {
    val ms = probe?.latencyMs
    val excellent = if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
    val good = if (darkTheme) Color(0xFFAED581) else Color(0xFF558B2F)
    val fair = if (darkTheme) Color(0xFFFFD54F) else Color(0xFFF9A825)
    val slow = if (darkTheme) Color(0xFFFFB74D) else Color(0xFFEF6C00)
    val bad = if (darkTheme) Color(0xFFEF9A9A) else Color(0xFFC62828)
    return when {
        probe == null -> null
        ms != null && ms <= 150L -> LinkQuality(4, excellent, "$ms")
        ms != null && ms <= 300L -> LinkQuality(3, good, "$ms")
        ms != null && ms <= 500L -> LinkQuality(2, fair, "$ms")
        ms != null && ms <= MqttBrokers.SLOW_MS -> LinkQuality(1, slow, "$ms")
        ms != null -> LinkQuality(0, bad, "$ms")
        else -> LinkQuality(0, bad, if (probe.error == "timeout") "TO" else "ERR")
    }
}

@Composable
private fun SignalBars(level: Int, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(4) { index ->
            val on = index < level
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((5 + index * 3).dp)
                    .clip(RoundedCornerShape(0.5.dp))
                    .background(if (on) color else color.copy(alpha = 0.22f))
            )
        }
    }
}

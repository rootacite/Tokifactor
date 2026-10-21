package com.acite.tokifactor.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.acite.tokifactor.OnSystemBack
import com.acite.tokifactor.model.MqttBroker
import com.acite.tokifactor.model.MqttBrokerProbe
import com.acite.tokifactor.model.MqttBrokers
import com.acite.tokifactor.services.SettingsStore
import com.acite.tokifactor.ui.theme.Appearance
import com.acite.tokifactor.ui.theme.TokiCapsuleButton
import com.acite.tokifactor.ui.theme.TokiChoiceRow
import com.acite.tokifactor.ui.theme.TokiQuietTextButton
import com.acite.tokifactor.ui.theme.TokiSection
import com.acite.tokifactor.ui.theme.TokiSurface
import com.acite.tokifactor.ui.theme.tokiColors
import com.acite.tokifactor.ui.theme.tokiTextFieldColors
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlin.math.roundToInt

@Composable
fun SettingPage(
    onBack: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onAppearanceChange: (String) -> Unit,
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
    val leave = {
        if (viewModel.save()) onBack()
    }
    OnSystemBack(enabled = true) { leave() }

    val avatarPickerLauncher = rememberFilePickerLauncher(
        type = PickerType.Image
    ) { file: PlatformFile? ->
        if (file != null) viewModel.pickAvatar(file)
    }

    val colors = tokiColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SettingsHeader(onBack = leave, onSave = leave)

        TokiSection(
            title = "Appearance",
            hint = "Choose the room's light. Changes preview immediately and save with your settings.",
        ) {
            Appearance.all.forEach { appearance ->
                val selected = appearance.id == viewModel.appearanceId
                TokiChoiceRow(
                    selected = selected,
                    onClick = {
                        viewModel.appearanceId = appearance.id
                        onAppearanceChange(appearance.id)
                    },
                ) {
                    PaletteSwatch(appearance)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            appearance.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                        )
                        Text(
                            appearance.blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDim,
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.accentPink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        TokiSection(title = "Profile", hint = "Shown next to your messages on this device.") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.bgCard)
                        .border(1.dp, colors.stroke.copy(alpha = 0.8f), CircleShape)
                        .clickable { avatarPickerLauncher.launch() },
                    contentAlignment = Alignment.Center,
                ) {
                    val photo = viewModel.avatarBytes
                    if (photo != null) {
                        ByteImage(
                            imageBytes = photo,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = colors.accentLilac,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Avatar", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Tap the circle to choose a photo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim,
                    )
                    Row {
                        TokiQuietTextButton(
                            text = "Choose photo",
                            onClick = { avatarPickerLauncher.launch() },
                        )
                        if (viewModel.avatarBytes != null) {
                            TokiQuietTextButton(
                                text = "Remove",
                                onClick = viewModel::clearAvatar,
                                color = colors.error,
                            )
                        }
                    }
                }
            }
            val error = viewModel.avatarError
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
            OutlinedTextField(
                value = viewModel.displayName,
                onValueChange = { viewModel.displayName = it },
                label = { Text("Display name") },
                supportingText = { Text("Blank restores the device name.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = tokiTextFieldColors(),
            )
        }

        FontSizeSetting(
            fontScale = viewModel.fontScale,
            onFontScaleChange = { value ->
                viewModel.fontScale = value
                onFontScaleChange(value)
            },
        )

        TokiSection(title = "Security", hint = "Both devices must use exactly the same magic string.") {
            OutlinedTextField(
                value = viewModel.magicString,
                onValueChange = { viewModel.magicString = it },
                label = { Text("Magic string") },
                placeholder = { Text(SettingsStore.DEFAULT_MAGIC_STRING) },
                supportingText = {
                    Text("Shared secret used to encrypt messages. Default: ${SettingsStore.DEFAULT_MAGIC_STRING}")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = tokiTextFieldColors(),
            )
        }

        BrokerSelector(viewModel)

        TokiSection(
            title = "Chat history",
            hint = "Messages, photos, and files on this device. Clearing does not tell other peers.",
        ) {
            TokiCapsuleButton(
                text = "Clear chat history",
                onClick = { clearStep = 1 },
                accent = colors.error,
                modifier = Modifier.fillMaxWidth(),
            )
            if (historyCleared) {
                Text(
                    text = "Chat history cleared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
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
private fun SettingsHeader(
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = tokiColors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokiQuietTextButton(text = "Back", onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            TokiQuietTextButton(text = "Save", onClick = onSave, color = colors.accentPink)
        }
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.text,
        )
        Text(
            text = "tokifactor  ·  local channel",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDim,
        )
    }
}

@Composable
private fun PaletteSwatch(appearance: Appearance) {
    Canvas(Modifier.size(44.dp)) {
        val radius = 11.dp.toPx()
        val cy = size.height / 2f
        drawCircle(appearance.palette.accentPink, radius, Offset(11.dp.toPx(), cy))
        drawCircle(appearance.palette.accentBlue, radius, Offset(size.width / 2f, cy))
        drawCircle(appearance.palette.accentLilac, radius, Offset(size.width - 11.dp.toPx(), cy))
    }
}

@Composable
private fun FontSizeSetting(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
) {
    val colors = tokiColors
    TokiSection(title = "Text size", hint = "Scales chat, controls, and settings on this device.") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Preview size",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textDim,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(fontScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentPink,
            )
        }
        Slider(
            value = fontScale,
            onValueChange = { value ->
                onFontScaleChange((value * 20).roundToInt() / 20f)
            },
            valueRange = SettingsStore.MIN_FONT_SCALE..SettingsStore.MAX_FONT_SCALE,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Smaller",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textDim,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (fontScale != SettingsStore.DEFAULT_FONT_SCALE) {
                TokiQuietTextButton(
                    text = "Reset",
                    onClick = { onFontScaleChange(SettingsStore.DEFAULT_FONT_SCALE) },
                )
            }
            Text(
                text = "Larger",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textDim,
            )
        }
    }
}

@Composable
private fun BrokerSelector(viewModel: SettingPageViewModel) {
    val brokers = viewModel.brokers
    val selectedId = viewModel.selectedBrokerId
    val probing = viewModel.probing
    val probeById = viewModel.probeById
    var showSlow by remember { mutableStateOf(false) }
    val folded = brokers.filter { broker ->
        broker.id != selectedId && (probeById[broker.id]?.isSlow() == true)
    }
    val visible = brokers.filter { broker -> folded.none { it.id == broker.id } }
    val colors = tokiColors

    TokiSection(
        title = "Connection",
        hint = "Custom address or a preset. Both devices must use the same broker. >${MqttBrokers.SLOW_MS} ms folds away.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MQTT broker",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textDim,
                modifier = Modifier.weight(1f),
            )
            if (probing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.accentPink,
                )
            }
            if (!probing) {
                TokiQuietTextButton(
                    text = "Test all",
                    onClick = viewModel::testAllBrokers,
                )
            } else {
                Text(
                    text = "Testing…",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textDim,
                )
            }
        }
        CustomBrokerEditor(viewModel)
        visible.forEach { broker ->
            BrokerRow(
                broker = broker,
                selected = broker.id == selectedId,
                probing = probing,
                probe = probeById[broker.id],
                onSelect = { viewModel.selectedBrokerId = broker.id },
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
                    tint = colors.textDim,
                )
                Text(
                    text = "${folded.size} slow or unreachable",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textDim,
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
                            onSelect = { viewModel.selectedBrokerId = broker.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomBrokerEditor(viewModel: SettingPageViewModel) {
    val selected = viewModel.selectedBrokerId == MqttBrokers.CUSTOM_ID
    val error = viewModel.customFormError
    val parsed = MqttBrokers.parseCustom(
        viewModel.customHost,
        viewModel.customPort,
        viewModel.customTls,
        viewModel.customUsername,
        viewModel.customPassword,
    )
    var showPassword by remember { mutableStateOf(false) }
    val colors = tokiColors
    val shape = RoundedCornerShape(16.dp)
    val background = if (selected) {
        colors.accentPink.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.025f)
    }
    TokiSurface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = shape,
        color = background,
        borderColor = if (selected) colors.accentPink.copy(alpha = 0.30f) else colors.stroke.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = viewModel::selectCustom),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .padding(vertical = 9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) colors.accentPink else Color.Transparent),
                )
                Text(
                    text = parsed.broker?.let { "Custom  ${it.endpoint}" } ?: "Custom",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                QualityMark(
                    probing = viewModel.customProbing || viewModel.probing,
                    probe = viewModel.probeById[MqttBrokers.CUSTOM_ID],
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            OutlinedTextField(
                value = viewModel.customHost,
                onValueChange = viewModel::onCustomHostChange,
                label = { Text("Host") },
                placeholder = { Text("broker.example.com") },
                supportingText = {
                    val hostError = error?.takeUnless { it.startsWith("Port") }
                    Text(hostError ?: "Hostname, host:port, or mqtts://host:port")
                },
                isError = error != null && !error.startsWith("Port"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = tokiTextFieldColors(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = viewModel.customPort,
                    onValueChange = viewModel::onCustomPortChange,
                    label = { Text("Port") },
                    placeholder = { Text(if (viewModel.customTls) "8883" else "1883") },
                    isError = error?.startsWith("Port") == true,
                    supportingText = if (error?.startsWith("Port") == true) {
                        { Text(error) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = tokiTextFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TLS", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Switch(
                        checked = viewModel.customTls,
                        onCheckedChange = viewModel::onCustomTlsChange,
                    )
                }
            }
            OutlinedTextField(
                value = viewModel.customUsername,
                onValueChange = viewModel::onCustomUsernameChange,
                label = { Text("Username") },
                supportingText = { Text("Optional. Leave blank for anonymous brokers.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = tokiTextFieldColors(),
            )
            OutlinedTextField(
                value = viewModel.customPassword,
                onValueChange = viewModel::onCustomPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = colors.textDim,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = tokiTextFieldColors(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (viewModel.customProbing) {
                    Text(
                        text = "Testing…",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textDim,
                    )
                } else {
                    TokiCapsuleButton(
                        text = "Test",
                        onClick = viewModel::testCustom,
                        enabled = viewModel.canTestCustom,
                        accent = colors.accentBlue,
                    )
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
    val colors = tokiColors
    val shape = RoundedCornerShape(14.dp)
    val background = if (selected) {
        colors.accentPink.copy(alpha = 0.13f)
    } else {
        Color.White.copy(alpha = 0.03f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(background)
            .border(
                1.dp,
                if (selected) colors.accentPink.copy(alpha = 0.28f) else colors.stroke.copy(alpha = 0.38f),
                shape,
            )
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
                    if (selected) colors.accentPink
                    else Color.Transparent
                )
        )
        Text(
            text = broker.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = broker.transportLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textDim,
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
    val colors = tokiColors
    val quality = qualityOf(probe)
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
                    color = colors.accentPink,
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

@Composable
private fun qualityOf(probe: MqttBrokerProbe?): LinkQuality? {
    val colors = tokiColors
    val ms = probe?.latencyMs
    val excellent = colors.qualityGreen
    val good = colors.qualityMint
    val fair = colors.qualityYellow
    val slow = colors.qualityOrange
    val bad = colors.qualityRed
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

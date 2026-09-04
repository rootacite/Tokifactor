package com.acite.tokifactor.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.FileChunk
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import com.acite.tokifactor.OnSystemBack
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.random.Random

@Composable
fun MessageImage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val pic = message.pic
    val path = message.filePath
    when {
        pic != null -> ByteImage(
            imageBytes = pic,
            modifier = modifier,
            contentScale = contentScale,
        )
        !path.isNullOrBlank() -> {
            val context = LocalPlatformContext.current
            val imageRequest = ImageRequest.Builder(context)
                .data(File(path))
                .crossfade(true)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            AsyncImage(
                model = imageRequest,
                contentDescription = "Image",
                contentScale = contentScale,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun ByteImage(
    imageBytes: ByteArray,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalPlatformContext.current

    val imageRequest = ImageRequest.Builder(context)
        .data(imageBytes)
        .crossfade(true)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()

    AsyncImage(
        model = imageRequest,
        contentDescription = "Image",
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
fun MainPage(
    onOpenSettings: () -> Unit,
    viewModel: MainPageViewModel = metroViewModel(),
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var lastCount by remember { mutableIntStateOf(-1) }

    val imagePickerLauncher = rememberFilePickerLauncher(
        type = PickerType.Image
    ) { file: PlatformFile? ->
        if (file != null) {
            viewModel.sendPicture(file = file)
        }
    }

    val filePickerLauncher = rememberFilePickerLauncher(
        type = PickerType.File()
    ) { file: PlatformFile? ->
        if (file != null) {
            viewModel.sendFile(file = file)
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
        confirmDelete = false
    }

    fun enterSelection(id: String) {
        if (!selectionMode) {
            selectionMode = true
            selectedIds.clear()
            selectedIds.add(id)
        } else if (id in selectedIds) {
            selectedIds.remove(id)
            if (selectedIds.isEmpty()) exitSelection()
        } else {
            selectedIds.add(id)
        }
    }

    fun toggleSelected(id: String) {
        if (id in selectedIds) {
            selectedIds.remove(id)
            if (selectedIds.isEmpty()) exitSelection()
        } else {
            selectedIds.add(id)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }

    LaunchedEffect(viewModel.messages.size) {
        val size = viewModel.messages.size
        if (size > 0 && size > lastCount) {
            if (lastCount <= 0) {
                listState.scrollToItem(size - 1)
            } else {
                listState.animateScrollToItem(size - 1)
            }
        }
        lastCount = size
    }

    OnSystemBack(enabled = selectionMode) {
        exitSelection()
    }

    val listTopPad by animateDpAsState(
        targetValue = if (selectionMode) 56.dp else 16.dp,
        animationSpec = tween(220),
        label = "listTopPad",
    )

    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = listTopPad,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = viewModel.messages,
                key = { it.id }
            ) { message ->
                val selected = message.id in selectedIds
                SelectableMessageRow(
                    selected = selected,
                    selectionMode = selectionMode,
                    onToggle = { toggleSelected(message.id) },
                    onLongPress = { enterSelection(message.id) },
                    modifier = Modifier.animateItem(),
                ) {
                    when {
                        message.isSystem -> SystemChip(message = message)
                        message.isFile -> FileBubble(
                            message = message,
                            mineAvatar = viewModel.avatarBytes,
                            peerAvatar = viewModel.peerAvatars[message.senderId],
                            onSaveTo = { dest -> viewModel.saveMessageFile(message, dest) },
                            onCancel = if (message.isMine && message.isTransferring) {
                                { viewModel.cancelOutgoing(message.id) }
                            } else {
                                null
                            },
                        )
                        message.isImage -> ImageBubble(
                            message = message,
                            mineAvatar = viewModel.avatarBytes,
                            peerAvatar = viewModel.peerAvatars[message.senderId],
                            onCancel = if (message.isMine && message.isTransferring) {
                                { viewModel.cancelOutgoing(message.id) }
                            } else {
                                null
                            },
                        )
                        else -> TextBubble(
                            message = message,
                            mineAvatar = viewModel.avatarBytes,
                            peerAvatar = viewModel.peerAvatars[message.senderId],
                            onCancel = if (message.isMine && message.isTransferring) {
                                { viewModel.cancelOutgoing(message.id) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { exitSelection() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel selection",
                        )
                    }
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { if (selectedIds.isNotEmpty()) confirmDelete = true },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = if (selectedIds.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !selectionMode,
            enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(180)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Type a message...") },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }

                SendActionButton(
                    connected = viewModel.connected,
                    canSend = viewModel.connected && inputText.isNotBlank(),
                    onSend = {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                    },
                    onPickFile = { filePickerLauncher.launch() },
                    onPickPhoto = { imagePickerLauncher.launch() },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(180)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FilledTonalButton(
                        onClick = { if (selectedIds.isNotEmpty()) confirmDelete = true },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (count == 1) "Delete message?" else "Delete $count messages?") },
            text = { Text("This removes them from this device. It cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessages(selectedIds.toList())
                        exitSelection()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private enum class MenuAction { File, Photo, Settings }

private enum class ComposerDragLock { None, Send, Menu, Ignore }

@Composable
private fun SendActionButton(
    connected: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onPickFile: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val slopPx = with(density) { 18.dp.toPx() }
    val sendThresholdPx = with(density) { 40.dp.toPx() }
    val menuThresholdPx = with(density) { 32.dp.toPx() }
    val stackStepPx = with(density) { 56.dp.toPx() }
    val itemHitRadiusPx = with(density) { 32.dp.toPx() }
    val maxNudgePx = with(density) { 20.dp.toPx() }

    val currentConnected by rememberUpdatedState(connected)
    val currentCanSend by rememberUpdatedState(canSend)
    val currentOnSend by rememberUpdatedState(onSend)
    val currentOnPickFile by rememberUpdatedState(onPickFile)
    val currentOnPickPhoto by rememberUpdatedState(onPickPhoto)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)

    var pressed by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var sendArmed by remember { mutableStateOf(false) }
    var sendProgress by remember { mutableFloatStateOf(0f) }
    var hovered by remember { mutableStateOf<MenuAction?>(null) }
    var sendNudgePx by remember { mutableFloatStateOf(0f) }
    var hintToken by remember { mutableIntStateOf(0) }
    var showHint by remember { mutableStateOf(false) }

    LaunchedEffect(hintToken) {
        if (hintToken == 0) return@LaunchedEffect
        showHint = true
        delay(1400)
        showHint = false
    }

    val stackProgress by animateFloatAsState(
        targetValue = if (menuVisible || showHint) 1f else 0f,
        label = "stackProgress",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (pressed && !menuVisible) 0.92f else 1f,
        label = "sendScale",
    )

    val menuItems = remember {
        listOf(
            MenuSpec(MenuAction.File, 0, Icons.AutoMirrored.Filled.InsertDriveFile, "File"),
            MenuSpec(MenuAction.Photo, 1, Icons.Default.Photo, "Photo"),
            MenuSpec(MenuAction.Settings, 2, Icons.Default.Settings, "Settings"),
        )
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    menuVisible = false
                    sendArmed = false
                    sendProgress = 0f
                    hovered = null
                    sendNudgePx = 0f
                    showHint = false

                    var current = down.position
                    val start = down.position
                    var locked = ComposerDragLock.None
                    val origin = Offset(size.width / 2f, size.height / 2f)

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            current = change.position
                            change.consume()
                            if (!change.pressed) break

                            val drag = current - start
                            val distance = drag.getDistance()
                            if (locked == ComposerDragLock.None && distance >= slopPx) {
                                locked = when {
                                    drag.y < -menuThresholdPx &&
                                        kotlin.math.abs(drag.y) >= kotlin.math.abs(drag.x) -> ComposerDragLock.Menu
                                    drag.x < -slopPx &&
                                        kotlin.math.abs(drag.x) >= kotlin.math.abs(drag.y) -> ComposerDragLock.Send
                                    distance > slopPx * 2f &&
                                        (drag.x > slopPx || drag.y > slopPx) -> ComposerDragLock.Ignore
                                    else -> ComposerDragLock.None
                                }
                            }

                            when (locked) {
                                ComposerDragLock.Menu -> {
                                    menuVisible = true
                                    sendArmed = false
                                    sendProgress = 0f
                                    sendNudgePx = 0f
                                    hovered = hitTestStack(
                                        pointer = current,
                                        origin = origin,
                                        stepPx = stackStepPx,
                                        hitRadiusPx = itemHitRadiusPx,
                                        connected = currentConnected,
                                    )
                                }
                                ComposerDragLock.Send -> {
                                    val progress = if (currentConnected && currentCanSend) {
                                        (-drag.x / sendThresholdPx).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    sendProgress = progress
                                    sendArmed = progress >= 1f
                                    sendNudgePx = if (currentConnected && currentCanSend) {
                                        drag.x.coerceIn(-maxNudgePx, 0f)
                                    } else {
                                        0f
                                    }
                                }
                                else -> Unit
                            }
                        }
                    } finally {
                        val drag = current - start
                        val isTap = drag.getDistance() < slopPx
                        when {
                            locked == ComposerDragLock.Menu -> when (hovered) {
                                MenuAction.File -> if (currentConnected) currentOnPickFile()
                                MenuAction.Photo -> if (currentConnected) currentOnPickPhoto()
                                MenuAction.Settings -> currentOnOpenSettings()
                                null -> Unit
                            }
                            locked == ComposerDragLock.Send && sendArmed -> currentOnSend()
                            isTap -> {
                                showHint = true
                                hintToken++
                            }
                        }
                        pressed = false
                        menuVisible = false
                        sendArmed = false
                        sendProgress = 0f
                        hovered = null
                        sendNudgePx = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (stackProgress > 0.01f) {
            menuItems.forEach { spec ->
                val enabled = spec.action == MenuAction.Settings || connected
                val itemProgress = ((stackProgress * 1.45f) - spec.index * 0.22f).coerceIn(0f, 1f)
                StackMenuItem(
                    index = spec.index,
                    stepPx = stackStepPx,
                    progress = itemProgress,
                    icon = spec.icon,
                    contentDescription = spec.label,
                    hovered = hovered == spec.action,
                    enabled = enabled,
                )
            }
        }

        if (sendProgress > 0.01f) {
            SendSwipeHint(progress = sendProgress, armed = sendArmed)
        }

        val restContainer = if (connected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        val restContent = if (connected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }

        Surface(
            shape = CircleShape,
            color = lerp(restContainer, MaterialTheme.colorScheme.error, sendProgress),
            contentColor = lerp(restContent, MaterialTheme.colorScheme.onError, sendProgress),
            shadowElevation = if (pressed || sendArmed) 6.dp else 2.dp,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    val morphScale = sendScale * (1f + 0.08f * sendProgress)
                    scaleX = morphScale
                    scaleY = morphScale
                    translationX = sendNudgePx
                    rotationZ = 45f * sendProgress
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Actions. Tap to preview menu, swipe left to send, swipe back to cancel, swipe up to choose.",
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SendSwipeHint(
    progress: Float,
    armed: Boolean,
) {
    val color = lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.error,
        progress,
    )
    repeat(3) { index ->
        val stagger = ((progress * 1.35f) - index * 0.22f).coerceIn(0f, 1f)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = color.copy(alpha = stagger * if (armed) 1f else 0.72f),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-(26 + index * 16)).dp)
                .graphicsLayer {
                    scaleX = 0.8f + 0.25f * stagger
                    scaleY = 0.8f + 0.25f * stagger
                    translationX = -6f * progress
                },
        )
    }
}

private data class MenuSpec(
    val action: MenuAction,
    val index: Int,
    val icon: ImageVector,
    val label: String,
)

@Composable
private fun BoxScope.StackMenuItem(
    index: Int,
    stepPx: Float,
    progress: Float,
    icon: ImageVector,
    contentDescription: String,
    hovered: Boolean,
    enabled: Boolean,
) {
    val y = -stepPx * (index + 1) * progress
    val scale = (if (hovered && enabled) 1.15f else 1f) * (0.55f + 0.45f * progress)

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset { IntOffset(0, y.roundToInt()) }
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = progress
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                hovered && enabled -> MaterialTheme.colorScheme.primary
                enabled -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when {
                hovered && enabled -> MaterialTheme.colorScheme.onPrimary
                enabled -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            shadowElevation = if (hovered) 6.dp else 3.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun hitTestStack(
    pointer: Offset,
    origin: Offset,
    stepPx: Float,
    hitRadiusPx: Float,
    connected: Boolean,
): MenuAction? {
    var best: MenuAction? = null
    var bestDist = hitRadiusPx
    val items = arrayOf(MenuAction.File, MenuAction.Photo, MenuAction.Settings)
    for ((index, action) in items.withIndex()) {
        if (action != MenuAction.Settings && !connected) continue
        val center = origin + Offset(0f, -stepPx * (index + 1))
        val dist = (pointer - center).getDistance()
        if (dist < bestDist) {
            bestDist = dist
            best = action
        }
    }
    return best
}

@Composable
private fun SelectableMessageRow(
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val highlight by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(180),
        label = "selectHighlight",
    )
    val pop by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "selectPop",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(highlight)
            .detectMessageLongPress {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongPress()
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = selectionMode,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn() + scaleIn(initialScale = 0.6f),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            SelectionMark(
                selected = selected,
                scale = pop,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClick = onToggle),
                )
            }
        }
    }
}

@Composable
private fun SelectionMark(
    selected: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val ring = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun Modifier.detectMessageLongPress(onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val timeout = viewConfiguration.longPressTimeoutMillis
            val slop = viewConfiguration.touchSlop
            val pointerId = down.id
            val timedOut = withTimeoutOrNull(timeout) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: return@withTimeoutOrNull false
                    if ((change.position - down.position).getDistance() > slop) {
                        return@withTimeoutOrNull false
                    }
                    if (!change.pressed) {
                        return@withTimeoutOrNull false
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
            if (timedOut == null) {
                awaitPointerEvent(PointerEventPass.Initial).changes
                    .filter { it.id == pointerId }
                    .forEach { it.consume() }
                onLongPress()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    change?.consume()
                    if (change == null || !change.pressed) break
                }
            }
        }
    }

@Composable
private fun SystemChip(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (message.isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            }
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = if (message.isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun LetterAvatar(name: String) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun UserAvatar(name: String, photo: ByteArray?) {
    if (photo != null) {
        ByteImage(
            imageBytes = photo,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
    } else {
        LetterAvatar(name)
    }
}

private fun bubbleShape(isMine: Boolean) = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = if (isMine) 16.dp else 4.dp,
    bottomEnd = if (isMine) 4.dp else 16.dp,
)

@Composable
private fun ChatRow(
    isMine: Boolean,
    sender: String,
    mineAvatar: ByteArray?,
    peerAvatar: ByteArray?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (isMine) {
            Spacer(modifier = Modifier.weight(0.22f))
            Column(
                modifier = Modifier.weight(0.78f, fill = false),
                horizontalAlignment = Alignment.End,
                content = content
            )
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar(name = sender, photo = mineAvatar)
        } else {
            UserAvatar(name = sender, photo = peerAvatar)
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(0.78f, fill = false),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = sender.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                content()
            }
            Spacer(modifier = Modifier.weight(0.22f))
        }
    }
}

@Composable
fun TextBubble(
    message: ChatMessage,
    mineAvatar: ByteArray? = null,
    peerAvatar: ByteArray? = null,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val isMine = message.isMine
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val maxPreviewHeight = with(LocalDensity.current) {
        val raw = LocalWindowInfo.current.containerSize.height
        if (raw <= 0) 240.dp else (raw / 3f).toDp().coerceAtLeast(96.dp)
    }
    var overflows by remember(message.id, message.content) { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }
    val body = message.content
    val showPreview = body.isNotEmpty()
    val collapsed = overflows && showPreview

    ChatRow(
        isMine = isMine,
        sender = message.sender,
        mineAvatar = mineAvatar,
        peerAvatar = peerAvatar,
        modifier = modifier,
    ) {
        Surface(
            shape = bubbleShape(isMine),
            color = if (isMine) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = if (isMine) 0.dp else 1.dp,
            modifier = if (collapsed) {
                Modifier.clickable { showViewer = true }
            } else {
                Modifier
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (showPreview) {
                    val textModifier = Modifier
                        .heightIn(max = maxPreviewHeight)
                        .clipToBounds()
                    if (collapsed) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            overflow = TextOverflow.Ellipsis,
                            modifier = textModifier,
                            onTextLayout = { overflows = it.didOverflowHeight },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap to view",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                                overflow = TextOverflow.Ellipsis,
                                modifier = textModifier,
                                onTextLayout = { overflows = it.didOverflowHeight },
                            )
                        }
                    }
                } else if (message.isTransferring) {
                    Text(
                        text = if (isMine) "Sending text" else "Receiving text",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }
                if (message.isTransferring) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { message.transferProgress },
                        modifier = Modifier.widthIn(min = 140.dp).fillMaxWidth(),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.24f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = transferStatusLabel(message),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.8f),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
            if (showPreview) {
                CopyIconButton(
                    text = body,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showViewer && showPreview) {
        TextViewerDialog(
            text = body,
            onDismiss = { showViewer = false },
        )
    }
}

@Composable
private fun CopyIconButton(
    text: String,
    tint: Color,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
        },
        enabled = text.isNotEmpty(),
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (copied) "Copied" else "Copy",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun TextViewerDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp, vertical = 20.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    CopyIconButton(
                        text = text,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                }
                HorizontalDivider()
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ImageBubble(
    message: ChatMessage,
    mineAvatar: ByteArray? = null,
    peerAvatar: ByteArray? = null,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val imageBytes = message.pic
    val imagePath = message.filePath
    val hasImage = imageBytes != null || !imagePath.isNullOrBlank()
    var showFull by remember { mutableStateOf(false) }
    val fileSaverLauncher = rememberFileSaverLauncher { }

    ChatRow(
        isMine = message.isMine,
        sender = message.sender,
        mineAvatar = mineAvatar,
        peerAvatar = peerAvatar,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .heightIn(min = if (hasImage) 0.dp else 160.dp, max = 240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (hasImage) Modifier.clickable { showFull = true } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasImage) {
                MessageImage(
                    message = message,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .heightIn(max = 240.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(220.dp, 160.dp))
            }

            if (message.isTransferring) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (message.isMine) "Sending" else "Receiving",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { message.transferProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = transferStatusLabel(message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        if (onCancel != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onCancel) {
                                Text("Cancel", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFull && hasImage) {
        Dialog(
            onDismissRequest = { showFull = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
            ) {
                MessageImage(
                    message = message,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            val bytes = imageBytes ?: imagePath?.let { path ->
                                try {
                                    File(path).readBytes()
                                } catch (_: Exception) {
                                    null
                                }
                            } ?: return@IconButton
                            fileSaverLauncher.launch(
                                baseName = "image_${Random.nextInt(10000, 99999)}",
                                extension = "png",
                                bytes = bytes
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Image",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showFull = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileBubble(
    message: ChatMessage,
    mineAvatar: ByteArray? = null,
    peerAvatar: ByteArray? = null,
    modifier: Modifier = Modifier,
    onSaveTo: (PlatformFile) -> Unit = {},
    onCancel: (() -> Unit)? = null,
) {
    val canSave = message.filePath != null && !message.isTransferring
    val name = message.fileName ?: "file.bin"
    val fileSaverLauncher = rememberFileSaverLauncher { dest ->
        if (dest != null) onSaveTo(dest)
    }
    val isMine = message.isMine

    ChatRow(
        isMine = isMine,
        sender = message.sender,
        mineAvatar = mineAvatar,
        peerAvatar = peerAvatar,
        modifier = modifier,
    ) {
        Surface(
            shape = bubbleShape(isMine),
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = if (isMine) 0.dp else 1.dp,
            modifier = Modifier.widthIn(min = 180.dp, max = 260.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (isMine) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isMine) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fileStatusLabel(message),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (onCancel != null) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel send"
                            )
                        }
                    } else if (canSave) {
                        IconButton(
                            onClick = {
                                val (base, ext) = splitFileName(name)
                                fileSaverLauncher.launch(
                                    baseName = base,
                                    extension = ext,
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save file"
                            )
                        }
                    }
                }
                if (message.isTransferring) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { message.transferProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun fileStatusLabel(message: ChatMessage): String {
    if (message.isTransferring) return transferStatusLabel(message)
    val size = message.fileSize.takeIf { it > 0 } ?: message.fileBytes?.size?.toLong()
    return if (size != null) formatBytes(size) else "File"
}

private fun transferStatusLabel(message: ChatMessage): String {
    val verb = if (message.isMine) "Sending" else "Receiving"
    val percent = (message.transferProgress * 100f).toInt().coerceIn(0, 99)
    val size = message.fileSize.takeIf { it > 0 }
    return if (size != null) "$verb ${formatBytes(size)} · $percent%" else "$verb $percent%"
}

private fun splitFileName(name: String): Pair<String, String> {
    val leaf = FileChunk.sanitizeFileName(name)
    val dot = leaf.lastIndexOf('.')
    return if (dot > 0 && dot < leaf.length - 1) {
        leaf.substring(0, dot) to leaf.substring(dot + 1)
    } else {
        leaf to "bin"
    }
}

private fun formatBytes(n: Long): String = when {
    n < 1024L -> "$n B"
    n < 1024L * 1024 -> {
        val kb = n / 1024.0
        if (kb < 10) "%.1f KB".format(kb) else "${n / 1024} KB"
    }
    n < 1024L * 1024 * 1024 -> "%.1f MB".format(n / (1024.0 * 1024))
    else -> "%.1f GB".format(n / (1024.0 * 1024 * 1024))
}

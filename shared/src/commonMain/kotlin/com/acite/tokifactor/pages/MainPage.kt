package com.acite.tokifactor.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlin.random.Random

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
    var attachMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

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

    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = viewModel.messages,
                key = { it.id }
            ) { message ->
                when {
                    message.isSystem -> SystemChip(
                        message = message,
                        modifier = Modifier.animateItem()
                    )
                    message.isFile -> FileBubble(
                        message = message,
                        mineAvatar = viewModel.avatarBytes,
                        peerAvatar = viewModel.peerAvatars[message.senderId],
                        modifier = Modifier.animateItem(),
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
                        modifier = Modifier.animateItem(),
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
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Channel settings"
                    )
                }

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

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = viewModel.connected,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message"
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    IconButton(
                        onClick = { attachMenuOpen = true },
                        enabled = viewModel.connected,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach"
                        )
                    }
                    DropdownMenu(
                        expanded = attachMenuOpen,
                        onDismissRequest = { attachMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photo") },
                            onClick = {
                                attachMenuOpen = false
                                imagePickerLauncher.launch()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File") },
                            onClick = {
                                attachMenuOpen = false
                                filePickerLauncher.launch()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
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
) {
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
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = if (isMine) 0.dp else 1.dp
        ) {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isMine) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
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
    val image = message.pic
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
                .heightIn(min = if (image == null) 160.dp else 0.dp, max = 240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (image != null) Modifier.clickable { showFull = true } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                ByteImage(
                    imageBytes = image,
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

    if (showFull && image != null) {
        Dialog(
            onDismissRequest = { showFull = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
            ) {
                ByteImage(
                    imageBytes = image,
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
                            fileSaverLauncher.launch(
                                baseName = "image_${Random.nextInt(10000, 99999)}",
                                extension = "png",
                                bytes = image
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

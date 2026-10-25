package com.acite.tokifactor.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.ui.theme.tokiColors
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tokifactor.shared.generated.resources.Res

@Serializable
private data class EmojiCatalogFile(
    val version: Int = 1,
    val emojis: List<EmojiAsset> = emptyList(),
)

@Serializable
data class EmojiAsset(
    val name: String,
    val file: String,
    val desc: String = "",
)

data class EmojiCatalog(val assets: Map<String, EmojiAsset>) {
    fun resolve(name: String): EmojiAsset? = assets[name]

    companion object {
        val EMPTY = EmojiCatalog(emptyMap())
    }
}

internal fun emojiGroups(catalog: EmojiCatalog): List<Pair<String, List<EmojiAsset>>> =
    catalog.assets.values
        .sortedWith(compareBy({ it.name.substringBefore('_') }, { it.name }))
        .groupBy { it.name.substringBefore('_') }
        .map { (member, assets) -> member to assets }

internal fun emojiToken(asset: EmojiAsset): String {
    val desc = asset.desc
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace("]", "）")
        .trim()
        .ifBlank { asset.name }
    return "#[${asset.name}:$desc]"
}

object EmojiRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var catalogCache: EmojiCatalog? = null
    private val imageCache = mutableMapOf<String, ByteArray>()

    suspend fun catalog(): EmojiCatalog {
        catalogCache?.let { return it }
        return lock.withLock {
            catalogCache?.let { return@withLock it }
            val loaded = runCatching {
                val bytes = Res.readBytes("files/emoji/catalog.json")
                val parsed = json.decodeFromString<EmojiCatalogFile>(bytes.decodeToString())
                val assets = parsed.emojis
                    .filter { it.name.isNotBlank() && it.file.startsWith("emoji/") }
                    .associateBy { it.name }
                EmojiCatalog(assets)
            }.getOrDefault(EmojiCatalog.EMPTY)
            catalogCache = loaded
            loaded
        }
    }

    suspend fun image(asset: EmojiAsset): ByteArray? {
        imageCache[asset.name]?.let { return it }
        return lock.withLock {
            imageCache[asset.name]?.let { return@withLock it }
            val bytes = runCatching {
                Res.readBytes("files/${asset.file}")
            }.getOrNull()
            if (bytes != null) imageCache[asset.name] = bytes
            bytes
        }
    }
}

internal data class EmojiToken(
    val name: String,
    val desc: String?,
    val raw: String,
)

internal sealed interface EmojiContentSegment {
    data class Text(val text: String) : EmojiContentSegment

    data class Emoji(
        val token: EmojiToken,
        val asset: EmojiAsset,
    ) : EmojiContentSegment
}

private val emojiTokenRegex = Regex("#\\[([a-z0-9_]+)(?::([^\\]\\r\\n]*))?\\]")
private val inlineCodeRegex = Regex("`[^`\\n]*`")
private val fenceRegex = Regex("(?m)^\\s*(```|~~~).*$")

internal fun splitEmojiSegments(
    content: String,
    catalog: EmojiCatalog,
): List<EmojiContentSegment> {
    if (content.isEmpty() || catalog.assets.isEmpty()) {
        return listOf(EmojiContentSegment.Text(content))
    }
    val protected = BooleanArray(content.length)
    for (match in inlineCodeRegex.findAll(content)) {
        for (index in match.range) protected[index] = true
    }
    val fenceMatches = fenceRegex.findAll(content).toList()
    var fenceStart = -1
    for (match in fenceMatches) {
        if (fenceStart < 0) {
            fenceStart = match.range.first
        } else {
            for (index in fenceStart until match.range.last + 1) protected[index] = true
            fenceStart = -1
        }
    }
    if (fenceStart >= 0) {
        for (index in fenceStart until content.length) protected[index] = true
    }

    val segments = mutableListOf<EmojiContentSegment>()
    var cursor = 0
    for (match in emojiTokenRegex.findAll(content)) {
        if (protected.getOrElse(match.range.first) { false }) continue
        val name = match.groupValues[1]
        val asset = catalog.resolve(name) ?: continue
        val text = content.substring(cursor, match.range.first).trim()
        if (text.isNotEmpty()) segments += EmojiContentSegment.Text(text)
        segments += EmojiContentSegment.Emoji(
            token = EmojiToken(
                name = name,
                desc = match.groupValues[2].takeIf { it.isNotBlank() },
                raw = match.value,
            ),
            asset = asset,
        )
        cursor = match.range.last + 1
    }
    if (segments.isEmpty()) return listOf(EmojiContentSegment.Text(content))
    val tail = content.substring(cursor).trim()
    if (tail.isNotEmpty()) segments += EmojiContentSegment.Text(tail)
    return segments
}

@Composable
internal fun EmojiStickerMessage(
    message: ChatMessage,
    token: EmojiToken,
    asset: EmojiAsset,
    showAttachments: Boolean,
    peerAvatar: ByteArray?,
    mineAvatar: ByteArray? = null,
    onAvatarClick: (() -> Unit)? = null,
) {
    var bytes by remember(asset.name) { mutableStateOf<ByteArray?>(null) }
    var loaded by remember(asset.name) { mutableStateOf(false) }
    LaunchedEffect(asset.name) {
        bytes = EmojiRepository.image(asset)
        loaded = true
    }
    var showFull by remember(asset.name) { mutableStateOf(false) }
    ChatRow(
        isMine = message.isMine,
        sender = message.sender,
        senderId = message.senderId,
        mineAvatar = mineAvatar,
        peerAvatar = peerAvatar,
        onAvatarClick = onAvatarClick,
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            val imageBytes = bytes
            when {
                imageBytes != null -> ByteImage(
                    imageBytes = imageBytes,
                    contentDescription = asset.desc,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 126.dp)
                        .clickable { showFull = true },
                )
                loaded -> Text(
                    text = token.raw,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokiColors.text,
                )
                else -> CircularProgressIndicator(
                    modifier = Modifier.widthIn(max = 32.dp),
                    strokeWidth = 2.dp,
                )
            }
            if (showAttachments && message.hasAttachments) {
                MessageAttachmentStrip(
                    attachments = message.attachments,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    if (showFull && bytes != null) {
        Dialog(
            onDismissRequest = { showFull = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showFull = false },
                contentAlignment = Alignment.Center,
            ) {
                ByteImage(
                    imageBytes = bytes!!,
                    contentDescription = asset.desc,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                )
                IconButton(
                    onClick = { showFull = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun EmojiPickerDialog(
    catalog: EmojiCatalog,
    onDismiss: () -> Unit,
    onSelect: (EmojiAsset) -> Unit,
) {
    val groups = remember(catalog) { emojiGroups(catalog) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("表情包") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 440.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groups.forEach { (member, assets) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "group:$member") {
                        Text(
                            text = member.replaceFirstChar { it.uppercaseChar() },
                            style = MaterialTheme.typography.labelMedium,
                            color = tokiColors.textDim,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    gridItems(assets, key = { it.name }) { asset ->
                        EmojiPickerCell(asset = asset, onSelect = { onSelect(asset) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun EmojiPickerCell(
    asset: EmojiAsset,
    onSelect: () -> Unit,
) {
    var bytes by remember(asset.name) { mutableStateOf<ByteArray?>(null) }
    var loaded by remember(asset.name) { mutableStateOf(false) }
    LaunchedEffect(asset.name) {
        bytes = EmojiRepository.image(asset)
        loaded = true
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.045f)),
            contentAlignment = Alignment.Center,
        ) {
            val imageBytes = bytes
            when {
                imageBytes != null -> ByteImage(
                    imageBytes = imageBytes,
                    contentDescription = asset.desc,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                )
                loaded -> Text("×", color = tokiColors.textDim)
                else -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 1.5.dp,
                )
            }
        }
        Text(
            text = asset.name.substringAfter('_').replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            color = tokiColors.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

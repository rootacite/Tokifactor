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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.ToolGroupTone
import com.acite.tokifactor.model.parseToolCallDetail
import com.acite.tokifactor.model.toolCallCounts
import com.acite.tokifactor.model.toolCallStatusLabel
import com.acite.tokifactor.model.toolDisplayName
import com.acite.tokifactor.model.toolGroupSummary
import com.acite.tokifactor.model.toolGroupTone
import com.acite.tokifactor.model.ChatAttachment
import com.acite.tokifactor.model.FileChunk
import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.MEMBER_KIND_AGENT
import com.acite.tokifactor.model.MEMBER_KIND_USER
import com.acite.tokifactor.model.approvalBadgeLabel
import com.acite.tokifactor.model.modelLabel
import com.acite.tokifactor.model.memberNameKey
import com.acite.tokifactor.model.legacyMemberId
import com.acite.tokifactor.model.queueBadgeLabel
import com.acite.tokifactor.model.recentMemberMessages
import com.acite.tokifactor.services.OutgoingAttachment
import com.acite.tokifactor.services.MAX_APPROVAL_MESSAGE_CHARS
import com.acite.tokifactor.ui.theme.TokiBubbleSurface
import com.acite.tokifactor.ui.theme.TokiSurface
import com.acite.tokifactor.ui.theme.tokiColors
import com.acite.tokifactor.ui.theme.tokiTextFieldColors
import com.acite.tokifactor.ui.theme.tokiTokens
import com.mikepenz.markdown.m3.Markdown
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
    contentDescription: String = "Image",
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
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
private fun FullImageContent(
    path: String?,
    explicitBytes: ByteArray? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var bytes by remember(path, explicitBytes) { mutableStateOf(explicitBytes) }
    var failed by remember(path, explicitBytes) { mutableStateOf(false) }
    LaunchedEffect(path, explicitBytes) {
        if (bytes == null && !path.isNullOrBlank()) {
            bytes = withContext(Dispatchers.IO) {
                runCatching { File(path).readBytes() }.getOrNull()
            }
            failed = bytes == null
        }
    }
    val value = bytes
    when {
        value != null -> ByteImage(
            imageBytes = value,
            modifier = modifier,
            contentScale = contentScale,
        )
        !path.isNullOrBlank() -> {
            val context = LocalPlatformContext.current
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(path))
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
                if (failed) {
                    Text(
                        text = "图片加载失败",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "图片不可用",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PendingImageThumbnail(
    file: PlatformFile,
    modifier: Modifier = Modifier,
) {
    var bytes by remember(file) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(file) {
        bytes = withContext(Dispatchers.IO) {
            runCatching { file.readBytes() }.getOrNull()
        }
    }
    val imageBytes = bytes
    if (imageBytes != null) {
        ByteImage(
            imageBytes = imageBytes,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Photo,
                contentDescription = null,
                tint = tokiColors.textDim,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun MessageAttachmentStrip(
    attachments: List<ChatAttachment>,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val colors = tokiColors
    var selected by remember(attachments) { mutableStateOf<ChatAttachment?>(null) }
    val saver = rememberFileSaverLauncher { }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Column(
                modifier = Modifier.width(156.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgCard.copy(alpha = 0.62f))
                        .clickable(enabled = !attachment.filePath.isNullOrBlank()) {
                            selected = attachment
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!attachment.filePath.isNullOrBlank()) {
                        val context = LocalPlatformContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(attachment.filePath))
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = attachment.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        if (attachment.isTransferring) {
                            CircularProgressIndicator(
                                progress = { attachment.progress },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = "图片不可用",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textDim,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val active = selected
    if (active != null) {
        Dialog(
            onDismissRequest = { selected = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f)),
            ) {
                val path = active.filePath
                if (!path.isNullOrBlank()) {
                    FullImageContent(
                        path = path,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
                Text(
                    text = active.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            val bytes = active.filePath?.let { runCatching { File(it).readBytes() }.getOrNull() }
                                ?: return@IconButton
                            saver.launch(
                                baseName = active.name.substringBeforeLast('.').ifBlank { "image" },
                                extension = active.name.substringAfterLast('.', "png"),
                                bytes = bytes,
                            )
                        },
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download image", tint = Color.White)
                    }
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

internal sealed class ChatDisplayItem {
    abstract val key: String

    sealed class MessageItem : ChatDisplayItem() {
        abstract val message: ChatMessage
        abstract val staged: Boolean
    }

    data class Single(
        override val message: ChatMessage,
        override val staged: Boolean = false,
    ) : MessageItem() {
        override val key: String = "message:${message.id}"
    }

    data class TextPart(
        override val message: ChatMessage,
        val index: Int,
        val isLast: Boolean,
        override val staged: Boolean = false,
    ) : MessageItem() {
        override val key: String = "text-part:${message.id}:$index"
    }

    data class EmojiPart(
        override val message: ChatMessage,
        val token: EmojiToken,
        val asset: EmojiAsset,
        val index: Int,
        val isLast: Boolean,
        override val staged: Boolean = false,
    ) : MessageItem() {
        override val key: String = "emoji:${message.id}:$index"
    }

    data class ToolGroup(
        val id: String,
        val messages: List<ChatMessage>,
    ) : ChatDisplayItem() {
        override val key: String = "tools:$id"
    }
}

private data class MemberProfileTarget(
    val member: AgentStatus,
    val isSelf: Boolean,
)

internal fun buildChatDisplayItems(
    messages: List<ChatMessage>,
    emojiCatalog: EmojiCatalog,
    stagedIds: Set<String> = emptySet(),
): List<ChatDisplayItem> {
    val items = mutableListOf<ChatDisplayItem>()
    var index = 0
    while (index < messages.size) {
        val message = messages[index]
        val staged = message.id in stagedIds
        if (message.isAgent &&
            message.agentKind == "turn" &&
            message.phase == "started" &&
            message.content.isBlank()
        ) {
            index += 1
            continue
        }
        if (!message.isGroupableToolStep()) {
            val isAgentTurn = message.isAgent &&
                message.agentKind == "turn" &&
                message.content.isNotBlank()
            val isUserText = !message.isAgent &&
                !message.isSystem &&
                !message.isFile &&
                !message.isImage &&
                message.content.isNotBlank()
            if (isAgentTurn || isUserText) {
                val segments = splitEmojiSegments(message.content, emojiCatalog)
                val hasEmoji = segments.any { it is EmojiContentSegment.Emoji }
                if (isAgentTurn || hasEmoji) {
                    var displayIndex = 0
                    segments.forEachIndexed { segmentIndex, segment ->
                        when (segment) {
                            is EmojiContentSegment.Text -> {
                                val paragraphs = splitMessageParagraphs(segment.text)
                                paragraphs.forEachIndexed { partIndex, paragraph ->
                                    items += ChatDisplayItem.TextPart(
                                        message = message.copy(content = paragraph),
                                        index = displayIndex++,
                                        isLast = segmentIndex == segments.lastIndex &&
                                            partIndex == paragraphs.lastIndex,
                                        staged = staged,
                                    )
                                }
                            }
                            is EmojiContentSegment.Emoji -> {
                                items += ChatDisplayItem.EmojiPart(
                                    message = message,
                                    token = segment.token,
                                    asset = segment.asset,
                                    index = displayIndex++,
                                    isLast = segmentIndex == segments.lastIndex,
                                    staged = staged,
                                )
                            }
                        }
                    }
                } else {
                    val paragraphs = splitMessageParagraphs(message.content)
                    if (paragraphs.size > 1) {
                        paragraphs.forEachIndexed { partIndex, paragraph ->
                            items += ChatDisplayItem.TextPart(
                                message = message.copy(content = paragraph),
                                index = partIndex,
                                isLast = partIndex == paragraphs.lastIndex,
                                staged = staged,
                            )
                        }
                    } else {
                        items += ChatDisplayItem.Single(message, staged)
                    }
                }
            } else {
                items += ChatDisplayItem.Single(message, staged)
            }
            index += 1
            continue
        }
        val group = mutableListOf(message)
        var next = index + 1
        while (next < messages.size) {
            val candidate = messages[next]
            if (!candidate.isGroupableToolStep() || candidate.turnId != message.turnId) break
            group += candidate
            next += 1
        }
        if (group.size > 1) {
            items += ChatDisplayItem.ToolGroup(id = group.first().id, messages = group)
        } else {
            items += ChatDisplayItem.Single(message)
        }
        index = next
    }
    return items
}

private fun ChatMessage.isGroupableToolStep(): Boolean =
    isAgent && (agentKind == "tool" || agentKind == "approval")

/**
 * Tool groups that already have a later non-tool row. Staged drafts do not
 * count: the chain is only broken by a real message.
 */
internal fun interruptedToolGroupIds(items: List<ChatDisplayItem>): Set<String> {
    val interrupted = mutableSetOf<String>()
    for (index in items.indices) {
        val group = items[index] as? ChatDisplayItem.ToolGroup ?: continue
        val broken = items.subList(index + 1, items.size).any { later ->
            when (later) {
                is ChatDisplayItem.ToolGroup -> false
                is ChatDisplayItem.MessageItem -> !later.staged
            }
        }
        if (broken) interrupted += group.id
    }
    return interrupted
}

internal fun approvalRequesterMatches(requesterId: String, localMemberId: String): Boolean =
    requesterId.isEmpty() || requesterId == localMemberId

@Composable
fun MainPage(
    onOpenSettings: () -> Unit,
    viewModel: MainPageViewModel = metroViewModel(),
) {
    var inputText by remember { mutableStateOf("") }
    val pendingImages = remember { mutableStateListOf<Pair<PlatformFile, String>>() }
    val partialFragments = remember { mutableStateListOf<String>() }
    var renameImageIndex by remember { mutableStateOf<Int?>(null) }
    var renameImageDraft by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val colors = tokiColors
    val listState = rememberLazyListState()
    val stickToBottom = remember { mutableStateOf(true) }
    val autoScrolling = remember { mutableStateOf(false) }
    val followBottomScroll = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source.adjustsBottomStick(autoScrolling.value)) {
                    stickToBottom.value = listState.isAtEnd()
                }
                return Offset.Zero
            }
        }
    }
    suspend fun revealBottomWhileFollowing() {
        autoScrolling.value = true
        try {
            listState.revealBottom()
        } finally {
            autoScrolling.value = false
        }
    }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var profileTarget by remember { mutableStateOf<MemberProfileTarget?>(null) }
    var showConversations by remember { mutableStateOf(false) }
    var confirmEndSolo by remember { mutableStateOf(false) }
    val members = viewModel.members
    val solo = viewModel.soloState
    val canWrite = if (solo.active) {
        viewModel.currentConversationId == "solo:${solo.targetId}"
    } else {
        viewModel.currentConversationId == "group"
    }
    val historicalSoloId = viewModel.currentConversationId
        .takeIf { it.startsWith("solo:") && !solo.active }
    val historicalSolo = historicalSoloId?.let { id ->
        viewModel.conversations.firstOrNull { it.id == id }
    }
    val agentStatus = viewModel.agentStatus
    var emojiCatalog by remember { mutableStateOf(EmojiCatalog.EMPTY) }

    val imagePickerLauncher = rememberFilePickerLauncher(
        type = PickerType.Image
    ) { file: PlatformFile? ->
        if (file != null) {
            if (pendingImages.size >= 4) {
                viewModel.addMessage("Error : a message may contain at most 4 images.", true)
            } else {
                pendingImages += file to defaultAttachmentName(pendingImages.map { it.second })
            }
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

    fun openProfile(memberId: String, memberName: String, isSelf: Boolean) {
        val now = System.currentTimeMillis()
        val member = if (isSelf) {
            AgentStatus(
                present = true,
                lastSeenAt = now,
                memberId = viewModel.memberId,
                memberName = viewModel.displayName,
                description = viewModel.bio,
                memberKind = MEMBER_KIND_USER,
            )
        } else {
            viewModel.members.firstOrNull { it.memberId == memberId }
                ?: AgentStatus(
                    present = true,
                    lastSeenAt = now,
                    memberId = memberId.ifBlank { legacyMemberId(memberName) },
                    memberName = memberName,
                    memberKind = MEMBER_KIND_AGENT,
                )
        }
        profileTarget = MemberProfileTarget(member = member, isSelf = isSelf)
    }

    fun openMessageProfile(message: ChatMessage) {
        openProfile(
            memberId = message.senderId.ifBlank { legacyMemberId(message.sender) },
            memberName = message.sender,
            isSelf = message.isMine,
        )
    }

    fun stageComposerFragment(fragment: String) {
        val text = fragment.trim()
        if (text.isNotEmpty()) partialFragments += text
    }

    fun stageInputFragment() {
        val text = inputText.trim()
        if (text.isEmpty()) return
        partialFragments += text
        inputText = ""
    }

    fun stageEmojiFragment(asset: EmojiAsset) {
        stageInputFragment()
        stageComposerFragment(emojiToken(asset))
        showEmojiPicker = false
    }

    fun sendComposerMessage() {
        if (!canWrite || !viewModel.connected) return
        val names = normalizeAttachmentNames(pendingImages.map { it.second })
        val attachments = pendingImages.mapIndexed { index, item ->
            OutgoingAttachment(item.first, names[index])
        }
        val content = assemblePartialMessage(partialFragments.toList(), inputText)
        if (content.isBlank() && attachments.isEmpty()) return
        viewModel.sendMessage(content, attachments)
        inputText = ""
        partialFragments.clear()
        pendingImages.clear()
    }

    fun messageAvatar(message: ChatMessage): ByteArray? =
        viewModel.peerAvatars[message.senderId]

    fun displayItemIds(item: ChatDisplayItem): List<String> = when (item) {
        is ChatDisplayItem.MessageItem -> listOf(item.message.id)
        is ChatDisplayItem.ToolGroup -> item.messages.map { it.id }
    }

    fun enterSelection(item: ChatDisplayItem) {
        val ids = displayItemIds(item)
        if (!selectionMode) {
            selectionMode = true
            selectedIds.clear()
            selectedIds.addAll(ids)
        } else if (ids.all { it in selectedIds }) {
            selectedIds.removeAll(ids.toSet())
            if (selectedIds.isEmpty()) exitSelection()
        } else {
            ids.filter { it !in selectedIds }.forEach { selectedIds.add(it) }
        }
    }

    fun toggleSelected(item: ChatDisplayItem) {
        val ids = displayItemIds(item)
        if (ids.all { it in selectedIds }) {
            selectedIds.removeAll(ids.toSet())
            if (selectedIds.isEmpty()) exitSelection()
        } else {
            ids.filter { it !in selectedIds }.forEach { selectedIds.add(it) }
        }
    }

    val partialSnapshot = partialFragments.toList()
    val stagedDisplayItems = remember(
        partialSnapshot,
        emojiCatalog,
        viewModel.currentConversationId,
        viewModel.displayName,
        viewModel.memberId,
    ) {
        val stagedMessages = partialSnapshot.mapIndexed { index, content ->
            ChatMessage(
                id = "draft:${viewModel.currentConversationId}:$index",
                content = content,
                pic = null,
                conversationId = viewModel.currentConversationId,
                sender = viewModel.displayName,
                senderId = viewModel.memberId,
                isMine = true,
                sentAt = System.currentTimeMillis(),
            )
        }
        buildChatDisplayItems(
            messages = stagedMessages,
            emojiCatalog = emojiCatalog,
            stagedIds = stagedMessages.map { it.id }.toSet(),
        )
    }
    val displayItems by remember(viewModel.messages, emojiCatalog, stagedDisplayItems) {
        derivedStateOf {
            buildChatDisplayItems(viewModel.messages, emojiCatalog) + stagedDisplayItems
        }
    }
    val expandedToolGroups = remember { mutableStateMapOf<String, Boolean>() }
    val interruptedToolGroups = interruptedToolGroupIds(displayItems)
    var collapsedInterruptions by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(interruptedToolGroups) {
        for (id in interruptedToolGroups - collapsedInterruptions) {
            expandedToolGroups[id] = false
        }
        collapsedInterruptions = interruptedToolGroups
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
        emojiCatalog = EmojiRepository.catalog()
    }

    LaunchedEffect(viewModel.currentConversationId, canWrite) {
        partialFragments.clear()
    }

    val followToken = remember { mutableStateOf(0 to 0) }
    followToken.value = displayFeedKey(displayItems) to displayItems.size

    LaunchedEffect(viewModel.currentConversationId) {
        stickToBottom.value = true
        if (displayItems.isNotEmpty()) {
            revealBottomWhileFollowing()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { followToken.value }
            .collectLatest { (_, count) ->
                if (!stickToBottom.value || count == 0) return@collectLatest
                revealBottomWhileFollowing()
            }
    }

    OnSystemBack(enabled = selectionMode || profileTarget != null) {
        if (profileTarget != null) {
            profileTarget = null
        } else {
            exitSelection()
        }
    }

    val listTopPad by animateDpAsState(
        targetValue = when {
            selectionMode -> 68.dp
            historicalSolo != null -> 186.dp
            members.isNotEmpty() -> 130.dp
            else -> 16.dp
        },
        animationSpec = tween(220),
        label = "listTopPad",
    )

    Box(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .nestedScroll(followBottomScroll)
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
                items = displayItems,
                key = { it.key }
            ) { item ->
                val ids = displayItemIds(item)
                val selected = ids.isNotEmpty() && ids.all { it in selectedIds }
                val staged = (item as? ChatDisplayItem.MessageItem)?.staged == true
                SelectableMessageRow(
                    selected = !staged && selected,
                    selectionMode = selectionMode && !staged,
                    onToggle = { toggleSelected(item) },
                    onLongPress = { enterSelection(item) },
                    selectable = !staged,
                    alpha = if (staged) 0.45f else 1f,
                ) {
                    when (item) {
                        is ChatDisplayItem.ToolGroup -> AgentToolGroupCard(
                            messages = item.messages,
                            expanded = expandedToolGroups[item.id] == true,
                            onExpandedChange = { expanded ->
                                expandedToolGroups[item.id] = expanded
                            },
                            connected = viewModel.connected,
                            localMemberId = viewModel.memberId,
                            onApprovalRespond = { approvalId, allow, message ->
                                viewModel.respondToApproval(approvalId, allow, message)
                            },
                            onAvatarClick = {
                                val message = item.messages.first()
                                openMessageProfile(message)
                            },
                        )
                        is ChatDisplayItem.EmojiPart -> EmojiStickerMessage(
                            message = item.message,
                            token = item.token,
                            asset = item.asset,
                            showAttachments = item.isLast,
                            mineAvatar = viewModel.avatarBytes,
                            peerAvatar = messageAvatar(item.message),
                            onAvatarClick = { openMessageProfile(item.message) },
                        )
                        is ChatDisplayItem.MessageItem -> {
                            val message = item.message
                            when {
                                message.isSystem -> SystemChip(message = message)
                                message.isAgent -> when (message.agentKind) {
                                    "approval" -> AgentApprovalCard(
                                        message = message,
                                        connected = viewModel.connected,
                                        localMemberId = viewModel.memberId,
                                        onRespond = { allow, approvalMessage ->
                                            viewModel.respondToApproval(
                                                message.approvalId,
                                                allow,
                                                approvalMessage,
                                            )
                                        },
                                        onAvatarClick = {
                                            openMessageProfile(message)
                                        },
                                    )
                                    "tool" -> AgentToolCard(
                                        message = message,
                                        onAvatarClick = {
                                            openMessageProfile(message)
                                        },
                                    )
                                    "error" -> SystemChip(message = message)
                                    else -> AgentTextBubble(
                                        message = message,
                                        showAttachments = item !is ChatDisplayItem.TextPart || item.isLast,
                                        mineAvatar = viewModel.avatarBytes,
                                        peerAvatar = messageAvatar(message),
                                        onAvatarClick = {
                                            openMessageProfile(message)
                                        },
                                    )
                                }
                                message.isFile -> FileBubble(
                                    message = message,
                                    mineAvatar = viewModel.avatarBytes,
                                    peerAvatar = messageAvatar(message),
                                    onAvatarClick = {
                                        openMessageProfile(message)
                                    },
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
                                    peerAvatar = messageAvatar(message),
                                    onAvatarClick = {
                                        openMessageProfile(message)
                                    },
                                    onCancel = if (message.isMine && message.isTransferring) {
                                        { viewModel.cancelOutgoing(message.id) }
                                    } else {
                                        null
                                    },
                                )
                                else -> TextBubble(
                                    message = message,
                                    showAttachments = item !is ChatDisplayItem.TextPart || item.isLast,
                                    mineAvatar = viewModel.avatarBytes,
                                    peerAvatar = messageAvatar(message),
                                    onAvatarClick = {
                                        openMessageProfile(message)
                                    },
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
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TokiSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = colors.bgPanel.copy(alpha = 0.88f),
                borderColor = colors.accentPink.copy(alpha = 0.26f),
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
            visible = members.isNotEmpty() && !selectionMode,
            enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            AkiStatusBar(
                status = agentStatus,
                members = members,
                avatars = viewModel.peerAvatars,
                onSelect = viewModel::selectAgent,
                onShowInfo = { member ->
                    profileTarget = MemberProfileTarget(member = member, isSelf = false)
                },
                onOpenConversations = { showConversations = true },
            )
        }

        AnimatedVisibility(
            visible = historicalSolo != null && !selectionMode,
            enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val summary = historicalSolo
            if (summary != null) {
                TokiSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .padding(top = 132.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.bgPanel.copy(alpha = 0.92f),
                    borderColor = colors.accentPink.copy(alpha = 0.34f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = summary.title.replace("Solo · ", "Solo with "),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.text,
                        )
                        TextButton(
                            onClick = {
                                viewModel.startSolo(
                                    summary.memberId,
                                    summary.title.removePrefix("Solo · "),
                                )
                            }
                        ) { Text("Continue solo") }
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
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                TokiSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = colors.bgPanel.copy(alpha = 0.88f),
                    borderColor = colors.stroke.copy(alpha = 0.70f),
                ) {
                    Column {
                        if (pendingImages.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 60.dp, top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(pendingImages.size) { index ->
                                    val (file, name) = pendingImages[index]
                                    Row(
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(colors.bgCard.copy(alpha = 0.72f))
                                            .clickable {
                                                renameImageIndex = index
                                                renameImageDraft = name
                                            }
                                            .padding(start = 4.dp, end = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(colors.bgCard.copy(alpha = 0.72f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            PendingImageThumbnail(file = file, modifier = Modifier.fillMaxSize())
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 112.dp),
                                        )
                                        IconButton(
                                            onClick = {
                                                renameImageIndex = index
                                                renameImageDraft = name
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Rename image",
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                pendingImages.removeAt(index)
                                                if (renameImageIndex == index) renameImageIndex = null
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove image",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showEmojiPicker = true },
                                enabled = canWrite &&
                                    viewModel.connected &&
                                    emojiCatalog.assets.isNotEmpty(),
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = "表情包",
                                    tint = if (
                                        canWrite &&
                                        viewModel.connected &&
                                        emojiCatalog.assets.isNotEmpty()
                                    ) {
                                        colors.accentPink
                                    } else {
                                        colors.textDim.copy(alpha = 0.38f)
                                    },
                                )
                            }
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        if (canWrite) "Type a message..." else "Read-only conversation"
                                    )
                                },
                                enabled = canWrite,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                maxLines = 4,
                                shape = RoundedCornerShape(22.dp),
                                colors = tokiTextFieldColors(),
                            )
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                }

                SendActionButton(
                    connected = viewModel.connected,
                    canSend = canWrite && viewModel.connected &&
                        (inputText.isNotBlank() || partialFragments.isNotEmpty() || pendingImages.isNotEmpty()),
                    canStage = canWrite && viewModel.connected && inputText.isNotBlank(),
                    hasStaged = partialFragments.isNotEmpty(),
                    onStage = { stageInputFragment() },
                    onCancelStaged = { partialFragments.clear() },
                    onSend = { sendComposerMessage() },
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
            TokiSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.bgPanel.copy(alpha = 0.90f),
                borderColor = colors.stroke.copy(alpha = 0.65f),
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

        profileTarget?.let { target ->
            MemberProfilePage(
                member = target.member,
                avatar = if (target.isSelf) {
                    viewModel.avatarBytes
                } else {
                    viewModel.peerAvatars[target.member.agentId]
                },
                isSelf = target.isSelf,
                recentMessages = recentMemberMessages(
                    viewModel.messages,
                    target.member.agentId,
                ),
                onBack = { profileTarget = null },
                onEditProfile = if (target.isSelf) {
                    {
                        profileTarget = null
                        onOpenSettings()
                    }
                } else {
                    null
                },
                soloActive = solo.active,
                soloTargetId = solo.targetId,
                onSolo = if (!target.isSelf && target.member.present && !solo.active) {
                    { viewModel.startSolo(target.member.memberId, target.member.memberName) }
                } else if (!target.isSelf && solo.active && solo.targetId == target.member.memberId) {
                    { confirmEndSolo = true }
                } else {
                    null
                },
            )
        }
    }

    if (showConversations) {
        AlertDialog(
            onDismissRequest = { showConversations = false },
            title = { Text("Conversations") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(viewModel.conversations, key = { it.id }) { conversation ->
                        val active = conversation.id == viewModel.currentConversationId
                        TokiSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectConversation(conversation.id)
                                    showConversations = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (active) {
                                colors.accentPink.copy(alpha = 0.18f)
                            } else {
                                colors.bgCard.copy(alpha = 0.72f)
                            },
                            borderColor = if (active) {
                                colors.accentPink.copy(alpha = 0.52f)
                            } else {
                                colors.stroke.copy(alpha = 0.5f)
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = conversation.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (conversation.id.startsWith("solo:") &&
                                    viewModel.soloState.active &&
                                    conversation.memberId == viewModel.soloState.targetId
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.accentPink,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConversations = false }) { Text("Close") }
            },
        )
    }

    if (confirmEndSolo) {
        AlertDialog(
            onDismissRequest = { confirmEndSolo = false },
            title = { Text("End solo time?") },
            text = { Text("The solo conversation stays saved, and the group chat will resume.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEndSolo = false
                        profileTarget = null
                        viewModel.endSolo()
                    }
                ) { Text("End solo") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEndSolo = false }) { Text("Cancel") }
            },
        )
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            catalog = emojiCatalog,
            onDismiss = { showEmojiPicker = false },
            onSelect = { asset ->
                if (viewModel.connected && canWrite) {
                    stageEmojiFragment(asset)
                } else {
                    showEmojiPicker = false
                }
            },
        )
    }

        val renameIndex = renameImageIndex
        if (renameIndex != null && renameIndex in pendingImages.indices) {
            AlertDialog(
                onDismissRequest = { renameImageIndex = null },
                title = { Text("重命名图片") },
                text = {
                    OutlinedTextField(
                        value = renameImageDraft,
                        onValueChange = { renameImageDraft = it.take(64) },
                        singleLine = true,
                        label = { Text("图片名") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val current = pendingImages[renameIndex]
                            val normalized = normalizeAttachmentNames(
                                pendingImages.mapIndexed { index, item ->
                                    if (index == renameIndex) renameImageDraft else item.second
                                }
                            )
                            pendingImages[renameIndex] = current.first to normalized[renameIndex]
                            renameImageIndex = null
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameImageIndex = null }) { Text("取消") }
                },
            )
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

private enum class MenuAction { File, Photo, Settings, CancelStaged }

private enum class ComposerDragLock { None, Send, Menu, Ignore }

@Composable
private fun AkiStatusBar(
    status: AgentStatus,
    members: List<AgentStatus>,
    avatars: Map<String, ByteArray>,
    onSelect: (String) -> Unit,
    onShowInfo: (AgentStatus) -> Unit,
    onOpenConversations: () -> Unit,
) {
    val colors = tokiColors
    TokiSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.bgPanel.copy(alpha = 0.86f),
        borderColor = colors.stroke.copy(alpha = 0.68f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${members.size} online",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim,
                    maxLines = 1,
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = members, key = { it.agentId }) { member ->
                        MemberChip(
                            member = member,
                            avatar = avatars[member.agentId],
                            selected = member.agentId == status.agentId,
                            onSelect = { onSelect(member.agentId) },
                            onShowProfile = { onShowInfo(member) },
                        )
                    }
                }
                IconButton(
                    onClick = onOpenConversations,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Conversations",
                        tint = colors.textDim,
                    )
                }
                if (status.agentId.isNotBlank()) {
                    IconButton(
                        onClick = { onShowInfo(status) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Member info",
                            tint = colors.textDim,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (status.activeTurn) {
                    WorkingPill()
                }
                val model = status.modelLabel()
                if (model.isNotBlank()) {
                    StatusPill(
                        text = model,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (status.reasoning.isNotBlank()) {
                    StatusPill(
                        text = status.reasoning,
                        modifier = Modifier.weight(1f),
                    )
                }
                val percent = status.contextPercent
                if (percent != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${(percent * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textDim,
                        )
                        LinearProgressIndicator(
                            progress = { percent },
                            modifier = Modifier
                                .width(48.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            trackColor = colors.bgCard,
                        )
                    }
                }
                status.queueBadgeLabel()?.let { label ->
                    StatusBadge(label)
                }
                status.approvalBadgeLabel()?.let { label ->
                    StatusBadge(
                        text = label,
                        attention = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberChip(
    member: AgentStatus,
    avatar: ByteArray?,
    selected: Boolean,
    onSelect: () -> Unit,
    onShowProfile: () -> Unit,
) {
    val colors = tokiColors
    val waitAccent = when {
        member.waitingUser -> colors.accentBlue
        member.waitingOther -> colors.qualityOrange
        else -> null
    }
    TokiSurface(
        modifier = if (waitAccent != null) {
            Modifier
                .border(2.dp, waitAccent.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(2.dp)
        } else {
            Modifier
        },
        shape = RoundedCornerShape(50),
        color = when {
            waitAccent != null -> waitAccent.copy(alpha = 0.12f)
            selected -> colors.accentPink.copy(alpha = 0.18f)
            else -> Color.White.copy(alpha = 0.045f)
        },
        borderColor = when {
            waitAccent != null -> waitAccent.copy(alpha = 0.88f)
            selected -> colors.accentPink.copy(alpha = 0.34f)
            else -> colors.stroke.copy(alpha = 0.44f)
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        ) {
            Box(modifier = Modifier.clickable(onClick = onShowProfile)) {
                if (avatar != null) {
                    ByteImage(
                        imageBytes = avatar,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.accentBlue.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Open ${member.agentName} profile",
                            modifier = Modifier.size(13.dp),
                            tint = colors.accentBlue,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.clickable(onClick = onSelect),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = member.agentName.ifBlank { member.agentId },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.text else colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
                if (member.activeTurn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.accentPink,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(memberDotColor(member)),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkingPill() {
    val colors = tokiColors
    TokiSurface(
        shape = RoundedCornerShape(50),
        color = colors.accentBlue.copy(alpha = 0.16f),
        borderColor = colors.accentBlue.copy(alpha = 0.30f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(9.dp),
                strokeWidth = 1.5.dp,
                color = colors.accentBlue,
            )
            Text(
                text = "Working…",
                style = MaterialTheme.typography.labelSmall,
                color = colors.text,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun memberDotColor(member: AgentStatus): Color {
    val colors = tokiColors
    if (member.isUser) return colors.qualityGreen
    val attention = member.pendingApprovals > 0
    val working = member.activeTurn || member.queueDepth > 0
    return when {
        attention -> colors.error
        working -> colors.accentPink
        !member.backendReady -> colors.error
        else -> colors.qualityGreen
    }
}

@Composable
private fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = tokiColors
    TokiSurface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.045f),
        borderColor = colors.stroke.copy(alpha = 0.42f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun StatusBadge(
    text: String,
    attention: Boolean = false,
) {
    val colors = tokiColors
    TokiSurface(
        shape = RoundedCornerShape(50),
        color = if (attention) {
            colors.error.copy(alpha = 0.18f)
        } else {
            colors.accentLilac.copy(alpha = 0.16f)
        },
        borderColor = if (attention) {
            colors.error.copy(alpha = 0.38f)
        } else {
            colors.accentLilac.copy(alpha = 0.32f)
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (attention) colors.error else colors.text,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SendActionButton(
    connected: Boolean,
    canSend: Boolean,
    canStage: Boolean,
    hasStaged: Boolean,
    onStage: () -> Unit,
    onCancelStaged: () -> Unit,
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
    val currentCanStage by rememberUpdatedState(canStage)
    val currentHasStaged by rememberUpdatedState(hasStaged)
    val currentOnStage by rememberUpdatedState(onStage)
    val currentOnCancelStaged by rememberUpdatedState(onCancelStaged)
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

    val menuItems = remember(hasStaged) {
        buildList {
            add(MenuSpec(MenuAction.File, 0, Icons.AutoMirrored.Filled.InsertDriveFile, "File"))
            add(MenuSpec(MenuAction.Photo, 1, Icons.Default.Photo, "Photo"))
            add(MenuSpec(MenuAction.Settings, 2, Icons.Default.Settings, "Settings"))
            if (hasStaged) {
                add(MenuSpec(MenuAction.CancelStaged, 3, Icons.Default.Close, "取消暂存"))
            }
        }
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
                                        hasStaged = currentHasStaged,
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
                                MenuAction.CancelStaged -> if (currentHasStaged) currentOnCancelStaged()
                                null -> Unit
                            }
                            locked == ComposerDragLock.Send && sendArmed -> currentOnSend()
                            isTap -> {
                                if (currentConnected && currentCanStage) {
                                    currentOnStage()
                                } else {
                                    showHint = true
                                    hintToken++
                                }
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
                val enabled = when (spec.action) {
                    MenuAction.Settings, MenuAction.CancelStaged -> true
                    else -> connected
                }
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
                    contentDescription = "Tap to stage an unfinished message, swipe left to send, swipe up for attachments, settings, and canceling staged messages.",
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
    hasStaged: Boolean,
): MenuAction? {
    var best: MenuAction? = null
    var bestDist = hitRadiusPx
    val items = buildList {
        add(MenuAction.File)
        add(MenuAction.Photo)
        add(MenuAction.Settings)
        if (hasStaged) add(MenuAction.CancelStaged)
    }
    for ((index, action) in items.withIndex()) {
        if ((action == MenuAction.File || action == MenuAction.Photo) && !connected) continue
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
    selectable: Boolean = true,
    alpha: Float = 1f,
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
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (selectable) {
                    Modifier.detectMessageLongPress {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongPress()
                    }
                } else {
                    Modifier
                }
            )
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

private const val MESSAGE_LONG_PRESS_TIMEOUT_MS = 3_000L

private fun Modifier.detectMessageLongPress(onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val timeout = MESSAGE_LONG_PRESS_TIMEOUT_MS
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
    val colors = tokiColors
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        TokiSurface(
            shape = RoundedCornerShape(14.dp),
            color = if (message.isError) {
                colors.errorContainer.copy(alpha = 0.84f)
            } else {
                colors.bgPanel.copy(alpha = 0.72f)
            },
            borderColor = if (message.isError) {
                colors.error.copy(alpha = 0.42f)
            } else {
                colors.stroke.copy(alpha = 0.46f)
            },
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = if (message.isError) {
                    colors.onErrorContainer
                } else {
                    colors.textDim
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun AgentTextBubble(
    message: ChatMessage,
    showAttachments: Boolean = true,
    mineAvatar: ByteArray?,
    peerAvatar: ByteArray?,
    onAvatarClick: (() -> Unit)? = null,
) {
    val colors = tokiColors
    ChatRow(
        isMine = false,
        sender = message.sender,
        senderId = message.senderId,
        mineAvatar = mineAvatar,
        peerAvatar = peerAvatar,
        onAvatarClick = onAvatarClick,
    ) {
        TokiBubbleSurface(
            isMine = false,
            shape = bubbleShape(false),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (message.content.isNotBlank()) {
                    SelectionContainer {
                        Markdown(
                            content = message.content,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (showAttachments && message.hasAttachments) {
                    if (message.content.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                    MessageAttachmentStrip(attachments = message.attachments)
                }
            }
        }
        CopyIconButton(
            text = message.content,
            tint = colors.textDim,
        )
    }
}

@Composable
private fun AgentToolCard(
    message: ChatMessage,
    onAvatarClick: (() -> Unit)? = null,
) {
    val colors = tokiColors
    var showDetail by remember { mutableStateOf(false) }
    ChatRow(
        isMine = false,
        sender = message.sender,
        senderId = message.senderId,
        mineAvatar = null,
        peerAvatar = null,
        onAvatarClick = onAvatarClick,
    ) {
        TokiSurface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.status == "error") {
                colors.errorContainer.copy(alpha = 0.84f)
            } else {
                colors.bgCard.copy(alpha = 0.72f)
            },
            borderColor = if (message.status == "error") {
                colors.error.copy(alpha = 0.42f)
            } else {
                colors.stroke.copy(alpha = 0.48f)
            },
        ) {
            AgentToolRow(
                message = message,
                onErrorSurface = message.status == "error",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                onClick = { showDetail = true },
            )
        }
    }
    if (showDetail) {
        ToolCallDetailDialog(
            message = message,
            onDismiss = { showDetail = false },
        )
    }
}

@Composable
private fun AgentToolGroupCard(
    messages: List<ChatMessage>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    connected: Boolean,
    localMemberId: String,
    onApprovalRespond: (String, Boolean, String) -> Unit,
    onAvatarClick: (() -> Unit)? = null,
) {
    val colors = tokiColors
    val toolMessages = messages.filter { it.agentKind == "tool" }
    val approvalMessages = messages.filter { it.agentKind == "approval" }
    val requiresAction = approvalMessages.any {
        it.status == "pending" || it.status == "allow_pending" || it.status == "deny_pending"
    }
    LaunchedEffect(requiresAction) {
        if (requiresAction) onExpandedChange(true)
    }
    val effectiveExpanded = expanded || requiresAction
    val counts = toolCallCounts(toolMessages)
    val names = toolMessages.map { toolDisplayName(it.toolName) }.distinct()
    val summary = toolGroupSummary(
        toolNames = names,
        counts = counts,
        approvalCount = approvalMessages.size,
        requiresAction = requiresAction,
    )
    val subtitle = buildAnnotatedString {
        if (!summary.hasFailedSpan) {
            append(summary.text)
        } else {
            append(summary.text.substring(0, summary.failedStart))
            withStyle(SpanStyle(color = colors.error)) {
                append(summary.text.substring(summary.failedStart, summary.failedEnd))
            }
            append(summary.text.substring(summary.failedEnd))
        }
    }
    val title = buildList {
        if (toolMessages.isNotEmpty()) {
            add("${toolMessages.size} tool call${if (toolMessages.size == 1) "" else "s"}")
        }
        if (approvalMessages.isNotEmpty()) {
            add("${approvalMessages.size} approval${if (approvalMessages.size == 1) "" else "s"}")
        }
    }.joinToString(" · ")
    val tone = toolGroupTone(requiresAction)
    val containerColor = when (tone) {
        ToolGroupTone.Approval -> colors.accentBlue.copy(alpha = 0.15f)
        ToolGroupTone.Normal -> colors.bgCard.copy(alpha = 0.72f)
    }
    val contentColor = colors.text
    val borderColor = when (tone) {
        ToolGroupTone.Approval -> colors.accentBlue.copy(alpha = 0.34f)
        ToolGroupTone.Normal -> colors.stroke.copy(alpha = 0.48f)
    }
    var detailId by remember { mutableStateOf<String?>(null) }
    val detailMessage = detailId?.let { id -> messages.firstOrNull { it.id == id } }
    ChatRow(
        isMine = false,
        sender = messages.first().sender,
        senderId = messages.first().senderId,
        mineAvatar = null,
        peerAvatar = null,
        onAvatarClick = onAvatarClick,
    ) {
        TokiSurface(
            shape = RoundedCornerShape(18.dp),
            color = containerColor,
            borderColor = borderColor,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .then(
                            if (requiresAction) Modifier
                            else Modifier.clickable { onExpandedChange(!effectiveExpanded) }
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = toolGroupIcon(messages.map { it.toolName }),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.textDim,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                        )
                        if (subtitle.text.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (requiresAction) {
                        Text(
                            text = "Action",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    } else if (counts.running > 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else if (counts.failed == 0) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textDim,
                        )
                    }
                    if (!requiresAction) {
                        Icon(
                            imageVector = if (effectiveExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (effectiveExpanded) "Collapse tool calls" else "Expand tool calls",
                            modifier = Modifier.size(20.dp),
                            tint = colors.textDim,
                        )
                    }
                }
                AnimatedVisibility(visible = effectiveExpanded) {
                    Column {
                        HorizontalDivider(
                            color = colors.textDim.copy(alpha = 0.15f)
                        )
                        messages.forEachIndexed { index, message ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = colors.textDim.copy(alpha = 0.12f),
                                )
                            }
                            if (message.agentKind == "approval") {
                                TokiSurface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = approvalContainerColor(message.status),
                                    borderColor = if (message.status == "timeout") {
                                        colors.error.copy(alpha = 0.38f)
                                    } else {
                                        colors.accentBlue.copy(alpha = 0.26f)
                                    },
                                    modifier = Modifier.padding(8.dp),
                                ) {
                                    AgentApprovalContent(
                                        message = message,
                                        connected = connected,
                                        localMemberId = localMemberId,
                                        onRespond = { allow, approvalMessage ->
                                            onApprovalRespond(
                                                message.approvalId,
                                                allow,
                                                approvalMessage,
                                            )
                                        },
                                        modifier = Modifier.padding(10.dp),
                                        contentColor = approvalContentColor(message.status),
                                    )
                                }
                            } else {
                                AgentToolRow(
                                    message = message,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    onClick = { detailId = message.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (detailMessage != null) {
        ToolCallDetailDialog(
            message = detailMessage,
            onDismiss = { detailId = null },
        )
    }
}

@Composable
private fun AgentToolRow(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onErrorSurface: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = tokiColors
    val status = message.status
    val label = toolCallStatusLabel(status)
    val errorTint = if (onErrorSurface) colors.onErrorContainer else colors.error
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Tool call details" }
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = toolIcon(message.toolName),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (status == "error") errorTint else colors.textDim,
        )
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = toolDisplayName(message.toolName),
                style = MaterialTheme.typography.labelLarge,
                color = if (status == "error") errorTint else colors.text,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textDim,
            )
        }
        when (status) {
            "running" -> CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
            )
            "error" -> Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = errorTint,
            )
            else -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = colors.textDim,
            )
        }
    }
}

@Composable
private fun ToolCallDetailDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
) {
    val colors = tokiColors
    val detail = parseToolCallDetail(message.content)
    val finished = message.status == "success" ||
        message.status == "error" ||
        message.status == "complete"
    val maxHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.8f
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        TokiSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.bgPanel.copy(alpha = 0.98f),
            borderColor = colors.stroke.copy(alpha = 0.72f),
        ) {
            Column(modifier = Modifier.heightIn(max = maxHeight)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = toolIcon(message.toolName),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = colors.textDim,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = toolDisplayName(message.toolName),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                        )
                        Text(
                            text = toolCallStatusLabel(message.status),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (message.status == "error") colors.error else colors.textDim,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textDim,
                        )
                    }
                }
                HorizontalDivider(color = colors.stroke.copy(alpha = 0.55f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (detail == null) {
                        Text(
                            text = "This call was recorded before arguments and results were kept.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textDim,
                        )
                    } else {
                        ToolCallDetailSection(
                            title = "Arguments",
                            text = detail.arguments.ifBlank { "No arguments" },
                            copyText = detail.arguments,
                        )
                        when {
                            !finished && (message.status == "running" || message.status.isBlank()) -> {
                                ToolCallDetailSection(
                                    title = "Result",
                                    text = "Still running",
                                    copyText = "",
                                )
                            }
                            message.status == "error" -> {
                                val result = detail.result.orEmpty()
                                ToolCallDetailSection(
                                    title = "Error",
                                    text = result.ifBlank { "No output" },
                                    copyText = result,
                                )
                            }
                            else -> {
                                val result = detail.result.orEmpty()
                                ToolCallDetailSection(
                                    title = "Result",
                                    text = result.ifBlank { "No output" },
                                    copyText = result,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCallDetailSection(
    title: String,
    text: String,
    copyText: String,
) {
    val colors = tokiColors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            CopyIconButton(text = copyText, tint = colors.textDim)
        }
        TokiSurface(
            shape = RoundedCornerShape(12.dp),
            color = colors.bgApp.copy(alpha = 0.58f),
            borderColor = colors.stroke.copy(alpha = 0.42f),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun AgentApprovalCard(
    message: ChatMessage,
    connected: Boolean,
    localMemberId: String,
    onRespond: (Boolean, String) -> Unit,
    onAvatarClick: (() -> Unit)? = null,
) {
    val colors = tokiColors
    ChatRow(
        isMine = false,
        sender = message.sender,
        senderId = message.senderId,
        mineAvatar = null,
        peerAvatar = null,
        onAvatarClick = onAvatarClick,
    ) {
        TokiSurface(
            shape = RoundedCornerShape(18.dp),
            color = approvalContainerColor(message.status),
            borderColor = if (message.status == "timeout") {
                colors.error.copy(alpha = 0.42f)
            } else {
                colors.accentBlue.copy(alpha = 0.30f)
            },
        ) {
            AgentApprovalContent(
                message = message,
                connected = connected,
                localMemberId = localMemberId,
                onRespond = onRespond,
                modifier = Modifier.padding(12.dp),
                contentColor = approvalContentColor(message.status),
            )
        }
    }
}

@Composable
private fun AgentApprovalContent(
    message: ChatMessage,
    connected: Boolean,
    localMemberId: String,
    onRespond: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    var expanded by remember(message.approvalId) { mutableStateOf(false) }
    var showDenyDialog by remember(message.approvalId) { mutableStateOf(false) }
    var denyMessage by remember(message.approvalId) { mutableStateOf("") }
    val isRequester = approvalRequesterMatches(message.requesterId, localMemberId)
    val canAct = connected && isRequester && message.status == "pending"
    val statusLabel = when (message.status) {
        "pending" -> "Waiting for approval"
        "allow_pending" -> "Sending approval…"
        "deny_pending" -> "Sending denial…"
        "allow" -> "Approved"
        "deny" -> "Denied"
        "timeout" -> "Timed out"
        else -> message.status.replaceFirstChar { it.uppercase() }
    }
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = toolIcon(message.toolName),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Tool approval · ${toolDisplayName(message.toolName)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.8f),
        )
        if (message.timeoutS > 0 && message.status == "pending") {
            Text(
                text = "Request expires after ${message.timeoutS}s",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
            )
        }
        if (message.details.isNotBlank()) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
            ) {
                Text(if (expanded) "Hide details" else "Show details")
            }
            if (expanded) {
                val colors = tokiColors
                TokiSurface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bgApp.copy(alpha = 0.58f),
                    borderColor = colors.stroke.copy(alpha = 0.42f),
                ) {
                    SelectionContainer {
                        Text(
                            text = message.details,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
        when {
            canAct -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRespond(true, "") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Allow")
                    }
                    OutlinedButton(
                        onClick = { showDenyDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Deny")
                    }
                }
            }
            !isRequester && message.status == "pending" -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Waiting for the original sender to decide.",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.75f),
                )
            }
        }
    }
    if (showDenyDialog && canAct) {
        AlertDialog(
            onDismissRequest = { showDenyDialog = false },
            title = { Text("Deny tool call?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Add an optional message to tell the agent why. " +
                            "It will be returned with the denial.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = denyMessage,
                        onValueChange = {
                            denyMessage = it.take(MAX_APPROVAL_MESSAGE_CHARS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Approval message") },
                        placeholder = { Text("What should the agent do instead?") },
                        minLines = 3,
                        maxLines = 5,
                    )
                    Text(
                        "${denyMessage.length}/$MAX_APPROVAL_MESSAGE_CHARS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val messageToSend = denyMessage.trim()
                        showDenyDialog = false
                        denyMessage = ""
                        onRespond(false, messageToSend)
                    },
                ) {
                    Text("Send denial")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDenyDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun approvalContainerColor(status: String): Color =
    if (status == "timeout") {
        tokiColors.errorContainer.copy(alpha = 0.82f)
    } else {
        tokiColors.accentBlue.copy(alpha = 0.14f)
    }

@Composable
private fun approvalContentColor(status: String): Color =
    if (status == "timeout") {
        tokiColors.onErrorContainer
    } else {
        tokiColors.text
    }

@Composable
private fun LetterAvatar(name: String) {
    val colors = tokiColors
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(colors.accentBlue.copy(alpha = 0.16f))
            .border(1.dp, colors.accentBlue.copy(alpha = 0.34f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium,
            color = colors.text,
        )
    }
}

@Composable
private fun UserAvatar(
    name: String,
    photo: ByteArray?,
    onClick: (() -> Unit)? = null,
) {
    val colors = tokiColors
    val clickable = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    if (photo != null) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.bgCard)
                .border(1.dp, colors.accentLilac.copy(alpha = 0.42f), CircleShape)
                .padding(2.dp)
                .then(clickable),
        ) {
            ByteImage(
                imageBytes = photo,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    } else {
        Box(modifier = clickable) {
            LetterAvatar(name)
        }
    }
}

private fun bubbleShape(isMine: Boolean) = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = if (isMine) 20.dp else 6.dp,
    bottomEnd = if (isMine) 6.dp else 20.dp,
)

@Composable
internal fun ChatRow(
    isMine: Boolean,
    sender: String,
    senderId: String = "",
    mineAvatar: ByteArray?,
    peerAvatar: ByteArray?,
    onAvatarClick: (() -> Unit)? = null,
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
            UserAvatar(name = sender, photo = mineAvatar, onClick = onAvatarClick)
        } else {
            UserAvatar(name = sender, photo = peerAvatar, onClick = onAvatarClick)
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(0.78f, fill = false),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = sender.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokiColors.accentLilac,
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
    showAttachments: Boolean = true,
    mineAvatar: ByteArray? = null,
    peerAvatar: ByteArray? = null,
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val isMine = message.isMine
    val colors = tokiColors
    val contentColor = colors.text
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
        senderId = message.senderId,
        mineAvatar = mineAvatar,
        peerAvatar = peerAvatar,
        onAvatarClick = onAvatarClick,
        modifier = modifier,
    ) {
        TokiBubbleSurface(
            isMine = isMine,
            shape = bubbleShape(isMine),
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
                        text = when {
                            message.hasAttachments && isMine -> "Sending images"
                            message.hasAttachments -> "Receiving images"
                            isMine -> "Sending text"
                            else -> "Receiving text"
                        },
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
                if (showAttachments && message.hasAttachments) {
                    if (showPreview || message.isTransferring) Spacer(modifier = Modifier.height(8.dp))
                    MessageAttachmentStrip(attachments = message.attachments)
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
                    tint = colors.textDim,
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
    val colors = tokiColors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        TokiSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp, vertical = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.bgPanel.copy(alpha = 0.98f),
            borderColor = colors.stroke.copy(alpha = 0.72f),
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
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    CopyIconButton(
                        text = text,
                        tint = colors.textDim,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textDim,
                        )
                    }
                }
                HorizontalDivider(color = colors.stroke.copy(alpha = 0.55f))
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
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
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val imageBytes = message.pic
    val imagePath = message.filePath
    val hasImage = imageBytes != null || !imagePath.isNullOrBlank()
    val colors = tokiColors
    var showFull by remember { mutableStateOf(false) }
    val fileSaverLauncher = rememberFileSaverLauncher { }

    ChatRow(
        isMine = message.isMine,
        sender = message.sender,
        senderId = message.senderId,
        mineAvatar = mineAvatar,
        peerAvatar = peerAvatar,
        onAvatarClick = onAvatarClick,
        modifier = modifier,
    ) {
        TokiBubbleSurface(
            isMine = message.isMine,
            modifier = Modifier
                .widthIn(max = 220.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .heightIn(min = if (hasImage) 0.dp else 160.dp, max = 240.dp)
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

                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
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
                FullImageContent(
                    path = imagePath,
                    explicitBytes = imageBytes,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                if (message.content.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
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
    onAvatarClick: (() -> Unit)? = null,
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
    val colors = tokiColors

    ChatRow(
        isMine = isMine,
        sender = message.sender,
        senderId = message.senderId,
        mineAvatar = mineAvatar,
        peerAvatar = peerAvatar,
        onAvatarClick = onAvatarClick,
        modifier = modifier,
    ) {
        TokiBubbleSurface(
            isMine = isMine,
            shape = bubbleShape(isMine),
            modifier = Modifier.widthIn(min = 180.dp, max = 260.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (isMine) colors.accentPink else colors.accentBlue,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.text,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fileStatusLabel(message),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textDim,
                        )
                    }
                    if (onCancel != null) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel send",
                                tint = colors.textDim,
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
                                contentDescription = "Save file",
                                tint = colors.accentLilac,
                            )
                        }
                    }
                }
                if (message.isTransferring) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { message.transferProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accentPink,
                        trackColor = colors.accentPink.copy(alpha = 0.20f),
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

package com.acite.tokifactor.pages

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.acite.tokifactor.model.ChatMessage

internal const val LIST_BOTTOM_SLACK_PX = 2

/**
 * True when the end of the last item sits inside the viewport, above the
 * content padding. A tall last item whose tail is still below the viewport is
 * not at the bottom.
 */
internal fun isListAtBottom(
    lastVisibleIndex: Int,
    lastItemOffset: Int,
    lastItemSize: Int,
    totalItems: Int,
    viewportEndOffset: Int,
    afterContentPadding: Int,
    slackPx: Int = LIST_BOTTOM_SLACK_PX,
): Boolean {
    if (totalItems <= 0 || lastVisibleIndex < 0) return true
    if (lastVisibleIndex < totalItems - 1) return false
    val end = lastItemOffset + lastItemSize
    val target = viewportEndOffset - afterContentPadding
    return end <= target + slackPx
}

internal fun LazyListState.isAtEnd(slackPx: Int = LIST_BOTTOM_SLACK_PX): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull()
    return isListAtBottom(
        lastVisibleIndex = last?.index ?: -1,
        lastItemOffset = last?.offset ?: 0,
        lastItemSize = last?.size ?: 0,
        totalItems = info.totalItemsCount,
        viewportEndOffset = info.viewportEndOffset,
        afterContentPadding = info.afterContentPadding,
        slackPx = slackPx,
    )
}

internal suspend fun LazyListState.revealBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    scrollToItem(lastIndex)
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return
    if (last.index != lastIndex) return
    val targetEnd = info.viewportEndOffset - info.afterContentPadding
    val remainder = last.offset + last.size - targetEnd
    if (remainder > 1) {
        scroll { scrollBy(remainder.toFloat()) }
    }
}

internal fun NestedScrollSource.adjustsBottomStick(autoScrolling: Boolean): Boolean =
    this == NestedScrollSource.UserInput ||
        (this == NestedScrollSource.SideEffect && !autoScrolling)

/**
 * Changes when a system, tool, user, or agent message is added or updated,
 * including a tool call merged into an existing group.
 */
internal fun displayFeedKey(items: List<ChatDisplayItem>): Int {
    var hash = items.size
    for (item in items) {
        hash = 31 * hash + item.key.hashCode()
        hash = when (item) {
            is ChatDisplayItem.ToolGroup -> item.messages.fold(hash) { acc, message ->
                31 * acc + messageScrollToken(message)
            }
            is ChatDisplayItem.MessageItem -> 31 * hash + messageScrollToken(item.message)
        }
    }
    return hash
}

private fun messageScrollToken(message: ChatMessage): Int {
    var token = message.id.hashCode()
    token = 31 * token + message.content.hashCode()
    token = 31 * token + message.status.hashCode()
    token = 31 * token + message.seq
    token = 31 * token + message.phase.hashCode()
    token = 31 * token + message.isSystem.hashCode()
    token = 31 * token + message.agentKind.hashCode()
    token = 31 * token + message.attachments.size
    return token
}

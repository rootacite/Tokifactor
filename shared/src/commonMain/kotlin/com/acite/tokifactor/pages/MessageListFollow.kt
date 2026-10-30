package com.acite.tokifactor.pages

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
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
    if (layoutInfo.totalItemsCount <= 0) return
    // A split agent message adds several rows in one frame. Their heights are
    // still unknown on the first scroll, so one scrollToItem stops on the first
    // new row. Measure, then scroll again until the tail is actually in view.
    repeat(5) {
        val count = layoutInfo.totalItemsCount
        if (count <= 0) return
        val lastIndex = count - 1
        scrollToItem(lastIndex)
        val info = layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        if (last != null && last.index == lastIndex) {
            val targetEnd = info.viewportEndOffset - info.afterContentPadding
            val remainder = last.offset + last.size - targetEnd
            if (remainder > 1) {
                scroll { scrollBy(remainder.toFloat()) }
            }
        } else {
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
            scroll { scrollBy(viewport.toFloat()) }
        }
        withFrameNanos { }
        if (isAtEnd() && layoutInfo.totalItemsCount == count) return
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

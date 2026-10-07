package com.raahi.app.feature.tripeditor

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

@Stable
class StopReorderState(
    private val listState: LazyListState,
    private val firstStopItemIndex: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Long?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var indexAwaitingLayout: Int? = null

    fun start(key: Long) {
        draggingKey = key
        dragOffset = 0f
        indexAwaitingLayout = null
    }

    fun drag(delta: Float) {
        val key = draggingKey ?: return
        dragOffset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: return
        if (indexAwaitingLayout == dragged.index) return
        indexAwaitingLayout = null
        val center = dragged.offset + dragged.size / 2f + dragOffset
        val target = items.firstOrNull {
            it.key != key && it.key is Long && center >= it.offset && center < it.offset + it.size
        } ?: return
        val base = firstStopItemIndex()
        onMove(dragged.index - base, target.index - base)
        val newLayoutOffset = if (target.index > dragged.index) target.offset + target.size - dragged.size else target.offset
        dragOffset += dragged.offset - newLayoutOffset
        indexAwaitingLayout = dragged.index
    }

    fun end() {
        draggingKey = null
        dragOffset = 0f
        indexAwaitingLayout = null
    }
}

@Composable
fun rememberStopReorderState(
    listState: LazyListState,
    firstStopItemIndex: Int,
    onMove: (from: Int, to: Int) -> Unit,
): StopReorderState {
    val latestMove = rememberUpdatedState(onMove)
    val latestBase = rememberUpdatedState(firstStopItemIndex)
    return remember(listState) {
        StopReorderState(listState, { latestBase.value }) { from, to -> latestMove.value(from, to) }
    }
}

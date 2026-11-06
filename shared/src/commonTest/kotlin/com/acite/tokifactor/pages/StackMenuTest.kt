package com.acite.tokifactor.pages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StackMenuTest {

    @Test
    fun allItemsReachFullProgressWhenStackIsFullyOpen() {
        for (totalItems in listOf(4, 5)) {
            for (index in 0 until totalItems) {
                val progress = stackItemProgress(
                    stackProgress = 1.0f,
                    index = index,
                    totalItems = totalItems,
                )
                assertEquals(1.0f, progress, "Item $index of $totalItems must reach 1.0f when fully open")
            }
        }
    }

    @Test
    fun itemsDoNotOverlapWhenFullyOpen() {
        val stepPx = 56f
        for (totalItems in listOf(4, 5)) {
            val positions = (0 until totalItems).map { index ->
                val progress = stackItemProgress(
                    stackProgress = 1.0f,
                    index = index,
                    totalItems = totalItems,
                )
                -stepPx * (index + 1) * progress
            }

            for (i in 0 until positions.size - 1) {
                val distance = kotlin.math.abs(positions[i + 1] - positions[i])
                assertEquals(stepPx, distance, 0.001f, "Spacing between item $i and ${i + 1} must be exactly stepPx")
            }
        }
    }

    @Test
    fun allItemsHaveZeroProgressWhenStackIsClosed() {
        for (totalItems in listOf(4, 5)) {
            for (index in 0 until totalItems) {
                val progress = stackItemProgress(
                    stackProgress = 0.0f,
                    index = index,
                    totalItems = totalItems,
                )
                assertEquals(0.0f, progress, "Item $index of $totalItems must be 0.0f when closed")
            }
        }
    }

    @Test
    fun intermediateProgressIsStaggered() {
        val totalItems = 4
        val p0 = stackItemProgress(stackProgress = 0.5f, index = 0, totalItems = totalItems)
        val p1 = stackItemProgress(stackProgress = 0.5f, index = 1, totalItems = totalItems)
        val p2 = stackItemProgress(stackProgress = 0.5f, index = 2, totalItems = totalItems)
        val p3 = stackItemProgress(stackProgress = 0.5f, index = 3, totalItems = totalItems)

        assertTrue(p0 > p1, "Item 0 should lead item 1")
        assertTrue(p1 > p2, "Item 1 should lead item 2")
        assertTrue(p2 > p3, "Item 2 should lead item 3")
    }
}

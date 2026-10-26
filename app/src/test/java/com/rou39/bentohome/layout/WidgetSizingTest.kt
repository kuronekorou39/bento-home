package com.rou39.bentohome.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizingTest {
    private val grid = GridSpec(4, 6, 2)

    @Test
    fun targetCellsAreBaseCellsTimesSubdivisions() {
        // Duolingo（小）: targetCell 2×1
        assertEquals(4 to 2, WidgetSizing.spans(2, 1, 0, 0, 260f, 300f, grid))
    }

    @Test
    fun minSizeIsRoundedUpToWholeCells() {
        // GitHub: targetCell 未指定。最小サイズから1マス単位に切り上げる
        assertEquals(6 to 2, WidgetSizing.spans(0, 0, 700, 250, 260f, 300f, grid))
    }

    @Test
    fun spansAreClampedToGrid() {
        assertEquals(8 to 12, WidgetSizing.spans(9, 9, 0, 0, 260f, 300f, grid))
        assertEquals(2 to 2, WidgetSizing.spans(0, 0, 0, 0, 260f, 300f, grid))
    }

    @Test
    fun resizeLimitsFromProviderBounds() {
        // 最小 300×250px → 2×1 マス、最大 800×700px → 3×2 マス（1マス 260×300px）
        val limits = WidgetSizing.resizeLimits(300, 250, 800, 700, 260f, 300f, grid)
        assertEquals(ResizeLimits(4, 2, 6, 4), limits)
    }

    @Test
    fun zeroMaxMeansWholeGrid() {
        val limits = WidgetSizing.resizeLimits(0, 0, 0, 0, 260f, 300f, grid)
        assertEquals(ResizeLimits(2, 2, 8, 12), limits)
    }

    @Test
    fun maxNeverBelowMin() {
        val limits = WidgetSizing.resizeLimits(600, 600, 100, 100, 260f, 300f, grid)
        assertEquals(ResizeLimits(6, 4, 6, 4), limits)
    }
}

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
}

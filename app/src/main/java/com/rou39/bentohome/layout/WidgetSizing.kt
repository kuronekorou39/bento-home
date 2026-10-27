package com.rou39.bentohome.layout

import kotlin.math.ceil
import kotlin.math.floor

/** ウィジェットの占有小マス数を決める（docs/design.md §5）。 */
object WidgetSizing {
    /**
     * 提供元の targetCell（通常の1マス単位）があれば優先し、無ければ最小サイズを1マスの寸法で割り上げる。
     * サイズは1マス刻みなので、結果は subdivisions の倍数になる。グリッドからはみ出さないよう上限を掛ける。
     *
     * @return 小マス単位の (幅, 高さ)
     */
    fun spans(
        targetCellWidth: Int,
        targetCellHeight: Int,
        minWidthPx: Int,
        minHeightPx: Int,
        baseCellWidthPx: Float,
        baseCellHeightPx: Float,
        grid: GridSpec,
    ): Pair<Int, Int> {
        val baseW = if (targetCellWidth > 0) targetCellWidth else cellsFor(minWidthPx, baseCellWidthPx)
        val baseH = if (targetCellHeight > 0) targetCellHeight else cellsFor(minHeightPx, baseCellHeightPx)
        return baseW.coerceIn(1, grid.baseColumns) * grid.subdivisions to
            baseH.coerceIn(1, grid.baseRows) * grid.subdivisions
    }

    private fun cellsFor(px: Int, cellPx: Float): Int =
        if (px <= 0 || cellPx <= 0f) 1 else ceil(px / cellPx).toInt()

    /**
     * リサイズできる範囲（小マス単位、subdivisions の倍数）。
     * 最小は提供元の minResize を1マスの寸法で割り上げ、最大は maxResize（0 なら無制限）で割り下げる。
     */
    fun resizeLimits(
        minResizeWidthPx: Int,
        minResizeHeightPx: Int,
        maxResizeWidthPx: Int,
        maxResizeHeightPx: Int,
        baseCellWidthPx: Float,
        baseCellHeightPx: Float,
        grid: GridSpec,
    ): ResizeLimits {
        val minW = cellsFor(minResizeWidthPx, baseCellWidthPx).coerceIn(1, grid.baseColumns)
        val minH = cellsFor(minResizeHeightPx, baseCellHeightPx).coerceIn(1, grid.baseRows)
        val maxW = maxCells(maxResizeWidthPx, baseCellWidthPx, grid.baseColumns).coerceAtLeast(minW)
        val maxH = maxCells(maxResizeHeightPx, baseCellHeightPx, grid.baseRows).coerceAtLeast(minH)
        val sub = grid.subdivisions
        return ResizeLimits(minW * sub, minH * sub, maxW * sub, maxH * sub)
    }

    private fun maxCells(px: Int, cellPx: Float, limit: Int): Int =
        if (px <= 0 || cellPx <= 0f) limit else floor(px / cellPx).toInt().coerceIn(1, limit)
}

data class ResizeLimits(val minWidth: Int, val minHeight: Int, val maxWidth: Int, val maxHeight: Int) {
    val widthAdjustable: Boolean get() = minWidth < maxWidth
    val heightAdjustable: Boolean get() = minHeight < maxHeight

    fun clampWidth(w: Int) = w.coerceIn(minWidth, maxWidth)
    fun clampHeight(h: Int) = h.coerceIn(minHeight, maxHeight)
}

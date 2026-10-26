package com.rou39.bentohome.layout

import kotlin.math.ceil

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
}

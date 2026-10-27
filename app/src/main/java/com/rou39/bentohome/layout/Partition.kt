package com.rou39.bentohome.layout

/**
 * ページの仕切り（plan.md §10）。弁当箱の区切りを見た目だけで描く。項目は区画をまたいで置ける。
 * 区画はページに対する割合で持つ。マス目の線には合わせない（4列を3等分すると幅がそろわなくなるため）。
 */
enum class Partition(val label: String, val areas: List<Area>) {
    NONE("なし", emptyList()),

    /** 上下に2つ。 */
    NIDAN("二段", listOf(Area(0f, 0f, 1f, HALF), Area(0f, HALF, 1f, 1f))),

    /** 田の字に4つ。 */
    SHOKADO("松花堂", grid(listOf(0f, HALF, 1f), listOf(0f, HALF, 1f))),

    /** 左に大きな区画、右に小さな区画3つ。 */
    MAKUNOUCHI(
        "幕の内",
        listOf(Area(0f, 0f, HALF, 1f)) + grid(listOf(HALF, 1f), listOf(0f, THIRD, TWO_THIRDS, 1f)),
    ),

    /** 上に大きな区画、下に小さな区画2つ。 */
    HINOMARU("日の丸", listOf(Area(0f, 0f, 1f, TWO_THIRDS)) + grid(listOf(0f, HALF, 1f), listOf(TWO_THIRDS, 1f))),

    /** 3×3。 */
    JUBAKO("重箱の1段", grid(listOf(0f, THIRD, TWO_THIRDS, 1f), listOf(0f, THIRD, TWO_THIRDS, 1f))),
    ;

    /** ページに対する割合の区画。 */
    data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    companion object {
        fun fromName(name: String?): Partition = entries.firstOrNull { it.name == name } ?: NONE
    }
}

private const val HALF = 0.5f
private const val THIRD = 1f / 3
private const val TWO_THIRDS = 2f / 3

/** 縦横の境目の割合から、格子状の区画を作る。 */
private fun grid(xs: List<Float>, ys: List<Float>): List<Partition.Area> =
    ys.zipWithNext().flatMap { (top, bottom) ->
        xs.zipWithNext().map { (left, right) -> Partition.Area(left, top, right, bottom) }
    }

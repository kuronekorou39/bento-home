package com.rou39.bentohome.layout

import org.junit.Assert.assertEquals
import org.junit.Test

/** 一覧で選んだアプリをまとめて置く（plan.md §10）。 */
class PlaceAppsTest {
    private val grid = GridSpec(2, 2, 2) // 1ページに4個
    private fun apps(n: Int) = (1..n).map { AppRef("com.app$it/.Main", 0) }
    private fun ws(pages: Int) = Workspace((1..pages).map { Page(it.toLong(), it - 1, grid) }, emptyList(), emptyList())
    private fun pagesOf(w: Workspace, ids: List<Long>) = ids.map { w.item(it)!!.pageId }

    @Test
    fun placesFromChosenPageAndOverflowsForwardOnly() {
        // 2ページ目を選んで6個 → 2ページ目に4個、入りきらない2個は新しい3ページ目（1ページ目には戻らない）
        val (w, ids) = LayoutEngine.placeApps(ws(2), apps(6), startPageId = 2, grid = grid)
        val pages = pagesOf(w, ids)
        assertEquals(listOf(2L, 2L, 2L, 2L), pages.take(4))
        assertEquals(setOf(3L), pages.drop(4).toSet())
        assertEquals(0, w.itemsOn(1).size)
        assertEquals(apps(6), ids.map { (w.item(it)!!.content as ItemContent.App).app })
    }

    @Test
    fun nullStartMeansNewPage() {
        val (w, ids) = LayoutEngine.placeApps(ws(2), apps(1), startPageId = null, grid = grid)
        assertEquals(3, w.pages.size)
        assertEquals(listOf(3L), pagesOf(w, ids))
    }
}

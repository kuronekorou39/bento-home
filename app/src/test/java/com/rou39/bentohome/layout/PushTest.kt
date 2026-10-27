package com.rou39.bentohome.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 押し出して置く・選んだ項目をまとめて運ぶ。 */
class PushTest {
    private val grid = GridSpec(4, 4, 2) // 小マス 8×8、アイコンは 2×2
    private fun app(id: Long, page: Long, x: Int, y: Int, w: Int = 2, h: Int = 2) =
        WorkspaceItem(id, page, CellRect(x, y, w, h), ItemContent.App(AppRef("com.app$id/.Main", 0)))

    private fun ws(vararg items: WorkspaceItem, pages: Int = 1) =
        Workspace((1..pages).map { Page(it.toLong(), it - 1, grid) }, items.toList(), emptyList())

    private fun noOverlap(w: Workspace) {
        for (page in w.pages) {
            val items = w.itemsOn(page.id)
            for (i in items.indices) for (j in i + 1 until items.size) {
                assertTrue("${items[i]} と ${items[j]} が重なる", !items[i].rect.overlaps(items[j].rect))
            }
        }
    }

    @Test
    fun widgetPushesIconsToNearestFreeCells() {
        // 左上に 4×4 のウィジェットを置くと、そこにあるアイコン2つが近くの空きへ移る
        val start = ws(app(1, 1, 0, 0), app(2, 1, 2, 2), app(3, 1, 6, 6))
        val widget = WorkspaceItem(10, 1, CellRect(0, 0, 4, 4), ItemContent.Widget(5, "p/.W"))
        val r = LayoutEngine.placeWithPush(start, 1, listOf(widget))
        assertNotNull(r)
        r!!
        assertEquals(setOf(1L, 2L), r.displaced)
        assertEquals(CellRect(6, 6, 2, 2), r.workspace.item(3)!!.rect)
        noOverlap(r.workspace)
        // マス目の揃い方を保つ（偶数の位置のまま）
        for (id in listOf(1L, 2L)) {
            val rect = r.workspace.item(id)!!.rect
            assertEquals(0, rect.x % 2)
            assertEquals(0, rect.y % 2)
        }
    }

    @Test
    fun moveIntoOccupiedCellSwapsIntoFreedSpot() {
        // 1ページに2つだけ。隣へ動かすと、相手は近くの空きへ（元の場所も候補になる）
        val start = ws(app(1, 1, 0, 0), app(2, 1, 2, 0))
        val r = LayoutEngine.placeWithPush(start, 1, listOf(start.item(1)!!.copy(rect = CellRect(2, 0, 2, 2))))!!
        assertEquals(CellRect(2, 0, 2, 2), r.workspace.item(1)!!.rect)
        assertEquals(setOf(2L), r.displaced)
        noOverlap(r.workspace)
    }

    @Test
    fun blockedWhenNoRoomToPush() {
        // ページが埋まっているところにウィジェットを足すと、押し出す先が無い
        val full = (0 until 16).map { i -> app(i + 1L, 1, (i % 4) * 2, (i / 4) * 2) }
        val start = ws(*full.toTypedArray())
        val widget = WorkspaceItem(100, 1, CellRect(0, 0, 4, 4), ItemContent.Widget(5, "p/.W"))
        assertNull(LayoutEngine.placeWithPush(start, 1, listOf(widget)))
    }

    @Test
    fun resizeIntoNeighbourPushesIt() {
        val widget = WorkspaceItem(10, 1, CellRect(0, 0, 4, 2), ItemContent.Widget(5, "p/.W"))
        val start = ws(widget, app(1, 1, 4, 0))
        val r = LayoutEngine.placeWithPush(start, 1, listOf(widget.copy(rect = CellRect(0, 0, 6, 2))))!!
        assertEquals(CellRect(0, 0, 6, 2), r.workspace.item(10)!!.rect)
        assertEquals(setOf(1L), r.displaced)
        noOverlap(r.workspace)
    }

    @Test
    fun groupKeepsFormationAndClampsToPage() {
        // 横に並んだ2つを、右端へ運ぶ。はみ出す分は収まるところまで寄せる
        val start = ws(app(1, 1, 0, 0), app(2, 1, 2, 0), pages = 2)
        val movers = LayoutEngine.groupMovers(start, listOf(1, 2), anchorId = 1, pageId = 2, x = 6, y = 4)!!
        val rects = movers.associate { it.id to it.rect }
        assertEquals(CellRect(4, 4, 2, 2), rects[1])
        assertEquals(CellRect(6, 4, 2, 2), rects[2])
        assertTrue(movers.all { it.pageId == 2L })
        val r = LayoutEngine.placeWithPush(start, 2, movers)!!
        assertEquals(0, r.workspace.itemsOn(1).size)
        assertEquals(2, r.workspace.itemsOn(2).size)
    }

    @Test
    fun groupFromDifferentPagesFlowsNextToAnchor() {
        // 別々のページの同じ位置から選ぶと並びが重なるので、anchor の近くの空きへ並べる
        val start = ws(app(1, 1, 0, 0), app(2, 2, 0, 0), pages = 2)
        val movers = LayoutEngine.groupMovers(start, listOf(1, 2), anchorId = 1, pageId = 2, x = 2, y = 2)!!
        val rects = movers.associate { it.id to it.rect }
        assertEquals(CellRect(2, 2, 2, 2), rects[1])
        assertTrue(!rects[1]!!.overlaps(rects[2]!!))
        val r = LayoutEngine.placeWithPush(start, 2, movers)!!
        noOverlap(r.workspace)
    }

    @Test
    fun placeItemsMovesExistingItemsToFreeCells() {
        val start = ws(app(1, 1, 0, 0), app(2, 1, 2, 0), app(3, 2, 0, 0), pages = 2)
        val (w, ids) = LayoutEngine.placeItems(start, listOf(start.item(1)!!, start.item(2)!!), startPageId = 2, grid = grid)
        assertEquals(listOf(1L, 2L), ids)
        assertEquals(0, w.itemsOn(1).size)
        assertEquals(3, w.itemsOn(2).size)
        assertEquals(3, w.items.size)
        noOverlap(w)
    }
}

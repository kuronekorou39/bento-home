package com.rou39.bentohome.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineTest {
    private val grid = GridSpec(4, 6, 2) // 8×12 小マス
    private val app = AppRef("com.example/.Main", 0)
    private val other = AppRef("com.other/.Main", 0)

    private fun icon(id: Long, x: Int, y: Int, pageId: Long = 1, ref: AppRef = app) =
        WorkspaceItem(id, pageId, CellRect(x, y, 2, 2), ItemContent.App(ref))

    private fun ws(vararg items: WorkspaceItem, pages: Int = 1, dock: List<DockSlot> = emptyList()) = Workspace(
        pages = (1..pages).map { Page(it.toLong(), it - 1, grid) },
        items = items.toList(),
        dock = dock,
    )

    private fun Placement.ok(): Workspace = (this as Placement.Ok).workspace

    @Test
    fun halfCellOffsetsDoNotCollideWhenSeparated() {
        // 1小マスずらして横に並べる: x=1 と x=3 は重ならない
        val w = ws(icon(1, 1, 0))
        assertTrue(LayoutEngine.canPlace(w, 1, CellRect(3, 0, 2, 2)))
        assertFalse(LayoutEngine.canPlace(w, 1, CellRect(2, 0, 2, 2)))
        assertFalse(LayoutEngine.canPlace(w, 1, CellRect(0, 1, 2, 2)))
    }

    @Test
    fun centeredIconBetweenColumnsIsAllowed() {
        // 4列の中央（小マス 3〜4）に置ける
        val w = ws()
        val moved = LayoutEngine.add(w, icon(1, 3, 4)).ok()
        assertEquals(CellRect(3, 4, 2, 2), moved.item(1)!!.rect)
    }

    @Test
    fun outOfBoundsIsBlocked() {
        val w = ws()
        assertFalse(LayoutEngine.canPlace(w, 1, CellRect(7, 0, 2, 2)))
        assertFalse(LayoutEngine.canPlace(w, 1, CellRect(0, 11, 2, 2)))
        assertFalse(LayoutEngine.canPlace(w, 1, CellRect(-1, 0, 2, 2)))
        assertTrue(LayoutEngine.canPlace(w, 1, CellRect(6, 10, 2, 2)))
    }

    @Test
    fun moveOntoOccupiedCellIsBlockedAndKeepsOriginal() {
        val w = ws(icon(1, 0, 0), icon(2, 4, 0))
        assertEquals(Placement.Blocked, LayoutEngine.move(w, 1, 1, 3, 0))
        assertEquals(CellRect(0, 0, 2, 2), w.item(1)!!.rect)
    }

    @Test
    fun moveToOwnOverlappingPositionIsAllowed() {
        // 自分自身との重なりは無視する
        val w = ws(icon(1, 0, 0))
        assertEquals(CellRect(1, 1, 2, 2), LayoutEngine.move(w, 1, 1, 1, 1).ok().item(1)!!.rect)
    }

    @Test
    fun moveAcrossPages() {
        val w = ws(icon(1, 0, 0), pages = 2)
        val moved = LayoutEngine.move(w, 1, 2, 0, 0).ok()
        assertEquals(2L, moved.item(1)!!.pageId)
    }

    @Test
    fun firstFreeCellPrefersAlignedPositions() {
        val w = ws(icon(1, 0, 0))
        assertEquals(CellRect(2, 0, 2, 2), LayoutEngine.firstFreeCell(w, 1, 2, 2, step = 2))
    }

    @Test
    fun firstFreeCellFallsBackToHalfCells() {
        // 2マス幅の列が残らない配置でも、小マス単位なら空きがある
        // 8×2 小マスに、半マスずれた2個（1〜3 と 5〜7）を置くと、空きは 3〜5 だけになる
        val g = GridSpec(4, 1, 2)
        val w = Workspace(
            pages = listOf(Page(1, 0, g)),
            items = listOf(icon(1, 1, 0), icon(2, 5, 0)),
            dock = emptyList(),
        )
        assertEquals(CellRect(3, 0, 2, 2), LayoutEngine.firstFreeCell(w, 1, 2, 2, step = 2))
    }

    @Test
    fun firstFreeCellReturnsNullWhenFull() {
        val g = GridSpec(1, 1, 2)
        val w = Workspace(listOf(Page(1, 0, g)), listOf(icon(1, 0, 0)), emptyList())
        assertNull(LayoutEngine.firstFreeCell(w, 1, 2, 2, step = 2))
    }

    @Test
    fun findSpaceSearchesFollowingPagesFirst() {
        val g = GridSpec(1, 1, 2)
        val w = Workspace(
            pages = listOf(Page(1, 0, g), Page(2, 1, g), Page(3, 2, g)),
            items = listOf(icon(1, 0, 0, pageId = 2)),
            dock = emptyList(),
        )
        assertEquals(3L, LayoutEngine.findSpace(w, preferredPageId = 2, 2, 2, 2)!!.first)
    }

    @Test
    fun pruneRemovesOnlyTrailingEmptyPagesAndKeepsOne() {
        val w = ws(icon(1, 0, 0, pageId = 2), pages = 4)
        val pruned = LayoutEngine.pruneTrailingEmptyPages(w)
        assertEquals(listOf(1L, 2L), pruned.orderedPages.map { it.id })
        assertEquals(1, LayoutEngine.pruneTrailingEmptyPages(ws(pages = 3)).pages.size)
    }

    @Test
    fun dockMoveRejectsOccupiedSlotAndWidgets() {
        val widget = WorkspaceItem(2, 1, CellRect(0, 4, 4, 2), ItemContent.Widget(10, "p/.W"))
        val w = ws(icon(1, 0, 0), widget, dock = listOf(DockSlot(0, other)))
        assertEquals(Placement.Blocked, LayoutEngine.moveItemToDock(w, 1, 0, 4))
        assertEquals(Placement.Blocked, LayoutEngine.moveItemToDock(w, 2, 1, 4))
        val moved = LayoutEngine.moveItemToDock(w, 1, 1, 4).ok()
        assertNull(moved.item(1))
        assertEquals(app, moved.dockAt(1)!!.app)
    }

    @Test
    fun dockToDockSwaps() {
        val w = ws(dock = listOf(DockSlot(0, app), DockSlot(1, other)))
        val swapped = LayoutEngine.moveDockToDock(w, 0, 1, 4).ok()
        assertEquals(other, swapped.dockAt(0)!!.app)
        assertEquals(app, swapped.dockAt(1)!!.app)
    }

    @Test
    fun dockToPageRespectsCollision() {
        val w = ws(icon(1, 0, 0), dock = listOf(DockSlot(0, other)))
        assertEquals(Placement.Blocked, LayoutEngine.moveDockToPage(w, 0, 1, 1, 1, newItemId = 5))
        val moved = LayoutEngine.moveDockToPage(w, 0, 1, 2, 0, newItemId = 5).ok()
        assertTrue(moved.dock.isEmpty())
        assertEquals(CellRect(2, 0, 2, 2), moved.item(5)!!.rect)
    }

    private fun widget(id: Long, x: Int, y: Int, w: Int, h: Int, pageId: Long = 1) =
        WorkspaceItem(id, pageId, CellRect(x, y, w, h), ItemContent.Widget(id.toInt() + 100, "p/.W"))

    @Test
    fun resizeRejectsCollisionAndOutOfBounds() {
        val w = ws(widget(1, 0, 0, 4, 2), icon(2, 4, 0))
        assertEquals(Placement.Blocked, LayoutEngine.resize(w, 1, CellRect(0, 0, 6, 2)))
        assertEquals(Placement.Blocked, LayoutEngine.resize(w, 1, CellRect(0, 0, 4, 14)))
        assertEquals(CellRect(0, 0, 4, 4), LayoutEngine.resize(w, 1, CellRect(0, 0, 4, 4)).ok().item(1)!!.rect)
    }

    @Test
    fun changeGridKeepsFittingItemsInPlace() {
        val w = ws(icon(1, 0, 0), icon(2, 2, 2))
        val change = LayoutEngine.changeGrid(w, GridSpec(5, 7, 2))
        assertEquals(CellRect(0, 0, 2, 2), change.workspace.item(1)!!.rect)
        assertEquals(CellRect(2, 2, 2, 2), change.workspace.item(2)!!.rect)
        assertTrue(change.movedItemIds.isEmpty())
        assertTrue(change.workspace.pages.all { it.grid == GridSpec(5, 7, 2) })
    }

    @Test
    fun changeGridMovesOverflowWithoutDeleting() {
        // 4列→3列で、右端（小マス 6〜7）のアイコンは収まらない
        val w = ws(icon(1, 0, 0), icon(2, 6, 0))
        val change = LayoutEngine.changeGrid(w, GridSpec(3, 6, 2))
        assertEquals(setOf(2L), change.movedItemIds)
        assertEquals(2, change.workspace.items.size)
        val moved = change.workspace.item(2)!!
        assertTrue(LayoutEngine.canPlace(change.workspace, moved.pageId, moved.rect, ignoreItemId = 2))
    }

    @Test
    fun changeGridShrinksOversizedWidgetsAndAddsPageWhenFull() {
        val small = GridSpec(1, 1, 2)
        val w = ws(widget(1, 0, 0, 8, 4), icon(2, 0, 4))
        val change = LayoutEngine.changeGrid(w, small)
        assertEquals(setOf(1L), change.resizedItemIds)
        assertEquals(CellRect(0, 0, 2, 2), change.workspace.item(1)!!.rect)
        // 1ページに1個しか入らないので、アイコンは新しいページへ
        assertEquals(2, change.workspace.pages.size)
        assertEquals(2, change.workspace.items.size)
    }

    @Test
    fun shrinkDockMovesAppsToHome() {
        val w = ws(dock = listOf(DockSlot(0, app), DockSlot(3, other)))
        var next = 10L
        val shrunk = LayoutEngine.shrinkDock(w, 3, { next++ }, grid)
        assertEquals(listOf(0), shrunk.dock.map { it.slot })
        assertEquals(other, (shrunk.items.single().content as ItemContent.App).app)
    }

    @Test
    fun moveToLaterPageUsesNextPageWithSpace() {
        val w = ws(icon(1, 0, 0, pageId = 1), pages = 2)
        val moved = LayoutEngine.moveToLaterPage(w, 1, grid).ok()
        assertEquals(2L, moved.item(1)!!.pageId)
    }

    @Test
    fun moveToLaterPageAddsPageWhenLastPage() {
        val w = ws(icon(1, 0, 0, pageId = 1), icon(2, 4, 4, pageId = 1))
        val moved = LayoutEngine.moveToLaterPage(w, 2, grid).ok()
        assertEquals(2, moved.pages.size)
        assertEquals(CellRect(0, 0, 2, 2), moved.item(2)!!.rect)
        assertEquals(1L, moved.item(1)!!.pageId)
    }
}

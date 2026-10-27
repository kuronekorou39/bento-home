package com.rou39.bentohome.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** フォルダーの操作（plan.md §10）。 */
class FolderTest {
    private val grid = GridSpec(4, 6, 2)
    private val a = AppRef("com.a/.Main", 0)
    private val b = AppRef("com.b/.Main", 0)
    private val c = AppRef("com.c/.Main", 0)

    private fun icon(id: Long, x: Int, ref: AppRef) = WorkspaceItem(id, 1, CellRect(x, 0, 2, 2), ItemContent.App(ref))
    private fun folder(id: Long, vararg apps: AppRef) =
        WorkspaceItem(id, 1, CellRect(4, 4, 2, 2), ItemContent.Folder("仕事", apps.toList()))

    private fun ws(vararg items: WorkspaceItem, dock: List<DockSlot> = emptyList()) =
        Workspace(listOf(Page(1, 0, grid)), items.toList(), dock)

    private fun Placement.ok(): Workspace = (this as Placement.Ok).workspace

    @Test
    fun mergingTwoAppsMakesFolderInPlaceOfTarget() {
        val w = LayoutEngine.mergeInto(LayoutEngine.remove(ws(icon(1, 0, a), icon(2, 4, b)), 2), 1, b).ok()
        val item = w.item(1)!!
        assertEquals(CellRect(0, 0, 2, 2), item.rect)
        assertEquals(ItemContent.Folder(ItemContent.Folder.DEFAULT_TITLE, listOf(a, b)), item.content)
        assertNull(w.item(2))
    }

    @Test
    fun mergingIntoFolderAppendsAndSkipsDuplicates() {
        val w = ws(folder(1, a, b))
        val added = LayoutEngine.mergeInto(w, 1, c).ok()
        assertEquals(listOf(a, b, c), (added.item(1)!!.content as ItemContent.Folder).apps)
        val same = LayoutEngine.mergeInto(w, 1, a).ok()
        assertEquals(listOf(a, b), (same.item(1)!!.content as ItemContent.Folder).apps)
    }

    @Test
    fun cannotMergeAppOntoItselfOrWidget() {
        val widget = WorkspaceItem(2, 1, CellRect(0, 4, 4, 2), ItemContent.Widget(7, "p"))
        val w = ws(icon(1, 0, a), widget)
        assertEquals(Placement.Blocked, LayoutEngine.mergeInto(w, 1, a))
        assertEquals(Placement.Blocked, LayoutEngine.mergeInto(w, 2, a))
        assertTrue(!LayoutEngine.acceptsMerge(widget))
    }

    @Test
    fun removingDownToOneTurnsFolderBackIntoApp() {
        val w = LayoutEngine.removeFromFolder(ws(folder(1, a, b)), 1, a)
        assertEquals(ItemContent.App(b), w.item(1)!!.content)
        assertEquals(CellRect(4, 4, 2, 2), w.item(1)!!.rect)
    }

    @Test
    fun uninstallRemovesAppsEverywhereAndCollapsesFolders() {
        val w = ws(icon(1, 0, a), folder(2, a, b), folder(3, a, b, c), dock = listOf(DockSlot(0, a), DockSlot(1, c)))
        val next = LayoutEngine.removeApps(w) { it == a }
        assertNull(next.item(1))
        assertEquals(ItemContent.App(b), next.item(2)!!.content)
        assertEquals(listOf(b, c), (next.item(3)!!.content as ItemContent.Folder).apps)
        assertEquals(listOf(DockSlot(1, c)), next.dock)
    }

    @Test
    fun renameTrimsAndFallsBackToDefault() {
        val w = ws(folder(1, a, b))
        assertEquals("遊び", (LayoutEngine.renameFolder(w, 1, "  遊び ").item(1)!!.content as ItemContent.Folder).title)
        assertEquals(
            ItemContent.Folder.DEFAULT_TITLE,
            (LayoutEngine.renameFolder(w, 1, "   ").item(1)!!.content as ItemContent.Folder).title,
        )
    }

    @Test
    fun folderCannotGoToDockAndCountsItsApps() {
        val w = ws(folder(1, a, b), icon(2, 0, c), dock = listOf(DockSlot(0, a)))
        assertEquals(Placement.Blocked, LayoutEngine.moveItemToDock(w, 1, 1, 5))
        assertEquals(4, w.appCount)
    }

    @Test
    fun removingPackageAlsoRemovesItsShortcuts() {
        val chromeShortcut = WorkspaceItem(5, 1, CellRect(0, 6, 2, 2), ItemContent.Shortcut("com.a", "web-1", 0, "ページ"))
        val otherProfile = WorkspaceItem(6, 1, CellRect(2, 6, 2, 2), ItemContent.Shortcut("com.a", "web-2", 10, "仕事"))
        val w = ws(icon(1, 0, a), folder(2, a, b, c), chromeShortcut, otherProfile)
        val next = LayoutEngine.removePackage(w, "com.a", 0)
        assertNull(next.item(1))
        assertNull(next.item(5))
        assertEquals(otherProfile, next.item(6))
        assertEquals(listOf(b, c), (next.item(2)!!.content as ItemContent.Folder).apps)
    }

    @Test
    fun shortcutsCannotBeMergedOrDocked() {
        val s = WorkspaceItem(5, 1, CellRect(0, 6, 2, 2), ItemContent.Shortcut("com.a", "web-1", 0, "ページ"))
        val w = ws(s)
        assertEquals(Placement.Blocked, LayoutEngine.mergeInto(w, 5, a))
        assertEquals(Placement.Blocked, LayoutEngine.moveItemToDock(w, 5, 0, 5))
        assertEquals(0, w.appCount)
    }
}

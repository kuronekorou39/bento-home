package com.rou39.bentohome.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** ページの整理（plan.md §10）。 */
class PageOrderTest {
    private val grid = GridSpec(4, 6, 2)
    private fun ws(pages: Int, itemOn: Long? = null) = Workspace(
        (1..pages).map { Page(it.toLong(), it - 1, grid) },
        listOfNotNull(itemOn?.let { WorkspaceItem(1, it, CellRect(0, 0, 2, 2), ItemContent.App(AppRef("a/.M", 0))) }),
        emptyList(),
    )

    @Test
    fun moveKeepsOrdersContiguous() {
        val w = LayoutEngine.movePage(ws(3), pageId = 3, toIndex = 0)
        assertEquals(listOf(3L, 1L, 2L), w.orderedPages.map { it.id })
        assertEquals(listOf(0, 1, 2), w.orderedPages.map { it.order })
        assertEquals(listOf(1L, 2L, 3L), LayoutEngine.movePage(w, 3, 2).orderedPages.map { it.id })
    }

    @Test
    fun onlyEmptyPagesAreRemovedAndOneRemains() {
        val w = ws(3, itemOn = 2)
        assertSame(w, LayoutEngine.removeEmptyPage(w, 2))
        val removed = LayoutEngine.removeEmptyPage(w, 1)
        assertEquals(listOf(2L, 3L), removed.orderedPages.map { it.id })
        assertEquals(listOf(0, 1), removed.orderedPages.map { it.order })
        val single = ws(1)
        assertSame(single, LayoutEngine.removeEmptyPage(single, 1))
    }
}

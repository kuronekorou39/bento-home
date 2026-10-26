package com.rou39.bentohome.layout

/**
 * 配置の純粋ロジック。衝突するときは自動で押し出さずに拒否する（plan.md §5）。
 */
object LayoutEngine {

    fun canPlace(ws: Workspace, pageId: Long, rect: CellRect, ignoreItemId: Long? = null): Boolean {
        val page = ws.page(pageId) ?: return false
        if (!rect.fitsIn(page.grid)) return false
        return ws.itemsOn(pageId).none { it.id != ignoreItemId && it.rect.overlaps(rect) }
    }

    fun add(ws: Workspace, item: WorkspaceItem): Placement {
        if (ws.item(item.id) != null) return Placement.Blocked
        if (!canPlace(ws, item.pageId, item.rect)) return Placement.Blocked
        return Placement.Ok(ws.copy(items = ws.items + item))
    }

    fun move(ws: Workspace, itemId: Long, pageId: Long, x: Int, y: Int): Placement {
        val item = ws.item(itemId) ?: return Placement.Blocked
        val rect = item.rect.movedTo(x, y)
        if (!canPlace(ws, pageId, rect, ignoreItemId = itemId)) return Placement.Blocked
        return Placement.Ok(
            ws.copy(items = ws.items.map { if (it.id == itemId) it.copy(pageId = pageId, rect = rect) else it }),
        )
    }

    fun remove(ws: Workspace, itemId: Long): Workspace = ws.copy(items = ws.items.filterNot { it.id == itemId })

    /**
     * 空いている位置を探す。まず step 単位（通常の1マス）の位置を、無ければ小マス単位で探す。
     * 上の行から、同じ行では左から。
     */
    fun firstFreeCell(ws: Workspace, pageId: Long, width: Int, height: Int, step: Int): CellRect? {
        val page = ws.page(pageId) ?: return null
        for (s in listOf(step, 1).distinct()) {
            var y = 0
            while (y + height <= page.grid.rows) {
                var x = 0
                while (x + width <= page.grid.columns) {
                    val rect = CellRect(x, y, width, height)
                    if (canPlace(ws, pageId, rect)) return rect
                    x += s
                }
                y += s
            }
        }
        return null
    }

    /** 指定ページから後ろ、次に前のページの順で空きを探す。 */
    fun findSpace(ws: Workspace, preferredPageId: Long?, width: Int, height: Int, step: Int): Pair<Long, CellRect>? {
        val ordered = ws.orderedPages
        val start = ordered.indexOfFirst { it.id == preferredPageId }.coerceAtLeast(0)
        val search = ordered.drop(start) + ordered.take(start)
        for (page in search) {
            firstFreeCell(ws, page.id, width, height, step)?.let { return page.id to it }
        }
        return null
    }

    fun addPage(ws: Workspace, grid: GridSpec): Pair<Workspace, Page> {
        val order = (ws.pages.maxOfOrNull { it.order } ?: -1) + 1
        val page = Page(ws.nextPageId(), order, grid)
        return ws.copy(pages = ws.pages + page) to page
    }

    /** 末尾の空ページを削除する。最低1ページは残す。 */
    fun pruneTrailingEmptyPages(ws: Workspace): Workspace {
        val ordered = ws.orderedPages.toMutableList()
        while (ordered.size > 1 && ws.itemsOn(ordered.last().id).isEmpty()) {
            ordered.removeAt(ordered.lastIndex)
        }
        if (ordered.size == ws.pages.size) return ws
        val kept = ordered.map { it.id }.toSet()
        return ws.copy(pages = ws.pages.filter { it.id in kept })
    }

    // ---- ドック ----

    fun moveItemToDock(ws: Workspace, itemId: Long, slot: Int, dockSize: Int): Placement {
        val item = ws.item(itemId) ?: return Placement.Blocked
        val app = (item.content as? ItemContent.App)?.app ?: return Placement.Blocked
        if (slot !in 0 until dockSize || ws.dockAt(slot) != null) return Placement.Blocked
        return Placement.Ok(remove(ws, itemId).copy(dock = ws.dock + DockSlot(slot, app)))
    }

    fun moveDockToPage(ws: Workspace, slot: Int, pageId: Long, x: Int, y: Int, newItemId: Long): Placement {
        val dockSlot = ws.dockAt(slot) ?: return Placement.Blocked
        val span = ws.page(pageId)?.grid?.iconSpan ?: return Placement.Blocked
        val item = WorkspaceItem(newItemId, pageId, CellRect(x, y, span, span), ItemContent.App(dockSlot.app))
        val added = add(ws, item) as? Placement.Ok ?: return Placement.Blocked
        return Placement.Ok(added.workspace.copy(dock = ws.dock.filterNot { it.slot == slot }))
    }

    /** ドック内の移動。移動先が埋まっていれば入れ替える。 */
    fun moveDockToDock(ws: Workspace, from: Int, to: Int, dockSize: Int): Placement {
        val source = ws.dockAt(from) ?: return Placement.Blocked
        if (to !in 0 until dockSize) return Placement.Blocked
        if (from == to) return Placement.Ok(ws)
        val target = ws.dockAt(to)
        val rest = ws.dock.filterNot { it.slot == from || it.slot == to }
        val moved = listOfNotNull(source.copy(slot = to), target?.copy(slot = from))
        return Placement.Ok(ws.copy(dock = rest + moved))
    }

    fun addAppToDock(ws: Workspace, app: AppRef, slot: Int, dockSize: Int): Placement {
        if (slot !in 0 until dockSize || ws.dockAt(slot) != null) return Placement.Blocked
        return Placement.Ok(ws.copy(dock = ws.dock + DockSlot(slot, app)))
    }

    fun removeDock(ws: Workspace, slot: Int): Workspace = ws.copy(dock = ws.dock.filterNot { it.slot == slot })
}

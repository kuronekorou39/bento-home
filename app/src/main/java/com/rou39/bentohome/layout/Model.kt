package com.rou39.bentohome.layout

/**
 * 配置グリッド。座標はすべて小マス単位（docs/design.md §5）。
 * baseColumns × baseRows が見た目上のマス目で、各マスを subdivisions 分割した小マスに配置する。
 */
data class GridSpec(val baseColumns: Int, val baseRows: Int, val subdivisions: Int = 2) {
    init {
        require(baseColumns > 0 && baseRows > 0 && subdivisions > 0) { "invalid grid: $this" }
    }

    val columns: Int get() = baseColumns * subdivisions
    val rows: Int get() = baseRows * subdivisions

    /** アイコン1個が占める小マス数（1辺）。 */
    val iconSpan: Int get() = subdivisions
}

data class CellRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun overlaps(other: CellRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom

    fun fitsIn(grid: GridSpec): Boolean =
        x >= 0 && y >= 0 && width > 0 && height > 0 && right <= grid.columns && bottom <= grid.rows

    fun movedTo(newX: Int, newY: Int): CellRect = copy(x = newX, y = newY)
}

/** Android の型に依存しないアプリ参照。component は ComponentName.flattenToString()。 */
data class AppRef(val component: String, val profileSerial: Long) {
    val packageName: String get() = component.substringBefore('/')
}

sealed interface ItemContent {
    data class App(val app: AppRef) : ItemContent
    data class Widget(val widgetId: Int, val provider: String) : ItemContent
}

data class WorkspaceItem(val id: Long, val pageId: Long, val rect: CellRect, val content: ItemContent)

data class Page(val id: Long, val order: Int, val grid: GridSpec)

data class DockSlot(val slot: Int, val app: AppRef)

data class Workspace(
    val pages: List<Page>,
    val items: List<WorkspaceItem>,
    val dock: List<DockSlot>,
) {
    val orderedPages: List<Page> get() = pages.sortedBy { it.order }

    fun page(id: Long): Page? = pages.firstOrNull { it.id == id }

    fun pageAt(index: Int): Page? = orderedPages.getOrNull(index)

    fun itemsOn(pageId: Long): List<WorkspaceItem> = items.filter { it.pageId == pageId }

    fun item(id: Long): WorkspaceItem? = items.firstOrNull { it.id == id }

    fun dockAt(slot: Int): DockSlot? = dock.firstOrNull { it.slot == slot }

    fun nextItemId(): Long = (items.maxOfOrNull { it.id } ?: 0L) + 1

    fun nextPageId(): Long = (pages.maxOfOrNull { it.id } ?: 0L) + 1

    val widgetIds: Set<Int>
        get() = items.mapNotNullTo(HashSet()) { (it.content as? ItemContent.Widget)?.widgetId }

    companion object {
        val EMPTY = Workspace(emptyList(), emptyList(), emptyList())
    }
}

sealed interface Placement {
    data class Ok(val workspace: Workspace) : Placement

    /** 範囲外・衝突などで置けない。呼び出し側は元の状態を表示し続ける。 */
    data object Blocked : Placement
}

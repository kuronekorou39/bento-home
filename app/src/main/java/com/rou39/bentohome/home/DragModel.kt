package com.rou39.bentohome.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.ItemContent

/** ドラッグ中の項目がどこから来たか。 */
sealed interface DragSource {
    data class Item(val itemId: Long) : DragSource
    data class Dock(val slot: Int) : DragSource

    /** アプリ一覧からホームへ新しく置く。 */
    data class NewApp(val app: AppRef) : DragSource

    /** 開いたフォルダーの中から運び出す。 */
    data class FolderApp(val folderItemId: Long, val app: AppRef) : DragSource

    /** ホームで選んだ項目をまとめて運ぶ。anchorId はつまんだ項目で、置き先はこの項目の位置で決める。 */
    data class Items(val ids: List<Long>, val anchorId: Long) : DragSource
}

sealed interface DropTarget {
    /** pageIndex がページ数と同じなら、末尾に新しいページを作って置く。 */
    data class Cell(val pageIndex: Int, val x: Int, val y: Int) : DropTarget
    data class Dock(val slot: Int) : DropTarget

    /** ホームのアプリかフォルダーに重ねて、フォルダーにまとめる（plan.md §10）。 */
    data class Merge(val pageIndex: Int, val itemId: Long) : DropTarget

    /**
     * 運んでいる間に下に並ぶページの縮小表示（[PageStrip]）。離すとそのページの空きへ置く。
     * pageIndex がページ数と同じなら、新しいページ。
     */
    data class Page(val pageIndex: Int) : DropTarget
    data object None : DropTarget
}

sealed interface DragVisual {
    data class App(val app: AppRef) : DragVisual
    data class Widget(val label: String) : DragVisual
    data class Folder(val folder: ItemContent.Folder) : DragVisual
    data class Shortcut(val shortcut: ItemContent.Shortcut) : DragVisual
}

/** 画面全体（ルート座標）で共有するドラッグ状態。 */
@Stable
class DragState {
    var source by mutableStateOf<DragSource?>(null)
        private set
    var visual by mutableStateOf<DragVisual?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    var fromDrawer by mutableStateOf(false)
        private set

    /** 掴んだ位置の、項目の左上からのずれ。 */
    var grabOffset = Offset.Zero
        private set
    var itemSize by mutableStateOf(IntSize.Zero)
        private set

    /** 置いたときの占有小マス数。 */
    var spanX = 0
        private set
    var spanY = 0
        private set

    val active: Boolean get() = source != null

    fun start(
        source: DragSource,
        visual: DragVisual,
        pointer: Offset,
        grabOffset: Offset,
        itemSize: IntSize,
        spanX: Int,
        spanY: Int,
        fromDrawer: Boolean = false,
    ) {
        this.source = source
        this.visual = visual
        this.pointer = pointer
        this.grabOffset = grabOffset
        this.itemSize = itemSize
        this.spanX = spanX
        this.spanY = spanY
        this.fromDrawer = fromDrawer
    }

    fun clear() {
        source = null
        visual = null
        fromDrawer = false
    }

    /** 掴んだ位置の比率。ドロップ先の小マスは、この比率で占有範囲を指の下に合わせて決める。 */
    val grabFraction: Offset
        get() = if (itemSize.width == 0 || itemSize.height == 0) {
            Offset(0.5f, 0.5f)
        } else {
            Offset(grabOffset.x / itemSize.width, grabOffset.y / itemSize.height)
        }
}

/** ドロップ判定に使う画面上の領域（ルート座標）。 */
@Stable
class HomeGeometry {
    var gridArea by mutableStateOf(Rect.Zero)
    var dockArea by mutableStateOf(Rect.Zero)

    /** ページの縮小表示の帯と、各ページの縮小表示（キーはページの番号）。帯を出していないときは空。 */
    var stripArea by mutableStateOf(Rect.Zero)
    val stripTiles = mutableStateMapOf<Int, Rect>()
}

/** ホーム・ドック・一覧のアイコンに共通の操作。 */
class ItemGestureCallbacks(
    val onTap: (android.graphics.Rect?) -> Unit,
    val onLongPress: () -> Unit,
    val canDrag: Boolean,
    val onDragStart: (pointer: Offset, grabOffset: Offset, size: IntSize) -> Unit,
    val onDragMove: (pointer: Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragCancel: () -> Unit,
    /** 長押しが成立した（つまんだ）。指を離すまで項目をハイライトする。 */
    val onHoldStart: () -> Unit = {},
    /** 指を離した、または操作が打ち切られた。 */
    val onHoldEnd: () -> Unit = {},
)

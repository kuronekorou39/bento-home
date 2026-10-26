package com.rou39.bentohome.home

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.layout.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** ホーム画面の一時的な UI 状態と、操作の組み立て。配置そのものは [HomeViewModel] が持つ。 */
@Stable
class HomeController(
    val vm: HomeViewModel,
    val drag: DragState,
    val geometry: HomeGeometry,
    val pagerState: PagerState,
    private val scope: CoroutineScope,
) {
    var drawerOpen by mutableStateOf(false)
    var query by mutableStateOf("")

    /** 背景の長押しメニューの表示位置（ルート座標）。null なら非表示。 */
    var backgroundMenuAt by mutableStateOf<Offset?>(null)
    var locked by mutableStateOf(false)
    var dockVisible by mutableStateOf(true)
    var style by mutableStateOf(IconStyle(56.dp, IconShape.SYSTEM, true, 12))

    /** リサイズ中のウィジェット項目。null ならリサイズしていない。 */
    var resizingItemId by mutableStateOf<Long?>(null)

    /** 外観の個別設定ダイアログの対象。 */
    var appearanceTarget by mutableStateOf<AppEntry?>(null)

    /** ウィジェットの設定画面を開く。Activity の結果受け取りが必要なので HomeActivity が設定する。 */
    var onReconfigureWidget: (Int) -> Unit = {}

    /** ホーム操作のたびに増える。各項目のメニューを閉じるのに使う。 */
    var resetKey by mutableStateOf(0)
        private set

    fun resetForHome() {
        drawerOpen = false
        query = ""
        backgroundMenuAt = null
        resizingItemId = null
        appearanceTarget = null
        drag.clear()
        resetKey++
        scope.launch { pagerState.scrollToPage(0) }
    }

    fun openDrawer() {
        backgroundMenuAt = null
        drawerOpen = true
    }

    fun itemCallbacks(
        source: DragSource,
        visual: DragVisual,
        spanX: Int,
        spanY: Int,
        entry: AppEntry?,
        canDrag: Boolean,
        onLongPress: () -> Unit,
        onDragStarted: () -> Unit,
        fromDrawer: Boolean = false,
    ) = ItemGestureCallbacks(
        onTap = { bounds -> entry?.let { vm.catalog.launch(it, bounds) } },
        onLongPress = onLongPress,
        canDrag = canDrag && !locked,
        onDragStart = { pointer, grab, size ->
            onDragStarted()
            drag.start(source, visual, pointer, grab, size, spanX, spanY, fromDrawer)
        },
        onDragMove = { drag.pointer = it },
        onDragEnd = ::endDrag,
        onDragCancel = { drag.clear() },
    )

    fun endDrag() {
        val source = drag.source ?: return
        vm.drop(source, computeTarget(vm.workspace.value))
        if (drag.fromDrawer) drawerOpen = false
        drag.clear()
    }

    /** 指の位置から置き先を決める。掴んだ比率で占有範囲を指の下に合わせ、最も近い小マスへ吸着させる。 */
    fun computeTarget(ws: Workspace?): DropTarget {
        if (ws == null || !drag.active) return DropTarget.None
        val p = drag.pointer
        val dock = geometry.dockArea
        if (dockVisible && dock.width > 0f && dock.contains(p)) {
            if (drag.visual is DragVisual.Widget) return DropTarget.None
            val slotWidth = dock.width / vm.dockSize
            return DropTarget.Dock(((p.x - dock.left) / slotWidth).toInt().coerceIn(0, vm.dockSize - 1))
        }
        val area = geometry.gridArea
        if (area.width <= 0f || !area.contains(p)) return DropTarget.None
        val pageIndex = pagerState.currentPage
        val grid = ws.pageAt(pageIndex)?.grid ?: vm.currentGrid()
        val cellW = area.width / grid.columns
        val cellH = area.height / grid.rows
        val anchorX = p.x - area.left - drag.grabFraction.x * cellW * drag.spanX
        val anchorY = p.y - area.top - drag.grabFraction.y * cellH * drag.spanY
        val x = (anchorX / cellW).roundToInt().coerceIn(0, (grid.columns - drag.spanX).coerceAtLeast(0))
        val y = (anchorY / cellH).roundToInt().coerceIn(0, (grid.rows - drag.spanY).coerceAtLeast(0))
        return DropTarget.Cell(pageIndex, x, y)
    }

    /** ウィジェット上の横ドラッグでページを動かす。 */
    val pagerDrag = PagerDragHandler(
        onDrag = { dx -> pagerState.dispatchRawDelta(-dx) },
        onDragEnd = { velocity ->
            scope.launch {
                val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val target = when {
                    velocity < -FLING_VELOCITY -> ceil(position)
                    velocity > FLING_VELOCITY -> floor(position)
                    else -> position.roundToInt().toFloat()
                }.toInt().coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                pagerState.animateScrollToPage(target)
            }
        },
    )

    private companion object {
        /** これより速い横ドラッグは、半分に届かなくても次のページへ送る（px/秒）。 */
        const val FLING_VELOCITY = 800f
    }
}

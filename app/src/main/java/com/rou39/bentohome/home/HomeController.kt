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
import com.rou39.bentohome.layout.LayoutEngine
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.monitor.SelfCounters
import com.rou39.bentohome.monitor.WidgetMeasurement
import com.rou39.bentohome.monitor.WidgetMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** 開いているフォルダーの項目 ID。null なら閉じている。 */
    var openFolderId by mutableStateOf<Long?>(null)

    /** 開いたフォルダーで名前を編集中か。 */
    var renamingFolder by mutableStateOf(false)

    /** 外観の個別設定ダイアログの対象。 */
    var appearanceTarget by mutableStateOf<AppEntry?>(null)

    /** ウィジェットの設定画面を開く。Activity の結果受け取りが必要なので HomeActivity が設定する。 */
    var onReconfigureWidget: (Int) -> Unit = {}

    /** 「再追加が必要」なウィジェットを追加し直す。HomeActivity が設定する。 */
    var onReAddWidget: (itemId: Long, provider: String) -> Unit = { _, _ -> }

    /** 負荷の計測で一時的に隠しているウィジェット。 */
    var hiddenWidgetId by mutableStateOf<Int?>(null)
        private set

    /** 計測中の表示。null なら計測していない。 */
    var measuring by mutableStateOf<String?>(null)
        private set
    /** 計測が終わる時刻（elapsedRealtime）。 */
    var measureEndsAt by mutableStateOf(0L)
        private set
    var measureResult by mutableStateOf<MeasureResult?>(null)
    private var measureJob: Job? = null

    /** ホーム操作のたびに増える。各項目のメニューを閉じるのに使う。 */
    var resetKey by mutableStateOf(0)
        private set

    fun resetForHome() {
        drawerOpen = false
        query = ""
        backgroundMenuAt = null
        resizingItemId = null
        appearanceTarget = null
        closeFolder()
        drag.clear()
        measureJob?.cancel()
        measureResult = null
        resetKey++
        scope.launch { pagerState.scrollToPage(0) }
    }

    /**
     * ウィジェットを表示した状態と隠した状態で、Bento の CPU 時間と描画フレーム数を比べる（docs/monitor.md）。
     * ウィジェットの描画は Bento のプロセスで行うので、差がそのウィジェットの負荷になる。
     */
    fun measureWidget(itemId: Long, widgetId: Int, label: String) {
        if (measureJob?.isActive == true) return
        val self = vm.selfMetrics
        measureJob = scope.launch {
            try {
                measureEndsAt = android.os.SystemClock.elapsedRealtime() +
                    MEASURE_CYCLES * 2 * (MEASURE_SETTLE_MS + MEASURE_PHASE_MS)
                measuring = "「$label」の負荷を測っています"
                val starts = self.homeStarts
                val animations = WidgetMetrics.detectAnimations(vm.widgetHost.viewFor(widgetId))
                // 他のウィジェットの揺らぎを均すため、表示と非表示を短く交互に繰り返して合計する
                var shown = SelfCounters(0, 0, 0)
                var hidden = SelfCounters(0, 0, 0)
                repeat(MEASURE_CYCLES) {
                    for (hide in listOf(false, true)) {
                        hiddenWidgetId = if (hide) widgetId else null
                        delay(MEASURE_SETTLE_MS)
                        val start = self.now()
                        delay(MEASURE_PHASE_MS)
                        val d = self.now() - start
                        if (hide) hidden += d else shown += d
                    }
                }
                // 途中でホームを離れたら結果を捨てる
                if (!self.homeVisible || self.homeStarts != starts) return@launch
                val m = WidgetMeasurement(System.currentTimeMillis(), shown, hidden, animations)
                vm.widgetMetrics.record(widgetId, m)
                measureResult = MeasureResult(itemId, label, m)
            } finally {
                hiddenWidgetId = null
                measuring = null
            }
        }
    }

    fun openDrawer() {
        backgroundMenuAt = null
        drawerOpen = true
    }

    fun openFolder(itemId: Long, rename: Boolean = false) {
        backgroundMenuAt = null
        openFolderId = itemId
        renamingFolder = rename
    }

    fun closeFolder() {
        openFolderId = null
        renamingFolder = false
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
        onTap: ((android.graphics.Rect?) -> Unit)? = null,
    ) = ItemGestureCallbacks(
        onTap = onTap ?: { bounds -> if (entry != null) vm.launch(entry, bounds) },
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
        if (source is DragSource.FolderApp) closeFolder()
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
        mergeTarget(ws, pageIndex, (p.x - area.left) / cellW, (p.y - area.top) / cellH)?.let { return it }
        val anchorX = p.x - area.left - drag.grabFraction.x * cellW * drag.spanX
        val anchorY = p.y - area.top - drag.grabFraction.y * cellH * drag.spanY
        val x = (anchorX / cellW).roundToInt().coerceIn(0, (grid.columns - drag.spanX).coerceAtLeast(0))
        val y = (anchorY / cellH).roundToInt().coerceIn(0, (grid.rows - drag.spanY).coerceAtLeast(0))
        return DropTarget.Cell(pageIndex, x, y)
    }

    /**
     * 指がアプリかフォルダーの中ほどにあれば、そこへまとめる。端の近くなら普通の移動として扱い、
     * 隣に並べて置く操作と区別する。運べるのはアプリだけ（フォルダーやウィジェットはまとめない）。
     * @param cx 指の位置（小マス単位）
     */
    private fun mergeTarget(ws: Workspace, pageIndex: Int, cx: Float, cy: Float): DropTarget.Merge? {
        if (drag.visual !is DragVisual.App) return null
        val page = ws.pageAt(pageIndex) ?: return null
        val draggedId = (drag.source as? DragSource.Item)?.itemId
        val hit = ws.itemsOn(page.id).firstOrNull { item ->
            val r = item.rect
            val mx = r.width * MERGE_EDGE
            val my = r.height * MERGE_EDGE
            item.id != draggedId && LayoutEngine.acceptsMerge(item) &&
                cx in (r.x + mx)..(r.right - mx) && cy in (r.y + my)..(r.bottom - my)
        } ?: return null
        val folderSource = drag.source as? DragSource.FolderApp
        if (folderSource?.folderItemId == hit.id) return null
        return DropTarget.Merge(pageIndex, hit.id)
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
        /** 表示を切り替えてから測り始めるまでの待ち。 */
        const val MEASURE_SETTLE_MS = 500L
        const val MEASURE_PHASE_MS = 2_000L
        const val MEASURE_CYCLES = 3

        /** 重ねてまとめる判定から外す、項目の端の幅（項目の大きさに対する比率）。 */
        const val MERGE_EDGE = 0.2f

        /** これより速い横ドラッグは、半分に届かなくても次のページへ送る（px/秒）。 */
        const val FLING_VELOCITY = 800f
    }
}

data class MeasureResult(val itemId: Long, val label: String, val measurement: WidgetMeasurement)

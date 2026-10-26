package com.rou39.bentohome.home

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rou39.bentohome.catalog.PackageEvent
import com.rou39.bentohome.container
import com.rou39.bentohome.data.LauncherSettings
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.CellRect
import com.rou39.bentohome.layout.DockSlot
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.LayoutEngine
import com.rou39.bentohome.layout.Page
import com.rou39.bentohome.layout.Placement
import com.rou39.bentohome.layout.WidgetSizing
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.layout.WorkspaceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 取り消し可能な操作の通知。message があればスナックバーを出す。 */
data class UndoState(val message: String?, val token: Long)

/**
 * ホームの配置の正本を持つ。変更は LayoutEngine で計算し、成功したときだけ反映・保存する（docs/design.md §4）。
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val catalog = c.appCatalog
    val widgetHost = c.widgetHost
    val iconCache = c.iconCache
    private val repo = c.workspaceRepository
    private val settingsRepo = c.settingsRepository

    val settings: StateFlow<LauncherSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    private val _workspace = MutableStateFlow<Workspace?>(null)
    val workspace: StateFlow<Workspace?> = _workspace.asStateFlow()

    private var undoSnapshot: Workspace? = null
    private var undoToken = 0L
    private val _undo = MutableStateFlow<UndoState?>(null)
    val undo: StateFlow<UndoState?> = _undo.asStateFlow()

    /** 取り消しの猶予中なので、まだ解放していないウィジェット ID（docs/design.md §4）。 */
    private val pendingWidgetDeletes = mutableSetOf<Int>()

    val defaultGrid = GridSpec(
        LauncherSettings.DEFAULT_BASE_COLUMNS,
        LauncherSettings.DEFAULT_BASE_ROWS,
        LauncherSettings.DEFAULT_SUBDIVISIONS,
    )
    val dockSize = LauncherSettings.DOCK_SIZE

    /** UI が報告する通常の1マスの寸法（px）。ウィジェットの大きさの算出に使う。 */
    var baseCellSizePx: Pair<Float, Float>? = null

    /** UI が報告する表示中のページ。新しい項目はここから空きを探す。 */
    var currentPageIndex = 0

    init {
        viewModelScope.launch {
            var ws = repo.load()
            if (ws.pages.isEmpty()) ws = LayoutEngine.addPage(ws, defaultGrid).first
            ws = migrateSpikeWidgets(ws)
            ws = cleanupApps(ws)
            _workspace.value = ws
            repo.save(ws)
            releaseOrphanWidgetIds(ws)
        }
        viewModelScope.launch {
            catalog.events.filterIsInstance<PackageEvent.Removed>().collect(::onPackageRemoved)
        }
    }

    override fun onCleared() {
        finalizePendingWidgetDeletes()
    }

    // ---- ドラッグ＆ドロップ ----

    fun canDrop(source: DragSource, target: DropTarget): Boolean {
        val ws = _workspace.value ?: return false
        return computeDrop(ws, source, target) != null
    }

    /** @return 置けたら true。置けなければ配置は変わらない。 */
    fun drop(source: DragSource, target: DropTarget): Boolean {
        val ws = _workspace.value ?: return false
        val next = computeDrop(ws, source, target)
        if (next == null) {
            Log.d(TAG, "drop rejected: $source -> $target")
            return false
        }
        if (next != ws) commit(next)
        return true
    }

    private fun computeDrop(ws: Workspace, source: DragSource, target: DropTarget): Workspace? {
        val result = when (target) {
            DropTarget.None -> return null
            is DropTarget.Dock -> when (source) {
                is DragSource.Item -> LayoutEngine.moveItemToDock(ws, source.itemId, target.slot, dockSize)
                is DragSource.Dock -> LayoutEngine.moveDockToDock(ws, source.slot, target.slot, dockSize)
                is DragSource.NewApp -> LayoutEngine.addAppToDock(ws, source.app, target.slot, dockSize)
            }
            is DropTarget.Cell -> {
                val (base, page) = pageForIndex(ws, target.pageIndex) ?: return null
                when (source) {
                    is DragSource.Item -> LayoutEngine.move(base, source.itemId, page.id, target.x, target.y)
                    is DragSource.Dock ->
                        LayoutEngine.moveDockToPage(base, source.slot, page.id, target.x, target.y, base.nextItemId())
                    is DragSource.NewApp -> {
                        val span = page.grid.iconSpan
                        val item = WorkspaceItem(
                            base.nextItemId(), page.id, CellRect(target.x, target.y, span, span),
                            ItemContent.App(source.app),
                        )
                        LayoutEngine.add(base, item)
                    }
                }
            }
        }
        return (result as? Placement.Ok)?.workspace
    }

    /** 既存ページ、またはページ数と同じ番号なら新しいページ。 */
    private fun pageForIndex(ws: Workspace, index: Int): Pair<Workspace, Page>? {
        ws.pageAt(index)?.let { return ws to it }
        return if (index == ws.pages.size) LayoutEngine.addPage(ws, defaultGrid) else null
    }

    // ---- 追加・削除 ----

    fun addAppToHome(app: AppRef) {
        val ws = _workspace.value ?: return
        val span = defaultGrid.iconSpan
        val next = placeSomewhere(ws, span, span) { pageId, rect ->
            WorkspaceItem(ws.nextItemId(), pageId, rect, ItemContent.App(app))
        }
        commit(next, message = "ホームに追加しました")
    }

    fun placeNewWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        val ws = _workspace.value ?: return
        val (spanX, spanY) = widgetSpans(info, ws.pageAt(currentPageIndex)?.grid ?: defaultGrid)
        val next = placeSomewhere(ws, spanX, spanY) { pageId, rect ->
            WorkspaceItem(ws.nextItemId(), pageId, rect, ItemContent.Widget(widgetId, info.provider.flattenToString()))
        }
        commit(next, message = "ウィジェットを追加しました")
    }

    fun removeItem(itemId: Long) {
        val ws = _workspace.value ?: return
        val item = ws.item(itemId) ?: return
        val widgetId = (item.content as? ItemContent.Widget)?.widgetId
        commit(LayoutEngine.remove(ws, itemId), message = "ホームから削除しました", removedWidgetIds = setOfNotNull(widgetId))
    }

    fun removeDock(slot: Int) {
        val ws = _workspace.value ?: return
        commit(LayoutEngine.removeDock(ws, slot), message = "ドックから外しました")
    }

    fun setLayoutLocked(locked: Boolean) {
        viewModelScope.launch { settingsRepo.setLayoutLocked(locked) }
    }

    /** 表示中のページから空きを探し、どこにも無ければ末尾にページを足して置く。 */
    private fun placeSomewhere(
        ws: Workspace,
        spanX: Int,
        spanY: Int,
        make: (pageId: Long, rect: CellRect) -> WorkspaceItem,
    ): Workspace {
        val preferred = ws.pageAt(currentPageIndex)?.id
        val found = LayoutEngine.findSpace(ws, preferred, spanX, spanY, defaultGrid.subdivisions)
        val (base, pageId, rect) = if (found != null) {
            Triple(ws, found.first, found.second)
        } else {
            val (withPage, page) = LayoutEngine.addPage(ws, defaultGrid)
            Triple(withPage, page.id, CellRect(0, 0, spanX, spanY))
        }
        val item = make(pageId, rect).copy(id = base.nextItemId())
        return (LayoutEngine.add(base, item) as? Placement.Ok)?.workspace ?: ws
    }

    private fun widgetSpans(info: AppWidgetProviderInfo, grid: GridSpec): Pair<Int, Int> {
        val (cellW, cellH) = baseCellSizePx ?: estimatedBaseCellSize(grid)
        return WidgetSizing.spans(
            info.targetCellWidth, info.targetCellHeight, info.minWidth, info.minHeight, cellW, cellH, grid,
        )
    }

    private fun estimatedBaseCellSize(grid: GridSpec): Pair<Float, Float> {
        val dm = getApplication<Application>().resources.displayMetrics
        // 画面が描かれる前の見積もり。上下のシステム領域とドックを除いたおおよその高さで割る。
        return dm.widthPixels / grid.baseColumns.toFloat() to dm.heightPixels * 0.65f / grid.baseRows
    }

    // ---- 取り消し ----

    private fun commit(next: Workspace, message: String? = null, removedWidgetIds: Set<Int> = emptySet()) {
        val prev = _workspace.value ?: return
        finalizePendingWidgetDeletes()
        val pruned = LayoutEngine.pruneTrailingEmptyPages(next)
        _workspace.value = pruned
        repo.save(pruned)
        undoSnapshot = prev
        pendingWidgetDeletes += removedWidgetIds
        _undo.value = UndoState(message, ++undoToken)
    }

    val canUndo: Boolean get() = undoSnapshot != null

    fun undo() {
        val snapshot = undoSnapshot ?: return
        val current = _workspace.value ?: return
        undoSnapshot = null
        // 削除を取り消したウィジェットは ID を残す。追加を取り消したウィジェットは ID を解放する。
        pendingWidgetDeletes.removeAll(snapshot.widgetIds)
        (current.widgetIds - snapshot.widgetIds).forEach(widgetHost::delete)
        finalizePendingWidgetDeletes()
        _workspace.value = snapshot
        repo.save(snapshot)
        _undo.value = null
    }

    private fun finalizePendingWidgetDeletes() {
        pendingWidgetDeletes.forEach(widgetHost::delete)
        pendingWidgetDeletes.clear()
    }

    // ---- 起動時の整理 ----

    /** 段階1の検証で追加したウィジェットを最初のページへ移す。 */
    private fun migrateSpikeWidgets(start: Workspace): Workspace {
        val ids = c.spikeWidgetStore.load()
        if (ids.isEmpty()) return start
        var ws = start
        for (id in ids) {
            if (id in ws.widgetIds) continue
            val info = widgetHost.info(id) ?: continue
            val (spanX, spanY) = widgetSpans(info, defaultGrid)
            ws = placeSomewhere(ws, spanX, spanY) { pageId, rect ->
                WorkspaceItem(0, pageId, rect, ItemContent.Widget(id, info.provider.flattenToString()))
            }
        }
        c.spikeWidgetStore.clear()
        Log.d(TAG, "migrated spike widgets: $ids")
        return ws
    }

    /**
     * 停止中にアンインストールされたアプリを外す。起動 Activity 名だけ変わったアプリは付け替える。
     * 一時的に使えないだけのアプリは残し、見つからない表示にする。
     */
    private fun cleanupApps(ws: Workspace): Workspace {
        val known = catalog.byRef.value
        fun resolve(ref: AppRef): AppRef? = when {
            ref in known -> ref
            ref.profileSerial == catalog.mainProfileSerial && !catalog.isInstalled(ref.packageName) -> null
            else -> catalog.firstEntryOf(ref.packageName, ref.profileSerial)?.ref ?: ref
        }
        val items = ws.items.mapNotNull { item ->
            val app = (item.content as? ItemContent.App)?.app ?: return@mapNotNull item
            resolve(app)?.let { item.copy(content = ItemContent.App(it)) }
        }
        val dock = ws.dock.mapNotNull { slot -> resolve(slot.app)?.let { DockSlot(slot.slot, it) } }
        val cleaned = ws.copy(items = items, dock = dock)
        if (cleaned != ws) Log.d(TAG, "cleaned up missing apps")
        return cleaned
    }

    /** ホスト側にあって配置に無い ID を解放する。取り消し猶予中にプロセスが終わった場合の後始末。 */
    private fun releaseOrphanWidgetIds(ws: Workspace) {
        val orphans = widgetHost.hostIds() - ws.widgetIds
        if (orphans.isNotEmpty()) Log.d(TAG, "release orphan widget ids: $orphans")
        orphans.forEach(widgetHost::delete)
    }

    private fun onPackageRemoved(e: PackageEvent.Removed) {
        val ws = _workspace.value ?: return
        fun matches(ref: AppRef) = ref.packageName == e.packageName && ref.profileSerial == e.profileSerial
        val next = ws.copy(
            items = ws.items.filterNot { (it.content as? ItemContent.App)?.app?.let(::matches) == true },
            dock = ws.dock.filterNot { matches(it.app) },
        )
        if (next == ws) return
        // アンインストールは取り消せないので、取り消し用の状態も捨てる
        finalizePendingWidgetDeletes()
        undoSnapshot = null
        _undo.value = null
        val pruned = LayoutEngine.pruneTrailingEmptyPages(next)
        _workspace.value = pruned
        repo.save(pruned)
        Log.d(TAG, "removed items of uninstalled ${e.packageName}")
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}

package com.rou39.bentohome.home

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.catalog.PackageEvent
import com.rou39.bentohome.data.DrawerSort
import com.rou39.bentohome.usage.AppUsage
import com.rou39.bentohome.container
import com.rou39.bentohome.data.AppAppearance
import com.rou39.bentohome.data.LauncherSettings
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.CellRect
import com.rou39.bentohome.layout.DockSlot
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.LayoutEngine
import com.rou39.bentohome.layout.Page
import com.rou39.bentohome.layout.Placement
import com.rou39.bentohome.layout.ResizeLimits
import com.rou39.bentohome.layout.WidgetSizing
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.layout.WorkspaceItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 取り消し可能な操作の通知。message があればスナックバーを出す。 */
data class UndoState(val message: String?, val token: Long)

fun LauncherSettings.grid() = GridSpec(baseColumns, baseRows, LauncherSettings.SUBDIVISIONS)

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
    private val appearanceRepo = c.appearanceRepository
    private val usageRepo = c.usageRepository
    val selfMetrics = c.selfMetrics
    val widgetMetrics = c.widgetMetrics

    val settings: StateFlow<LauncherSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    val appearances: StateFlow<Map<AppRef, AppAppearance>> =
        appearanceRepo.appearances.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 表示名を反映したアプリ（plan.md §10）。名前順。非表示のアプリも含む。 */
    val displayApps: StateFlow<List<AppEntry>> = combine(catalog.apps, appearances) { list, ap ->
        list.map { e -> ap[e.ref]?.label?.let { e.copy(label = it) } ?: e }.sortedBy { it.label.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val displayByRef: StateFlow<Map<AppRef, AppEntry>> = displayApps
        .map { list -> list.associateBy { it.ref } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val usage: StateFlow<Map<AppRef, AppUsage>> =
        usageRepo.usage.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** グレー表示の判定に使う現在時刻。画面に戻るたびに更新する。 */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _workspace = MutableStateFlow<Workspace?>(null)
    val workspace: StateFlow<Workspace?> = _workspace.asStateFlow()

    private var undoSnapshot: Workspace? = null
    private var undoToken = 0L
    private val _undo = MutableStateFlow<UndoState?>(null)
    val undo: StateFlow<UndoState?> = _undo.asStateFlow()

    /** 操作の結果を見せるために表示したいページ。 */
    private val _focusPage = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusPage: SharedFlow<Int> = _focusPage.asSharedFlow()

    /** ホームから外したショートカットの提供元。取り消しの猶予が過ぎたら OS 側の固定も外す。 */
    private val pendingUnpins = mutableSetOf<Pair<String, Long>>()

    /** 取り消しの猶予中なので、まだ解放していないウィジェット ID（docs/design.md §4）。 */
    private val pendingWidgetDeletes = mutableSetOf<Int>()

    val dockSize: Int get() = settings.value.dockSlots

    /** UI が報告する通常の1マスの寸法（px）。ウィジェットの大きさの算出に使う。 */
    var baseCellSizePx: Pair<Float, Float>? = null

    /** UI が報告する表示中のページ。新しい項目はここから空きを探す。 */
    var currentPageIndex = 0

    init {
        viewModelScope.launch {
            val initialSettings = settingsRepo.settings.first()
            var ws = repo.load()
            if (ws.pages.isEmpty()) ws = LayoutEngine.addPage(ws, initialSettings.grid()).first
            ws = migrateSpikeWidgets(ws)
            ws = cleanupApps(ws)
            _workspace.value = ws
            repo.save(ws)
            releaseOrphanWidgetIds(ws)
            releaseOrphanPins(ws)
            // 配置を読み込んでから受け取る。読み込み前に届いたものはチャンネルに溜まっている
            launch { c.pinRequests.receiveAsFlow().collect(::placePinned) }
            // 設定画面でマス目やドックの枠を変えたら、ここで配置に反映する
            settingsRepo.settings.collect(::applyLayoutSettings)
        }
        viewModelScope.launch {
            catalog.events.filterIsInstance<PackageEvent.Removed>().collect(::onPackageRemoved)
        }
        viewModelScope.launch { c.restoreRequests.collect(::applyRestore) }
    }

    override fun onCleared() {
        finalizePendingWidgetDeletes()
        finalizeUnpins()
    }

    fun refreshClock() {
        _now.value = System.currentTimeMillis()
    }

    fun today(): Long = usageRepo.today()

    // ---- 起動 ----

    /** 起動に成功したときだけ使用回数に数える（plan.md §4）。 */
    fun launch(entry: AppEntry, bounds: android.graphics.Rect?) {
        if (catalog.launch(entry, bounds)) usageRepo.recordLaunch(entry.ref)
    }

    fun launchShortcut(entry: AppEntry, shortcut: android.content.pm.ShortcutInfo) {
        if (catalog.startShortcut(shortcut, null)) usageRepo.recordLaunch(entry.ref)
    }

    /** ホームに置いたショートカットを開く。提供元が消していたら知らせる。 */
    fun launchPinned(s: ItemContent.Shortcut, bounds: android.graphics.Rect?) {
        val info = catalog.pinnedShortcut(s.packageName, s.shortcutId, s.profileSerial)
        val message = when {
            info == null -> "このショートカットは見つかりません"
            !info.isEnabled -> info.disabledMessage?.toString() ?: "このショートカットは使えません"
            catalog.startShortcut(info, bounds) -> return
            else -> "開けませんでした"
        }
        android.widget.Toast.makeText(getApplication(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun setDrawerSort(sort: DrawerSort) {
        viewModelScope.launch { settingsRepo.update { it.copy(drawerSort = sort) } }
    }

    /** すべてのページは同じグリッドを使う。 */
    fun currentGrid(): GridSpec = gridOf(_workspace.value)

    private fun gridOf(ws: Workspace?): GridSpec = ws?.orderedPages?.firstOrNull()?.grid ?: settings.value.grid()

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
                is DragSource.FolderApp -> LayoutEngine.addAppToDock(
                    LayoutEngine.removeFromFolder(ws, source.folderItemId, source.app), source.app, target.slot, dockSize,
                )
            }
            is DropTarget.Cell -> {
                val (base, page) = pageForIndex(ws, target.pageIndex) ?: return null
                when (source) {
                    is DragSource.Item -> LayoutEngine.move(base, source.itemId, page.id, target.x, target.y)
                    is DragSource.Dock ->
                        LayoutEngine.moveDockToPage(base, source.slot, page.id, target.x, target.y, base.nextItemId())
                    is DragSource.NewApp -> addAppAt(base, page, target, source.app)
                    is DragSource.FolderApp ->
                        addAppAt(LayoutEngine.removeFromFolder(base, source.folderItemId, source.app), page, target, source.app)
                }
            }
            is DropTarget.Merge -> {
                val (base, page) = pageForIndex(ws, target.pageIndex) ?: return null
                val targetItem = base.item(target.itemId)?.takeIf { it.pageId == page.id && LayoutEngine.acceptsMerge(it) }
                    ?: return null
                val (rest, app) = takeApp(base, source, target.itemId) ?: return null
                LayoutEngine.mergeInto(rest, targetItem.id, app)
            }
        }
        return (result as? Placement.Ok)?.workspace
    }

    private fun addAppAt(ws: Workspace, page: Page, target: DropTarget.Cell, app: AppRef): Placement {
        val span = page.grid.iconSpan
        val item = WorkspaceItem(ws.nextItemId(), page.id, CellRect(target.x, target.y, span, span), ItemContent.App(app))
        return LayoutEngine.add(ws, item)
    }

    /**
     * 運んでいるアプリを元の場所から外す。フォルダーにまとめるときに使う。
     * まとめ先そのもの・フォルダー・ウィジェットは運べないので null。
     */
    private fun takeApp(ws: Workspace, source: DragSource, targetItemId: Long): Pair<Workspace, AppRef>? = when (source) {
        is DragSource.Item -> {
            val app = (ws.item(source.itemId)?.content as? ItemContent.App)?.app
            if (app == null || source.itemId == targetItemId) null else LayoutEngine.remove(ws, source.itemId) to app
        }
        is DragSource.Dock -> ws.dockAt(source.slot)?.let { LayoutEngine.removeDock(ws, source.slot) to it.app }
        is DragSource.NewApp -> ws to source.app
        is DragSource.FolderApp -> if (source.folderItemId == targetItemId) {
            null
        } else {
            LayoutEngine.removeFromFolder(ws, source.folderItemId, source.app) to source.app
        }
    }

    /** 既存ページ、またはページ数と同じ番号なら新しいページ。 */
    private fun pageForIndex(ws: Workspace, index: Int): Pair<Workspace, Page>? {
        ws.pageAt(index)?.let { return ws to it }
        return if (index == ws.pages.size) LayoutEngine.addPage(ws, gridOf(ws)) else null
    }

    // ---- 追加・削除・リサイズ ----

    fun addAppToHome(app: AppRef) {
        val ws = _workspace.value ?: return
        val span = gridOf(ws).iconSpan
        val (next, itemId) = placeSomewhere(ws, span, span) { pageId, rect ->
            WorkspaceItem(0, pageId, rect, ItemContent.App(app))
        }
        commit(next, message = "ホームに追加しました", focusItemId = itemId)
    }

    fun placeNewWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        val ws = _workspace.value ?: return
        val (spanX, spanY) = widgetSpans(info, gridOf(ws))
        val (next, itemId) = placeSomewhere(ws, spanX, spanY) { pageId, rect ->
            WorkspaceItem(0, pageId, rect, ItemContent.Widget(widgetId, info.provider.flattenToString()))
        }
        commit(next, message = "ウィジェットを追加しました", focusItemId = itemId)
    }

    /** バックアップから復元した「再追加が必要」なウィジェットを、新しく割り当てた ID で置き換える。 */
    fun replaceWidget(itemId: Long, widgetId: Int, info: AppWidgetProviderInfo) {
        val ws = _workspace.value ?: return
        val item = ws.item(itemId) ?: return
        val next = ws.copy(
            items = ws.items.map {
                if (it.id == itemId) it.copy(content = ItemContent.Widget(widgetId, info.provider.flattenToString())) else it
            },
        )
        commit(next, message = "ウィジェットを追加し直しました", focusItemId = item.id)
    }

    /** 設定画面からの復元。配置は取り消せる。設定・外観の個別設定・使用履歴は置き換える。 */
    private suspend fun applyRestore(plan: com.rou39.bentohome.backup.RestorePlan) {
        val current = _workspace.value ?: return
        commit(plan.workspace, message = "バックアップから復元しました", removedWidgetIds = current.widgetIds)
        com.rou39.bentohome.backup.BackupService.applyNonLayout(c, plan)
    }

    fun removeItem(itemId: Long) {
        val ws = _workspace.value ?: return
        val item = ws.item(itemId) ?: return
        val widgetId = (item.content as? ItemContent.Widget)?.widgetId
        commit(LayoutEngine.remove(ws, itemId), message = "ホームから削除しました", removedWidgetIds = setOfNotNull(widgetId))
    }

    /** 重いウィジェットを後ろのページへ移し、そのページを表示する。 */
    fun moveToLaterPage(itemId: Long) {
        val ws = _workspace.value ?: return
        val result = LayoutEngine.moveToLaterPage(ws, itemId, gridOf(ws)) as? Placement.Ok ?: return
        val moved = result.workspace.item(itemId) ?: return
        commit(result.workspace, message = "後ろのページへ移しました")
        val index = LayoutEngine.pruneTrailingEmptyPages(result.workspace).orderedPages.indexOfFirst { it.id == moved.pageId }
        if (index >= 0) _focusPage.tryEmit(index)
    }

    // ---- ホーム画面に追加（plan.md §10） ----

    private fun placePinned(item: PinnedItem) {
        when (item) {
            is PinnedItem.Shortcut -> {
                val ws = _workspace.value ?: return
                val span = gridOf(ws).iconSpan
                val (next, itemId) = placeSomewhere(ws, span, span) { pageId, rect -> WorkspaceItem(0, pageId, rect, item.content) }
                commit(next, message = "ホームに追加しました", focusItemId = itemId)
            }
            is PinnedItem.Widget -> {
                val info = widgetHost.info(item.widgetId)
                if (info == null) widgetHost.delete(item.widgetId) else placeNewWidget(item.widgetId, info)
            }
        }
    }

    // ---- フォルダー（plan.md §10） ----

    fun renameFolder(itemId: Long, title: String) {
        val ws = _workspace.value ?: return
        val next = LayoutEngine.renameFolder(ws, itemId, title)
        if (next != ws) commit(next)
    }

    /** フォルダーから出して、ホームの空いている場所に置く。 */
    fun takeOutOfFolder(folderItemId: Long, app: AppRef) {
        val ws = _workspace.value ?: return
        val removed = LayoutEngine.removeFromFolder(ws, folderItemId, app)
        val span = gridOf(removed).iconSpan
        val (next, itemId) = placeSomewhere(removed, span, span) { pageId, rect ->
            WorkspaceItem(0, pageId, rect, ItemContent.App(app))
        }
        commit(next, message = "フォルダーから出しました", focusItemId = itemId)
    }

    fun removeDock(slot: Int) {
        val ws = _workspace.value ?: return
        commit(LayoutEngine.removeDock(ws, slot), message = "ドックから外しました")
    }

    fun canResize(itemId: Long, rect: CellRect): Boolean {
        val ws = _workspace.value ?: return false
        return LayoutEngine.resize(ws, itemId, rect) is Placement.Ok
    }

    fun resizeWidget(itemId: Long, rect: CellRect): Boolean {
        val ws = _workspace.value ?: return false
        val result = LayoutEngine.resize(ws, itemId, rect) as? Placement.Ok ?: return false
        if (result.workspace != ws) commit(result.workspace)
        return true
    }

    /** 提供元のサイズ制約から、リサイズできる範囲を求める。 */
    fun resizeLimits(info: AppWidgetProviderInfo): ResizeLimits {
        val grid = currentGrid()
        val (cellW, cellH) = baseCellSizePx ?: estimatedBaseCellSize(grid)
        return WidgetSizing.resizeLimits(
            info.minResizeWidth, info.minResizeHeight, info.maxResizeWidth, info.maxResizeHeight, cellW, cellH, grid,
        )
    }

    fun setLayoutLocked(locked: Boolean) {
        viewModelScope.launch { settingsRepo.setLayoutLocked(locked) }
    }

    fun setAppearance(app: AppRef, appearance: AppAppearance) {
        viewModelScope.launch { appearanceRepo.set(app, appearance) }
    }

    /**
     * 表示中のページから空きを探し、どこにも無ければ末尾にページを足して置く。
     * @return 新しい配置と、置いた項目の ID
     */
    private fun placeSomewhere(
        ws: Workspace,
        spanX: Int,
        spanY: Int,
        make: (pageId: Long, rect: CellRect) -> WorkspaceItem,
    ): Pair<Workspace, Long?> {
        val grid = gridOf(ws)
        val preferred = ws.pageAt(currentPageIndex)?.id
        val found = LayoutEngine.findSpace(ws, preferred, spanX, spanY, grid.subdivisions)
        val (base, pageId, rect) = if (found != null) {
            Triple(ws, found.first, found.second)
        } else {
            val (withPage, page) = LayoutEngine.addPage(ws, grid)
            Triple(withPage, page.id, CellRect(0, 0, spanX, spanY))
        }
        val item = make(pageId, rect).copy(id = base.nextItemId())
        val placed = (LayoutEngine.add(base, item) as? Placement.Ok)?.workspace ?: return ws to null
        return placed to item.id
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

    // ---- 設定の反映 ----

    /** マス目の数やドックの枠数が変わったら配置を組み直す。項目は削除せず、取り消せる。 */
    private fun applyLayoutSettings(s: LauncherSettings) {
        val ws = _workspace.value ?: return
        val grid = s.grid()
        var next = ws
        val notes = mutableListOf<String>()
        if (ws.pages.any { it.grid != grid }) {
            val change = LayoutEngine.changeGrid(ws, grid)
            next = change.workspace
            val moved = change.movedItemIds.size
            notes += "マス目を ${s.baseColumns}×${s.baseRows} にしました" + if (moved > 0) "（${moved}個を移動）" else ""
        }
        if (next.dock.any { it.slot >= s.dockSlots }) {
            var id = next.nextItemId()
            next = LayoutEngine.shrinkDock(next, s.dockSlots, { id++ }, grid)
            notes += "ドックから外れたアプリをホームへ移しました"
        }
        if (next != ws) commit(next, message = notes.joinToString("。"))
    }

    // ---- 取り消し ----

    private fun commit(
        next: Workspace,
        message: String? = null,
        removedWidgetIds: Set<Int> = emptySet(),
        focusItemId: Long? = null,
    ) {
        val prev = _workspace.value ?: return
        finalizePendingWidgetDeletes()
        finalizeUnpins()
        val pruned = LayoutEngine.pruneTrailingEmptyPages(next)
        val after = shortcutsOf(pruned)
        pendingUnpins += shortcutsOf(prev).filter { (key, ids) -> !after[key].orEmpty().containsAll(ids) }.keys
        _workspace.value = pruned
        repo.save(pruned)
        undoSnapshot = prev
        pendingWidgetDeletes += removedWidgetIds
        _undo.value = UndoState(message, ++undoToken)
        focusItemId?.let { focusOnItem(pruned, it) }
    }

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
        // 戻した配置を基準に固定を合わせる（戻したショートカットは固定したまま）
        finalizeUnpins()
        _undo.value = null
        syncSettingsWith(snapshot)
        // 戻した項目のあるページを表示する
        snapshot.items.firstOrNull { current.item(it.id) != it }?.let { focusOnItem(snapshot, it.id) }
    }

    /**
     * マス目やドック枠の変更を取り消したときは、設定も配置に合わせて戻す。
     * 戻さないと、次に設定が変わったときに取り消した変更がまた適用される。
     */
    private fun syncSettingsWith(ws: Workspace) {
        val grid = gridOf(ws)
        val neededDock = (ws.dock.maxOfOrNull { it.slot } ?: -1) + 1
        val s = settings.value
        if (grid == s.grid() && neededDock <= s.dockSlots) return
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(
                    baseColumns = grid.baseColumns,
                    baseRows = grid.baseRows,
                    dockSlots = maxOf(it.dockSlots, neededDock),
                )
            }
        }
    }

    private fun focusOnItem(ws: Workspace, itemId: Long) {
        val pageId = ws.item(itemId)?.pageId ?: return
        val index = ws.orderedPages.indexOfFirst { it.id == pageId }
        if (index >= 0) _focusPage.tryEmit(index)
    }

    /** 提供元ごとの、ホームに置いたショートカットの ID。 */
    private fun shortcutsOf(ws: Workspace): Map<Pair<String, Long>, Set<String>> =
        ws.items.mapNotNull { it.content as? ItemContent.Shortcut }
            .groupBy({ it.packageName to it.profileSerial }, { it.shortcutId })
            .mapValues { it.value.toSet() }

    private fun finalizeUnpins() {
        if (pendingUnpins.isEmpty()) return
        val current = _workspace.value?.let(::shortcutsOf).orEmpty()
        for (key in pendingUnpins) catalog.pinShortcuts(key.first, current[key].orEmpty().toList(), key.second)
        pendingUnpins.clear()
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
            val (spanX, spanY) = widgetSpans(info, gridOf(ws))
            ws = placeSomewhere(ws, spanX, spanY) { pageId, rect ->
                WorkspaceItem(0, pageId, rect, ItemContent.Widget(id, info.provider.flattenToString()))
            }.first
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
            when (val c = item.content) {
                is ItemContent.App -> resolve(c.app)?.let { item.copy(content = ItemContent.App(it)) }
                is ItemContent.Folder -> item.copy(content = c.copy(apps = c.apps.mapNotNull(::resolve).distinct()))
                is ItemContent.Widget -> item
                is ItemContent.Shortcut ->
                    item.takeUnless { c.profileSerial == catalog.mainProfileSerial && !catalog.isInstalled(c.packageName) }
            }
        }
        val dock = ws.dock.mapNotNull { slot -> resolve(slot.app)?.let { DockSlot(slot.slot, it) } }
        val cleaned = LayoutEngine.normalizeFolders(ws.copy(items = items, dock = dock))
        if (cleaned != ws) Log.d(TAG, "cleaned up missing apps")
        return cleaned
    }

    /** OS 側で固定したままで配置に無いショートカットを外す。取り消し猶予中にプロセスが終わった場合の後始末。 */
    private fun releaseOrphanPins(ws: Workspace) {
        val onHome = shortcutsOf(ws)
        val main = catalog.mainProfileSerial
        for ((pkg, pinned) in catalog.pinnedByBento()) {
            val keep = onHome[pkg to main].orEmpty()
            if (!keep.containsAll(pinned)) catalog.pinShortcuts(pkg, keep.toList(), main)
        }
    }

    /** ホスト側にあって配置に無い ID を解放する。取り消し猶予中にプロセスが終わった場合の後始末。 */
    private fun releaseOrphanWidgetIds(ws: Workspace) {
        val orphans = widgetHost.hostIds() - ws.widgetIds
        if (orphans.isNotEmpty()) Log.d(TAG, "release orphan widget ids: $orphans")
        orphans.forEach(widgetHost::delete)
    }

    private fun onPackageRemoved(e: PackageEvent.Removed) {
        val ws = _workspace.value ?: return
        val next = LayoutEngine.removePackage(ws, e.packageName, e.profileSerial)
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

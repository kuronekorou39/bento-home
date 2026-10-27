package com.rou39.bentohome.home

import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.rou39.bentohome.badge.BadgeKey
import com.rou39.bentohome.badge.BadgeRepository
import com.rou39.bentohome.badge.BadgeState
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.data.AppAppearance
import com.rou39.bentohome.data.BadgeMode
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.CellRect
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.Page
import com.rou39.bentohome.layout.Partition
import com.rou39.bentohome.layout.ResizeLimits
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.layout.WorkspaceItem
import com.rou39.bentohome.usage.GrayReason
import com.rou39.bentohome.usage.RankInput
import com.rou39.bentohome.usage.UsagePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val DOCK_HEIGHT = 88.dp

/** 画面端でこの時間止まると、ドラッグ中にページを送る。 */
private const val EDGE_FLIP_DELAY_MS = 600L

/** アプリ一覧の開閉の時間と、せり上がる距離（画面の高さに対する割合の逆数）。 */
private const val DRAWER_ANIM_MS = 220
private const val DRAWER_SLIDE_FRACTION = 3

/** 画面の大きさが分かる前に使う、ページの横÷縦。 */
private const val DEFAULT_PAGE_ASPECT = 0.6f

private val OK_COLOR = Color(0x5534C759)
private val NG_COLOR = Color(0x55FF3B30)

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    roleHeld: Boolean,
    homeResetCount: Int,
    onRequestRole: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMonitor: () -> Unit,
    onReconfigureWidget: (Int) -> Unit,
    onReAddWidget: (itemId: Long, provider: String) -> Unit,
) {
    val ws by vm.workspace.collectAsState()
    val settings by vm.settings.collectAsState()
    val appearances by vm.appearances.collectAsState()
    val apps by vm.displayApps.collectAsState()
    val byRef by vm.displayByRef.collectAsState()
    val badgeState by BadgeRepository.state.collectAsState()
    val undo by vm.undo.collectAsState()
    val usage by vm.usage.collectAsState()
    val now by vm.now.collectAsState()

    val workspace = ws ?: return
    // ページ数とキーのラムダは固定する。再コンポーズのたびに作り直すと、ページャーが状態を書き換えて
    // HomeScreen がまた再コンポーズされ、待機中も毎フレーム描き直し続ける（docs/stage5-notes.md）
    val pages = remember(workspace) { workspace.orderedPages }
    val drag = remember { DragState() }
    val geometry = remember { HomeGeometry() }
    val pageCount by rememberUpdatedState(pages.size + if (drag.active) 1 else 0)
    val pagerState = rememberPagerState(pageCount = remember { { pageCount } })
    val pageKey: (Int) -> Any = remember(pages) { { index -> pages.getOrNull(index)?.id ?: -1L } }
    val scope = rememberCoroutineScope()
    val controller = remember { HomeController(vm, drag, geometry, pagerState, scope) }
    controller.locked = settings.layoutLocked
    controller.dockVisible = settings.dockVisible
    controller.style = settings.iconStyle()
    controller.onReconfigureWidget = onReconfigureWidget
    controller.onReAddWidget = onReAddWidget

    val counts = (badgeState as? BadgeState.Counts)?.counts
    val badgeFor: (AppEntry?) -> BadgeDisplay? = { entry ->
        entry?.let {
            val count = counts?.get(BadgeKey(it.packageName, it.ref.profileSerial)) ?: 0
            if (count <= 0) {
                null
            } else {
                when (appearances[it.ref]?.badgeMode ?: settings.badgeMode) {
                    BadgeMode.NONE -> null
                    BadgeMode.DOT -> BadgeDisplay.Dot
                    BadgeMode.NUMBER -> BadgeDisplay.Count(count)
                }
            }
        }
    }
    val hostIds = remember(workspace) { vm.widgetHost.hostIds() }
    // 使わなくなったアプリのグレー表示（plan.md §4）。バッジのあるアプリ・通常色に固定したアプリは対象外
    val grayFor: (AppEntry?) -> GrayReason? = { entry ->
        entry?.let {
            val hasBadge = (counts?.get(BadgeKey(it.packageName, it.ref.profileSerial)) ?: 0) > 0
            UsagePolicy.grayReason(
                usage[it.ref], now, settings.grayStages.days, appearances[it.ref]?.keepColor == true, hasBadge,
            )
        }
    }
    val homeGrayFor: (AppEntry?) -> GrayReason? = if (settings.grayOnHome) grayFor else { _ -> null }
    val drawerGrayFor: (AppEntry?) -> GrayReason? = if (settings.grayInDrawer) grayFor else { _ -> null }

    val wsState = rememberUpdatedState(workspace)
    val target by remember { derivedStateOf { controller.computeTarget(wsState.value) } }
    val targetValid by remember {
        derivedStateOf { drag.source?.let { vm.canDrop(it, target) } ?: false }
    }

    LaunchedEffect(homeResetCount) { controller.resetForHome() }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            vm.currentPageIndex = it
            controller.resizingItemId = null
        }
    }
    LaunchedEffect(vm) { vm.focusPage.collect { pagerState.animateScrollToPage(it) } }
    EdgeFlipEffect(controller)
    WallpaperScrollEffect(pagerState, pages.size)

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undo?.token) {
        val message = undo?.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = "元に戻す", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) vm.undo()
    }

    // ホームでの戻る操作は何もしない。開いているものだけ閉じる。
    BackHandler(enabled = true) {}
    BackHandler(enabled = controller.drawerOpen) { controller.drawerOpen = false }
    BackHandler(enabled = controller.selectedApps.isNotEmpty()) { controller.clearSelection() }
    BackHandler(enabled = controller.overviewOpen) { controller.overviewOpen = false }
    BackHandler(enabled = controller.backgroundMenuAt != null) { controller.backgroundMenuAt = null }
    BackHandler(enabled = controller.resizingItemId != null) { controller.resizingItemId = null }
    BackHandler(enabled = controller.openFolderId != null) { controller.closeFolder() }
    BackHandler(enabled = drag.active) { drag.clear() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (!roleHeld) {
                Button(onClick = onRequestRole, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Text("Bento Home を既定のホームにする")
                }
            }
            val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .onGloballyPositioned { geometry.gridArea = it.boundsInRoot() }
                    .verticalSwipe(
                        swipeThreshold,
                        onSwipeUp = controller::openDrawer,
                        onSwipeDown = if (settings.swipeDownNotifications) controller::openNotifications else null,
                    )
                    // 背景の長押し・上スワイプは読み上げ中に使えないので、同じ操作を用意する
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("ホームのメニュー") {
                                controller.backgroundMenuAt = geometry.gridArea.center
                                true
                            },
                            CustomAccessibilityAction("すべてのアプリ") {
                                controller.openDrawer()
                                true
                            },
                            CustomAccessibilityAction("通知を開く") {
                                controller.openNotifications()
                                true
                            },
                        )
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = pages.size + 1,
                    key = pageKey,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val page = pages.getOrNull(index)
                    // 見えていないページ・一覧の下のウィジェットは View を非表示にして、提供元のアニメーションを止める
                    val pageVisible by remember(index) {
                        derivedStateOf { pagerState.layoutInfo.visiblePagesInfo.any { it.index == index } }
                    }
                    val highlight = when (val t = target) {
                        is DropTarget.Cell -> CellRect(t.x, t.y, drag.spanX, drag.spanY).takeIf { t.pageIndex == index }
                        // まとめ先は、その項目の範囲を示す
                        is DropTarget.Merge -> workspace.item(t.itemId)?.rect?.takeIf { t.pageIndex == index }
                        else -> null
                    }
                    WorkspacePage(
                        controller = controller,
                        workspace = workspace,
                        page = page,
                        byRef = byRef,
                        hostIds = hostIds,
                        badgeFor = badgeFor,
                        grayFor = homeGrayFor,
                        highlight = highlight,
                        highlightValid = targetValid,
                        widgetsVisible = pageVisible && !controller.drawerOpen,
                    )
                }
            }
            PageIndicator(
                count = pagerState.pageCount,
                current = pagerState.currentPage,
                onOpenDrawer = controller::openDrawer,
            )
            if (settings.dockVisible) {
                DockBar(
                    controller = controller,
                    workspace = workspace,
                    byRef = byRef,
                    badgeFor = badgeFor,
                    grayFor = homeGrayFor,
                    highlightSlot = (target as? DropTarget.Dock)?.slot,
                    highlightValid = targetValid,
                )
            }
        }

        // 下からせり上がって開き、下へ戻って閉じる
        AnimatedVisibility(
            visible = controller.drawerOpen,
            enter = slideInVertically(tween(DRAWER_ANIM_MS)) { it / DRAWER_SLIDE_FRACTION } + fadeIn(tween(DRAWER_ANIM_MS)),
            exit = slideOutVertically(tween(DRAWER_ANIM_MS)) { it / DRAWER_SLIDE_FRACTION } + fadeOut(tween(DRAWER_ANIM_MS)),
        ) {
            AppDrawer(
                controller = controller,
                apps = apps,
                hidden = remember(appearances) { appearances.filterValues { it.hidden }.keys },
                multiPage = pages.size >= 2,
                badgeFor = badgeFor,
                grayFor = drawerGrayFor,
                sort = settings.drawerSort,
                rankInputs = { list ->
                    list.map { e ->
                        RankInput(e.ref, e.label, usage[e.ref], appearances[e.ref]?.excludeFromRanking == true)
                    }
                },
                // ドラッグ中は透明にするだけで残す。外すと指の操作が途切れる。
                modifier = Modifier.alpha(if (drag.fromDrawer) 0f else 1f),
            )
        }

        if (controller.overviewOpen && controller.drawerOpen) {
            val area = geometry.gridArea
            HomeOverview(
                controller = controller,
                workspace = workspace,
                byRef = byRef,
                count = controller.selectedApps.size,
                aspect = if (area.height > 0f) area.width / area.height else DEFAULT_PAGE_ASPECT,
                onPick = { pageIndex ->
                    vm.addAppsToPage(controller.selectedApps.toList(), pageIndex)
                    controller.clearSelection()
                    controller.drawerOpen = false
                },
                onDismiss = { controller.overviewOpen = false },
            )
        }

        val openFolder = controller.openFolderId?.let { workspace.item(it) }
        val openFolderContent = openFolder?.content as? ItemContent.Folder
        if (openFolder != null && openFolderContent != null) {
            FolderOverlay(controller, openFolder, openFolderContent, byRef, badgeFor, homeGrayFor)
        } else if (controller.openFolderId != null && !drag.active) {
            // 中が1つになってアプリに戻った、または削除されたフォルダーは閉じる
            LaunchedEffect(Unit) { controller.closeFolder() }
        }

        DragGhost(controller, byRef)

        controller.backgroundMenuAt?.let { at ->
            Box(Modifier.offset { at.round() }) {
                DropdownMenu(expanded = true, onDismissRequest = { controller.backgroundMenuAt = null }) {
                    if (!controller.locked) {
                        DropdownMenuItem(text = { Text("ウィジェットを追加") }, onClick = {
                            controller.backgroundMenuAt = null
                            onOpenWidgetPicker()
                        })
                    }
                    DropdownMenuItem(text = { Text("仕切り") }, onClick = {
                        controller.backgroundMenuAt = null
                        controller.partitionPickerOpen = true
                    })
                    if (undo != null) {
                        DropdownMenuItem(text = { Text("直前の操作を元に戻す") }, onClick = {
                            controller.backgroundMenuAt = null
                            vm.undo()
                        })
                    }
                    DropdownMenuItem(
                        text = { Text(if (controller.locked) "配置ロックを解除" else "配置をロック") },
                        onClick = {
                            controller.backgroundMenuAt = null
                            vm.setLayoutLocked(!controller.locked)
                        },
                    )
                    DropdownMenuItem(text = { Text("すべてのアプリ") }, onClick = controller::openDrawer)
                    DropdownMenuItem(text = { Text("システムモニター") }, onClick = {
                        controller.backgroundMenuAt = null
                        onOpenMonitor()
                    })
                    DropdownMenuItem(text = { Text("設定") }, onClick = {
                        controller.backgroundMenuAt = null
                        onOpenSettings()
                    })
                }
            }
        }

        if (controller.partitionPickerOpen) {
            val page = workspace.pageAt(pagerState.currentPage)
            PartitionPicker(
                current = page?.partition ?: Partition.NONE,
                onPick = {
                    controller.partitionPickerOpen = false
                    vm.setPartition(pagerState.currentPage, it)
                },
                onDismiss = { controller.partitionPickerOpen = false },
            )
        }

        controller.measuring?.let { MeasuringOverlay(it, controller.measureEndsAt) }
        controller.measureResult?.let { result ->
            MeasureResultDialog(
                result = result,
                canMove = !controller.locked,
                onMove = {
                    controller.measureResult = null
                    vm.moveToLaterPage(result.itemId)
                },
                onDismiss = { controller.measureResult = null },
            )
        }

        controller.appearanceTarget?.let { entry ->
            AppAppearanceDialog(
                entry = entry,
                appearance = appearances[entry.ref] ?: AppAppearance(),
                globalBadgeMode = settings.badgeMode,
                onChange = { vm.setAppearance(entry.ref, it) },
                onDismiss = { controller.appearanceTarget = null },
            )
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

/** 背景の縦スワイプ。上でアプリ一覧、下で通知パネル（onSwipeDown が null なら何もしない）。 */
private fun Modifier.verticalSwipe(threshold: Float, onSwipeUp: () -> Unit, onSwipeDown: (() -> Unit)?): Modifier =
    this.then(
        Modifier.pointerInput(onSwipeDown != null) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    when {
                        total < -threshold -> onSwipeUp()
                        total > threshold -> onSwipeDown?.invoke()
                    }
                },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    total += dy
                },
            )
        },
    )

/**
 * ページ送りに合わせて壁紙を横に動かす（docs/design.md §6）。壁紙の画像は OS の設定のもので、Bento は位置だけ伝える。
 * ページ数は実際のページの数を使う。ドラッグ中に置き先として足す空のページを数えると、つまんだだけで壁紙がずれる。
 */
@Composable
private fun WallpaperScrollEffect(pagerState: PagerState, realPageCount: Int) {
    val view = LocalView.current
    val wallpaper = remember { android.app.WallpaperManager.getInstance(view.context) }
    val realCount by rememberUpdatedState(realPageCount)
    LaunchedEffect(pagerState) {
        snapshotFlow {
            val count = realCount.coerceAtLeast(1)
            count to (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceAtMost((count - 1).toFloat())
        }.collect { (count, position) ->
            val token = view.windowToken ?: return@collect
            val steps = if (count > 1) 1f / (count - 1) else 0f
            val x = if (count > 1) (position / (count - 1)).coerceIn(0f, 1f) else 0.5f
            wallpaper.setWallpaperOffsetSteps(steps, 0f)
            wallpaper.setWallpaperOffsets(token, x, 0.5f)
        }
    }
}

@Composable
private fun EdgeFlipEffect(controller: HomeController) {
    val drag = controller.drag
    val edge = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(drag.active) {
        if (!drag.active) return@LaunchedEffect
        var direction = 0
        var since = 0L
        while (true) {
            val area = controller.geometry.gridArea
            val p = drag.pointer
            val inRows = p.y in area.top..area.bottom
            val d = when {
                !inRows -> 0
                p.x < area.left + edge -> -1
                p.x > area.right - edge -> 1
                else -> 0
            }
            val now = System.currentTimeMillis()
            if (d != 0 && d == direction) {
                if (now - since >= EDGE_FLIP_DELAY_MS) {
                    val pager = controller.pagerState
                    val next = pager.currentPage + d
                    if (next in 0 until pager.pageCount) pager.animateScrollToPage(next)
                    since = System.currentTimeMillis()
                }
            } else {
                direction = d
                since = now
            }
            delay(50)
        }
    }
}

@Composable
private fun WorkspacePage(
    controller: HomeController,
    workspace: Workspace,
    page: Page?,
    byRef: Map<AppRef, AppEntry>,
    hostIds: Set<Int>,
    badgeFor: (AppEntry?) -> BadgeDisplay?,
    grayFor: (AppEntry?) -> GrayReason?,
    highlight: CellRect?,
    highlightValid: Boolean,
    widgetsVisible: Boolean,
) {
    val vm = controller.vm
    val grid = page?.grid ?: vm.currentGrid()
    val style = controller.style
    var pageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { pageCoords = it }) {
        val cellW = maxWidth / grid.columns
        val cellH = maxHeight / grid.rows
        val density = LocalDensity.current
        LaunchedEffect(cellW, cellH, grid) {
            with(density) { vm.baseCellSizePx = cellW.toPx() * grid.subdivisions to cellH.toPx() * grid.subdivisions }
        }
        // アイコンは占有範囲からラベルの高さを引いた中に収める
        // ラベルの高さは端末の文字の大きさに合わせて見積もる
        val labelSpace = if (style.labelVisible) (style.labelSizeSp * 2 * density.fontScale).dp else 4.dp
        val iconSize = min(style.iconSize, min(cellW * grid.iconSpan - 8.dp, cellH * grid.iconSpan - labelSpace))

        // 背景: 項目の無い場所の長押しでメニュー、タップでリサイズを終える
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controller.resizingItemId = null },
                    onLongPress = { local ->
                        controller.resizingItemId = null
                        pageCoords?.let { controller.backgroundMenuAt = it.localToRoot(local) }
                    },
                )
            },
        )

        // つまんで運んでいる間だけ、マス目の薄い線と半マスの点を出す
        if (controller.drag.active) DragGuide(grid)
        if (page != null) {
            PartitionLayer(page.partition, maxWidth, maxHeight)
            for (item in workspace.itemsOn(page.id)) {
                key(item.id) {
                    val dragged = (controller.drag.source as? DragSource.Item)?.itemId == item.id
                    Box(Modifier.cellPlacement(item.rect, cellW, cellH).alpha(if (dragged) 0.3f else 1f)) {
                        when (val content = item.content) {
                            is ItemContent.App ->
                                HomeAppCell(controller, item, content.app, byRef[content.app], iconSize, badgeFor, grayFor)
                            is ItemContent.Widget ->
                                HomeWidgetCell(controller, item, content, content.widgetId in hostIds, widgetsVisible)
                            is ItemContent.Folder ->
                                HomeFolderCell(controller, item, content, byRef, iconSize, badgeFor)
                            is ItemContent.Shortcut -> HomeShortcutCell(controller, item, content, iconSize)
                        }
                    }
                }
            }
            val resizing = controller.resizingItemId?.let { workspace.item(it) }?.takeIf { it.pageId == page.id }
            val resizingWidget = resizing?.content as? ItemContent.Widget
            if (resizing != null && resizingWidget != null) {
                val info = remember(resizingWidget.widgetId) { vm.widgetHost.info(resizingWidget.widgetId) }
                if (info != null) {
                    ResizeFrame(controller, resizing, vm.resizeLimits(info), info.resizeMode, cellW, cellH)
                }
            }
        }

        // 置き先の枠は項目より上に描く（ウィジェットに隠れないように）
        if (highlight != null) {
            Box(
                Modifier
                    .cellPlacement(highlight, cellW, cellH)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (highlightValid) OK_COLOR else NG_COLOR),
            )
        }
    }
}

/** 運んでいる間の目安。マス目の線（薄い）と、半マスの交点の点（さらに薄い）。 */
@Composable
private fun DragGuide(grid: GridSpec) {
    Canvas(Modifier.fillMaxSize()) {
        val stepX = size.width / grid.columns
        val stepY = size.height / grid.rows
        val stroke = 1.dp.toPx()
        for (i in 0..grid.columns) {
            if (i % grid.subdivisions != 0) continue
            drawLine(GUIDE_LINE, Offset(i * stepX, 0f), Offset(i * stepX, size.height), stroke)
        }
        for (j in 0..grid.rows) {
            if (j % grid.subdivisions != 0) continue
            drawLine(GUIDE_LINE, Offset(0f, j * stepY), Offset(size.width, j * stepY), stroke)
        }
        val r = 1.5.dp.toPx()
        for (i in 1 until grid.columns) for (j in 1 until grid.rows) {
            if (i % grid.subdivisions == 0 && j % grid.subdivisions == 0) continue
            drawCircle(GUIDE_DOT, r, Offset(i * stepX, j * stepY))
        }
    }
}

private val GUIDE_LINE = Color.White.copy(alpha = 0.14f)
private val GUIDE_DOT = Color.White.copy(alpha = 0.22f)

private fun Modifier.cellPlacement(rect: CellRect, cellW: Dp, cellH: Dp): Modifier =
    this.offset(x = cellW * rect.x, y = cellH * rect.y).size(cellW * rect.width, cellH * rect.height)

private enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

/**
 * ウィジェットのリサイズ枠。辺のつまみを動かすと1マス刻みで大きさが変わり、置けない大きさは赤で示す。
 * 指を離したときに置ける大きさなら確定する。提供元が許す方向・範囲だけ動かせる。
 */
@Composable
private fun ResizeFrame(
    controller: HomeController,
    item: WorkspaceItem,
    limits: ResizeLimits,
    resizeMode: Int,
    cellW: Dp,
    cellH: Dp,
) {
    val vm = controller.vm
    val grid = vm.currentGrid()
    val sub = grid.subdivisions
    val density = LocalDensity.current
    val stepW = with(density) { cellW.toPx() } * sub
    val stepH = with(density) { cellH.toPx() } * sub
    var preview by remember(item.rect) { mutableStateOf(item.rect) }
    val valid = preview == item.rect || vm.canResize(item.id, preview)

    fun rectFor(edge: Edge, drag: Offset): CellRect {
        val start = item.rect
        val dx = (drag.x / stepW).roundToInt() * sub
        val dy = (drag.y / stepH).roundToInt() * sub
        return when (edge) {
            Edge.RIGHT -> start.copy(width = limits.clampWidth(start.width + dx).coerceAtMost(grid.columns - start.x))
            Edge.LEFT -> {
                val w = limits.clampWidth(start.width - dx).coerceAtMost(start.right)
                start.copy(x = start.right - w, width = w)
            }
            Edge.BOTTOM -> start.copy(height = limits.clampHeight(start.height + dy).coerceAtMost(grid.rows - start.y))
            Edge.TOP -> {
                val h = limits.clampHeight(start.height - dy).coerceAtMost(start.bottom)
                start.copy(y = start.bottom - h, height = h)
            }
        }
    }

    Box(
        Modifier
            .cellPlacement(preview, cellW, cellH)
            .border(2.dp, if (valid) Color.White else Color(0xFFFF3B30), RoundedCornerShape(16.dp))
            .background(if (valid) Color(0x22FFFFFF) else NG_COLOR, RoundedCornerShape(16.dp)),
    ) {
        val horizontal = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val edges = buildList {
            if (horizontal) addAll(listOf(Edge.LEFT, Edge.RIGHT))
            if (vertical) addAll(listOf(Edge.TOP, Edge.BOTTOM))
        }
        for (edge in edges) {
            val align = when (edge) {
                Edge.LEFT -> Alignment.CenterStart
                Edge.RIGHT -> Alignment.CenterEnd
                Edge.TOP -> Alignment.TopCenter
                Edge.BOTTOM -> Alignment.BottomCenter
            }
            Box(
                Modifier
                    .align(align)
                    .size(36.dp)
                    .pointerInput(edge, item.rect) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                preview = rectFor(edge, total)
                            },
                            onDragEnd = {
                                if (preview != item.rect && !vm.resizeWidget(item.id, preview)) preview = item.rect
                            },
                            onDragCancel = { preview = item.rect },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun HomeAppCell(
    controller: HomeController,
    item: WorkspaceItem,
    app: AppRef,
    entry: AppEntry?,
    iconSize: Dp,
    badgeFor: (AppEntry?) -> BadgeDisplay?,
    grayFor: (AppEntry?) -> GrayReason?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val style = controller.style
    val gray = grayFor(entry)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().launcherItemGestures(
            item.id,
            controller.itemCallbacks(
                source = DragSource.Item(item.id),
                visual = DragVisual.App(app),
                spanX = item.rect.width,
                spanY = item.rect.height,
                entry = entry,
                canDrag = true,
                onLongPress = { menuOpen = true },
                onDragStarted = { menuOpen = false },
            ),
        ),
    ) {
        AppIconView(
            entry, controller.vm.iconCache, iconSize, style.shape, badgeFor(entry),
            labelSizeSp = style.labelSizeSp.takeIf { style.labelVisible },
            grayLevel = gray?.level ?: 0,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuItems(controller, entry, close = { menuOpen = false }, gray = gray)
            if (!controller.locked) {
                DropdownMenuItem(text = { Text("ホームから削除") }, onClick = {
                    menuOpen = false
                    controller.vm.removeItem(item.id)
                })
            }
        }
    }
}

/** アプリショートカット・アプリ情報・アンインストール・外観の個別設定。ホーム・ドック・一覧で共通。 */
@Composable
fun AppMenuItems(controller: HomeController, entry: AppEntry?, close: () -> Unit, gray: GrayReason? = null) {
    val context = LocalContext.current
    if (entry == null) return
    if (gray != null) {
        DropdownMenuItem(text = { Text(gray.describe(), fontSize = 12.sp) }, onClick = {}, enabled = false)
        HorizontalDivider()
    }
    val catalog = controller.vm.catalog
    val density = context.resources.displayMetrics.densityDpi
    val shortcuts by produceState<List<Pair<android.content.pm.ShortcutInfo, ImageBitmap?>>>(emptyList(), entry) {
        value = withContext(Dispatchers.IO) {
            catalog.shortcutsFor(entry).map { s ->
                s to catalog.shortcutIcon(s, density)?.toBitmap(96, 96)?.asImageBitmap()
            }
        }
    }
    for ((shortcut, icon) in shortcuts) {
        DropdownMenuItem(
            text = { Text(shortcut.shortLabel?.toString() ?: shortcut.id) },
            leadingIcon = icon?.let { { Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) } },
            onClick = {
                close()
                controller.vm.launchShortcut(entry, shortcut)
            },
        )
    }
    if (shortcuts.isNotEmpty()) HorizontalDivider()
    DropdownMenuItem(text = { Text("アプリ情報") }, onClick = {
        close()
        catalog.openAppInfo(entry, null)
    })
    if (!entry.isSystemApp) {
        DropdownMenuItem(text = { Text("アンインストール") }, onClick = {
            close()
            context.startActivity(catalog.uninstallIntent(entry))
        })
    }
    DropdownMenuItem(text = { Text("外観の個別設定") }, onClick = {
        close()
        controller.appearanceTarget = entry
    })
}

@Composable
private fun HomeWidgetCell(
    controller: HomeController,
    item: WorkspaceItem,
    widget: ItemContent.Widget,
    onHost: Boolean,
    visible: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val vm = controller.vm
    val info = remember(widget.widgetId, onHost) { if (onHost) vm.widgetHost.info(widget.widgetId) else null }
    val label = remember(info) {
        info?.loadLabel(vm.getApplication<android.app.Application>().packageManager) ?: "ウィジェット"
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .widgetGestures(
                item.id,
                controller.itemCallbacks(
                    source = DragSource.Item(item.id),
                    visual = DragVisual.Widget(label),
                    spanX = item.rect.width,
                    spanY = item.rect.height,
                    entry = null,
                    canDrag = controller.resizingItemId != item.id,
                    onLongPress = { menuOpen = true },
                    onDragStarted = { menuOpen = false },
                ),
                controller.pagerDrag,
            ),
    ) {
        if (info == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xAA303030))
                    .clickable(enabled = !controller.locked && widget.provider.isNotEmpty()) {
                        controller.onReAddWidget(item.id, widget.provider)
                    },
            ) {
                Text("タップしてウィジェットを追加し直す", color = Color.White, fontSize = 12.sp)
            }
        } else {
            WidgetHostSurface(controller, widget.widgetId, visible && controller.hiddenWidgetId != widget.widgetId)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val reconfigurable = info != null && info.configure != null &&
                info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
            if (reconfigurable) {
                DropdownMenuItem(text = { Text("ウィジェットを設定") }, onClick = {
                    menuOpen = false
                    controller.onReconfigureWidget(widget.widgetId)
                })
            }
            if (info != null) {
                DropdownMenuItem(text = { Text("負荷を測る") }, onClick = {
                    menuOpen = false
                    controller.measureWidget(item.id, widget.widgetId, label.toString())
                })
            }
            if (controller.locked) {
                DropdownMenuItem(text = { Text("配置はロック中です") }, onClick = { menuOpen = false })
            } else {
                if (info != null && info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) {
                    DropdownMenuItem(text = { Text("サイズ変更") }, onClick = {
                        menuOpen = false
                        controller.resizingItemId = item.id
                    })
                }
                DropdownMenuItem(text = { Text("ホームから削除") }, onClick = {
                    menuOpen = false
                    vm.removeItem(item.id)
                })
            }
        }
    }
}

@Composable
private fun WidgetHostSurface(controller: HomeController, widgetId: Int, visible: Boolean) {
    val widgetHost = controller.vm.widgetHost
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val view = remember(widgetId) { widgetHost.viewFor(widgetId) }
        val w = maxWidth
        val h = maxHeight
        LaunchedEffect(widgetId, w, h) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(w.value, h.value)))
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { (view.parent as? ViewGroup)?.removeView(view); view },
            update = { it.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE },
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, onOpenDrawer: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(28.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .clearAndSetSemantics { contentDescription = "ページ ${current + 1} / $count" },
        ) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(if (i == current) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i == current) Color.White else Color.White.copy(alpha = 0.45f)),
                )
            }
        }
        // スワイプに頼れないとき用の、一覧を開くボタン（plan.md §2）
        Text(
            "︿",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .clickable(onClickLabel = "開く", onClick = onOpenDrawer)
                .semantics { contentDescription = "すべてのアプリ" }
                .padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun DockBar(
    controller: HomeController,
    workspace: Workspace,
    byRef: Map<AppRef, AppEntry>,
    badgeFor: (AppEntry?) -> BadgeDisplay?,
    grayFor: (AppEntry?) -> GrayReason?,
    highlightSlot: Int?,
    highlightValid: Boolean,
) {
    val vm = controller.vm
    Row(
        Modifier
            .fillMaxWidth()
            .height(DOCK_HEIGHT)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .onGloballyPositioned { controller.geometry.dockArea = it.boundsInRoot() },
    ) {
        for (slot in 0 until vm.dockSize) {
            val dockSlot = workspace.dockAt(slot)
            val highlighted = highlightSlot == slot
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        when {
                            !highlighted -> Color.Transparent
                            highlightValid -> OK_COLOR
                            else -> NG_COLOR
                        },
                    ),
            ) {
                if (dockSlot != null) DockAppCell(controller, slot, dockSlot.app, byRef[dockSlot.app], badgeFor, grayFor)
            }
        }
    }
}

@Composable
private fun DockAppCell(
    controller: HomeController,
    slot: Int,
    app: AppRef,
    entry: AppEntry?,
    badgeFor: (AppEntry?) -> BadgeDisplay?,
    grayFor: (AppEntry?) -> GrayReason?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val gray = grayFor(entry)
    val span = controller.vm.currentGrid().iconSpan
    val dragged = (controller.drag.source as? DragSource.Dock)?.slot == slot
    val style = controller.style
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().alpha(if (dragged) 0.3f else 1f).launcherItemGestures(
            "dock-$slot",
            controller.itemCallbacks(
                source = DragSource.Dock(slot),
                visual = DragVisual.App(app),
                spanX = span,
                spanY = span,
                entry = entry,
                canDrag = true,
                onLongPress = { menuOpen = true },
                onDragStarted = { menuOpen = false },
            ),
        ),
    ) {
        AppIconView(
            entry, controller.vm.iconCache, min(style.iconSize, DOCK_HEIGHT - 16.dp), style.shape, badgeFor(entry),
            labelSizeSp = null,
            grayLevel = gray?.level ?: 0,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuItems(controller, entry, close = { menuOpen = false }, gray = gray)
            if (!controller.locked) {
                DropdownMenuItem(text = { Text("ドックから外す") }, onClick = {
                    menuOpen = false
                    controller.vm.removeDock(slot)
                })
            }
        }
    }
}

@Composable
private fun DragGhost(controller: HomeController, byRef: Map<AppRef, AppEntry>) {
    val drag = controller.drag
    val visual = drag.visual ?: return
    val density = LocalDensity.current
    val size = with(density) { DpSize(drag.itemSize.width.toDp(), drag.itemSize.height.toDp()) }
    val style = controller.style
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { (drag.pointer - drag.grabOffset).let { IntOffset(it.x.toInt(), it.y.toInt()) } }
            .size(size)
            .alpha(0.85f),
    ) {
        when (visual) {
            is DragVisual.App ->
                AppIconView(byRef[visual.app], controller.vm.iconCache, style.iconSize, style.shape, null, labelSizeSp = null)
            is DragVisual.Shortcut -> ShortcutIconView(visual.shortcut, controller, style.iconSize, labelSizeSp = null)
            is DragVisual.Folder -> FolderIconView(
                visual.folder, byRef, controller.vm.iconCache, style.iconSize, style.shape, null, labelSizeSp = null,
            )
            is DragVisual.Widget -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0x99FFFFFF)),
            ) { Text(visual.label, color = Color.Black) }
        }
    }
}

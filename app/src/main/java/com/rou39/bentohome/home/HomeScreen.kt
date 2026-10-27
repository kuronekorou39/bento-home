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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
import kotlin.math.abs
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val DOCK_HEIGHT = 88.dp
private val PAGE_INDICATOR_HEIGHT = 28.dp

/** ドックを隠しているときの、ページの縮小表示の帯の高さ。 */
private val STRIP_HEIGHT_NO_DOCK = 96.dp

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
    homeResetToFirstPage: Boolean,
    onRequestRole: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    /** 具材にする画像を端末から選ぶ。Activity の結果受け取りが要るので HomeActivity が用意する。 */
    onPickGarnishImage: () -> Unit,
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
    controller.loopPages = settings.loopPages
    controller.style = settings.iconStyle()
    controller.disguiseExempt = remember(appearances) { appearances.filterValues { it.disguiseExempt }.keys }
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
    // 置き先が変わったときだけ計算し直す（押し出しの計算を含む）
    val preview by remember {
        derivedStateOf { drag.source?.let { vm.previewDrop(it, target, drag.spanX, drag.spanY) } }
    }
    val targetValid = preview?.valid ?: false
    val hoveredStripPage by remember { derivedStateOf { (target as? DropTarget.Page)?.pageIndex } }
    // 一覧で「ホームにある」印を付けるのに使う
    val placements = remember(workspace) { homePlacements(workspace) }
    val pageAspect = geometry.gridArea.let { if (it.height > 0f) it.width / it.height else DEFAULT_PAGE_ASPECT }

    LaunchedEffect(homeResetCount) { controller.resetForHome(toFirstPage = homeResetToFirstPage) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            vm.currentPageIndex = it
            controller.resizingItemId = null
        }
    }
    LaunchedEffect(vm) { vm.focusPage.collect { pagerState.animateScrollToPage(it) } }
    LaunchedEffect(vm) { vm.pulse.collect(controller::pulse) }
    // 消えた項目は選択から外す
    LaunchedEffect(workspace) {
        val ids = workspace.items.mapTo(HashSet()) { it.id }
        controller.selectedItems.retainAll(ids)
    }
    LaunchedEffect(settings.layoutLocked) { if (settings.layoutLocked) controller.clearItemSelection() }
    EdgeFlipEffect(controller)
    PageJumpEffect(controller, hoveredStripPage)
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
    BackHandler(enabled = controller.selectingItems) { controller.clearItemSelection() }
    BackHandler(enabled = controller.moveOverviewOpen) { controller.moveOverviewOpen = false }
    BackHandler(enabled = controller.pageManagerOpen) { controller.pageManagerOpen = false }
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
                    modifier = Modifier.fillMaxSize().loopAtEdges(controller),
                ) { index ->
                    val page = pages.getOrNull(index)
                    // 見えていないページ・一覧の下のウィジェットは View を非表示にして、提供元のアニメーションを止める
                    val pageVisible by remember(index) {
                        derivedStateOf { pagerState.layoutInfo.visiblePagesInfo.any { it.index == index } }
                    }
                    val onThisPage = (target as? DropTarget.Cell)?.pageIndex == index
                    val highlights = when (val t = target) {
                        is DropTarget.Cell -> if (onThisPage) preview?.rects.orEmpty() else emptyList()
                        // まとめ先は、その項目の範囲を示す
                        is DropTarget.Merge -> listOfNotNull(workspace.item(t.itemId)?.rect?.takeIf { t.pageIndex == index })
                        else -> emptyList()
                    }
                    WorkspacePage(
                        controller = controller,
                        workspace = workspace,
                        page = page,
                        byRef = byRef,
                        hostIds = hostIds,
                        badgeFor = badgeFor,
                        grayFor = homeGrayFor,
                        highlights = highlights,
                        highlightValid = targetValid,
                        displaced = if (onThisPage && targetValid) preview?.displaced.orEmpty() else emptyList(),
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
                placements = placements,
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
            HomeOverview(
                controller = controller,
                workspace = workspace,
                byRef = byRef,
                title = "${controller.selectedApps.size}個をどのページに置きますか",
                aspect = pageAspect,
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

        if (controller.selectingItems && !drag.active) {
            HomeSelectionBar(controller, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
        }
        if (controller.showsPageStrip) {
            PageStrip(
                controller, workspace, byRef, pageAspect, hoveredStripPage,
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .height(if (settings.dockVisible) DOCK_HEIGHT + PAGE_INDICATOR_HEIGHT else STRIP_HEIGHT_NO_DOCK),
            )
        }
        if (controller.moveOverviewOpen) {
            HomeOverview(
                controller = controller,
                workspace = workspace,
                byRef = byRef,
                title = "${controller.selectedItems.size}個をどのページへ移しますか",
                aspect = pageAspect,
                onPick = { pageIndex ->
                    vm.moveItemsToPage(controller.selectedItems.toList(), pageIndex)
                    controller.clearItemSelection()
                },
                onDismiss = { controller.moveOverviewOpen = false },
            )
        }

        DragGhost(controller, byRef)

        controller.backgroundMenuAt?.let { at ->
            Box(Modifier.offset { at.round() }) {
                BentoMenu(expanded = true, onDismissRequest = { controller.backgroundMenuAt = null }) {
                    if (!controller.locked) {
                        BentoMenuItem("ウィジェットを追加", MenuSymbol.WIDGET, onClick = {
                            controller.backgroundMenuAt = null
                            onOpenWidgetPicker()
                        })
                        BentoMenuItem("具材を置く", MenuSymbol.GARNISH, onClick = {
                            controller.backgroundMenuAt = null
                            controller.garnishPickerOpen = true
                        })
                        if (workspace.items.isNotEmpty()) {
                            BentoMenuItem("選択して移動", MenuSymbol.SELECT, onClick = { controller.startItemSelection() })
                        }
                    }
                    if (pages.size >= 2) {
                        BentoMenuItem("ページの整理", MenuSymbol.PAGES, onClick = {
                            controller.backgroundMenuAt = null
                            controller.pageManagerOpen = true
                        })
                    }
                    BentoMenuItem("仕切り", MenuSymbol.PARTITION, onClick = {
                        controller.backgroundMenuAt = null
                        controller.partitionPickerOpen = true
                    })
                    if (undo != null) {
                        BentoMenuItem("直前の操作を元に戻す", MenuSymbol.UNDO, onClick = {
                            controller.backgroundMenuAt = null
                            vm.undo()
                        })
                    }
                    BentoMenuItem(
                        if (controller.locked) "配置ロックを解除" else "配置をロック",
                        if (controller.locked) MenuSymbol.UNLOCK else MenuSymbol.LOCK,
                        onClick = {
                            controller.backgroundMenuAt = null
                            vm.setLayoutLocked(!controller.locked)
                        },
                    )
                    BentoMenuItem("すべてのアプリ", MenuSymbol.ALL_APPS, onClick = controller::openDrawer)
                    BentoMenuItem("システムモニター", MenuSymbol.MONITOR, onClick = {
                        controller.backgroundMenuAt = null
                        onOpenMonitor()
                    })
                    BentoMenuItem("設定", MenuSymbol.SETTINGS, onClick = {
                        controller.backgroundMenuAt = null
                        onOpenSettings()
                    })
                }
            }
        }

        if (controller.pageManagerOpen) {
            PageManager(
                controller = controller,
                workspace = workspace,
                byRef = byRef,
                aspect = pageAspect,
                onDismiss = { controller.pageManagerOpen = false },
            )
        }

        if (controller.garnishPickerOpen) {
            GarnishPicker(
                onPick = { kind ->
                    controller.garnishPickerOpen = false
                    vm.addGarnish(kind.name)
                },
                onPickImage = {
                    controller.garnishPickerOpen = false
                    onPickGarnishImage()
                },
                onDismiss = { controller.garnishPickerOpen = false },
            )
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

/**
 * ループの設定がオンのとき、端のページで外へめくったら反対の端へ移る（plan.md §10）。
 * 指の動きを見るだけで消費しないので、ページャーの端の伸び縮みの表示はそのまま出る。
 */
private fun Modifier.loopAtEdges(controller: HomeController): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pager = controller.pagerState
            if (!controller.loopPages || controller.drag.active || pager.pageCount < 2) return@awaitEachGesture
            if (pager.currentPageOffsetFraction != 0f) return@awaitEachGesture
            val atFirst = pager.currentPage == 0
            val atLast = pager.currentPage == pager.pageCount - 1
            if (!atFirst && !atLast) return@awaitEachGesture
            var total = Offset.Zero
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                total += change.position - change.previousPosition
            }
            if (controller.drag.active || abs(total.x) < abs(total.y)) return@awaitEachGesture
            val threshold = LOOP_THRESHOLD_PX
            when {
                atLast && total.x < -threshold -> controller.wrapPage(forward = true)
                atFirst && total.x > threshold -> controller.wrapPage(forward = false)
            }
        }
    },
)

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
    highlights: List<CellRect>,
    highlightValid: Boolean,
    /** 置くと押し出される項目（動いた後の位置）。元の位置は薄くし、動いた先に薄く示す。 */
    displaced: List<WorkspaceItem>,
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
            val displacedIds = displaced.mapTo(HashSet()) { it.id }
            // 具材は先に描いて、はみ出した絵がアイコンやウィジェットの下に回るようにする
            for (item in workspace.itemsOn(page.id).sortedBy { it.content !is ItemContent.Garnish }) {
                key(item.id) {
                    val dragged = when (val source = controller.drag.source) {
                        is DragSource.Item -> source.itemId == item.id
                        is DragSource.Items -> item.id in source.ids
                        else -> false
                    }
                    val faded = dragged || item.id in displacedIds
                    Box(Modifier.cellPlacement(item.rect, cellW, cellH).alpha(if (faded) 0.3f else 1f)) {
                        when (val content = item.content) {
                            is ItemContent.App ->
                                HomeAppCell(controller, item, content.app, byRef[content.app], iconSize, badgeFor, grayFor)
                            is ItemContent.Widget ->
                                HomeWidgetCell(controller, item, content, content.widgetId in hostIds, widgetsVisible)
                            is ItemContent.Folder ->
                                HomeFolderCell(controller, item, content, byRef, iconSize, badgeFor)
                            is ItemContent.Shortcut -> HomeShortcutCell(controller, item, content, iconSize)
                            is ItemContent.Garnish -> HomeGarnishCell(controller, item, content)
                        }
                        if (controller.selectingItems && item.id in controller.selectedItems) SelectedMark()
                        if (item.id in controller.pulseIds) PulseRing(controller.pulseToken)
                    }
                }
            }
            val resizing = controller.resizingItemId?.let { workspace.item(it) }?.takeIf { it.pageId == page.id }
            when (val content = resizing?.content) {
                is ItemContent.Widget -> {
                    val info = remember(content.widgetId) { vm.widgetHost.info(content.widgetId) }
                    if (info != null) {
                        ResizeFrame(controller, resizing, vm.resizeLimits(info), info.resizeMode, cellW, cellH, byRef)
                    }
                }
                // 具材は1マスからページいっぱいまで、縦横どちらにも変えられる
                is ItemContent.Garnish -> ResizeFrame(
                    controller, resizing,
                    ResizeLimits(grid.subdivisions, grid.subdivisions, grid.columns, grid.rows),
                    AppWidgetProviderInfo.RESIZE_BOTH, cellW, cellH, byRef,
                )
                else -> Unit
            }
        }

        for (item in displaced) DisplacedGhost(controller, item, byRef, cellW, cellH)
        // 置き先の枠は項目より上に描く（ウィジェットに隠れないように）
        for (rect in highlights) {
            Box(
                Modifier
                    .cellPlacement(rect, cellW, cellH)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (highlightValid) OK_COLOR else NG_COLOR),
            )
        }
    }
}

/** 押し出された項目の、動いた後の位置。点線の代わりに薄い枠と薄いアイコンで示す。 */
@Composable
private fun DisplacedGhost(
    controller: HomeController,
    item: WorkspaceItem,
    byRef: Map<AppRef, AppEntry>,
    cellW: Dp,
    cellH: Dp,
) {
    val w = cellW * item.rect.width
    val h = cellH * item.rect.height
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .cellPlacement(item.rect, cellW, cellH)
            .padding(2.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .alpha(GHOST_ALPHA),
    ) {
        MiniItem(controller, item, byRef, min(w, h) * GHOST_ICON_RATIO)
    }
}

private const val GHOST_ALPHA = 0.55f
private const val GHOST_ICON_RATIO = 0.6f

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

/** 提供元の指定で、どちらかの方向に大きさを変えられるか。 */
private fun resizable(info: AppWidgetProviderInfo, limits: ResizeLimits): Boolean =
    (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && limits.widthAdjustable) ||
        (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0 && limits.heightAdjustable)

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
    byRef: Map<AppRef, AppEntry>,
) {
    val vm = controller.vm
    val grid = vm.currentGrid()
    val sub = grid.subdivisions
    val density = LocalDensity.current
    val stepW = with(density) { cellW.toPx() } * sub
    val stepH = with(density) { cellH.toPx() } * sub
    var preview by remember(item.rect) { mutableStateOf(item.rect) }
    // 重なる項目は押し出す。押し出す先が無い大きさだけ赤にする
    val result = remember(preview, item.rect) { if (preview == item.rect) null else vm.previewResize(item.id, preview) }
    val valid = result?.valid ?: true
    if (valid) result?.displaced?.forEach { DisplacedGhost(controller, it, byRef, cellW, cellH) }

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
        // 提供元が許していても、最小と最大が同じ方向はつまみを出さない（引いても変わらないため）
        val horizontal = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && limits.widthAdjustable
        val vertical = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0 && limits.heightAdjustable
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
            // この辺で広げられるか・狭められるか。どちらもできない辺は触れないようにする
            val (grow, shrink) = edgeMoves(edge, item.rect, limits, grid)
            val sideways = edge == Edge.LEFT || edge == Edge.RIGHT
            Box(
                contentAlignment = align,
                modifier = Modifier
                    .align(align)
                    .size(if (sideways) DpSize(HANDLE_TOUCH_DEPTH, HANDLE_TOUCH_LENGTH) else DpSize(HANDLE_TOUCH_LENGTH, HANDLE_TOUCH_DEPTH))
                    .pointerInput(edge, item.rect, grow || shrink) {
                        if (!grow && !shrink) return@pointerInput
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
            ) {
                ResizeHandle(edge, grow, shrink)
            }
        }
    }
}

/** 辺を外へ動かして広げられるか（first）、内へ動かして狭められるか（second）。 */
private fun edgeMoves(edge: Edge, rect: CellRect, limits: ResizeLimits, grid: GridSpec): Pair<Boolean, Boolean> = when (edge) {
    Edge.LEFT -> (rect.width < limits.maxWidth && rect.x > 0) to (rect.width > limits.minWidth)
    Edge.RIGHT -> (rect.width < limits.maxWidth && rect.right < grid.columns) to (rect.width > limits.minWidth)
    Edge.TOP -> (rect.height < limits.maxHeight && rect.y > 0) to (rect.height > limits.minHeight)
    Edge.BOTTOM -> (rect.height < limits.maxHeight && rect.bottom < grid.rows) to (rect.height > limits.minHeight)
}

private val HANDLE_RADIUS = 20.dp
private val HANDLE_TOUCH_DEPTH = 28.dp
private val HANDLE_TOUCH_LENGTH = 52.dp
private val HANDLE_COLOR = Color.White.copy(alpha = 0.92f)
private val HANDLE_DISABLED_COLOR = Color.White.copy(alpha = 0.25f)
private val HANDLE_ARROW_COLOR = Color.Black.copy(alpha = 0.45f)

/**
 * リサイズのつまみ。平らな側を枠の辺に付けた半円で、中に動かせる向きを薄い矢印で示す。
 * 外向きの矢印は広げられる、内向きは狭められる。どちらもできない辺は薄くして矢印を出さない。
 */
@Composable
private fun ResizeHandle(edge: Edge, grow: Boolean, shrink: Boolean) {
    val sideways = edge == Edge.LEFT || edge == Edge.RIGHT
    val size = if (sideways) DpSize(HANDLE_RADIUS, HANDLE_RADIUS * 2) else DpSize(HANDLE_RADIUS * 2, HANDLE_RADIUS)
    Canvas(Modifier.size(size)) {
        val r = HANDLE_RADIUS.toPx()
        val w = this.size.width
        val h = this.size.height
        // 半円の中心（枠の辺の上）・描き始めの角度・外向きの向き
        val (center, startAngle, out) = when (edge) {
            Edge.LEFT -> Triple(Offset(0f, h / 2), -90f, Offset(-1f, 0f))
            Edge.RIGHT -> Triple(Offset(w, h / 2), 90f, Offset(1f, 0f))
            Edge.TOP -> Triple(Offset(w / 2, 0f), 0f, Offset(0f, -1f))
            Edge.BOTTOM -> Triple(Offset(w / 2, h), 180f, Offset(0f, 1f))
        }
        val enabled = grow || shrink
        drawArc(
            color = if (enabled) HANDLE_COLOR else HANDLE_DISABLED_COLOR,
            startAngle = startAngle,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = center - Offset(r, r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        )
        if (!enabled) return@Canvas
        val mid = center - out * (r * 0.5f)
        val half = r * 0.4f
        val inner = mid - out * half
        val outer = mid + out * half
        val head = r * 0.26f
        val across = Offset(-out.y, out.x)
        val stroke = 2.dp.toPx()
        drawLine(HANDLE_ARROW_COLOR, inner, outer, stroke)
        if (grow) {
            drawLine(HANDLE_ARROW_COLOR, outer, outer - out * head + across * head, stroke)
            drawLine(HANDLE_ARROW_COLOR, outer, outer - out * head - across * head, stroke)
        }
        if (shrink) {
            drawLine(HANDLE_ARROW_COLOR, inner, inner + out * head + across * head, stroke)
            drawLine(HANDLE_ARROW_COLOR, inner, inner + out * head - across * head, stroke)
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
            disguise = controller.disguiseFor(app, controller.isHeld(DragSource.Item(item.id), menuOpen)),
        )
        if (controller.isHeld(DragSource.Item(item.id), menuOpen)) HoldHighlight()
        BentoMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuItems(controller, entry, close = { menuOpen = false }, gray = gray)
            SelectMenuItem(controller, item.id, close = { menuOpen = false })
            if (!controller.locked) {
                BentoMenuItem("ホームから削除", MenuSymbol.REMOVE, onClick = {
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
        BentoMenuNote(gray.describe())
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
    BentoMenuItem("アプリ情報", MenuSymbol.INFO, onClick = {
        close()
        catalog.openAppInfo(entry, null)
    })
    if (!entry.isSystemApp) {
        BentoMenuItem("アンインストール", MenuSymbol.UNINSTALL, onClick = {
            close()
            context.startActivity(catalog.uninstallIntent(entry))
        })
    }
    BentoMenuItem("外観の個別設定", MenuSymbol.APPEARANCE, onClick = {
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
    val callbacks = controller.itemCallbacks(
        source = DragSource.Item(item.id),
        visual = DragVisual.Widget(label),
        spanX = item.rect.width,
        spanY = item.rect.height,
        entry = null,
        canDrag = controller.resizingItemId != item.id,
        onLongPress = { menuOpen = true },
        onDragStarted = { menuOpen = false },
    )
    // ホームで選んでいる間は、ウィジェットの中身を操作させず、アイコンと同じように選ぶ・運ぶ
    val selecting = controller.selectingItems && !controller.locked
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .then(if (selecting) Modifier else Modifier.widgetGestures(item.id, callbacks, controller.pagerDrag)),
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
        if (selecting) Box(Modifier.matchParentSize().launcherItemGestures(item.id, callbacks))
        if (controller.isHeld(DragSource.Item(item.id), menuOpen)) HoldHighlight()
        BentoMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val reconfigurable = info != null && info.configure != null &&
                info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
            if (reconfigurable) {
                BentoMenuItem("ウィジェットを設定", MenuSymbol.CONFIGURE, onClick = {
                    menuOpen = false
                    controller.onReconfigureWidget(widget.widgetId)
                })
            }
            if (info != null) {
                BentoMenuItem("負荷を測る", MenuSymbol.MEASURE, onClick = {
                    menuOpen = false
                    controller.measureWidget(item.id, widget.widgetId, label.toString())
                })
            }
            if (controller.locked) {
                BentoMenuItem("配置はロック中です", MenuSymbol.LOCK, onClick = { menuOpen = false })
            } else {
                SelectMenuItem(controller, item.id, close = { menuOpen = false })
                if (info != null) {
                    if (resizable(info, vm.resizeLimits(info))) {
                        BentoMenuItem("サイズ変更", MenuSymbol.RESIZE, onClick = {
                            menuOpen = false
                            controller.resizingItemId = item.id
                        })
                    } else {
                        BentoMenuNote("サイズは変えられません（提供元の指定）")
                    }
                }
                BentoMenuItem("ホームから削除", MenuSymbol.REMOVE, onClick = {
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
    Box(Modifier.fillMaxWidth().height(PAGE_INDICATOR_HEIGHT)) {
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
            disguise = controller.disguiseFor(app, controller.isHeld(DragSource.Dock(slot), menuOpen)),
        )
        if (controller.isHeld(DragSource.Dock(slot), menuOpen)) HoldHighlight()
        BentoMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuItems(controller, entry, close = { menuOpen = false }, gray = gray)
            if (!controller.locked) {
                BentoMenuItem("ドックから外す", MenuSymbol.REMOVE, onClick = {
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
                AppIconView(
                    byRef[visual.app], controller.vm.iconCache, style.iconSize, style.shape, null, labelSizeSp = null,
                    disguise = controller.disguiseFor(visual.app, held = true),
                )
            is DragVisual.Shortcut -> ShortcutIconView(visual.shortcut, controller, style.iconSize, labelSizeSp = null)
            is DragVisual.Garnish -> GarnishArt(visual.garnish, Modifier.fillMaxSize())
            is DragVisual.Folder -> FolderIconView(
                visual.folder, byRef, controller.vm.iconCache, style.iconSize, style.shape, null, labelSizeSp = null,
                disguiseFor = { controller.disguiseFor(it, held = true) },
            )
            is DragVisual.Widget -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0x99FFFFFF)),
            ) { Text(visual.label, color = Color.Black) }
        }
        // まとめて運んでいるときは個数を添える
        val count = (drag.source as? DragSource.Items)?.ids?.size ?: 0
        if (count > 1) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) { Text("$count", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }
        }
    }
}

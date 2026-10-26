package com.rou39.bentohome.home

import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rou39.bentohome.badge.BadgeKey
import com.rou39.bentohome.badge.BadgeRepository
import com.rou39.bentohome.badge.BadgeState
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.CellRect
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.Page
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.layout.WorkspaceItem
import kotlinx.coroutines.delay

/** アイコン画像の大きさ。設定画面は段階3で追加する。 */
private val DEFAULT_ICON_SIZE = 56.dp
private val DOCK_HEIGHT = 88.dp

/** 画面端でこの時間止まると、ドラッグ中にページを送る。 */
private const val EDGE_FLIP_DELAY_MS = 600L

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    roleHeld: Boolean,
    homeResetCount: Int,
    onRequestRole: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ws by vm.workspace.collectAsState()
    val settings by vm.settings.collectAsState()
    val apps by vm.catalog.apps.collectAsState()
    val byRef by vm.catalog.byRef.collectAsState()
    val badgeState by BadgeRepository.state.collectAsState()
    val undo by vm.undo.collectAsState()

    val workspace = ws ?: return
    val pages = workspace.orderedPages
    val drag = remember { DragState() }
    val geometry = remember { HomeGeometry() }
    val extraPage = drag.active
    val pagerState = rememberPagerState { pages.size + if (extraPage) 1 else 0 }
    val scope = rememberCoroutineScope()
    val controller = remember { HomeController(vm, drag, geometry, pagerState, scope) }
    controller.locked = settings.layoutLocked
    controller.dockVisible = settings.dockVisible

    val badgeFor: (AppEntry?) -> Int? = { entry ->
        entry?.let { (badgeState as? BadgeState.Counts)?.counts?.get(BadgeKey(it.packageName, it.ref.profileSerial)) }
    }
    val hostIds = remember(workspace) { vm.widgetHost.hostIds() }

    val wsState = rememberUpdatedState(workspace)
    val target by remember { derivedStateOf { controller.computeTarget(wsState.value) } }
    val targetValid by remember {
        derivedStateOf { drag.source?.let { vm.canDrop(it, target) } ?: false }
    }

    LaunchedEffect(homeResetCount) { controller.resetForHome() }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }.collect { vm.currentPageIndex = it }
    }
    EdgeFlipEffect(controller)
    WallpaperScrollEffect(pagerState)

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undo?.token) {
        val message = undo?.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = "元に戻す", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undo()
    }

    // ホームでの戻る操作は何もしない。開いているものだけ閉じる。
    BackHandler(enabled = true) {}
    BackHandler(enabled = controller.drawerOpen) { controller.drawerOpen = false }
    BackHandler(enabled = controller.backgroundMenuAt != null) { controller.backgroundMenuAt = null }
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
                    .pointerInputSwipeUp(swipeThreshold) { controller.openDrawer() },
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = pages.size + 1,
                    key = { index -> pages.getOrNull(index)?.id ?: -1L },
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val page = pages.getOrNull(index)
                    val highlight = (target as? DropTarget.Cell)?.takeIf { it.pageIndex == index }
                        ?.let { CellRect(it.x, it.y, drag.spanX, drag.spanY) }
                    WorkspacePage(
                        controller = controller,
                        workspace = workspace,
                        page = page,
                        byRef = byRef,
                        hostIds = hostIds,
                        badgeFor = badgeFor,
                        highlight = highlight,
                        highlightValid = targetValid,
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
                    highlightSlot = (target as? DropTarget.Dock)?.slot,
                    highlightValid = targetValid,
                )
            }
        }

        if (controller.drawerOpen) {
            AppDrawer(
                controller = controller,
                apps = apps,
                badgeFor = badgeFor,
                // ドラッグ中は透明にするだけで残す。外すと指の操作が途切れる。
                modifier = Modifier.alpha(if (drag.fromDrawer) 0f else 1f),
            )
        }

        DragGhost(drag, byRef, vm)

        controller.backgroundMenuAt?.let { at ->
            Box(Modifier.offset { at.round() }) {
                DropdownMenu(expanded = true, onDismissRequest = { controller.backgroundMenuAt = null }) {
                    if (!controller.locked) {
                        DropdownMenuItem(text = { Text("ウィジェットを追加") }, onClick = {
                            controller.backgroundMenuAt = null
                            onOpenWidgetPicker()
                        })
                    }
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
                    DropdownMenuItem(text = { Text("設定") }, onClick = {
                        controller.backgroundMenuAt = null
                        onOpenSettings()
                    })
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

private fun Modifier.pointerInputSwipeUp(threshold: Float, onSwipeUp: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total < -threshold) onSwipeUp() },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    total += dy
                },
            )
        },
    )

/** ページ送りに合わせて壁紙を横に動かす（docs/design.md §6）。 */
@Composable
private fun WallpaperScrollEffect(pagerState: androidx.compose.foundation.pager.PagerState) {
    val view = androidx.compose.ui.platform.LocalView.current
    val wallpaper = remember { android.app.WallpaperManager.getInstance(view.context) }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow {
            pagerState.pageCount to (pagerState.currentPage + pagerState.currentPageOffsetFraction)
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
    badgeFor: (AppEntry?) -> Int?,
    highlight: CellRect?,
    highlightValid: Boolean,
) {
    val vm = controller.vm
    val grid = page?.grid ?: vm.defaultGrid
    var pageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { pageCoords = it }) {
        val cellW = maxWidth / grid.columns
        val cellH = maxHeight / grid.rows
        val density = LocalDensity.current
        LaunchedEffect(cellW, cellH) {
            with(density) { vm.baseCellSizePx = cellW.toPx() * grid.subdivisions to cellH.toPx() * grid.subdivisions }
        }
        val iconSize = min(DEFAULT_ICON_SIZE, min(cellW * grid.iconSpan, cellH * grid.iconSpan - 24.dp))

        // 背景: 項目の無い場所の長押しでメニュー
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onLongPress = { local ->
                    pageCoords?.let { controller.backgroundMenuAt = it.localToRoot(local) }
                })
            },
        )

        if (page != null) {
            for (item in workspace.itemsOn(page.id)) {
                androidx.compose.runtime.key(item.id) {
                    val dragged = (controller.drag.source as? DragSource.Item)?.itemId == item.id
                    Box(Modifier.cellPlacement(item.rect, cellW, cellH).alpha(if (dragged) 0.3f else 1f)) {
                        when (val content = item.content) {
                            is ItemContent.App -> HomeAppCell(controller, item, content.app, byRef[content.app], iconSize, badgeFor)
                            is ItemContent.Widget -> HomeWidgetCell(controller, item, content, content.widgetId in hostIds)
                        }
                    }
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
                    .background(if (highlightValid) Color(0x5534C759) else Color(0x55FF3B30)),
            )
        }
    }
}

private fun Modifier.cellPlacement(rect: CellRect, cellW: Dp, cellH: Dp): Modifier =
    this.offset(x = cellW * rect.x, y = cellH * rect.y).size(cellW * rect.width, cellH * rect.height)

@Composable
private fun HomeAppCell(
    controller: HomeController,
    item: WorkspaceItem,
    app: AppRef,
    entry: AppEntry?,
    iconSize: Dp,
    badgeFor: (AppEntry?) -> Int?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
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
        AppIconView(entry, controller.vm.iconCache, iconSize, badgeFor(entry))
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuItems(controller, entry, close = { menuOpen = false })
            if (!controller.locked) {
                DropdownMenuItem(text = { Text("ホームから削除") }, onClick = {
                    menuOpen = false
                    controller.vm.removeItem(item.id)
                })
            }
        }
    }
}

/** アプリ情報・アンインストール。ホーム・ドック・一覧で共通。 */
@Composable
fun AppMenuItems(controller: HomeController, entry: AppEntry?, close: () -> Unit) {
    val context = LocalContext.current
    if (entry == null) return
    DropdownMenuItem(text = { Text("アプリ情報") }, onClick = {
        close()
        controller.vm.catalog.openAppInfo(entry, null)
    })
    if (!entry.isSystemApp) {
        DropdownMenuItem(text = { Text("アンインストール") }, onClick = {
            close()
            context.startActivity(controller.vm.catalog.uninstallIntent(entry))
        })
    }
}

@Composable
private fun HomeWidgetCell(controller: HomeController, item: WorkspaceItem, widget: ItemContent.Widget, onHost: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val widgetHost = controller.vm.widgetHost
    val info = remember(widget.widgetId, onHost) { if (onHost) widgetHost.info(widget.widgetId) else null }
    val label = remember(info) {
        info?.loadLabel(controller.vm.getApplication<android.app.Application>().packageManager) ?: "ウィジェット"
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
                    canDrag = true,
                    onLongPress = { menuOpen = true },
                    onDragStarted = { menuOpen = false },
                ),
                controller.pagerDrag,
            ),
    ) {
        if (info == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0xAA303030)),
            ) {
                Text("ウィジェットの再追加が必要", color = Color.White, fontSize = 12.sp)
            }
        } else {
            WidgetHostSurface(controller, widget.widgetId)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (controller.locked) {
                DropdownMenuItem(text = { Text("配置はロック中です") }, onClick = { menuOpen = false })
            } else {
                DropdownMenuItem(text = { Text("ホームから削除") }, onClick = {
                    menuOpen = false
                    controller.vm.removeItem(item.id)
                })
            }
        }
    }
}

@Composable
private fun WidgetHostSurface(controller: HomeController, widgetId: Int) {
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
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, onOpenDrawer: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(28.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
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
                .clickable(onClick = onOpenDrawer)
                .padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun DockBar(
    controller: HomeController,
    workspace: Workspace,
    byRef: Map<AppRef, AppEntry>,
    badgeFor: (AppEntry?) -> Int?,
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
                            highlightValid -> Color(0x5534C759)
                            else -> Color(0x55FF3B30)
                        },
                    ),
            ) {
                if (dockSlot != null) DockAppCell(controller, slot, dockSlot.app, byRef[dockSlot.app], badgeFor)
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
    badgeFor: (AppEntry?) -> Int?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val span = controller.vm.defaultGrid.iconSpan
    val dragged = (controller.drag.source as? DragSource.Dock)?.slot == slot
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
        AppIconView(entry, controller.vm.iconCache, DEFAULT_ICON_SIZE, badgeFor(entry), showLabel = false)
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuItems(controller, entry, close = { menuOpen = false })
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
private fun DragGhost(drag: DragState, byRef: Map<AppRef, AppEntry>, vm: HomeViewModel) {
    val visual = drag.visual ?: return
    val density = LocalDensity.current
    val size = with(density) { androidx.compose.ui.unit.DpSize(drag.itemSize.width.toDp(), drag.itemSize.height.toDp()) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { (drag.pointer - drag.grabOffset).let { IntOffset(it.x.toInt(), it.y.toInt()) } }
            .size(size)
            .alpha(0.85f),
    ) {
        when (visual) {
            is DragVisual.App -> AppIconView(byRef[visual.app], vm.iconCache, DEFAULT_ICON_SIZE, null, showLabel = false)
            is DragVisual.Widget -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0x99FFFFFF)),
            ) { Text(visual.label.toString(), color = Color.Black) }
        }
    }
}

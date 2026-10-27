package com.rou39.bentohome.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.data.DrawerSort
import com.rou39.bentohome.usage.GrayReason
import com.rou39.bentohome.usage.RankInput
import com.rou39.bentohome.usage.UsagePolicy

private val DRAWER_ICON_SIZE = 52.dp
private val SELECTED_COLOR = Color.White.copy(alpha = 0.16f)
private const val HIDDEN_ALPHA = 0.45f

/**
 * アプリ一覧（docs/design.md §13）。検索は一覧の中だけで使い、自動でフォーカスしない。
 * 並び順は一覧を開いた時点（または並び順を切り替えた時点）で固定し、開いている間に項目が動かないようにする。
 * インストールされたアプリは末尾に足し、削除されたアプリは外すだけにする。
 */
@Composable
fun AppDrawer(
    controller: HomeController,
    apps: List<AppEntry>,
    /** アプリ一覧に出さないアプリ（plan.md §10）。下のボタンで一時的に表示できる。 */
    hidden: Set<AppRef>,
    /**
     * ホームが2ページ以上か。そのときは長押しでつままずに選択し、まとめて「ホームに追加」する（plan.md §10）。
     * 運びながらページ送りを待たなくて済むように。
     */
    multiPage: Boolean,
    badgeFor: (AppEntry?) -> BadgeDisplay?,
    grayFor: (AppEntry?) -> GrayReason?,
    sort: DrawerSort,
    rankInputs: (List<AppEntry>) -> List<RankInput>,
    modifier: Modifier = Modifier,
) {
    val query = controller.query
    val selectMode = multiPage && !controller.locked
    // 一覧を閉じたら選択を捨てる
    DisposableEffect(Unit) { onDispose { controller.clearSelection() } }
    var showHidden by remember { mutableStateOf(false) }
    val hiddenCount = apps.count { it.ref in hidden }
    val listed = remember(apps, hidden, showHidden) { if (showHidden) apps else apps.filter { it.ref !in hidden } }
    // remember の計算は並び順を切り替えたときだけ走る。その時点の使用記録で順番を決める
    val frozenOrder = remember(sort) {
        when (sort) {
            DrawerSort.NAME -> listed.map { it.ref }
            DrawerSort.FREQUENT -> UsagePolicy.sortByFrequency(rankInputs(listed), controller.vm.today())
            DrawerSort.RECENT -> UsagePolicy.sortByRecent(rankInputs(listed))
        }
    }
    val ordered = remember(frozenOrder, listed) {
        val byRef = listed.associateBy { it.ref }
        val known = frozenOrder.toHashSet()
        frozenOrder.mapNotNull(byRef::get) + listed.filter { it.ref !in known }
    }
    // 表示名と元の名前のどちらでも見つかるようにする
    val filtered = remember(ordered, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            ordered
        } else {
            ordered.filter {
                it.label.contains(q, ignoreCase = true) || it.originalLabel.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
    }
    val closeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    // 一覧の先頭でさらに下へ引くと閉じる
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > closeThreshold) {
                        pulled = 0f
                        controller.drawerOpen = false
                    }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color(0xF2121216))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > closeThreshold) controller.drawerOpen = false },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            total += dy
                        },
                    )
                },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { controller.query = it },
                placeholder = { Text("アプリを検索") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { controller.drawerOpen = false }) { Text("閉じる") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            // 大きな文字でも折り返さないよう、横にスクロールできるようにする
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            for (option in DrawerSort.entries) {
                FilterChip(
                    selected = option == sort,
                    onClick = { controller.vm.setDrawerSort(option) },
                    label = { Text(option.label, fontSize = 12.sp) },
                )
            }
            Text("${filtered.size} 件", color = Color.LightGray, fontSize = 12.sp, maxLines = 1, softWrap = false)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(pullToClose),
        ) {
            items(filtered, key = { "${it.ref.component}#${it.ref.profileSerial}" }) { entry ->
                DrawerTile(
                    controller, entry, badgeFor(entry), grayFor(entry),
                    isHidden = entry.ref in hidden,
                    selectMode = selectMode,
                    selected = entry.ref in controller.selectedApps,
                )
            }
            if (hiddenCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { showHidden = !showHidden }, modifier = Modifier.padding(8.dp)) {
                        Text(if (showHidden) "非表示のアプリを隠す" else "非表示のアプリ ${hiddenCount}個を表示")
                    }
                }
            }
        }
        if (controller.selectedApps.isNotEmpty()) SelectionBar(controller)
    }
}

@Composable
private fun DrawerTile(
    controller: HomeController,
    entry: AppEntry,
    badge: BadgeDisplay?,
    gray: GrayReason?,
    isHidden: Boolean,
    selectMode: Boolean,
    selected: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    // 選択中は「その他」からメニューを開く
    LaunchedEffect(controller.drawerMenuRef) { if (controller.drawerMenuRef == entry.ref) menuOpen = true }
    val selecting = controller.selectedApps.isNotEmpty()
    val span = controller.vm.currentGrid().iconSpan
    val style = controller.style
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            // 一時的に表示している非表示のアプリは薄くする
            .alpha(if (isHidden) HIDDEN_ALPHA else 1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) SELECTED_COLOR else Color.Transparent)
            .semantics { if (selectMode) stateDescription = if (selected) "選択中" else "未選択" }
            .launcherItemGestures(
                entry.ref,
                controller.itemCallbacks(
                    source = DragSource.NewApp(entry.ref),
                    visual = DragVisual.App(entry.ref),
                    spanX = span,
                    spanY = span,
                    entry = entry,
                    canDrag = !selectMode,
                    onLongPress = { if (selectMode) controller.toggleSelected(entry.ref) else menuOpen = true },
                    onDragStarted = { menuOpen = false },
                    fromDrawer = true,
                    onTap = if (selecting) {
                        { _ -> controller.toggleSelected(entry.ref) }
                    } else {
                        null
                    },
                ),
            ),
    ) {
        AppIconView(
            entry, controller.vm.iconCache, DRAWER_ICON_SIZE, style.shape, badge,
            labelSizeSp = style.labelSizeSp,
            modifier = Modifier.padding(horizontal = 4.dp),
            gray = gray != null,
        )
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) { Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = {
                menuOpen = false
                controller.drawerMenuRef = null
            },
        ) {
            if (!controller.locked && !selectMode) {
                DropdownMenuItem(text = { Text("ホームに追加") }, onClick = {
                    menuOpen = false
                    controller.vm.addAppToHome(entry.ref)
                })
            }
            AppMenuItems(controller, entry, close = { menuOpen = false }, gray = gray)
        }
    }
}

/** 選択中の操作。件数・取り消し・（1個なら）その他・ホームに追加。 */
@Composable
private fun SelectionBar(controller: HomeController) {
    val count = controller.selectedApps.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF26262C))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("${count}個を選択中", color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = controller::clearSelection) { Text("キャンセル") }
        if (count == 1) TextButton(onClick = { controller.drawerMenuRef = controller.selectedApps.single() }) { Text("その他") }
        Button(onClick = { controller.overviewOpen = true }) { Text("ホームに追加") }
    }
}

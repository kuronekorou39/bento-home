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
import com.rou39.bentohome.catalog.AppEntry

private val DRAWER_ICON_SIZE = 52.dp

/**
 * アプリ一覧（docs/design.md §13）。検索は一覧の中だけで使い、自動でフォーカスしない。
 * 並び順は段階2では名前順のみ。頻度順・最近順は段階4で追加する。
 */
@Composable
fun AppDrawer(
    controller: HomeController,
    apps: List<AppEntry>,
    badgeFor: (AppEntry?) -> Int?,
    modifier: Modifier = Modifier,
) {
    val query = controller.query
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
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
        Text(
            "名前順・${filtered.size} 件",
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().nestedScroll(pullToClose),
        ) {
            items(filtered, key = { "${it.ref.component}#${it.ref.profileSerial}" }) { entry ->
                DrawerTile(controller, entry, badgeFor(entry))
            }
        }
    }
}

@Composable
private fun DrawerTile(controller: HomeController, entry: AppEntry, badge: Int?) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val span = controller.vm.defaultGrid.iconSpan
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .launcherItemGestures(
                entry.ref,
                controller.itemCallbacks(
                    source = DragSource.NewApp(entry.ref),
                    visual = DragVisual.App(entry.ref),
                    spanX = span,
                    spanY = span,
                    entry = entry,
                    canDrag = true,
                    onLongPress = { menuOpen = true },
                    onDragStarted = { menuOpen = false },
                    fromDrawer = true,
                ),
            ),
    ) {
        AppIconView(entry, controller.vm.iconCache, DRAWER_ICON_SIZE, badge, Modifier.padding(horizontal = 4.dp))
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!controller.locked) {
                DropdownMenuItem(text = { Text("ホームに追加") }, onClick = {
                    menuOpen = false
                    controller.vm.addAppToHome(entry.ref)
                })
            }
            AppMenuItems(controller, entry, close = { menuOpen = false })
        }
    }
}

package com.rou39.bentohome.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.Page
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.layout.WorkspaceItem

/** 縮小表示で、アイコンが占有範囲に対して占める割合。 */
private const val MINI_ICON_RATIO = 0.7f

/**
 * ホームの縮小表示（plan.md §10）。一覧で選んだアプリを置くページを選ぶ。
 * 各ページのアイコン・フォルダー・ウィジェット・仕切りを小さく描き、最後に「新しいページ」を並べる。
 * @param aspect ページの横÷縦（ホームの配置の範囲に合わせる）
 */
@Composable
fun HomeOverview(
    controller: HomeController,
    workspace: Workspace,
    byRef: Map<AppRef, AppEntry>,
    count: Int,
    aspect: Float,
    onPick: (pageIndex: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val pages = workspace.orderedPages
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            // 下の一覧に操作を通さない
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "${count}個をどのページに置きますか",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("戻る") }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                PageTile("${index + 1}ページ目", aspect, onClick = { onPick(index) }) {
                    PageMiniature(controller, workspace, page, byRef)
                }
            }
            item(key = "new") {
                PageTile("新しいページ", aspect, onClick = { onPick(null) }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("＋", color = Color.White, fontSize = 32.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTile(label: String, aspect: Float, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .semantics(mergeDescendants = true) { contentDescription = "$label に置く" }
                .clickable(onClick = onClick),
        ) { content() }
        Text(label, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 1ページを縮小して描く。操作は受けない。 */
@Composable
private fun PageMiniature(controller: HomeController, workspace: Workspace, page: Page, byRef: Map<AppRef, AppEntry>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = page.grid
        val cellW = maxWidth / grid.columns
        val cellH = maxHeight / grid.rows
        PartitionLayer(page.partition, maxWidth, maxHeight)
        for (item in workspace.itemsOn(page.id)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = cellW * item.rect.x, y = cellH * item.rect.y)
                    .size(cellW * item.rect.width, cellH * item.rect.height),
            ) {
                val iconSize = minOf(cellW * item.rect.width, cellH * item.rect.height) * MINI_ICON_RATIO
                MiniItem(controller, item, byRef, iconSize)
            }
        }
    }
}

@Composable
private fun MiniItem(controller: HomeController, item: WorkspaceItem, byRef: Map<AppRef, AppEntry>, iconSize: Dp) {
    val vm = controller.vm
    val shape = controller.style.shape
    when (val c = item.content) {
        is ItemContent.App -> {
            val icon = rememberAppIcon(byRef[c.app], iconSize, shape, vm.iconCache)
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
        is ItemContent.Folder -> FolderIconView(c, byRef, vm.iconCache, iconSize, shape, null, labelSizeSp = null)
        is ItemContent.Shortcut -> ShortcutIconView(c, controller, iconSize, labelSizeSp = null)
        is ItemContent.Widget -> {
            val label = remember(c.widgetId) {
                vm.widgetHost.info(c.widgetId)?.loadLabel(vm.getApplication<android.app.Application>().packageManager)
                    ?: "ウィジェット"
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * ページの整理（plan.md §10）。縮小したページを並べ、◀▶で順番を入れ替える。空のページは削除できる。
 * 変更はすぐ反映し、取り消せる。
 */
@Composable
fun PageManager(
    controller: HomeController,
    workspace: Workspace,
    byRef: Map<AppRef, AppEntry>,
    aspect: Float,
    onDismiss: () -> Unit,
) {
    val vm = controller.vm
    val pages = workspace.orderedPages
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("ページの整理", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("完了") }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            // 下に「元に戻す」のお知らせが出るので、最後の行のボタンが隠れないよう余白を空ける
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    ) { PageMiniature(controller, workspace, page, byRef) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.movePage(index, index - 1) }, enabled = index > 0) {
                            Text("◀", modifier = Modifier.semantics { contentDescription = "前へ" })
                        }
                        Text((index + 1).toString(), color = Color.LightGray, fontSize = 13.sp)
                        TextButton(onClick = { vm.movePage(index, index + 1) }, enabled = index < pages.lastIndex) {
                            Text("▶", modifier = Modifier.semantics { contentDescription = "後ろへ" })
                        }
                    }
                    if (pages.size > 1 && workspace.itemsOn(page.id).isEmpty()) {
                        TextButton(onClick = { vm.removeEmptyPage(index) }) { Text("空のページを削除") }
                    }
                }
            }
        }
    }
}

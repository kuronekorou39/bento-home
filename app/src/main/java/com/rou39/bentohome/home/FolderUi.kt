package com.rou39.bentohome.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.icon.IconCache
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.WorkspaceItem
import com.rou39.bentohome.usage.GrayReason

/** フォルダー（plan.md §10）。開くと画面の中央に中身を並べる。 */

/** 1行に並べるアプリの数。 */
private const val FOLDER_COLUMNS = 4

/** 中のアプリのバッジをまとめる。件数があれば合計、点だけなら点。 */
fun folderBadge(apps: List<AppRef>, byRef: Map<AppRef, AppEntry>, badgeFor: (AppEntry?) -> BadgeDisplay?): BadgeDisplay? {
    val badges = apps.mapNotNull { badgeFor(byRef[it]) }
    val total = badges.filterIsInstance<BadgeDisplay.Count>().sumOf { it.count }
    return when {
        total > 0 -> BadgeDisplay.Count(total)
        badges.isNotEmpty() -> BadgeDisplay.Dot
        else -> null
    }
}

/** フォルダーのアイコン。角丸の下地に、先頭の4つのアプリを小さく並べる。 */
@Composable
fun FolderIconView(
    folder: ItemContent.Folder,
    byRef: Map<AppRef, AppEntry>,
    cache: IconCache,
    iconSize: Dp,
    shape: IconShape,
    badge: BadgeDisplay?,
    labelSizeSp: Int?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.semantics { contentDescription = "フォルダー ${folder.title}、${folder.apps.size}個のアプリ" },
    ) {
        Box(Modifier.size(iconSize)) {
            val miniSize = iconSize * MINI_RATIO
            val gap = iconSize * GAP_RATIO
            Column(
                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(iconSize * CORNER_RATIO))
                    .background(Color.White.copy(alpha = 0.28f)),
            ) {
                folder.apps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { app ->
                            val icon = rememberAppIcon(byRef[app], miniSize, shape, cache)
                            if (icon != null) {
                                Image(icon, contentDescription = null, modifier = Modifier.size(miniSize))
                            } else {
                                Spacer(Modifier.size(miniSize))
                            }
                        }
                    }
                }
            }
            IconBadge(badge)
        }
        if (labelSizeSp != null) IconLabel(folder.title, labelSizeSp)
    }
}

private const val MINI_RATIO = 0.36f
private const val GAP_RATIO = 0.06f
private const val CORNER_RATIO = 0.25f

/** ホームに置いたフォルダー。タップで開き、長押しでメニュー、長押しのまま動かすと移動。 */
@Composable
fun HomeFolderCell(
    controller: HomeController,
    item: WorkspaceItem,
    folder: ItemContent.Folder,
    byRef: Map<AppRef, AppEntry>,
    iconSize: Dp,
    badgeFor: (AppEntry?) -> BadgeDisplay?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val style = controller.style
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().launcherItemGestures(
            item.id,
            controller.itemCallbacks(
                source = DragSource.Item(item.id),
                visual = DragVisual.Folder(folder),
                spanX = item.rect.width,
                spanY = item.rect.height,
                entry = null,
                canDrag = true,
                onLongPress = { menuOpen = true },
                onDragStarted = { menuOpen = false },
                onTap = { controller.openFolder(item.id) },
            ),
        ),
    ) {
        FolderIconView(
            folder, byRef, controller.vm.iconCache, iconSize, style.shape,
            folderBadge(folder.apps, byRef, badgeFor),
            labelSizeSp = style.labelSizeSp.takeIf { style.labelVisible },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("開く") }, onClick = {
                menuOpen = false
                controller.openFolder(item.id)
            })
            DropdownMenuItem(text = { Text("名前を変更") }, onClick = {
                menuOpen = false
                controller.openFolder(item.id, rename = true)
            })
            if (!controller.locked) {
                DropdownMenuItem(text = { Text("ホームから削除（中のアプリも）") }, onClick = {
                    menuOpen = false
                    controller.vm.removeItem(item.id)
                })
            }
        }
    }
}

/**
 * 開いたフォルダー。外側をタップすると閉じる。
 * 中のアプリを長押しのまま動かすとホームやドックへ運べる。運んでいる間は透明にして、指の操作が途切れないよう残す。
 */
@Composable
fun FolderOverlay(
    controller: HomeController,
    item: WorkspaceItem,
    folder: ItemContent.Folder,
    byRef: Map<AppRef, AppEntry>,
    badgeFor: (AppEntry?) -> BadgeDisplay?,
    grayFor: (AppEntry?) -> GrayReason?,
) {
    val dragging = controller.drag.source is DragSource.FolderApp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (dragging) 0f else 1f)
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { controller.closeFolder() } }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF0262626))
                // 中の余白のタップで閉じないようにする
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            FolderTitle(controller, item.id, folder.title)
            folder.apps.chunked(FOLDER_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        Box(Modifier.weight(1f)) {
                            FolderAppCell(controller, item.id, app, byRef[app], badgeFor, grayFor)
                        }
                    }
                    repeat(FOLDER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FolderTitle(controller: HomeController, itemId: Long, title: String) {
    if (!controller.renamingFolder) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "名前を変更") { controller.renamingFolder = true }
                .padding(vertical = 4.dp),
        )
        return
    }
    // 開いたときは名前全体を選び、そのまま打てば置き換わるようにする
    var text by remember { mutableStateOf(TextFieldValue(title, TextRange(0, title.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // 確定キーを押さずに外側のタップや戻る操作で閉じても、入力した名前を残す
    val latest by rememberUpdatedState(text.text)
    DisposableEffect(itemId) { onDispose { controller.vm.renameFolder(itemId, latest) } }
    fun done() {
        controller.renamingFolder = false
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        label = { Text("フォルダーの名前") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { done() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun FolderAppCell(
    controller: HomeController,
    folderItemId: Long,
    app: AppRef,
    entry: AppEntry?,
    badgeFor: (AppEntry?) -> BadgeDisplay?,
    grayFor: (AppEntry?) -> GrayReason?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val style = controller.style
    val vm = controller.vm
    val span = vm.currentGrid().iconSpan
    val gray = grayFor(entry)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(style.iconSize + 36.dp)
            .launcherItemGestures(
                "folder-$folderItemId-${app.component}-${app.profileSerial}",
                controller.itemCallbacks(
                    source = DragSource.FolderApp(folderItemId, app),
                    visual = DragVisual.App(app),
                    spanX = span,
                    spanY = span,
                    entry = entry,
                    canDrag = true,
                    onLongPress = { menuOpen = true },
                    onDragStarted = { menuOpen = false },
                    onTap = { bounds ->
                        entry?.let { vm.launch(it, bounds) }
                        controller.closeFolder()
                    },
                ),
            ),
    ) {
        AppIconView(
            entry, vm.iconCache, style.iconSize, style.shape, badgeFor(entry),
            labelSizeSp = style.labelSizeSp,
            gray = gray != null,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuItems(controller, entry, close = { menuOpen = false }, gray = gray)
            if (!controller.locked) {
                DropdownMenuItem(text = { Text("フォルダーから出す") }, onClick = {
                    menuOpen = false
                    controller.closeFolder()
                    vm.takeOutOfFolder(folderItemId, app)
                })
            }
        }
    }
}

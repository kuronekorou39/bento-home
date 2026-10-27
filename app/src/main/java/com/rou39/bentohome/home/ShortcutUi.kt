package com.rou39.bentohome.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.catalog.AppCatalog
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.icon.IconCache
import com.rou39.bentohome.icon.IconRenderer
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.WorkspaceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ショートカットのアイコン画像。キャッシュの鍵は「パッケージ名/」で始め、パッケージの更新で捨てられるようにする。
 * 提供元が消したショートカットは null。
 */
@Composable
fun rememberShortcutIcon(
    s: ItemContent.Shortcut,
    size: Dp,
    shape: IconShape,
    cache: IconCache,
    catalog: AppCatalog,
): ImageBitmap? {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val density = LocalContext.current.resources.displayMetrics.densityDpi
    val key = IconCache.key("${s.packageName}/#shortcut:${s.shortcutId}", s.profileSerial, px, shape)
    val bitmap by produceState(cache.get(key)?.asImageBitmap(), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                val info = catalog.pinnedShortcut(s.packageName, s.shortcutId, s.profileSerial) ?: return@withContext null
                val drawable = catalog.shortcutIcon(info, density) ?: return@withContext null
                IconRenderer.render(drawable, px, shape).also { cache.put(key, it) }.asImageBitmap()
            }
        }
    }
    return bitmap
}

@Composable
fun ShortcutIconView(s: ItemContent.Shortcut, controller: HomeController, iconSize: Dp, labelSizeSp: Int?) {
    val vm = controller.vm
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.semantics { contentDescription = "ショートカット ${s.label}" },
    ) {
        Box(Modifier.size(iconSize)) {
            val icon = rememberShortcutIcon(s, iconSize, controller.style.shape, vm.iconCache, vm.catalog)
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.DarkGray),
                ) { Text("?", color = Color.White) }
            }
        }
        if (labelSizeSp != null) IconLabel(s.label, labelSizeSp)
    }
}

/** ホームに置いたショートカット。タップで開き、長押しでメニュー、長押しのまま動かすと移動。 */
@Composable
fun HomeShortcutCell(controller: HomeController, item: WorkspaceItem, shortcut: ItemContent.Shortcut, iconSize: Dp) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val style = controller.style
    val vm = controller.vm
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().launcherItemGestures(
            item.id,
            controller.itemCallbacks(
                source = DragSource.Item(item.id),
                visual = DragVisual.Shortcut(shortcut),
                spanX = item.rect.width,
                spanY = item.rect.height,
                entry = null,
                canDrag = true,
                onLongPress = { menuOpen = true },
                onDragStarted = { menuOpen = false },
                onTap = { bounds -> vm.launchPinned(shortcut, bounds) },
            ),
        ),
    ) {
        ShortcutIconView(shortcut, controller, iconSize, style.labelSizeSp.takeIf { style.labelVisible })
        if (controller.isHeld(DragSource.Item(item.id), menuOpen)) HoldHighlight()
        BentoMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            BentoMenuItem("提供元のアプリ情報", MenuSymbol.INFO, onClick = {
                menuOpen = false
                vm.catalog.openAppInfo(shortcut.packageName, shortcut.profileSerial)
            })
            SelectMenuItem(controller, item.id, close = { menuOpen = false })
            if (!controller.locked) {
                BentoMenuItem("ホームから削除", MenuSymbol.REMOVE, onClick = {
                    menuOpen = false
                    vm.removeItem(item.id)
                })
            }
        }
    }
}

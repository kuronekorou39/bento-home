package com.rou39.bentohome.home

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.rou39.bentohome.data.SettingsRepository
import com.rou39.bentohome.widget.WidgetHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PREVIEW_BOX_WIDTH = 112.dp
private val PREVIEW_MAX_HEIGHT = 88.dp
private val HIDDEN_PREVIEW_SIZE = 48.dp
private val APP_ICON_SIZE = 32.dp

private val LINE_COLOR = Color.White.copy(alpha = 0.22f)
private val HEADER_COLOR = Color.White.copy(alpha = 0.07f)
private val CARD_COLOR = Color.White.copy(alpha = 0.04f)
private val QUIET_COLOR = Color.White.copy(alpha = 0.45f)

/** 非表示の記録で、アプリごと非表示にしたことを表す接頭辞。後から増えたウィジェットも隠す。 */
private const val APP_KEY_PREFIX = "app:"

private fun appKey(packageName: String) = APP_KEY_PREFIX + packageName

/** 一覧に出すウィジェット1つ。key は非表示の記録に使う提供元。 */
private data class WidgetEntry(val info: AppWidgetProviderInfo, val key: String, val label: String, val appLabel: String) {
    val packageName: String get() = info.provider.packageName

    /** 同じ提供元が仕事用プロファイルにもあると key が重なるので、一覧の行の識別にはプロファイルも含める。 */
    val rowKey: String get() = "$key#${info.profile.hashCode()}"
}

/** 提供元のアプリごとのまとまり。 */
private data class WidgetGroup(val packageName: String, val appLabel: String, val entries: List<WidgetEntry>)

/**
 * ウィジェットの選択。提供元のアプリごとに開閉でき、まとめて開く・閉じるもできる。
 * 追加しないと決めたものは、行の右端の「非表示」で外す。アプリの見出しの長押しでアプリごと外せる。
 * 非表示の一覧から1件ずつ・すべて戻せる。ホームに置いてあるウィジェットには家の印を付ける。
 * @param placedProviders ホームに置いてあるウィジェットの提供元（ComponentName.flattenToString()）
 */
@Composable
fun WidgetPicker(
    widgetHost: WidgetHostController,
    settings: SettingsRepository,
    placedProviders: Set<String>,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onClose: () -> Unit,
) {
    val pm = LocalContext.current.packageManager
    val scope = rememberCoroutineScope()
    val hidden by settings.hiddenWidgets.collectAsState(initial = emptySet())
    val groups = remember {
        widgetHost.providers().map { info ->
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            WidgetEntry(info, info.provider.flattenToString(), info.loadLabel(pm), appLabel)
        }
            .groupBy { it.packageName }
            .map { (pkg, list) -> WidgetGroup(pkg, list.first().appLabel, list.sortedBy { it.label.lowercase() }) }
            .sortedBy { it.appLabel.lowercase() }
    }
    val visible = remember(groups, hidden) {
        groups.filter { appKey(it.packageName) !in hidden }
            .map { g -> g.copy(entries = g.entries.filter { it.key !in hidden }) }
            .filter { it.entries.isNotEmpty() }
    }
    val hiddenApps = remember(groups, hidden) { groups.filter { appKey(it.packageName) in hidden } }
    val hiddenEntries = remember(groups, hidden) {
        groups.filter { appKey(it.packageName) !in hidden }.flatMap { it.entries }.filter { it.key in hidden }
    }
    val hiddenCount = hiddenApps.sumOf { it.entries.size } + hiddenEntries.size
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var showHidden by remember { mutableStateOf(false) }
    val allOpen = visible.isNotEmpty() && visible.all { it.packageName in expanded }
    fun setHidden(key: String, value: Boolean) {
        scope.launch { settings.setWidgetHidden(key, value) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2101010))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ウィジェットを選択（${visible.sumOf { it.entries.size }}）",
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onClose) { Text("閉じる") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            TextButton(onClick = {
                expanded = if (allOpen) emptySet() else visible.mapTo(HashSet()) { it.packageName }
            }) { Text(if (allOpen) "すべて閉じる" else "すべて開く") }
            Spacer(Modifier.weight(1f))
            if (hiddenCount > 0) {
                TextButton(onClick = { showHidden = true }) { Text("非表示（$hiddenCount）") }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (group in visible) {
                val open = group.packageName in expanded
                item(key = "h:${group.packageName}") {
                    GroupHeader(
                        group,
                        open,
                        placed = group.entries.count { it.key in placedProviders },
                        onToggle = { expanded = if (open) expanded - group.packageName else expanded + group.packageName },
                        onHideApp = { setHidden(appKey(group.packageName), true) },
                    )
                }
                if (open) {
                    items(group.entries, key = { "w:${it.rowKey}" }) { entry ->
                        WidgetRow(
                            entry,
                            placed = entry.key in placedProviders,
                            onPick = { onPick(entry.info) },
                            onHide = { setHidden(entry.key, true) },
                        )
                    }
                }
            }
        }
    }

    if (showHidden) {
        BackHandler { showHidden = false }
        HiddenWidgets(
            apps = hiddenApps,
            entries = hiddenEntries,
            onUnhide = { key -> setHidden(key, false) },
            onUnhideAll = {
                scope.launch { settings.clearHiddenWidgets() }
                showHidden = false
            },
            onBack = { showHidden = false },
        )
    }
}

/** 提供元のアプリの見出し。タップで開閉、長押しでアプリごと非表示にするメニュー。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(group: WidgetGroup, open: Boolean, placed: Int, onToggle: () -> Unit, onHideApp: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HEADER_COLOR)
                .border(1.dp, LINE_COLOR, RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClickLabel = if (open) "閉じる" else "開く",
                    onLongClickLabel = "メニューを開く",
                    onLongClick = { menuOpen = true },
                    onClick = onToggle,
                )
                .semantics { stateDescription = if (open) "開いている" else "閉じている" }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            AppIcon(group.packageName, APP_ICON_SIZE)
            Text(
                group.appLabel,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            if (placed > 0) HomeMark(Modifier.padding(end = 6.dp))
            Text("${group.entries.size}件", color = Color.LightGray, fontSize = 12.sp)
            Text(if (open) "▾" else "▸", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
        }
        BentoMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            BentoMenuItem("このアプリのウィジェットをすべて非表示", MenuSymbol.HIDE, onClick = {
                menuOpen = false
                onHideApp()
            })
        }
    }
}

/** ウィジェット1つ。行のタップで追加。右端の目立たない「非表示」で一覧から外す。枠線でタップできる範囲を示す。 */
@Composable
private fun WidgetRow(entry: WidgetEntry, placed: Boolean, onPick: () -> Unit, onHide: () -> Unit) {
    val info = entry.info
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CARD_COLOR)
            .border(1.dp, LINE_COLOR, RoundedCornerShape(12.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = "追加", onClick = onPick)
                .semantics { if (placed) stateDescription = "ホームに配置済み" }
                .padding(8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(PREVIEW_BOX_WIDTH).heightIn(min = PREVIEW_MAX_HEIGHT / 2, max = PREVIEW_MAX_HEIGHT),
            ) { WidgetPreview(info, PREVIEW_MAX_HEIGHT) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (placed) HomeMark(Modifier.padding(end = 6.dp))
                    Text(entry.label, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${info.targetCellWidth}×${info.targetCellHeight}" + (if (info.configure != null) " ・ 設定あり" else ""),
                    color = Color.LightGray,
                    fontSize = 11.sp,
                )
            }
        }
        TextButton(onClick = onHide) {
            Text("${MenuSymbol.HIDE} 非表示", color = QUIET_COLOR, fontSize = 12.sp)
        }
    }
}

/** 非表示にしたアプリとウィジェットの一覧。1件ずつ、またはすべて表示に戻す。 */
@Composable
private fun HiddenWidgets(
    apps: List<WidgetGroup>,
    entries: List<WidgetEntry>,
    onUnhide: (key: String) -> Unit,
    onUnhideAll: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("非表示のウィジェット", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onUnhideAll, enabled = apps.isNotEmpty() || entries.isNotEmpty()) { Text("すべて戻す") }
            OutlinedButton(onClick = onBack) { Text("戻る") }
        }
        if (apps.isEmpty() && entries.isEmpty()) {
            Text("非表示のウィジェットはありません", color = Color.LightGray, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(apps, key = { "a:${it.packageName}" }) { group ->
                HiddenRow(
                    icon = { AppIcon(group.packageName, APP_ICON_SIZE) },
                    title = group.appLabel,
                    subtitle = "アプリごと非表示（${group.entries.size}件）",
                    onUnhide = { onUnhide(appKey(group.packageName)) },
                )
            }
            items(entries, key = { "w:${it.rowKey}" }) { entry ->
                HiddenRow(
                    icon = { WidgetPreview(entry.info, HIDDEN_PREVIEW_SIZE) },
                    title = entry.label,
                    subtitle = entry.appLabel,
                    onUnhide = { onUnhide(entry.key) },
                )
            }
        }
    }
}

@Composable
private fun HiddenRow(icon: @Composable () -> Unit, title: String, subtitle: String, onUnhide: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CARD_COLOR)
            .border(1.dp, LINE_COLOR, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Box(Modifier.size(HIDDEN_PREVIEW_SIZE), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.LightGray, fontSize = 11.sp, maxLines = 1)
        }
        TextButton(onClick = onUnhide) { Text("表示に戻す") }
    }
}

/** 提供元のアプリのアイコン。 */
@Composable
private fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val image by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(size)) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

/** 提供元のプレビュー画像。無ければアイコン。縦横比を保って最大 maxHeight に収める。 */
@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo, maxHeight: Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val maxPx = with(LocalDensity.current) { maxHeight.roundToPx() }
    val image by produceState<ImageBitmap?>(null, info, maxPx) {
        value = withContext(Dispatchers.IO) {
            val drawable = runCatching { info.loadPreviewImage(context, density) ?: info.loadIcon(context, density) }
                .getOrNull() ?: return@withContext null
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxPx
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxPx
            val scale = minOf(1f, maxPx.toFloat() / h)
            drawable.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
        }
    }
    image?.let { Image(it, contentDescription = null, modifier = Modifier.heightIn(max = maxHeight)) }
}

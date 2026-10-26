package com.rou39.bentohome.home

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.rou39.bentohome.badge.BadgeKey
import com.rou39.bentohome.badge.BadgeRepository
import com.rou39.bentohome.badge.BadgeState
import com.rou39.bentohome.catalog.AppCatalog
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.widget.WidgetHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

private const val TAG = "HomeScreen"

@Composable
fun HomeScreen(
    catalog: AppCatalog,
    widgetHost: WidgetHostController,
    widgetIds: List<Int>,
    hostIds: Set<Int>,
    roleHeld: Boolean,
    showPicker: Boolean,
    homeResetCount: Int,
    onRequestRole: () -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onPickWidget: (AppWidgetProviderInfo) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val apps by catalog.apps.collectAsState()
    val badgeState by BadgeRepository.state.collectAsState()
    val listState = rememberLazyListState()

    // ホーム操作で戻ったら先頭へ戻す
    LaunchedEffect(homeResetCount) { listState.scrollToItem(0) }

    // ホームでの戻る操作は何もしない。ピッカー表示中だけ閉じる。
    BackHandler(enabled = true) {}
    BackHandler(enabled = showPicker, onBack = onClosePicker)

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                if (!roleHeld) {
                    item {
                        Button(onClick = onRequestRole, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Bento Home を既定のホームにする")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        OutlinedButton(onClick = onOpenPicker) { Text("ウィジェット追加") }
                        OutlinedButton(onClick = onOpenSettings) { Text("設定") }
                    }
                    Text(badgeStatusText(badgeState), color = Color.White, fontSize = 12.sp)
                }
                items(widgetIds, key = { "widget-$it" }) { id ->
                    WidgetCard(id, id in hostIds, widgetHost, onRemove = { onRemoveWidget(id) })
                }
                item {
                    Text("アプリ（${apps.size}）", color = Color.White, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(apps, key = { it.key.toString() }) { entry ->
                    AppRow(entry, badgeCount(badgeState, entry), onLaunch = { bounds -> catalog.launch(entry, bounds) })
                }
            }
            if (showPicker) {
                WidgetPicker(widgetHost, onPick = onPickWidget, onClose = onClosePicker)
            }
        }
    }
}

private fun badgeStatusText(state: BadgeState): String = when (state) {
    BadgeState.Disabled -> "通知バッジ: 通知へのアクセスが未許可"
    BadgeState.Unknown -> "通知バッジ: 未接続"
    is BadgeState.Counts -> "通知バッジ: ${state.counts.values.sum()} 件 / ${state.counts.size} アプリ"
}

private fun badgeCount(state: BadgeState, entry: AppEntry): Int? =
    (state as? BadgeState.Counts)?.counts?.get(BadgeKey(entry.packageName, entry.key.profileSerial))

@Composable
private fun AppRow(entry: AppEntry, badge: Int?, onLaunch: (Rect?) -> Unit) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { c ->
                val b = c.boundsInWindow()
                bounds = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            .clickable { onLaunch(bounds) }
            .padding(vertical = 6.dp),
    ) {
        AppIcon(entry)
        Spacer(Modifier.width(12.dp))
        Text(entry.label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (badge != null && badge > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFD93025)),
            ) {
                Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AppIcon(entry: AppEntry) {
    val iconSize = 40.dp
    val px = with(LocalDensity.current) { iconSize.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, entry.key, px) {
        value = withContext(Dispatchers.IO) { entry.info.getIcon(0).toBitmap(px, px).asImageBitmap() }
    }
    Box(Modifier.size(iconSize)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * ウィジェット表示。長押しは Initial パスで消費せずに検出し、成立後のイベントを消費して
 * AppWidgetHostView に ACTION_CANCEL を届ける（docs/design.md §6）。
 */
@Composable
private fun WidgetCard(id: Int, onHost: Boolean, widgetHost: WidgetHostController, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val info = remember(id) { widgetHost.info(id) }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var horizontalDrag = false
                    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - down.position
                            if (delta.getDistance() > viewConfiguration.touchSlop) {
                                horizontalDrag = abs(delta.x) > abs(delta.y)
                                break
                            }
                        }
                    }
                    val takeOver = when {
                        ended == null -> {
                            Log.d(TAG, "widget long-press detected id=$id")
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                            true
                        }
                        // 横方向のドラッグはページ送り用にホームが取る。
                        // View はスワイプでも枠内ならタップとして扱うため、取らないと提供元が開く。
                        horizontalDrag -> {
                            Log.d(TAG, "horizontal drag over widget id=$id taken by home")
                            true
                        }
                        // 縦方向はウィジェット内部のスクロールに任せる
                        else -> false
                    }
                    if (takeOver) {
                        // 以降のイベントを消費して、ウィジェット側の操作を取り消させる
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
    ) {
        if (!onHost || info == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.DarkGray),
            ) {
                Text("ウィジェットの再追加が必要（id=$id）", color = Color.White)
            }
        } else {
            WidgetSurface(id, info, widgetHost)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("削除") }, onClick = { menuOpen = false; onRemove() })
        }
    }
}

@Composable
private fun WidgetSurface(id: Int, info: AppWidgetProviderInfo, widgetHost: WidgetHostController) {
    val density = LocalDensity.current
    // minHeight はピクセル値。段階1では幅いっぱい・最小高さで表示する。
    val heightDp = with(density) { info.minHeight.toDp() }.coerceAtLeast(80.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().height(heightDp)) {
        val widthDp = maxWidth
        key(id) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    widgetHost.viewFor(id).also { view -> (view.parent as? ViewGroup)?.removeView(view) }
                },
                update = { view ->
                    view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.value, heightDp.value)))
                },
            )
        }
    }
}

@Composable
private fun WidgetPicker(
    widgetHost: WidgetHostController,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val providers = remember {
        widgetHost.providers().map { info ->
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            Triple(info, appLabel, info.loadLabel(pm))
        }.sortedWith(compareBy({ it.second.lowercase() }, { it.third.lowercase() }))
    }
    Column(Modifier.fillMaxSize().background(Color(0xF0101010)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ウィジェットを選択（${providers.size}）", color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("閉じる") }
        }
        LazyColumn {
            items(providers, key = { it.first.provider.flattenToString() }) { (info, appLabel, label) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(info) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(label, color = Color.White)
                    Text(
                        "$appLabel ・ ${info.provider.packageName}" +
                            " ・ セル ${info.targetCellWidth}×${info.targetCellHeight}" +
                            (if (info.configure != null) " ・ 設定あり" else ""),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

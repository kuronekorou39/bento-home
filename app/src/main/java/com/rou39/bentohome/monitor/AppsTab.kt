package com.rou39.bentohome.monitor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val HOUR_MS = 60L * 60 * 1000
private const val MAX_PROCESSES = 20
private const val DAILY_REFRESH_MS = 60_000L

/** 一覧の説明に出す電池の使用量の下限。これより小さい値は表示上 0.0 になるので省く。 */
private const val MIN_SHOWN_MAH = 0.05

/**
 * アプリ（plan.md §10 の棚卸しを含む）。インストールしているアプリと動いているプロセスを1つの一覧にし、
 * 並べ替えで「使っていない」「重い」を探す。アンインストール・強制停止へ進める。
 */
@Composable
internal fun AppsTab(vm: MonitorViewModel, open: (Intent) -> Unit) {
    val state by vm.inventory.collectAsState()
    val installed by vm.installedApps.collectAsState()
    val live by vm.rows.collectAsState()
    val status by vm.privilegedStatus.collectAsState()
    val detailed = status == PrivilegedBridge.Status.READY
    var sort by rememberSaveable { mutableStateOf(AppListSort.UNUSED) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (state.loadedAt == 0L) vm.loadInventory() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(DAILY_REFRESH_MS)
            vm.refreshDaily()
        }
    }
    // アンインストールした行はすぐ消す
    val installedPackages = remember(installed) { installed.mapTo(HashSet()) { it.packageName } }
    val all = remember(state.rows, live, installedPackages) {
        AppList.merge(state.rows.filter { it.packageName in installedPackages }, live)
    }
    val rows = remember(all, sort, showSystem) { sort.sort(all.filter { showSystem || !it.isSystem }) }
    val now = state.loadedAt

    LazyColumn(contentPadding = PaddingValues(TAB_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppListSort.entries.forEach { option ->
                    FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Small("${rows.size}個", Modifier.weight(1f))
                FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text("システムも表示") })
                TextButton(onClick = vm::loadInventory, enabled = !state.loading) { Text("読み直す") }
            }
            note(state, sort, detailed)?.let { Small(it, Modifier.padding(bottom = 8.dp)) }
        }
        items(rows, key = { it.key }) { row ->
            AppListItem(vm, row, sort, now) { selected = row.key }
        }
    }
    selected?.let { key ->
        val row = all.firstOrNull { it.key == key }
        if (row == null) {
            selected = null
        } else {
            AppDetailDialog(vm, row, now, detailed, open) { selected = null }
        }
    }
}

/** 一覧の上の一言。足りない権限があればそれを、無ければ並べている値の意味を出す。 */
private fun note(state: InventoryState, sort: AppListSort, detailed: Boolean): String? = when {
    state.loading -> "調べています…"
    sort in listOf(AppListSort.CPU, AppListSort.MEMORY) && !detailed -> "計測用プロセスを起動すると表示します（概要の下から）"
    sort == AppListSort.BATTERY && state.loadedAt != 0L && !state.privilegedRead -> "計測用プロセスを起動すると表示します（概要の下から）"
    sort != AppListSort.CPU && sort != AppListSort.MEMORY && sort != AppListSort.BATTERY &&
        state.loadedAt != 0L && !state.usageRead -> "使用状況へのアクセスを許可すると表示します（概要の下から）"
    else -> when (sort) {
        AppListSort.UNUSED -> "OS の使用記録（過去1年）"
        AppListSort.NETWORK, AppListSort.BACKGROUND_NETWORK -> "直近30日"
        AppListSort.BATTERY -> "前回のフル充電から"
        AppListSort.CPU -> "全コアの合計を 100% とした値"
        AppListSort.MEMORY -> "RSS の合計。共有部分を重ねて数える"
        else -> null
    }
}

@Composable
private fun AppListItem(vm: MonitorViewModel, row: AppListRow, sort: AppListSort, now: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        val icon = remember(row.packageName) { row.packageName?.let(vm::icon) }
        Box(Modifier.size(36.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) } }
        Column(Modifier.weight(1f)) {
            Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary(row, sort, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(valueText(row, sort, now) ?: "—", fontWeight = FontWeight.Medium)
    }
}

/** 並べ替えの値を表示用に。値が無ければ null。 */
private fun valueText(row: AppListRow, sort: AppListSort, now: Long): String? {
    val inv = row.inventory
    val live = row.live
    return when (sort) {
        AppListSort.UNUSED -> inv?.let { lastUsedText(it.lastUsedAt, now) }
        AppListSort.SCREEN -> live?.foregroundMs?.let(::duration)
        AppListSort.STORAGE -> inv?.storageBytes?.let(::bytes)
        AppListSort.NETWORK -> inv?.networkBytes?.let(::bytes)
        AppListSort.BACKGROUND_NETWORK -> inv?.backgroundNetworkBytes?.let(::bytes)
        AppListSort.BATTERY -> inv?.battery?.let { mah(it.totalMah) }
        AppListSort.CPU -> live?.cpuPercent?.let(::percent)
        AppListSort.MEMORY -> live?.rssKb?.let { bytes(it * 1024) }
    }
}

/** 並べている値以外を短く並べる。 */
private fun summary(row: AppListRow, sort: AppListSort, now: Long): String = AppListSort.entries
    .filter { it != sort && (it == AppListSort.UNUSED || (it.value(row) ?: 0.0) > 0.0) }
    .filterNot { it == AppListSort.BACKGROUND_NETWORK }
    .filterNot { it == AppListSort.BATTERY && (row.inventory?.battery?.totalMah ?: 0.0) < MIN_SHOWN_MAH }
    .mapNotNull { s -> valueText(row, s, now)?.let { "${shortLabel(s)} $it" } }
    .joinToString("・")
    .ifEmpty { row.packageName ?: if (row.label.startsWith("[")) "OS のカーネルの処理" else "OS のプロセス" }

private fun shortLabel(s: AppListSort) = when (s) {
    AppListSort.UNUSED -> "最後に使った"
    AppListSort.SCREEN -> "今日"
    AppListSort.NETWORK -> "通信"
    AppListSort.BACKGROUND_NETWORK -> "裏の通信"
    else -> s.label
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppDetailDialog(
    vm: MonitorViewModel,
    row: AppListRow,
    now: Long,
    detailed: Boolean,
    open: (Intent) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    val inv = row.inventory
    val live = row.live
    val pkg = row.packageName
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pkg?.let { Small(it) }
                if (inv != null) {
                    Text("最後に使った: ${lastUsedText(inv.lastUsedAt, now)}")
                    Text("Bento から最後に開いた: ${inv.bentoLastLaunchAt?.let { ago(it, now) } ?: "記録なし"}")
                    inv.storageBytes?.let { Text("容量: ${bytes(it)}") }
                    inv.networkBytes?.let { Text("通信量（30日）: ${bytes(it)}・うち裏 ${bytes(inv.backgroundNetworkBytes ?: 0)}") }
                    inv.battery?.let { Text("電池（前回のフル充電から）: ${mah(it.totalMah)}・うち裏 ${mah(it.backgroundMah)}") }
                }
                live?.foregroundMs?.let { Text("今日の使用時間: ${duration(it)}") }
                live?.networkBytes?.let { Text("今日の通信量: ${bytes(it)}") }
                live?.cpuPercent?.let { Text("CPU: ${percent(it)}") }
                live?.rssKb?.let { Text("メモリ: ${bytes(it * 1024)}") }
                inv?.sensitive?.let { s ->
                    HorizontalDivider()
                    Text("カメラ: ${s.cameraAt?.let { ago(it, now) } ?: "記録なし"}")
                    Text("マイク: ${s.microphoneAt?.let { ago(it, now) } ?: "記録なし"}")
                    Text("位置情報: ${s.locationAt?.let { ago(it, now) } ?: "記録なし"}")
                }
                if (live != null && live.processes.isNotEmpty()) {
                    HorizontalDivider()
                    Text("プロセス ${live.processes.size}", style = MaterialTheme.typography.titleSmall)
                    live.processes.take(MAX_PROCESSES).forEach { p ->
                        Small("${p.pid}  ${AppRows.baseName(p.name)}\nCPU ${percent(p.cpuPercent)}・メモリ ${bytes(p.rssKb * 1024)}")
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.End) {
                if (pkg != null) {
                    if (inv != null && !inv.isSystem) {
                        TextButton(onClick = {
                            onDismiss()
                            open(vm.uninstallIntent(pkg))
                        }) { Text("アンインストール") }
                    }
                    if (detailed && live != null) TextButton(onClick = { confirmStop = true }) { Text("強制停止") }
                    TextButton(onClick = {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
                    }) { Text("アプリ情報") }
                }
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
        },
    )
    if (confirmStop && pkg != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("「${row.label}」を強制停止しますか") },
            text = { Text("保存していない内容は失われることがあります。通知や同期は、次に開くまで止まります。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.forceStop(pkg)
                    confirmStop = false
                    onDismiss()
                }) { Text("強制停止") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("やめる") } },
        )
    }
}

/** 過去1年に記録が無ければ「記録なし」。 */
private fun lastUsedText(at: Long?, now: Long): String = at?.let { ago(it, now) } ?: "記録なし"

/** 「3日前」「5時間前」「1時間以内」。 */
private fun ago(at: Long, now: Long): String {
    val d = (now - at).coerceAtLeast(0)
    return when {
        d >= DAY_MS -> "${d / DAY_MS}日前"
        d >= HOUR_MS -> "${d / HOUR_MS}時間前"
        else -> "1時間以内"
    }
}

private fun mah(v: Double): String = String.format(Locale.JAPAN, if (v < 10) "%.1f mAh" else "%.0f mAh", v)

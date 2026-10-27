package com.rou39.bentohome.monitor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
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
import java.util.Locale

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val HOUR_MS = 60L * 60 * 1000

/** 一覧の説明に出す電池の使用量の下限。これより小さい値は表示上 0.0 になるので省く。 */
private const val MIN_SHOWN_MAH = 0.05

/**
 * 棚卸し（plan.md §10）。インストールしているアプリを、使っていない日数・容量・通信量・電池で並べ、
 * アンインストールへ進める。ホームには出さない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryTab(vm: MonitorViewModel, open: (Intent) -> Unit) {
    val state by vm.inventory.collectAsState()
    val installed by vm.installedApps.collectAsState()
    var sort by rememberSaveable { mutableStateOf(InventorySort.UNUSED) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (state.loadedAt == 0L) vm.loadInventory() }
    // アンインストールした行はすぐ消す
    val installedPackages = remember(installed) { installed.mapTo(HashSet()) { it.packageName } }
    val rows = remember(state.rows, installedPackages, sort, showSystem) {
        sort.sort(state.rows.filter { it.packageName in installedPackages && (showSystem || !it.isSystem) })
    }
    val now = state.loadedAt

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventorySort.entries.forEach { option ->
                    FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("システムのアプリも表示", modifier = Modifier.weight(1f))
                Switch(checked = showSystem, onCheckedChange = { showSystem = it })
            }
            val notes = buildList {
                if (state.loading) add("調べています…（数秒かかります）")
                if (!state.loading && state.loadedAt != 0L && !state.usageRead) {
                    add("使用状況へのアクセスを許可すると、最後に使った日・容量・通信量を表示します（概要のタブから）")
                }
                if (!state.loading && state.loadedAt != 0L && !state.privilegedRead) {
                    add("電池とカメラ・マイク・位置情報の使用は、計測用プロセスを起動すると表示します（概要のタブから）")
                }
                when (sort) {
                    InventorySort.UNUSED -> add("最後に使った日は OS の使用記録（過去1年、通知から開いた分なども含む）")
                    InventorySort.NETWORK, InventorySort.BACKGROUND_NETWORK ->
                        add("直近30日。裏の通信量は、画面に出ていないときに送受信した分")
                    InventorySort.BATTERY -> add("前回のフル充電から。OS の推定値")
                    InventorySort.STORAGE -> add("アプリ本体・データ・キャッシュの合計")
                }
            }
            notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${rows.size}個", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::loadInventory, enabled = !state.loading) { Text("読み直す") }
            }
        }
        items(rows, key = { it.packageName }) { row ->
            InventoryRowItem(vm, row, sort, now) { selected = row.packageName }
        }
    }
    selected?.let { pkg ->
        val row = rows.firstOrNull { it.packageName == pkg }
        if (row == null) {
            selected = null
        } else {
            InventoryDetailDialog(row, now, onUninstall = {
                selected = null
                open(vm.uninstallIntent(pkg))
            }, onAppInfo = {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
            }, onDismiss = { selected = null })
        }
    }
}

@Composable
private fun InventoryRowItem(vm: MonitorViewModel, row: InventoryRow, sort: InventorySort, now: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        val icon = remember(row.packageName) { vm.icon(row.packageName) }
        Box(Modifier.size(36.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) } }
        Column(Modifier.weight(1f)) {
            Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary(row, sort, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(main(row, sort, now), fontWeight = FontWeight.Medium)
    }
}

private fun main(row: InventoryRow, sort: InventorySort, now: Long): String = when (sort) {
    InventorySort.UNUSED -> lastUsedText(row.lastUsedAt, now)
    InventorySort.STORAGE -> row.storageBytes?.let(::bytes) ?: "—"
    InventorySort.NETWORK -> row.networkBytes?.let(::bytes) ?: "—"
    InventorySort.BACKGROUND_NETWORK -> row.backgroundNetworkBytes?.let(::bytes) ?: "—"
    InventorySort.BATTERY -> row.battery?.let { mah(it.totalMah) } ?: "—"
}

/** 並べている値以外を短く並べる。 */
private fun summary(row: InventoryRow, sort: InventorySort, now: Long): String = buildList {
    if (sort != InventorySort.UNUSED) add("最後に使った ${lastUsedText(row.lastUsedAt, now)}")
    if (sort != InventorySort.STORAGE) row.storageBytes?.let { add("容量 ${bytes(it)}") }
    if (sort != InventorySort.NETWORK && sort != InventorySort.BACKGROUND_NETWORK) {
        row.networkBytes?.takeIf { it > 0 }?.let { add("通信 ${bytes(it)}（裏 ${bytes(row.backgroundNetworkBytes ?: 0)}）") }
    } else {
        row.networkBytes?.let { add("通信 ${bytes(it)}・うち裏 ${bytes(row.backgroundNetworkBytes ?: 0)}") }
    }
    if (sort != InventorySort.BATTERY) row.battery?.takeIf { it.totalMah >= MIN_SHOWN_MAH }?.let { add("電池 ${mah(it.totalMah)}") }
    sensitiveText(row.sensitive, now)?.let(::add)
}.joinToString("・").ifEmpty { row.packageName }

private fun sensitiveText(s: SensitiveUse?, now: Long): String? {
    s ?: return null
    return listOfNotNull(
        s.cameraAt?.let { "カメラ ${ago(it, now)}" },
        s.microphoneAt?.let { "マイク ${ago(it, now)}" },
        s.locationAt?.let { "位置 ${ago(it, now)}" },
    ).joinToString("・").ifEmpty { null }
}

@Composable
private fun InventoryDetailDialog(
    row: InventoryRow,
    now: Long,
    onUninstall: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.packageName, style = MaterialTheme.typography.bodySmall)
                Text("最後に使った: ${lastUsedText(row.lastUsedAt, now)}")
                Text("Bento から最後に開いた: ${row.bentoLastLaunchAt?.let { ago(it, now) } ?: "記録なし"}")
                row.storageBytes?.let { Text("容量: ${bytes(it)}") }
                row.networkBytes?.let { Text("通信量（30日）: ${bytes(it)}・うち裏 ${bytes(row.backgroundNetworkBytes ?: 0)}") }
                row.battery?.let { Text("電池（前回のフル充電から）: ${mah(it.totalMah)}・うち裏 ${mah(it.backgroundMah)}") }
                row.sensitive?.let { s ->
                    HorizontalDivider()
                    Text("カメラ: ${s.cameraAt?.let { ago(it, now) } ?: "記録なし"}")
                    Text("マイク: ${s.microphoneAt?.let { ago(it, now) } ?: "記録なし"}")
                    Text("位置情報: ${s.locationAt?.let { ago(it, now) } ?: "記録なし"}")
                }
                if (row.isSystem) Text("システムのアプリなのでアンインストールできません", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row {
                if (!row.isSystem) TextButton(onClick = onUninstall) { Text("アンインストール") }
                TextButton(onClick = onAppInfo) { Text("アプリ情報") }
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
        },
    )
}

/** 過去1年に記録が無ければ「記録なし」。期間は一覧の上に書く。 */
private fun lastUsedText(at: Long?, now: Long): String = at?.let { ago(it, now) } ?: "記録なし"

/** 「3日前」「5時間前」「今日」。 */
private fun ago(at: Long, now: Long): String {
    val d = (now - at).coerceAtLeast(0)
    return when {
        d >= DAY_MS -> "${d / DAY_MS}日前"
        d >= HOUR_MS -> "${d / HOUR_MS}時間前"
        else -> "1時間以内"
    }
}

private fun mah(v: Double): String = String.format(Locale.JAPAN, if (v < 10) "%.1f mAh" else "%.0f mAh", v)

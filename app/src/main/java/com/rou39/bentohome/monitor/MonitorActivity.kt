package com.rou39.bentohome.monitor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.home.describe
import kotlinx.coroutines.delay
import com.rou39.bentohome.monitor.MonitorActivity.Companion.TAB_INVENTORY
import java.util.Locale

/** システムモニター（docs/monitor.md）。表示中だけ短い間隔で詳しく読む。 */
class MonitorActivity : ComponentActivity() {
    private val vm: MonitorViewModel by viewModels()
    private var handle: AutoCloseable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initialTab = intent.getIntExtra(EXTRA_TAB, 0)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    MonitorScreen(vm, initialTab, ::openUrl)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        handle = container().monitor.attach(detailed = true)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
        vm.refreshDaily()
    }

    override fun onStop() {
        super.onStop()
        handle?.close()
        handle = null
    }

    private fun container() = (application as com.rou39.bentohome.BentoApplication).container

    private fun openUrl(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_INVENTORY = 2
        const val TAB_BENTO = 4
    }
}

private val TABS = listOf("概要", "アプリ", "棚卸し", "ウィジェット", "Bento")
private val WINDOWS = listOf(5 * 60_000L to "5分", MonitorRepository.WINDOW_MS to "30分")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorScreen(vm: MonitorViewModel, initialTab: Int, open: (Intent) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            TABS.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> OverviewTab(vm, open)
            1 -> AppsTab(vm, open)
            TAB_INVENTORY -> InventoryTab(vm, open)
            3 -> WidgetsTab(vm)
            else -> BentoTab(vm, open)
        }
    }
}

// ---- 概要 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(vm: MonitorViewModel, open: (Intent) -> Unit) {
    val s by vm.state.collectAsState()
    val rows by vm.rows.collectAsState()
    var windowMs by rememberSaveable { mutableLongStateOf(WINDOWS[0].first) }
    val now = s.series.values.maxOfOrNull { it.lastOrNull()?.t ?: 0L }?.takeIf { it > 0 } ?: System.currentTimeMillis()
    fun series(label: String, m: Metric) = ChartSeries(label, s.series[m].orEmpty())

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AccessCard(vm, open)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WINDOWS.forEach { (ms, label) ->
                FilterChip(selected = windowMs == ms, onClick = { windowMs = ms }, label = { Text("直近$label") })
            }
        }
        if (s.series[Metric.CPU].isNullOrEmpty()) {
            MetricChart(
                "CPU 周波数", listOf(series("周波数", Metric.CPU_FREQ)), ::percent, now, windowMs, YRange.Fixed(0f, 100f),
                note = "全コアの最大周波数に対する平均。使用率は計測用プロセスを起動すると表示します",
            )
        } else {
            MetricChart("CPU 使用率", listOf(series("CPU", Metric.CPU)), ::percent, now, windowMs, YRange.Fixed(0f, 100f),
                note = "全コアの合計を 100% とした値")
            val top = AppRows.sorted(rows, AppSort.CPU).take(TOP_APPS)
            if (top.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CPU を多く使っているもの", style = MaterialTheme.typography.titleSmall)
                    top.forEach { r ->
                        Row {
                            Text(r.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(percent(r.cpuPercent ?: 0f))
                        }
                    }
                }
            }
        }
        val usedGb = (s.memTotal - s.memAvail) / GB
        MetricChart(
            "メモリ", listOf(series("使用中", Metric.MEMORY)), ::percent, now, windowMs, YRange.Fixed(0f, 100f),
            note = if (s.memTotal > 0) String.format(Locale.JAPAN, "使用中 %.1f GB / 全体 %.1f GB", usedGb, s.memTotal / GB) else null,
        )
        MetricChart(
            "通信速度", listOf(series("受信", Metric.NET_RX), series("送信", Metric.NET_TX)), ::rate, now, windowMs, YRange.FromZero,
        )
        val temps = listOf(series("表面", Metric.TEMP_SKIN), series("電池", Metric.TEMP_BATTERY), series("CPU", Metric.TEMP_CPU))
            .filter { it.points.isNotEmpty() }
        if (temps.isNotEmpty()) {
            MetricChart(
                "温度", temps, ::celsius, now, windowMs, YRange.Fit,
                note = if (temps.size == 1) "表面と CPU の温度は、温度の読み取り権限を付与すると表示します" else null,
            )
        }
        MetricChart(
            "電池の電流", listOf(series("電流", Metric.BATTERY_CURRENT)), ::milliamp, now, windowMs, YRange.FromZero,
            note = buildString {
                if (s.batteryLevel >= 0) append("残量 ${s.batteryLevel}%・${if (s.charging) "充電中" else "放電中"}。")
                append("正の値は充電、負の値は放電")
            },
        )
        MetricChart(
            "発熱による制限までの余裕", listOf(series("使用", Metric.THERMAL_HEADROOM)), ::percent, now, windowMs,
            YRange.Fixed(0f, 100f),
            note = "100% に達すると OS が性能を抑え始めます。現在の状態: ${thermalLabel(s.thermalStatus)}",
        )
    }
}

@Composable
private fun AccessCard(vm: MonitorViewModel, open: (Intent) -> Unit) {
    val status by vm.privilegedStatus.collectAsState()
    val usage by vm.usageGranted.collectAsState()
    val dump by vm.dumpGranted.collectAsState()
    val connected = status == PrivilegedBridge.Status.READY
    if (connected && usage && dump) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("表示できる情報を増やす", style = MaterialTheme.typography.titleSmall)
            if (!connected) {
                Text(
                    "全プロセスの CPU・メモリと端末の CPU 使用率は、PC につないで次のコマンドで計測用プロセスを起動すると表示されます。端末を再起動するまで動き続けます。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(vm.startCommand, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                OutlinedButton(onClick = vm::copyStartCommand) { Text("コマンドをコピー") }
            }
            if (!usage || !dump) {
                Text(
                    buildList {
                        if (!usage) add("使用状況へのアクセス（アプリごとの使用時間・通信量・容量）")
                        if (!dump) add("温度の読み取り（表面・CPU の温度）")
                    }.joinToString("、") + "が未許可です。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (connected) {
                    Button(onClick = vm::grantWithPrivileged) { Text("計測用プロセスでまとめて許可する") }
                } else if (!usage) {
                    OutlinedButton(onClick = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                        Text("使用状況へのアクセスの設定を開く")
                    }
                }
            }
        }
    }
}

// ---- アプリ ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsTab(vm: MonitorViewModel, open: (Intent) -> Unit) {
    val rows by vm.rows.collectAsState()
    val status by vm.privilegedStatus.collectAsState()
    val usage by vm.usageGranted.collectAsState()
    val storageLoading by vm.storageLoading.collectAsState()
    val detailed = status == PrivilegedBridge.Status.READY
    var sort by rememberSaveable { mutableStateOf(if (detailed) AppSort.CPU else AppSort.SCREEN) }
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(DAILY_REFRESH_MS)
            vm.refreshDaily()
        }
    }
    LaunchedEffect(sort) { if (sort == AppSort.STORAGE) vm.loadStorage() }
    val sorted = remember(rows, sort) { AppRows.sorted(rows, sort) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSort.entries.forEach { option ->
                    FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) })
                }
            }
            val missing = when {
                (sort == AppSort.CPU || sort == AppSort.MEMORY) && !detailed -> "CPU とメモリは、計測用プロセスを起動すると表示します（docs/operations.md）"
                sort != AppSort.CPU && sort != AppSort.MEMORY && !usage -> "使用状況へのアクセスを許可すると表示します（概要のタブから）"
                sort == AppSort.STORAGE && storageLoading -> "容量を調べています…"
                sort == AppSort.MEMORY -> "メモリは各プロセスの物理メモリ使用量（RSS）の合計。共有部分を重ねて数えるため、合計は実際より大きくなります"
                sort == AppSort.CPU -> "全コアの合計を 100% とした値"
                else -> null
            }
            missing?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
        }
        items(sorted, key = { it.key }) { row ->
            AppRowItem(vm, row, sort) { selected = row.key }
        }
    }
    selected?.let { key ->
        val row = rows.firstOrNull { it.key == key }
        if (row == null) {
            selected = null
        } else {
            AppDetailDialog(vm, row, detailed, open) { selected = null }
        }
    }
}

@Composable
private fun AppRowItem(vm: MonitorViewModel, row: AppRow, sort: AppSort, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        val icon = remember(row.packageName) { row.packageName?.let(vm::icon) }
        Box(Modifier.size(36.dp)) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                secondary(row, sort),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(primary(row, sort), fontWeight = FontWeight.Medium)
    }
}

private fun primary(row: AppRow, sort: AppSort): String = when (sort) {
    AppSort.CPU -> percent(row.cpuPercent ?: 0f)
    AppSort.MEMORY -> bytes((row.rssKb ?: 0) * 1024)
    AppSort.SCREEN -> duration(row.foregroundMs ?: 0)
    AppSort.NETWORK -> bytes(row.networkBytes ?: 0)
    AppSort.STORAGE -> bytes(row.storageBytes ?: 0)
}

private fun secondary(row: AppRow, sort: AppSort): String = AppSort.entries.filter { it != sort && (it.value(row) ?: 0.0) > 0.0 }
    .joinToString("・") { "${it.label} ${primary(row, it)}" }
    .ifEmpty { row.packageName ?: if (row.label.startsWith("[")) "OS のカーネルの処理" else "OS のプロセス" }

@Composable
private fun AppDetailDialog(vm: MonitorViewModel, row: AppRow, detailed: Boolean, open: (Intent) -> Unit, onDismiss: () -> Unit) {
    var confirmStop by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                row.packageName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                AppSort.entries.forEach { s -> if (s.value(row) != null) Text("${s.label}: ${primary(row, s)}") }
                if (row.processes.isNotEmpty()) {
                    HorizontalDivider()
                    Text("プロセス ${row.processes.size}", style = MaterialTheme.typography.titleSmall)
                    row.processes.take(MAX_PROCESSES).forEach { p ->
                        Text(
                            "${p.pid}  ${AppRows.baseName(p.name)}\nCPU ${percent(p.cpuPercent)}・メモリ ${bytes(p.rssKb * 1024)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                row.packageName?.let { pkg ->
                    if (detailed) TextButton(onClick = { confirmStop = true }) { Text("強制停止") }
                    TextButton(onClick = {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
                    }) { Text("アプリ情報") }
                }
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
        },
    )
    if (confirmStop && row.packageName != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("「${row.label}」を強制停止しますか") },
            text = { Text("保存していない内容は失われることがあります。通知や同期は、次に開くまで止まります。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.forceStop(row.packageName)
                    confirmStop = false
                    onDismiss()
                }) { Text("強制停止") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("やめる") } },
        )
    }
}

// ---- ウィジェット ----

@Composable
private fun WidgetsTab(vm: MonitorViewModel) {
    val stats by vm.widgetStats.collectAsState()
    val rows by produceState<List<WidgetRow>?>(null, stats) { value = vm.widgets() }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "ホームでウィジェットを長押しして「負荷を測る」を選ぶと、表示している間の CPU と描画の負荷を測れます。" +
                "見えていないページのウィジェットは止めているので、負荷がかかるのは表示中だけです。",
            style = MaterialTheme.typography.bodySmall,
        )
        val list = rows ?: return@Column
        if (list.isEmpty()) Text("ホームにウィジェットはありません。")
        list.forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${w.label}（${w.page}ページ目）", style = MaterialTheme.typography.titleSmall)
                val m = w.stat?.measurement
                Text(
                    if (m != null) describe(m) + if (m.heavy) "。重め" else "。軽い" else "未計測",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (m != null && m.animations.isNotEmpty()) {
                    Text("動き続ける部品: ${m.animations.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                }
                val updates = w.stat?.recentUpdates?.size ?: 0
                Text(
                    "提供元からの更新: 直近1時間 $updates 回（Bento の起動後 ${w.stat?.updates ?: 0} 回）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Bento（透明性） ----

@Composable
private fun BentoTab(vm: MonitorViewModel, open: (Intent) -> Unit) {
    val status by vm.privilegedStatus.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val info by produceState<Transparency?>(null, tick, status) { value = vm.transparency() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TRANSPARENCY_REFRESH_MS)
            tick++
        }
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val t = info ?: return@Column
        Heading("通信")
        Text(
            if (t.internetRequested) "INTERNET 権限を持っています" else "INTERNET 権限を持っていません。Bento は通信できません。",
            fontWeight = FontWeight.Medium,
        )
        Text("端末の起動後に Bento が送受信したデータ: 受信 ${bytes(t.ownRxBytes)}・送信 ${bytes(t.ownTxBytes)}")
        Small("リリース版はビルドのたびに、INTERNET 権限が含まれていないことを検査しています。起動したアプリやウィジェットの提供元は、それぞれ独自に通信することがあります。")

        Heading("権限")
        Small("Bento が要求している権限はこれで全部です。")
        t.permissions.forEach { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (p.granted) "許可" else "未許可", Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyMedium)
                    Small(p.purpose)
                }
            }
        }
        Text("計測用プロセス（シェル権限）: ${privilegedLabel(status)}", style = MaterialTheme.typography.bodyMedium)
        Small("adb から許可する場合: adb shell appops set ${vm.packageName} GET_USAGE_STATS allow と adb shell pm grant ${vm.packageName} android.permission.DUMP")

        Heading("保存しているデータ")
        Text("配置: ページ ${t.pages}・アプリ ${t.apps}・ウィジェット ${t.widgets}")
        Text("データベース ${bytes(t.databaseBytes)}・設定 ${bytes(t.settingsBytes)}・キャッシュ ${bytes(t.cacheBytes)}")
        Small("データベースには配置・外観の個別設定・起動回数と最終起動日時が入っています。通知の内容は読まず、件数もメモリ上で数えるだけで保存しません。グラフの値もメモリ上だけで、Bento を終了すると消えます。OS のクラウドバックアップと端末間転送は無効です。")

        Heading("Bento 自身の負荷")
        Text("起動後の CPU 時間 ${duration(t.processCpuMs)}（起動から ${duration(t.processUptimeMs)}）")
        Text("メモリ（PSS）${bytes(t.pssKb * 1024L)}")
        if (t.home.elapsedMs > 0) {
            Text(
                String.format(
                    Locale.JAPAN, "ホームを表示している間: CPU 平均 %.1f%%（1コアあたり）、描画 毎秒 %.1f 回（表示時間 %s）",
                    t.home.cpuPercent, t.home.framesPerSecond, duration(t.home.elapsedMs),
                ),
            )
        }
        Small("ホームの CPU と描画には、ホームに置いたウィジェットの描画も含まれます。ウィジェットごとの負荷はウィジェットのタブで確認できます。")
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", vm.packageName, null)))
        }) { Text("OS のアプリ情報を開く") }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun privilegedLabel(s: PrivilegedBridge.Status) = when (s) {
    PrivilegedBridge.Status.NOT_CONNECTED -> "起動していません"
    PrivilegedBridge.Status.READY -> "接続中（読み取るのはモニター表示中だけです）"
}

private fun thermalLabel(status: Int) = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "問題なし"
    PowerManager.THERMAL_STATUS_LIGHT -> "やや高い"
    PowerManager.THERMAL_STATUS_MODERATE -> "高い"
    PowerManager.THERMAL_STATUS_SEVERE -> "かなり高い"
    PowerManager.THERMAL_STATUS_CRITICAL -> "危険"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "緊急"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "停止寸前"
    else -> "不明"
}

private const val GB = 1024f * 1024f * 1024f
private const val TOP_APPS = 5
private const val MAX_PROCESSES = 20
private const val DAILY_REFRESH_MS = 60_000L
private const val TRANSPARENCY_REFRESH_MS = 5_000L

internal fun percent(v: Float) = String.format(Locale.JAPAN, if (v < 10f && v != 0f) "%.1f%%" else "%.0f%%", v)
private fun celsius(v: Float) = String.format(Locale.JAPAN, "%.1f℃", v)
private fun milliamp(v: Float) = String.format(Locale.JAPAN, "%.0f mA", v)

private fun rate(kbPerSec: Float) = when {
    kbPerSec >= 1024f -> String.format(Locale.JAPAN, "%.1f MB/s", kbPerSec / 1024f)
    else -> String.format(Locale.JAPAN, "%.0f KB/s", kbPerSec)
}

internal fun bytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.JAPAN, "%.1f GB", b / GB)
    b >= 1L shl 20 -> String.format(Locale.JAPAN, "%.0f MB", b / (1024f * 1024f))
    b >= 1L shl 10 -> String.format(Locale.JAPAN, "%.0f KB", b / 1024f)
    else -> "$b B"
}

internal fun duration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min >= 60 -> "${min / 60}時間${min % 60}分"
        min >= 1 -> "${min}分"
        else -> "${ms / 1000}秒"
    }
}

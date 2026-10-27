package com.rou39.bentohome.monitor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    LazyColumn(contentPadding = PaddingValues(TAB_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            NoteBox(listOfNotNull(missing), Modifier.padding(top = 4.dp, bottom = 8.dp))
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
        Modifier.verticalScroll(rememberScrollState()).padding(TAB_PADDING),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoteBox(
            listOf(
                "ホームでウィジェットを長押しして「負荷を測る」を選ぶと、表示している間の CPU と描画の負荷を測れます。",
                "見えていないページのウィジェットは止めているので、負荷がかかるのは表示中だけです。",
            ),
        )
        val list = rows ?: return@Column
        if (list.isEmpty()) Small("ホームにウィジェットはありません。")
        list.forEach { w ->
            SectionCard(null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            w.label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Small("${w.page}ページ目")
                    }
                    val m = w.stat?.measurement
                    Text(
                        if (m != null) describe(m) + if (m.heavy) "。重め" else "。軽い" else "未計測",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (m?.heavy == true) FontWeight.Medium else null,
                    )
                    if (m != null && m.animations.isNotEmpty()) {
                        Small("動き続ける部品: ${m.animations.joinToString("、")}")
                    }
                    val updates = w.stat?.recentUpdates?.size ?: 0
                    Small("提供元からの更新: 直近1時間 $updates 回（Bento の起動後 ${w.stat?.updates ?: 0} 回）")
                }
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
        Modifier.verticalScroll(rememberScrollState()).padding(TAB_PADDING),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val t = info ?: return@Column
        SectionCard("通信") {
            Text(
                if (t.internetRequested) "INTERNET 権限を持っています" else "INTERNET 権限を持っていません。Bento は通信できません。",
                fontWeight = FontWeight.Medium,
            )
            Text("端末の起動後に Bento が送受信したデータ: 受信 ${bytes(t.ownRxBytes)}・送信 ${bytes(t.ownTxBytes)}")
            Small("リリース版はビルドのたびに、INTERNET 権限が含まれていないことを検査しています。起動したアプリやウィジェットの提供元は、それぞれ独自に通信することがあります。")
        }

        SectionCard("権限") {
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
        }

        SectionCard("保存しているデータ") {
            Text("配置: ページ ${t.pages}・アプリ ${t.apps}・ウィジェット ${t.widgets}")
            Text("データベース ${bytes(t.databaseBytes)}・設定 ${bytes(t.settingsBytes)}・キャッシュ ${bytes(t.cacheBytes)}")
            Small("データベースには配置・外観の個別設定・起動回数と最終起動日時が入っています。通知の内容は読まず、件数もメモリ上で数えるだけで保存しません。グラフの値もメモリ上だけで、Bento を終了すると消えます。OS のクラウドバックアップと端末間転送は無効です。")
        }

        SectionCard("Bento 自身の負荷") {
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
        }
        OutlinedButton(onClick = {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", vm.packageName, null)))
        }) { Text("OS のアプリ情報を開く") }
    }
}

private fun privilegedLabel(s: PrivilegedBridge.Status) = when (s) {
    PrivilegedBridge.Status.NOT_CONNECTED -> "起動していません"
    PrivilegedBridge.Status.READY -> "接続中（読み取るのはモニター表示中だけです）"
}

internal const val GB = 1024f * 1024f * 1024f
private const val MAX_PROCESSES = 20
private const val DAILY_REFRESH_MS = 60_000L
private const val TRANSPARENCY_REFRESH_MS = 5_000L

internal fun percent(v: Float) = String.format(Locale.JAPAN, if (v < 10f && v != 0f) "%.1f%%" else "%.0f%%", v)
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

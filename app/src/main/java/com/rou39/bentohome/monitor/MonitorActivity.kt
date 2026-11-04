package com.rou39.bentohome.monitor

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import com.rou39.bentohome.ui.BentoTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.rou39.bentohome.monitor.MonitorActivity.Companion.TAB_APPS
import java.util.Locale

/** システムモニター（docs/monitor.md）。表示中だけ短い間隔で詳しく読む。 */
class MonitorActivity : ComponentActivity() {
    private val vm: MonitorViewModel by viewModels()
    private var handle: AutoCloseable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        val initialTab = intent.getIntExtra(EXTRA_TAB, 0).coerceIn(0, TABS.lastIndex)
        setContent {
            BentoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MonitorScreen(vm, initialTab, ::openUrl)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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
        const val TAB_APPS = 1
    }
}

private val TABS = listOf("概要", "アプリ", "ウィジェット")

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
            TAB_APPS -> AppsTab(vm, open)
            2 -> WidgetsTab(vm)
            else -> OverviewTab(vm, open)
        }
    }
}

// ---- ウィジェット ----

@Composable
private fun WidgetsTab(vm: MonitorViewModel) {
    val stats by vm.widgetStats.collectAsState()
    val rows by produceState<List<WidgetRow>?>(null, stats) { value = vm.widgets() }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(TAB_PADDING),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val list = rows ?: return@Column
        if (list.isEmpty()) Small("ホームにウィジェットはありません。")
        list.forEach { w -> WidgetItem(vm, w) }
        Small(
            "ホームでウィジェットを長押し→「負荷を測る」で計測します。見えていないページのウィジェットは止めています。",
            Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun WidgetItem(vm: MonitorViewModel, w: WidgetRow) {
    val m = w.stat?.measurement
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        // 提供元のアプリのアイコン
        val icon = remember(w.packageName) { vm.icon(w.packageName) }
        Box(Modifier.size(36.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) } }
        Column(Modifier.weight(1f)) {
            Text(w.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Small(if (m != null) describe(m) else "未計測")
            if (m != null && m.animations.isNotEmpty()) Small("動き続ける部品: ${m.animations.joinToString("、")}")
            Small("${w.page}ページ目・直近1時間の更新 ${w.stat?.recentUpdates?.size ?: 0}回")
        }
        if (m != null) {
            Text(
                if (m.heavy) "重め" else "軽い",
                fontWeight = FontWeight.Medium,
                color = if (m.heavy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

internal const val GB = 1024f * 1024f * 1024f

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

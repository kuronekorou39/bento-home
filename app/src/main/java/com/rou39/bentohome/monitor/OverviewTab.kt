package com.rou39.bentohome.monitor

import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

private val WINDOWS = listOf(5 * 60_000L to "5分", MonitorRepository.WINDOW_MS to "30分")
private const val TOP_APPS = 5

/** 概要の数値カード。選んだカードの大きいグラフをカードの下に出す。 */
private enum class Tile { CPU, MEMORY, TEMP, BATTERY, NETWORK, THERMAL }

/**
 * カード1枚と、選んだときの大きいグラフの両方の材料。
 * カードには [series] の先頭の値を大きく出し、2つ目以降は小さく添える。
 */
private class TileSpec(
    val tile: Tile,
    val title: String,
    /** 大きいグラフの見出し。カードは幅が狭いので短い [title] を使う。 */
    val chartTitle: String,
    val series: List<ChartSeries>,
    val format: (Float) -> String,
    val yRange: YRange,
    /** カードの値の下に添える一言。 */
    val extra: String? = null,
    /** 大きいグラフの下の説明。 */
    val note: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewTab(vm: MonitorViewModel, open: (Intent) -> Unit) {
    val s by vm.state.collectAsState()
    val rows by vm.rows.collectAsState()
    var windowMs by rememberSaveable { mutableLongStateOf(WINDOWS[0].first) }
    // 最初は CPU を開いておき、何も選ばれていない空の画面にしない
    var selected by rememberSaveable { mutableStateOf<Tile?>(Tile.CPU) }
    val now = s.series.values.maxOfOrNull { it.lastOrNull()?.t ?: 0L }?.takeIf { it > 0 } ?: System.currentTimeMillis()
    val tiles = tileSpecs(s)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(TAB_PADDING),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AccessCard(vm, open)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WINDOWS.forEach { (ms, label) ->
                FilterChip(selected = windowMs == ms, onClick = { windowMs = ms }, label = { Text("直近$label") })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(2).forEach { pair ->
                // 同じ段のカードは高さを揃える
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { spec ->
                        MetricTile(
                            spec, now, windowMs, selected == spec.tile,
                            Modifier.weight(1f).fillMaxHeight(),
                        ) { selected = if (selected == spec.tile) null else spec.tile }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        tiles.firstOrNull { it.tile == selected }?.let { spec ->
            SectionCard(null, Modifier.animateContentSize()) {
                MetricChart(spec.chartTitle, spec.series, spec.format, now, windowMs, spec.yRange, note = spec.note)
                if (spec.tile == Tile.CPU && !s.series[Metric.CPU].isNullOrEmpty()) TopCpuApps(rows)
            }
        }
    }
}

private fun tileSpecs(s: MonitorState): List<TileSpec> {
    fun series(label: String, m: Metric) = ChartSeries(label, s.series[m].orEmpty())
    val cpu = if (s.series[Metric.CPU].isNullOrEmpty()) {
        TileSpec(
            Tile.CPU, "CPU 周波数", "CPU 周波数", listOf(series("周波数", Metric.CPU_FREQ)), ::percent, YRange.Fixed(0f, 100f),
            note = "全コアの最大周波数に対する平均。使用率は計測用プロセスを起動すると表示します",
        )
    } else {
        TileSpec(
            Tile.CPU, "CPU 使用率", "CPU 使用率", listOf(series("CPU", Metric.CPU)), ::percent, YRange.Fixed(0f, 100f),
            note = "全コアの合計を 100% とした値",
        )
    }
    val usedGb = (s.memTotal - s.memAvail) / GB
    val totalGb = s.memTotal / GB
    val memory = TileSpec(
        Tile.MEMORY, "メモリ", "メモリ", listOf(series("使用中", Metric.MEMORY)), ::percent, YRange.Fixed(0f, 100f),
        extra = if (s.memTotal > 0) String.format(Locale.JAPAN, "%.1f / %.1f GB", usedGb, totalGb) else null,
        note = if (s.memTotal > 0) String.format(Locale.JAPAN, "使用中 %.1f GB / 全体 %.1f GB", usedGb, totalGb) else null,
    )
    val temps = listOf(series("表面", Metric.TEMP_SKIN), series("電池", Metric.TEMP_BATTERY), series("CPU", Metric.TEMP_CPU))
        .filter { it.points.isNotEmpty() }
    val temp = temps.takeIf { it.isNotEmpty() }?.let {
        TileSpec(
            Tile.TEMP, "温度（${it[0].label}）", "温度", it, ::celsius, YRange.Fit,
            note = if (it.size == 1) "表面と CPU の温度は、温度の読み取り権限を付与すると表示します" else null,
        )
    }
    val level = if (s.batteryLevel >= 0) "残量 ${s.batteryLevel}%・${if (s.charging) "充電中" else "放電中"}" else null
    val battery = TileSpec(
        Tile.BATTERY, "電池の電流", "電池の電流", listOf(series("電流", Metric.BATTERY_CURRENT)), ::milliamp, YRange.FromZero,
        extra = level,
        note = listOfNotNull(level, "正の値は充電、負の値は放電").joinToString("。"),
    )
    val network = TileSpec(
        Tile.NETWORK, "通信（受信）", "通信速度", listOf(series("受信", Metric.NET_RX), series("送信", Metric.NET_TX)),
        ::rate, YRange.FromZero,
    )
    val thermal = TileSpec(
        Tile.THERMAL, "発熱の度合い", "発熱による制限までの余裕", listOf(series("使用", Metric.THERMAL_HEADROOM)), ::percent,
        YRange.Fixed(0f, 100f),
        extra = "100% で制限・${thermalLabel(s.thermalStatus)}",
        note = "100% に達すると OS が性能を抑え始めます。現在の状態: ${thermalLabel(s.thermalStatus)}",
    )
    return listOfNotNull(cpu, memory, temp, battery, network, thermal)
}

/** 数値カード。名前・今の値・直近の推移を1枚に収め、押すと大きいグラフを開く。 */
@Composable
private fun MetricTile(spec: TileSpec, now: Long, windowMs: Long, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = seriesColors()
    fun latest(s: ChartSeries) = s.points.lastOrNull()?.v?.let(spec.format) ?: "—"
    val value = spec.series.firstOrNull()?.let(::latest) ?: "—"
    val others = spec.series.drop(1)
    val description = listOfNotNull(
        "${spec.title} $value",
        others.joinToString("、") { "${it.label} ${latest(it)}" }.ifEmpty { null },
        spec.extra,
    ).joinToString("、")
    Card(
        onClick = onClick,
        modifier = modifier.semantics { selected = isSelected },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxHeight().padding(12.dp).clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                spec.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            others.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 小さいグラフの線と色で対応させる
                    Box(Modifier.size(6.dp).background(colors[(i + 1) % colors.size], CircleShape))
                    Text("${s.label} ${latest(s)}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            spec.extra?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Sparkline(spec.series, now, windowMs, spec.yRange, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TopCpuApps(rows: List<AppRow>) {
    val top = AppRows.sorted(rows, AppSort.CPU).take(TOP_APPS)
    if (top.isEmpty()) return
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("CPU を多く使っているもの", style = MaterialTheme.typography.titleSmall)
        top.forEach { r ->
            Row {
                Text(r.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(percent(r.cpuPercent ?: 0f))
            }
        }
    }
}

/**
 * 表示できる情報を増やす手順。普段は1行に畳み、必要なときだけ開いてコマンドを見る。
 * 計測用プロセスが動いていて権限も揃っていれば、出すことが無いので丸ごと隠す。
 */
@Composable
private fun AccessCard(vm: MonitorViewModel, open: (Intent) -> Unit) {
    val status by vm.privilegedStatus.collectAsState()
    val usage by vm.usageGranted.collectAsState()
    val dump by vm.dumpGranted.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val connected = status == PrivilegedBridge.Status.READY
    if (connected && usage && dump) return
    val summary = listOfNotNull(
        "計測用プロセスが未起動".takeIf { !connected },
        "使用状況へのアクセスが未許可".takeIf { !usage },
        "温度の読み取りが未許可".takeIf { !dump },
    ).joinToString("・")
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "閉じる" else "開く") { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "開いています" else "閉じています" }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("表示できる情報を増やす", style = MaterialTheme.typography.titleSmall)
                Small(summary)
            }
            Text(if (expanded) "▴" else "▾", modifier = Modifier.clearAndSetSemantics {})
        }
        if (!expanded) return@Card
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!connected) {
                Small("全プロセスの CPU・メモリと端末の CPU 使用率は、PC につないで次のコマンドで計測用プロセスを起動すると表示されます。端末を再起動するまで動き続けます。")
                Text(
                    vm.startCommand,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                )
                OutlinedButton(onClick = vm::copyStartCommand) { Text("コマンドをコピー") }
            }
            if (!usage || !dump) {
                Small(
                    buildList {
                        if (!usage) add("使用状況へのアクセス（アプリごとの使用時間・通信量・容量）")
                        if (!dump) add("温度の読み取り（表面・CPU の温度）")
                    }.joinToString("、") + "が未許可です。",
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

private fun celsius(v: Float) = String.format(Locale.JAPAN, "%.1f℃", v)
private fun milliamp(v: Float) = String.format(Locale.JAPAN, "%.0f mA", v)

private fun rate(kbPerSec: Float) = when {
    kbPerSec >= 1024f -> String.format(Locale.JAPAN, "%.1f MB/s", kbPerSec / 1024f)
    else -> String.format(Locale.JAPAN, "%.0f KB/s", kbPerSec)
}

package com.rou39.bentohome.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartSeries(val label: String, val points: List<Point>)

/** 縦軸の範囲の決め方。 */
sealed interface YRange {
    data class Fixed(val min: Float, val max: Float) : YRange

    /** 0 から、見えている最大値の少し上まで。 */
    data object FromZero : YRange

    /** 見えている最小値から最大値まで（温度など 0 が意味を持たない値）。 */
    data object Fit : YRange
}

/**
 * 系列の色。参照パレットの先頭3色（青・橙・緑青）を明暗それぞれの値で使う。
 * 3色は全組み合わせで色覚の違いがあっても見分けられることを確認済みの並び。
 */
@Composable
fun seriesColors(): List<Color> = if (isSystemInDarkTheme()) {
    listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70))
} else {
    listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A))
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm:ss")

/** これより間が空いた点は線でつながない（ホームを離れていた間など）。 */
private const val GAP_MS = 30_000L

/**
 * 時系列の折れ線グラフ。縦軸は1つだけ。2系列以上なら凡例に名前と値を出す。
 * 押さえたまま横に動かすと、その時刻の値を見出しに出す。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricChart(
    title: String,
    series: List<ChartSeries>,
    format: (Float) -> String,
    now: Long,
    windowMs: Long,
    yRange: YRange,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val colors = seriesColors()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val measurer = rememberTextMeasurer()
    var scrubT by remember { mutableStateOf<Long?>(null) }
    val start = now - windowMs
    val startState = rememberUpdatedState(start)
    val windowState = rememberUpdatedState(windowMs)
    val visible = series.map { s -> s.copy(points = s.points.filter { it.t >= start - GAP_MS }) }
    val values = visible.flatMap { s -> s.points.filter { it.t >= start }.map { it.v } }
    val (yMin, yMax) = range(yRange, values)

    fun valueAt(s: ChartSeries, t: Long?): Float? =
        if (t == null) s.points.lastOrNull()?.v else s.points.minByOrNull { abs(it.t - t) }?.v

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val head = if (series.size == 1) valueAt(visible[0], scrubT)?.let(format) ?: "—" else null
            val time = scrubT?.let { TIME_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
            Text(listOfNotNull(time, head).joinToString("  "), style = MaterialTheme.typography.titleSmall)
        }
        if (series.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                visible.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(colors[i % colors.size], CircleShape))
                        Text("${s.label} ${valueAt(s, scrubT)?.let(format) ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        val summary = visible.joinToString("、") { s -> "${s.label} ${s.points.lastOrNull()?.v?.let(format) ?: "値なし"}" }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = "$title のグラフ。現在 $summary" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun toT(x: Float) = startState.value + (x / size.width * windowState.value).toLong()
                        scrubT = toT(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            scrubT = toT(change.position.x.coerceIn(0f, size.width.toFloat()))
                        }
                        scrubT = null
                    }
                },
        ) {
            val labelWidth = 44.dp.toPx()
            val plotLeft = labelWidth
            val plotW = size.width - plotLeft
            val plotH = size.height - 4.dp.toPx()
            fun x(t: Long) = plotLeft + (t - start).toFloat() / windowMs * plotW
            fun y(v: Float) = plotH - (v - yMin) / (yMax - yMin) * plotH + 2.dp.toPx()

            // 目盛りは控えめに3本
            for (i in 0..2) {
                val v = yMin + (yMax - yMin) * i / 2f
                val yy = y(v)
                drawLine(grid, Offset(plotLeft, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx())
                val text = measurer.measure(format(v), labelStyle)
                drawText(text, topLeft = Offset(0f, (yy - text.size.height / 2f).coerceIn(0f, size.height - text.size.height)))
            }
            visible.forEachIndexed { i, s ->
                val path = Path()
                var prev: Point? = null
                for (p in s.points) {
                    val px = x(p.t).coerceAtLeast(plotLeft)
                    if (prev == null || p.t - prev.t > GAP_MS) path.moveTo(px, y(p.v)) else path.lineTo(px, y(p.v))
                    prev = p
                }
                drawPath(
                    path,
                    colors[i % colors.size],
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // 点が1つしか無いときも見えるように最新点に印を付ける
                s.points.lastOrNull()?.let { drawCircle(colors[i % colors.size], 3.dp.toPx(), Offset(x(it.t), y(it.v))) }
            }
            scrubT?.let { t ->
                val xx = x(t).coerceIn(plotLeft, size.width)
                drawLine(muted, Offset(xx, 0f), Offset(xx, size.height), strokeWidth = 1.dp.toPx())
            }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
    }
}

private fun range(yRange: YRange, values: List<Float>): Pair<Float, Float> = when (yRange) {
    is YRange.Fixed -> yRange.min to yRange.max
    YRange.FromZero -> {
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(values.minOrNull()?.let { -it } ?: 0f)
        val top = nice(max * 1.15f)
        val min = values.minOrNull() ?: 0f
        (if (min < 0) -nice(-min * 1.15f) else 0f) to top
    }
    YRange.Fit -> {
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        floor(min - 1f) to ceil(max + 1f).coerceAtLeast(floor(min - 1f) + 2f)
    }
}

/** 1・2・5 の倍数に切り上げる。0 のときは 1。 */
private fun nice(v: Float): Float {
    if (v <= 0f) return 1f
    val mag = 10f.pow(floor(log10(v)))
    val n = v / mag
    return mag * when {
        n <= 1f -> 1f
        n <= 2f -> 2f
        n <= 5f -> 5f
        else -> 10f
    }
}

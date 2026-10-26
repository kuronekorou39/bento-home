package com.rou39.bentohome.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** グラフの1点。t は壁時計の時刻（ミリ秒）。 */
data class Point(val t: Long, val v: Float)

enum class Metric {
    /** 端末全体の CPU 使用率（%）。シェル権限が必要。 */
    CPU,

    /** CPU 周波数の最大値に対する平均（%）。権限なしで読める。 */
    CPU_FREQ,
    MEMORY,
    NET_RX,
    NET_TX,
    TEMP_SKIN,
    TEMP_BATTERY,
    TEMP_CPU,

    /** 電池の電流（mA、正は充電）。 */
    BATTERY_CURRENT,

    /** 発熱による制限までの余裕（1.0 で制限が始まる）を % で表す。 */
    THERMAL_HEADROOM,
}

data class MonitorState(
    val series: Map<Metric, List<Point>> = emptyMap(),
    val memTotal: Long = 0,
    val memAvail: Long = 0,
    val batteryLevel: Int = -1,
    val charging: Boolean = false,
    val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    /** 直近のプロセスごとの負荷。シェル権限が無ければ空。 */
    val processes: List<ProcessLoad> = emptyList(),
    val processesAt: Long = 0,
) {
    fun latest(metric: Metric): Float? = series[metric]?.lastOrNull()?.v
}

/**
 * 端末の状態を定期的に読み、直近 [WINDOW_MS] 分の時系列を持つ（docs/monitor.md）。
 * 読むのは画面が見ている間だけ。モニター画面は短い間隔で詳しく、ホームは長い間隔で軽い値だけ読む。
 */
class MonitorRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val privileged: PrivilegedBridge,
) {
    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val activity = context.getSystemService(ActivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)

    /** 読み取りを求めている画面。値は詳しく読むかどうか。 */
    private val clients = MutableStateFlow<Map<Any, Boolean>>(emptyMap())

    private var lastTraffic: Triple<Long, Long, Long>? = null
    private var lastSnapshot: ProcSnapshot? = null
    private var lastThermalAt = 0L
    private val cpuMaxFreq: List<Long> by lazy { cpuFiles("cpuinfo_max_freq").map { readLong(it) ?: 0L } }

    init {
        scope.launch(Dispatchers.Default) {
            clients.collectLatest { map ->
                if (map.isEmpty()) return@collectLatest
                val detailed = map.values.any { it }
                val interval = if (detailed) DETAILED_INTERVAL_MS else LIGHT_INTERVAL_MS
                while (isActive) {
                    runCatching { sample(detailed) }.onFailure { Log.w(TAG, "sample failed", it) }
                    delay(interval)
                }
            }
        }
    }

    /** 画面が見えている間だけ呼ぶ。戻り値を閉じると読み取りをやめる。 */
    fun attach(detailed: Boolean): AutoCloseable {
        val token = Any()
        clients.update { it + (token to detailed) }
        if (detailed) privileged.acquire()
        return AutoCloseable {
            clients.update { it - token }
            if (detailed) privileged.release()
        }
    }

    private fun sample(detailed: Boolean) {
        val now = System.currentTimeMillis()
        val points = mutableMapOf<Metric, Float>()

        val mem = ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }
        points[Metric.MEMORY] = (mem.totalMem - mem.availMem) * 100f / mem.totalMem

        // 端末全体の通信量はアプリの権限でも読める
        val elapsed = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        lastTraffic?.let { (t0, rx0, tx0) ->
            val sec = (elapsed - t0) / 1000f
            if (sec > 0 && rx >= rx0 && tx >= tx0) {
                points[Metric.NET_RX] = (rx - rx0) / 1024f / sec
                points[Metric.NET_TX] = (tx - tx0) / 1024f / sec
            }
        }
        lastTraffic = Triple(elapsed, rx, tx)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val plugged = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { points[Metric.TEMP_BATTERY] = it / 10f }
        battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE && it != 0 }?.let { points[Metric.BATTERY_CURRENT] = it / 1000f }

        power.getThermalHeadroom(0).takeIf { !it.isNaN() }?.let { points[Metric.THERMAL_HEADROOM] = it * 100f }

        cpuFrequencyPercent()?.let { points[Metric.CPU_FREQ] = it }

        var processes: List<ProcessLoad>? = null
        if (detailed) {
            val service = privileged.service.value
            val text = service?.let { runCatching { it.snapshot() }.getOrNull() }
            if (text != null) {
                val snap = ProcSnapshot.parse(text)
                lastSnapshot?.let { prev ->
                    val load = ProcMath.load(prev, snap)
                    points[Metric.CPU] = load.totalPercent
                    processes = load.processes
                }
                snap.batteryCurrentUa?.let { points[Metric.BATTERY_CURRENT] = it / 1000f }
                lastSnapshot = snap
            } else {
                lastSnapshot = null
            }
            if (now - lastThermalAt >= THERMAL_INTERVAL_MS) {
                lastThermalAt = now
                thermal()?.let { t ->
                    t.skin?.let { points[Metric.TEMP_SKIN] = it }
                    t.cpu?.let { points[Metric.TEMP_CPU] = it }
                    t.battery?.let { points[Metric.TEMP_BATTERY] = it }
                }
            }
        }

        _state.update { s ->
            val series = s.series.toMutableMap()
            points.forEach { (metric, v) ->
                val list = series[metric].orEmpty()
                series[metric] = (list.dropWhile { it.t < now - WINDOW_MS } + Point(now, v)).takeLast(MAX_POINTS)
            }
            s.copy(
                series = series,
                memTotal = mem.totalMem,
                memAvail = mem.availMem,
                batteryLevel = level,
                charging = plugged,
                thermalStatus = power.currentThermalStatus,
                processes = processes ?: s.processes,
                processesAt = if (processes != null) now else s.processesAt,
            )
        }
    }

    /** 温度は DUMP 権限があれば自分で、無ければシェル権限のプロセスで読む。 */
    private fun thermal(): Temperatures? {
        val text = if (hasDump()) {
            runCatching {
                val p = ProcessBuilder("dumpsys", "thermalservice").redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText().also { p.waitFor() }
            }.getOrNull()
        } else {
            privileged.exec("dumpsys", "thermalservice")
        }
        return text?.let { Temperatures.parse(it) }
    }

    fun hasDump(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    private fun cpuFrequencyPercent(): Float? {
        val max = cpuMaxFreq
        if (max.isEmpty() || max.any { it <= 0 }) return null
        val cur = cpuFiles("scaling_cur_freq").map { readLong(it) ?: return null }
        if (cur.size != max.size) return null
        return cur.indices.map { cur[it] * 100f / max[it] }.average().toFloat()
    }

    private fun cpuFiles(name: String): List<File> =
        (0 until Runtime.getRuntime().availableProcessors())
            .map { File("/sys/devices/system/cpu/cpu$it/cpufreq/$name") }

    private fun readLong(file: File): Long? = runCatching { file.readText().trim().toLong() }.getOrNull()

    companion object {
        private const val TAG = "Monitor"

        /** グラフに残す長さ。 */
        const val WINDOW_MS = 30 * 60_000L
        private const val MAX_POINTS = 1200
        private const val DETAILED_INTERVAL_MS = 2_000L
        private const val LIGHT_INTERVAL_MS = 10_000L

        /** dumpsys は別プロセスを起こすので、間隔を空ける。 */
        private const val THERMAL_INTERVAL_MS = 6_000L
    }
}

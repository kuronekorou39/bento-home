package com.rou39.bentohome.monitor

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterViewFlipper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.ViewFlipper
import android.graphics.drawable.Animatable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** ある時点の Bento 自身の累計値。2つの差で区間の負荷を求める。 */
data class SelfCounters(val elapsedMs: Long, val cpuMs: Long, val frames: Long) {
    operator fun minus(o: SelfCounters) = SelfCounters(elapsedMs - o.elapsedMs, cpuMs - o.cpuMs, frames - o.frames)
    operator fun plus(o: SelfCounters) = SelfCounters(elapsedMs + o.elapsedMs, cpuMs + o.cpuMs, frames + o.frames)

    /** 1コアを 100% とした CPU 使用率。 */
    val cpuPercent: Float get() = if (elapsedMs > 0) cpuMs * 100f / elapsedMs else 0f
    val framesPerSecond: Float get() = if (elapsedMs > 0) frames * 1000f / elapsedMs else 0f
}

/**
 * Bento 自身の負荷（docs/monitor.md）。ホームの描画フレーム数と、プロセスの CPU 時間を数える。
 * ウィジェットの描画は Bento のプロセスで行うので、ウィジェットの負荷もここに含まれる。
 */
class SelfMetrics {
    private val frames = AtomicLong()
    private val thread by lazy { HandlerThread("frame-metrics").apply { start() } }

    /** ホームを表示していた間の合計。 */
    private var homeTotal = SelfCounters(0, 0, 0)
    private var homeStartedAt: SelfCounters? = null

    var homeVisible = false
        private set

    /** ホームを表示し直した回数。計測の途中でホームを離れたかの判定に使う。 */
    var homeStarts = 0
        private set

    fun now() = SelfCounters(SystemClock.elapsedRealtime(), Process.getElapsedCpuTime(), frames.get())

    /** フレームが描かれたときだけ呼ばれる。待機中は何もしない。 */
    fun observe(window: Window) {
        window.addOnFrameMetricsAvailableListener(
            { _, _, dropped -> frames.addAndGet(1L + dropped) },
            Handler(thread.looper),
        )
    }

    fun onHomeStart() {
        homeVisible = true
        homeStarts++
        homeStartedAt = now()
    }

    fun onHomeStop() {
        homeVisible = false
        homeStartedAt?.let { start ->
            val d = now() - start
            homeTotal = SelfCounters(homeTotal.elapsedMs + d.elapsedMs, homeTotal.cpuMs + d.cpuMs, homeTotal.frames + d.frames)
        }
        homeStartedAt = null
    }

    /** 起動してからホームを表示していた間の合計。 */
    fun homeTotals(): SelfCounters {
        val running = homeStartedAt?.let { now() - it } ?: SelfCounters(0, 0, 0)
        return SelfCounters(homeTotal.elapsedMs + running.elapsedMs, homeTotal.cpuMs + running.cpuMs, homeTotal.frames + running.frames)
    }
}

/** ウィジェット1つの負荷の計測結果（表示したときと隠したときの差）。 */
data class WidgetMeasurement(
    val at: Long,
    val shown: SelfCounters,
    val hidden: SelfCounters,
    /** 見つかったアニメーションする部品。 */
    val animations: List<String>,
) {
    val cpuPercent: Float get() = (shown.cpuPercent - hidden.cpuPercent).coerceAtLeast(0f)
    val framesPerSecond: Float get() = (shown.framesPerSecond - hidden.framesPerSecond).coerceAtLeast(0f)
    val heavy: Boolean get() = cpuPercent >= HEAVY_CPU_PERCENT || framesPerSecond >= HEAVY_FPS

    companion object {
        /**
         * これ以上なら、後ろのページへ移すことを勧める。CPU は他のウィジェットの揺らぎで数%ぶれるので、
         * 誤差を超える値にしている（docs/monitor.md）。
         */
        const val HEAVY_CPU_PERCENT = 8f
        const val HEAVY_FPS = 5f
    }
}

data class WidgetStat(
    val updates: Int = 0,
    /** 直近1時間の更新時刻。 */
    val recentUpdates: List<Long> = emptyList(),
    val lastUpdateAt: Long = 0,
    val measurement: WidgetMeasurement? = null,
)

/** ウィジェットごとの更新回数と計測結果。プロセスが生きている間だけ持つ。 */
class WidgetMetrics {
    private val _stats = MutableStateFlow<Map<Int, WidgetStat>>(emptyMap())
    val stats: StateFlow<Map<Int, WidgetStat>> = _stats.asStateFlow()

    val since: Long = System.currentTimeMillis()

    fun onUpdate(widgetId: Int) {
        val now = System.currentTimeMillis()
        _stats.update { map ->
            val s = map[widgetId] ?: WidgetStat()
            map + (widgetId to s.copy(
                updates = s.updates + 1,
                recentUpdates = s.recentUpdates.filter { it > now - HOUR_MS } + now,
                lastUpdateAt = now,
            ))
        }
    }

    fun record(widgetId: Int, m: WidgetMeasurement) {
        _stats.update { map -> map + (widgetId to (map[widgetId] ?: WidgetStat()).copy(measurement = m)) }
    }

    companion object {
        const val HOUR_MS = 3_600_000L

        /** View の木から、動き続ける部品を探す。表示中に呼ぶ。 */
        fun detectAnimations(root: View): List<String> {
            val found = linkedSetOf<String>()
            fun walk(v: View) {
                if (v.visibility != View.VISIBLE) return
                when (v) {
                    is ImageView -> if ((v.drawable as? Animatable)?.isRunning == true) found += "アニメーション画像"
                    is ProgressBar -> if (v.isIndeterminate) found += "回転する読み込み表示"
                    is ViewFlipper -> if (v.isFlipping) found += "自動で切り替わる表示"
                    is AdapterViewFlipper -> if (v.isFlipping) found += "自動で切り替わる表示"
                    is TextClock -> {
                        val format = (if (v.is24HourModeEnabled) v.format24Hour else v.format12Hour)?.toString().orEmpty()
                        if ('s' in format) found += "秒を表示する時計"
                    }
                }
                v.background?.let { if ((it as? Animatable)?.isRunning == true) found += "アニメーション背景" }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(root)
            return found.toList()
        }
    }
}

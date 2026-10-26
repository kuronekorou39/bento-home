package com.rou39.bentohome.monitor

/** シェル権限のプロセスが返す /proc の値（docs/monitor.md）。 */
data class ProcSnapshot(
    val time: Long,
    /** 全 CPU の合計 tick（idle を含む）。 */
    val cpuTotal: Long,
    val cpuIdle: Long,
    val netRx: Long,
    val netTx: Long,
    /** 電池の電流（µA、正は充電）。読めなければ null。 */
    val batteryCurrentUa: Long?,
    val processes: List<ProcLine>,
) {
    data class ProcLine(val pid: Int, val uid: Int, val ticks: Long, val rssKb: Long, val name: String)

    companion object {
        fun parse(text: String): ProcSnapshot {
            var time = 0L
            var total = 0L
            var idle = 0L
            var rx = -1L
            var tx = -1L
            var current: Long? = null
            val procs = ArrayList<ProcLine>()
            text.lineSequence().forEach { line ->
                val parts = line.split(' ')
                when (parts[0]) {
                    "T" -> time = parts[1].toLong()
                    "CPU" -> {
                        val v = parts.drop(1).filter { it.isNotEmpty() }.map { it.toLong() }
                        total = v.sum()
                        // idle と iowait を待機とみなす
                        idle = v.getOrElse(3) { 0 } + v.getOrElse(4) { 0 }
                    }
                    "NET" -> {
                        rx = parts[1].toLong()
                        tx = parts[2].toLong()
                    }
                    "BAT" -> current = parts[1].toLong().takeIf { it != Long.MIN_VALUE }
                    "P" -> if (parts.size >= 6) {
                        procs += ProcLine(
                            parts[1].toInt(), parts[2].toInt(), parts[3].toLong(), parts[4].toLong(),
                            parts.subList(5, parts.size).joinToString(" "),
                        )
                    }
                }
            }
            return ProcSnapshot(time, total, idle, rx, tx, current, procs)
        }
    }
}

/** 2つの時点の差から求めた、プロセスごとの負荷。CPU は端末全体（全コア）を 100% とする。 */
data class ProcessLoad(val pid: Int, val uid: Int, val name: String, val cpuPercent: Float, val rssKb: Long)

data class CpuLoad(val totalPercent: Float, val processes: List<ProcessLoad>)

object ProcMath {
    fun load(prev: ProcSnapshot, next: ProcSnapshot): CpuLoad {
        val dTotal = (next.cpuTotal - prev.cpuTotal).coerceAtLeast(1)
        val dIdle = next.cpuIdle - prev.cpuIdle
        val total = ((dTotal - dIdle) * 100f / dTotal).coerceIn(0f, 100f)
        val before = prev.processes.associateBy { it.pid }
        val loads = next.processes.map { p ->
            // pid が使い回されたときは名前か tick が合わないので、新しいプロセスとして 0 から数える
            val old = before[p.pid]?.takeIf { it.name == p.name && it.ticks <= p.ticks }
            val dt = p.ticks - (old?.ticks ?: p.ticks)
            ProcessLoad(p.pid, p.uid, p.name, (dt * 100f / dTotal).coerceIn(0f, 100f), p.rssKb)
        }
        return CpuLoad(total, loads)
    }
}

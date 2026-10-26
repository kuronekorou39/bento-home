package com.rou39.bentohome.monitor.priv

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Shizuku がシェル権限（uid 2000）で起動するプロセスで動く。
 * アプリの権限では読めない /proc の値をまとめて返す（docs/monitor.md）。
 * 形式は [com.rou39.bentohome.monitor.ProcSnapshot] が解析する。
 */
class PrivilegedService : IPrivilegedService.Stub() {
    /** uid とプロセス名は起動中に変わらないので、pid と開始時刻で覚えておく。 */
    private class Known(val uid: Int, val name: String)
    private val procs = HashMap<Long, Known>()

    private class Stat(val comm: String, val ticks: Long, val start: Long, val rssPages: Long)

    /** 2秒ごとに数百のファイルを読むので、文字列の分割を避けてバイト列から直接数値を拾う。 */
    private val buffer = ByteArray(1024)

    override fun destroy() {
        exitProcess(0)
    }

    @Synchronized
    override fun snapshot(): String = buildString(64 * 1024) {
        append("T ").append(System.currentTimeMillis()).append('\n')
        File("/proc/stat").useLines { lines ->
            lines.firstOrNull()?.let { append("CPU ").append(it.removePrefix("cpu").trim()).append('\n') }
        }
        append("NET ").append(netTotals()).append('\n')
        append("BAT ").append(readLong("/sys/class/power_supply/battery/current_now")).append('\n')
        val alive = HashSet<Long>()
        File("/proc").list()?.forEach { entry ->
            val pid = entry.toIntOrNull() ?: return@forEach
            val stat = readStat(pid) ?: return@forEach
            val key = (pid.toLong() shl 32) xor stat.start
            alive += key
            val known = procs.getOrPut(key) {
                val uid = try { Os.stat("/proc/$pid").st_uid } catch (_: Exception) { return@forEach }
                Known(uid, cmdline(pid) ?: "[${stat.comm}]")
            }
            append("P ").append(pid).append(' ').append(known.uid).append(' ').append(stat.ticks).append(' ')
                .append(stat.rssPages * PAGE_KB).append(' ').append(known.name).append('\n')
        }
        procs.keys.retainAll(alive)
    }

    override fun exec(command: Array<String>): String = try {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(EXEC_TIMEOUT_S, TimeUnit.SECONDS)) p.destroy()
        out
    } catch (e: Exception) {
        "error: ${e.message}"
    }

    private fun readStat(pid: Int): Stat? {
        val n = try {
            FileInputStream("/proc/$pid/stat").use { it.read(buffer) }
        } catch (_: Exception) {
            return null
        }
        if (n <= 0) return null
        var close = n - 1
        while (close > 0 && buffer[close] != ')'.code.toByte()) close--
        val open = buffer.indexOf('('.code.toByte())
        if (close <= 0 || open < 0) return null
        // ')' の後ろは3番目の項目（state）から始まる。utime=14, stime=15, starttime=22, rss=24
        var field = 3
        var i = close + 2
        var utime = 0L
        var stime = 0L
        var start = 0L
        var rss = 0L
        while (i < n && field <= 24) {
            var v = 0L
            val begin = i
            while (i < n && buffer[i] != ' '.code.toByte()) {
                val d = buffer[i] - '0'.code.toByte()
                if (d in 0..9) v = v * 10 + d
                i++
            }
            if (i > begin) {
                when (field) {
                    14 -> utime = v
                    15 -> stime = v
                    22 -> start = v
                    24 -> rss = v
                }
            }
            field++
            i++
        }
        if (field <= 24) return null
        return Stat(String(buffer, open + 1, close - open - 1), utime + stime, start, rss)
    }

    private fun cmdline(pid: Int): String? = try {
        File("/proc/$pid/cmdline").readBytes()
            .takeWhile { it != 0.toByte() }.toByteArray().decodeToString().trim()
            .takeIf { it.isNotEmpty() }
            ?.replace(' ', '_')
    } catch (_: Exception) {
        null
    }

    /** lo 以外の全インターフェースの受信・送信バイト数。 */
    private fun netTotals(): String = try {
        var rx = 0L
        var tx = 0L
        File("/proc/net/dev").readLines().drop(2).forEach { line ->
            val parts = line.split(':', limit = 2)
            if (parts.size != 2 || parts[0].trim() == "lo") return@forEach
            val v = parts[1].trim().split(Regex("\\s+"))
            rx += v[0].toLong()
            tx += v[8].toLong()
        }
        "$rx $tx"
    } catch (_: Exception) {
        "-1 -1"
    }

    private fun readLong(path: String): Long =
        try { File(path).readText().trim().toLong() } catch (_: Exception) { Long.MIN_VALUE }

    private companion object {
        val PAGE_KB = Os.sysconf(OsConstants._SC_PAGESIZE) / 1024
        const val EXEC_TIMEOUT_S = 20L
    }
}

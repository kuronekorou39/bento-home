package com.rou39.bentohome.monitor

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 前回のフル充電からの電池の使用量（mAh）。background は前面以外（裏・常駐・キャッシュ）の合計。 */
data class BatteryUse(val totalMah: Double, val backgroundMah: Double)

/** カメラ・マイク・位置情報を最後に使った時刻（ミリ秒）。使っていなければ null。 */
data class SensitiveUse(val cameraAt: Long? = null, val microphoneAt: Long? = null, val locationAt: Long? = null)

/**
 * 棚卸し（plan.md §10）で、計測用プロセスから受け取る文字列を読む。Android に依存しない（単体テスト対象）。
 * どちらも OS の表示用の出力なので、形が変わったら読めた分だけ返す。
 */
object InventoryParsing {
    /** 計測用プロセスで電池の使用量の行だけを取り出すコマンド。 */
    val BATTERY_COMMAND = arrayOf(
        "sh", "-c", "dumpsys batterystats | awk '/Estimated power use/{on=1} on && /^  UID /{print}'",
    )

    /** 計測用プロセスで、カメラ・マイク・位置情報の使用記録の行だけを取り出すコマンド（全体は約2MBあるため）。 */
    val APPOPS_COMMAND = arrayOf(
        "sh", "-c",
        "dumpsys appops | awk '/^    Package /{print; next} " +
            "/^      [A-Z_0-9]+ \\(/{keep = (\$1==\"CAMERA\"||\$1==\"RECORD_AUDIO\"||\$1==\"FINE_LOCATION\"||\$1==\"COARSE_LOCATION\"); " +
            "if (keep) print; next} keep && /Access:/{print}'",
    )

    private val UID_LINE = Regex("""^\s*UID (\S+): ([\d.]+)(.*)$""")
    private val FG_PART = Regex("""\bfg: ([\d.]+)""")
    private val ACCESS_TIME = Regex("""(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})""")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * `UID u0a515: 341 fg: 55.4 (24m 22s) bg: 7.26 cached: 15.2` の行を uid ごとにまとめる。
     * 同じ uid が複数の区間に出たら最初の行を使う（最初が前回のフル充電から）。
     */
    fun parseBattery(text: String): Map<Int, BatteryUse> {
        val result = LinkedHashMap<Int, BatteryUse>()
        for (line in text.lineSequence()) {
            val m = UID_LINE.find(line) ?: continue
            val uid = parseUid(m.groupValues[1]) ?: continue
            if (uid in result) continue
            val total = m.groupValues[2].toDoubleOrNull() ?: continue
            val fg = FG_PART.find(m.groupValues[3])?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            result[uid] = BatteryUse(total, (total - fg).coerceAtLeast(0.0))
        }
        return result
    }

    /** `u0a515` → 10515、`u10a5` → 1010005、`1000` → 1000。 */
    fun parseUid(text: String): Int? {
        text.toIntOrNull()?.let { return it }
        val m = Regex("""^u(\d+)a(\d+)$""").find(text) ?: return null
        val user = m.groupValues[1].toInt()
        val app = m.groupValues[2].toInt()
        return user * PER_USER_RANGE + FIRST_APPLICATION_UID + app
    }

    /** パッケージごとに、カメラ・マイク・位置情報（細かい・おおまか）の最後の使用時刻。 */
    fun parseAppOps(text: String, zone: ZoneId = ZoneId.systemDefault()): Map<String, SensitiveUse> {
        val result = HashMap<String, SensitiveUse>()
        var pkg: String? = null
        var op: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("    Package ") -> {
                    pkg = line.removePrefix("    Package ").removeSuffix(":").trim()
                    op = null
                }
                line.startsWith("      ") && !line.startsWith("       ") && line.contains(" (") ->
                    op = line.trim().substringBefore(' ')
                line.contains("Access:") -> {
                    val p = pkg ?: continue
                    val time = ACCESS_TIME.find(line)?.groupValues?.get(1)?.let { parseTime(it, zone) } ?: continue
                    val cur = result[p] ?: SensitiveUse()
                    result[p] = when (op) {
                        "CAMERA" -> cur.copy(cameraAt = maxOf(cur.cameraAt ?: 0, time))
                        "RECORD_AUDIO" -> cur.copy(microphoneAt = maxOf(cur.microphoneAt ?: 0, time))
                        "FINE_LOCATION", "COARSE_LOCATION" -> cur.copy(locationAt = maxOf(cur.locationAt ?: 0, time))
                        else -> cur
                    }
                }
            }
        }
        return result
    }

    private fun parseTime(text: String, zone: ZoneId): Long? = runCatching {
        LocalDateTime.parse(text, TIME_FORMAT).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APPLICATION_UID = 10_000
}

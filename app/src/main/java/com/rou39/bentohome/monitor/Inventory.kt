package com.rou39.bentohome.monitor

/**
 * 棚卸しの値（plan.md §10）。インストールしているアプリを、整理するための値で並べる。
 * 値は読めたものだけ入る（使用状況へのアクセスが無い・計測用プロセスが無いときは null）。
 */
data class InventoryRow(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    /** OS の使用記録で最後に使った時刻（過去1年）。通知から開いた分なども含む。 */
    val lastUsedAt: Long?,
    /** Bento から最後に開いた時刻。 */
    val bentoLastLaunchAt: Long?,
    val storageBytes: Long?,
    /** 直近30日の受信+送信。 */
    val networkBytes: Long?,
    /** そのうち、画面に出ていないとき（裏）の分。 */
    val backgroundNetworkBytes: Long?,
    /** 前回のフル充電から。計測用プロセスがあるときだけ。 */
    val battery: BatteryUse?,
    /** カメラ・マイク・位置情報を最後に使った時刻。計測用プロセスがあるときだけ。 */
    val sensitive: SensitiveUse?,
)

data class InventoryState(
    val rows: List<InventoryRow> = emptyList(),
    val loading: Boolean = false,
    /** 使用状況へのアクセスがあって、使用記録・通信量・容量を読めたか。 */
    val usageRead: Boolean = false,
    /** 計測用プロセスで電池・権限を読めたか。 */
    val privilegedRead: Boolean = false,
    val loadedAt: Long = 0,
)

/** アプリのタブの1行。インストールしているアプリの値（棚卸し）と、動いているプロセスの値をまとめる。 */
data class AppListRow(
    val key: String,
    val label: String,
    /** アプリならパッケージ名。OS のプロセスなら null。 */
    val packageName: String?,
    /** システムのアプリか、起動できないアプリ・OS のプロセス。普段は隠す。 */
    val isSystem: Boolean,
    val inventory: InventoryRow?,
    val live: AppRow?,
)

object AppList {
    /** 棚卸しの行に、同じアプリのプロセスの値を重ねる。起動できないものは後ろに足す。 */
    fun merge(inventory: List<InventoryRow>, live: List<AppRow>): List<AppListRow> {
        val liveByPackage = live.filter { it.packageName != null }.associateBy { it.packageName }
        val known = inventory.mapTo(HashSet()) { it.packageName }
        return inventory.map { AppListRow(it.packageName, it.label, it.packageName, it.isSystem, it, liveByPackage[it.packageName]) } +
            live.filter { it.packageName !in known }.map { AppListRow(it.key, it.label, it.packageName, true, null, it) }
    }
}

enum class AppListSort(val label: String, private val live: Boolean = false) {
    UNUSED("使っていない順"),
    SCREEN("今日の使用時間", live = true),
    STORAGE("容量"),
    NETWORK("通信量"),
    BACKGROUND_NETWORK("裏の通信量"),
    BATTERY("電池"),
    CPU("CPU", live = true),
    MEMORY("メモリ", live = true),
    ;

    fun value(row: AppListRow): Double? = when (this) {
        UNUSED -> null
        SCREEN -> row.live?.foregroundMs?.toDouble()
        STORAGE -> row.inventory?.storageBytes?.toDouble()
        NETWORK -> row.inventory?.networkBytes?.toDouble()
        BACKGROUND_NETWORK -> row.inventory?.backgroundNetworkBytes?.toDouble()
        BATTERY -> row.inventory?.battery?.totalMah
        CPU -> row.live?.cpuPercent?.toDouble()
        MEMORY -> row.live?.rssKb?.toDouble()
    }

    /**
     * 並べ替え。今の値（CPU など）は 0 の行を省く。棚卸しの値は、値の無い行を後ろに回す
     * （使っていない順では、1年使っていないアプリを先頭）。
     */
    fun sort(rows: List<AppListRow>): List<AppListRow> = when {
        this == UNUSED -> rows.filter { it.inventory != null }
            .sortedWith(compareBy<AppListRow> { it.inventory?.lastUsedAt ?: Long.MIN_VALUE }.thenBy { it.label.lowercase() })
        live -> rows.filter { (value(it) ?: 0.0) > 0.0 }.sortedByDescending { value(it) }
        else -> rows.filter { it.inventory != null }.sortedByDescending { value(it) ?: -1.0 }
    }
}

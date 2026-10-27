package com.rou39.bentohome.monitor

/**
 * 棚卸しの1行（plan.md §10）。インストールしているアプリを、整理するための値で並べる。
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

enum class InventorySort(val label: String) {
    UNUSED("使っていない順"),
    STORAGE("容量"),
    NETWORK("通信量"),
    BACKGROUND_NETWORK("裏の通信量"),
    BATTERY("電池"),
    ;

    /** 並べ替え。値の無い行は後ろ（使っていない順では、1年使っていないアプリを先頭）。 */
    fun sort(rows: List<InventoryRow>): List<InventoryRow> = when (this) {
        UNUSED -> rows.sortedWith(compareBy<InventoryRow> { it.lastUsedAt ?: Long.MIN_VALUE }.thenBy { it.label.lowercase() })
        STORAGE -> rows.sortedByDescending { it.storageBytes ?: -1 }
        NETWORK -> rows.sortedByDescending { it.networkBytes ?: -1 }
        BACKGROUND_NETWORK -> rows.sortedByDescending { it.backgroundNetworkBytes ?: -1 }
        BATTERY -> rows.sortedByDescending { it.battery?.totalMah ?: -1.0 }
    }
}

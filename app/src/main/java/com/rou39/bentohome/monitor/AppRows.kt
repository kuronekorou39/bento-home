package com.rou39.bentohome.monitor

/** アプリ一覧の1行。プロセス・使用時間・通信量・容量をアプリ単位にまとめたもの。 */
data class AppRow(
    val key: String,
    val label: String,
    /** アプリなら パッケージ名。システムのプロセスなら null。 */
    val packageName: String?,
    val cpuPercent: Float?,
    val rssKb: Long?,
    val processes: List<ProcessLoad>,
    val foregroundMs: Long?,
    val networkBytes: Long?,
    val storageBytes: Long?,
    val uids: Set<Int>,
)

enum class AppSort(val label: String) {
    CPU("CPU"),
    MEMORY("メモリ"),
    SCREEN("今日の使用時間"),
    NETWORK("今日の通信量"),
    STORAGE("容量"),
    ;

    fun value(row: AppRow): Double? = when (this) {
        CPU -> row.cpuPercent?.toDouble()
        MEMORY -> row.rssKb?.toDouble()
        SCREEN -> row.foregroundMs?.toDouble()
        NETWORK -> row.networkBytes?.toDouble()
        STORAGE -> row.storageBytes?.toDouble()
    }
}

/** プロセスや uid をアプリにまとめる。Android に依存しない部分（単体テスト対象）。 */
object AppRows {
    /** これより小さい uid は OS のプロセス。android.os.Process.FIRST_APPLICATION_UID と同じ値。 */
    const val FIRST_APPLICATION_UID = 10000

    fun build(
        processes: List<ProcessLoad>,
        daily: DailyAppStats?,
        storage: Map<Int, Long>,
        packagesForUid: (Int) -> List<String>,
        label: (String) -> String,
        ownPackage: String = "",
    ): List<AppRow> {
        class Acc(val key: String, val packageName: String?, val name: String) {
            val procs = ArrayList<ProcessLoad>()
            val uids = HashSet<Int>()
            var fg: Long? = null
            var net: Long? = null
        }
        val acc = LinkedHashMap<String, Acc>()
        fun accFor(key: String, pkg: String?, name: String) = acc.getOrPut(key) { Acc(key, pkg, name) }

        for (p in processes) {
            val pkg = packageOf(p, packagesForUid)
            val a = when {
                pkg != null -> accFor(pkg, pkg, pkg)
                // Bento の計測用プロセス。シェル権限なので uid ではアプリに結び付かない
                ownPackage.isNotEmpty() && p.name.startsWith("$ownPackage:") -> accFor("proc:own", null, "Bento の計測用プロセス")
                else -> accFor("proc:${baseName(p.name)}", null, baseName(p.name))
            }
            a.procs += p
            a.uids += p.uid
        }
        daily?.foregroundMs?.forEach { (pkg, ms) -> accFor(pkg, pkg, pkg).fg = ms }
        daily?.networkBytes?.forEach { (uid, bytes) ->
            val pkg = if (uid >= FIRST_APPLICATION_UID) packagesForUid(uid).firstOrNull() else null
            val a = when {
                pkg != null -> accFor(pkg, pkg, pkg)
                uid == UID_REMOVED -> accFor("uid:removed", null, "削除したアプリ")
                uid == UID_TETHERING -> accFor("uid:tethering", null, "テザリング")
                else -> accFor("uid:$uid", null, "システム（uid $uid）")
            }
            a.net = (a.net ?: 0L) + bytes
            a.uids += uid
        }
        return acc.values.map { a ->
            AppRow(
                key = a.key,
                label = a.packageName?.let(label) ?: a.name,
                packageName = a.packageName,
                cpuPercent = if (processes.isEmpty()) null else a.procs.sumOf { it.cpuPercent.toDouble() }.toFloat(),
                rssKb = if (processes.isEmpty()) null else a.procs.sumOf { it.rssKb },
                processes = a.procs.sortedByDescending { it.cpuPercent },
                foregroundMs = a.fg,
                networkBytes = a.net,
                storageBytes = a.uids.filter { it >= FIRST_APPLICATION_UID }.mapNotNull { storage[it] }.maxOrNull(),
                uids = a.uids,
            )
        }
    }

    fun sorted(rows: List<AppRow>, sort: AppSort): List<AppRow> =
        rows.filter { (sort.value(it) ?: 0.0) > 0.0 }.sortedByDescending { sort.value(it) }

    /**
     * プロセスの持ち主のアプリ。uid を共有するアプリが複数あればプロセス名に合うものを選ぶ。
     * 隔離プロセス（uid が割り当て外）はプロセス名から推測する。
     */
    private fun packageOf(p: ProcessLoad, packagesForUid: (Int) -> List<String>): String? {
        if (p.uid < FIRST_APPLICATION_UID) return null
        val fromName = p.name.substringBefore(':').removeSuffix("_zygote")
        val pkgs = packagesForUid(p.uid)
        return when {
            fromName in pkgs -> fromName
            pkgs.isNotEmpty() -> pkgs.first()
            '.' in fromName && !fromName.startsWith("/") -> fromName
            else -> null
        }
    }

    /** "/system/bin/surfaceflinger" → "surfaceflinger" */
    fun baseName(name: String): String = name.substringAfterLast('/')

    // android.app.usage.NetworkStats.Bucket の特別な uid
    private const val UID_REMOVED = -4
    private const val UID_TETHERING = -5
}

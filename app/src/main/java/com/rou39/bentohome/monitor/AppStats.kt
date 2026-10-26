package com.rou39.bentohome.monitor

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import android.os.storage.StorageManager
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

/** 今日の使用時間と通信量。キーはパッケージ名と uid。 */
data class DailyAppStats(
    val foregroundMs: Map<String, Long>,
    /** uid ごとの受信+送信バイト数（Wi-Fi とモバイルの合計）。 */
    val networkBytes: Map<Int, Long>,
)

/** 使用状況へのアクセスで読める、アプリごとの値（docs/monitor.md）。 */
class AppStats(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val network = context.getSystemService(NetworkStatsManager::class.java)
    private val storage = context.getSystemService(StorageStatsManager::class.java)

    fun usageAccessGranted(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** 今日0時から今まで。重い処理なので IO スレッドで呼ぶ。 */
    fun today(): DailyAppStats {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val fg = runCatching {
            usage.queryAndAggregateUsageStats(start, end)
                .mapValues { it.value.totalTimeVisible }
                .filterValues { it > 0 }
        }.onFailure { Log.w(TAG, "usage stats", it) }.getOrDefault(emptyMap())
        val bytes = HashMap<Int, Long>()
        for (type in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            runCatching {
                network.querySummary(type, null, start, end).use { stats ->
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        bytes.merge(bucket.uid, bucket.rxBytes + bucket.txBytes, Long::plus)
                    }
                }
            }.onFailure { Log.w(TAG, "network stats type=$type", it) }
        }
        return DailyAppStats(fg, bytes)
    }

    /** アプリ本体・データ・キャッシュの合計バイト数。読めなければ null。 */
    fun storageBytes(uid: Int): Long? = runCatching {
        val s = storage.queryStatsForUid(StorageManager.UUID_DEFAULT, uid)
        s.appBytes + s.dataBytes + s.cacheBytes
    }.getOrNull()

    private companion object {
        const val TAG = "AppStats"
    }
}

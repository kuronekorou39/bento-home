package com.rou39.bentohome.usage

import android.util.Log
import com.rou39.bentohome.data.UsageDailyEntity
import com.rou39.bentohome.data.UsageDao
import com.rou39.bentohome.data.UsageSummaryEntity
import com.rou39.bentohome.layout.AppRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * このランチャーからの起動の記録（plan.md §4）。通知・履歴画面・他アプリのリンクからの起動は含まない。
 * 書き込みはアプリスコープで行い、画面を閉じても捨てない。
 */
class UsageRepository(private val dao: UsageDao, private val appScope: CoroutineScope) {
    private val lastRecorded = mutableMapOf<AppRef, Long>()

    val usage: Flow<Map<AppRef, AppUsage>> = combine(dao.observeDaily(), dao.observeSummaries()) { daily, summaries ->
        val dailyByApp = daily.groupBy { AppRef(it.component, it.profileSerial) }
            .mapValues { (_, rows) -> rows.associate { it.epochDay to it.count } }
        val summaryByApp = summaries.associateBy { AppRef(it.component, it.profileSerial) }
        (dailyByApp.keys + summaryByApp.keys).associateWith { ref ->
            val s = summaryByApp[ref]
            AppUsage(dailyByApp[ref].orEmpty(), s?.observedSince, s?.lastLaunchAt)
        }
    }

    fun today(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    /** 起動に成功したときに呼ぶ。短い間隔の連続起動は1回にまとめる。 */
    fun recordLaunch(app: AppRef, now: Long = System.currentTimeMillis()) {
        if (!UsagePolicy.shouldRecord(lastRecorded[app], now)) return
        lastRecorded[app] = now
        val day = today()
        appScope.launch(Dispatchers.IO) {
            runCatching { dao.recordLaunch(app.component, app.profileSerial, day, now) }
                .onFailure { Log.e(TAG, "record failed", it) }
        }
    }

    /** カタログに現れたアプリの観測開始を記録する。既に記録があるアプリは変えない。 */
    fun observe(apps: Collection<AppRef>, now: Long = System.currentTimeMillis()) {
        if (apps.isEmpty()) return
        appScope.launch(Dispatchers.IO) {
            dao.insertSummariesIfMissing(apps.map { UsageSummaryEntity(it.component, it.profileSerial, now, null) })
        }
    }

    fun prune() {
        val minDay = today() - UsagePolicy.RETENTION_DAYS + 1
        appScope.launch(Dispatchers.IO) { dao.pruneBefore(minDay) }
    }

    /** アンインストールされたアプリの記録を消す。入れ直したら観測をやり直す。 */
    fun forgetPackage(packageName: String, profileSerial: Long) {
        appScope.launch(Dispatchers.IO) { dao.deletePackage(packageName, profileSerial) }
    }

    /** バックアップから戻す。観測開始が無いアプリは今の時刻から観測する。 */
    suspend fun replaceAll(all: Map<AppRef, AppUsage>) {
        val now = System.currentTimeMillis()
        lastRecorded.clear()
        dao.replaceAll(
            daily = all.flatMap { (ref, u) -> u.daily.map { (day, n) -> UsageDailyEntity(ref.component, ref.profileSerial, day, n) } },
            summaries = all.map { (ref, u) -> UsageSummaryEntity(ref.component, ref.profileSerial, u.observedSince ?: now, u.lastLaunchAt) },
        )
    }

    suspend fun clearHistory() {
        lastRecorded.clear()
        dao.clearHistory(System.currentTimeMillis())
    }

    private companion object {
        const val TAG = "UsageRepository"
    }
}

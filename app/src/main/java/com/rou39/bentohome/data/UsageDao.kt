package com.rou39.bentohome.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UsageDao {
    @Query("SELECT * FROM usage_daily")
    abstract fun observeDaily(): Flow<List<UsageDailyEntity>>

    @Query("SELECT * FROM usage_summary")
    abstract fun observeSummaries(): Flow<List<UsageSummaryEntity>>

    /** 初めて見たアプリだけ観測開始を記録する。既にある行は変えない。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSummariesIfMissing(rows: List<UsageSummaryEntity>)

    @Query(
        "INSERT INTO usage_daily (component, profileSerial, epochDay, count) VALUES (:component, :profileSerial, :epochDay, 1) " +
            "ON CONFLICT(component, profileSerial, epochDay) DO UPDATE SET count = count + 1",
    )
    protected abstract suspend fun incrementDaily(component: String, profileSerial: Long, epochDay: Long)

    @Query("UPDATE usage_summary SET lastLaunchAt = :at WHERE component = :component AND profileSerial = :profileSerial")
    protected abstract suspend fun setLastLaunch(component: String, profileSerial: Long, at: Long)

    @Transaction
    open suspend fun recordLaunch(component: String, profileSerial: Long, epochDay: Long, at: Long) {
        insertSummariesIfMissing(listOf(UsageSummaryEntity(component, profileSerial, at, null)))
        incrementDaily(component, profileSerial, epochDay)
        setLastLaunch(component, profileSerial, at)
    }

    @Query("DELETE FROM usage_daily WHERE epochDay < :minEpochDay")
    abstract suspend fun pruneBefore(minEpochDay: Long)

    /** prefix は「パッケージ名/」。component がこれで始まる行を消す。 */
    @Query("DELETE FROM usage_daily WHERE substr(component, 1, length(:prefix)) = :prefix AND profileSerial = :profileSerial")
    protected abstract suspend fun deleteDailyOf(prefix: String, profileSerial: Long)

    @Query("DELETE FROM usage_summary WHERE substr(component, 1, length(:prefix)) = :prefix AND profileSerial = :profileSerial")
    protected abstract suspend fun deleteSummaryOf(prefix: String, profileSerial: Long)

    @Transaction
    open suspend fun deletePackage(packageName: String, profileSerial: Long) {
        deleteDailyOf("$packageName/", profileSerial)
        deleteSummaryOf("$packageName/", profileSerial)
    }

    @Query("DELETE FROM usage_daily")
    protected abstract suspend fun deleteAllDaily()

    @Query("UPDATE usage_summary SET lastLaunchAt = NULL, observedSince = :now")
    protected abstract suspend fun restartObservation(now: Long)

    /** 履歴を消し、観測をやり直す。消した直後に全アプリを「未使用」と判定しない。 */
    @Transaction
    open suspend fun clearHistory(now: Long) {
        deleteAllDaily()
        restartObservation(now)
    }
}

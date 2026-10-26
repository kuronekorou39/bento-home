package com.rou39.bentohome.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppearanceDao {
    @Query("SELECT * FROM app_appearance")
    fun observeAll(): Flow<List<AppAppearanceEntity>>

    @Upsert
    suspend fun upsert(entity: AppAppearanceEntity)

    @Query("DELETE FROM app_appearance WHERE component = :component AND profileSerial = :profileSerial")
    suspend fun delete(component: String, profileSerial: Long)

    @Query("DELETE FROM app_appearance")
    suspend fun deleteAll()

    @androidx.room.Insert
    suspend fun insertAll(rows: List<AppAppearanceEntity>)

    @androidx.room.Transaction
    suspend fun replaceAll(rows: List<AppAppearanceEntity>) {
        deleteAll()
        insertAll(rows)
    }
}

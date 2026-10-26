package com.rou39.bentohome.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PageEntity::class, ItemEntity::class, DockEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BentoDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        fun create(context: Context): BentoDatabase =
            Room.databaseBuilder(context, BentoDatabase::class.java, "bento.db").build()
    }
}

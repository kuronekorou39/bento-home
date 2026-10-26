package com.rou39.bentohome.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PageEntity::class, ItemEntity::class, DockEntity::class, AppAppearanceEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: app_appearance を追加
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class BentoDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    abstract fun appearanceDao(): AppearanceDao

    companion object {
        fun create(context: Context): BentoDatabase =
            Room.databaseBuilder(context, BentoDatabase::class.java, "bento.db").build()
    }
}

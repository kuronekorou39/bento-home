package com.rou39.bentohome.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PageEntity::class,
        ItemEntity::class,
        DockEntity::class,
        AppAppearanceEntity::class,
        UsageDailyEntity::class,
        UsageSummaryEntity::class,
        FolderAppEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        // v2: app_appearance を追加
        AutoMigration(from = 1, to = 2),
        // v3: usage_daily・usage_summary を追加
        AutoMigration(from = 2, to = 3),
        // v4: フォルダー（workspace_item.title と folder_app）を追加
        AutoMigration(from = 3, to = 4),
        // v5: ショートカット（workspace_item.shortcutId）を追加
        AutoMigration(from = 4, to = 5),
        // v6: アプリの非表示と表示名（app_appearance.hidden・label）を追加
        AutoMigration(from = 5, to = 6),
        // v7: ページの仕切り（workspace_page.partition）を追加
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class BentoDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    abstract fun appearanceDao(): AppearanceDao

    abstract fun usageDao(): UsageDao

    companion object {
        fun create(context: Context): BentoDatabase =
            Room.databaseBuilder(context, BentoDatabase::class.java, "bento.db").build()
    }
}

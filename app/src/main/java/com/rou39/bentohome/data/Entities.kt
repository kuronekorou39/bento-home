package com.rou39.bentohome.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workspace_page")
data class PageEntity(
    @PrimaryKey val id: Long,
    /** 表示順。`order` は SQL の予約語なので position とする。 */
    val position: Int,
    val baseColumns: Int,
    val baseRows: Int,
    val subdivisions: Int,
    /** 仕切りのプリセット名（v7、plan.md §10）。 */
    @ColumnInfo(defaultValue = "NONE") val partition: String = "NONE",
)

@Entity(
    tableName = "workspace_item",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId")],
)
data class ItemEntity(
    @PrimaryKey val id: Long,
    val pageId: Long,
    /** [TYPE_APP]・[TYPE_WIDGET]・[TYPE_FOLDER]・[TYPE_SHORTCUT] */
    val type: String,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val component: String?,
    val profileSerial: Long?,
    val widgetId: Int?,
    val widgetProvider: String?,
    /** フォルダー・ショートカットの名前（v4）。フォルダーの中のアプリは [FolderAppEntity]。 */
    val title: String? = null,
    /** ショートカットの ID（v5）。パッケージ名は component に入れる。 */
    val shortcutId: String? = null,
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_WIDGET = "WIDGET"
        const val TYPE_FOLDER = "FOLDER"
        const val TYPE_SHORTCUT = "SHORTCUT"
    }
}

/** フォルダーの中のアプリ（v4、plan.md §10）。position は表示順。 */
@Entity(
    tableName = "folder_app",
    primaryKeys = ["itemId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FolderAppEntity(
    val itemId: Long,
    val position: Int,
    val component: String,
    val profileSerial: Long,
)

@Entity(tableName = "dock_item")
data class DockEntity(
    @PrimaryKey val slot: Int,
    val component: String,
    val profileSerial: Long,
)

/** アプリごとの外観（plan.md §2）。すべて既定値に戻したら行を消す。 */
@Entity(tableName = "app_appearance", primaryKeys = ["component", "profileSerial"])
data class AppAppearanceEntity(
    val component: String,
    val profileSerial: Long,
    /** 使わなくなってもグレー表示にしない（段階4で使う）。 */
    val keepColor: Boolean,
    /** 使用頻度の集計から外す（段階4で使う）。 */
    val excludeFromRanking: Boolean,
    /** null なら全体の設定に従う。BadgeMode の名前。 */
    val badgeMode: String?,
    /** アプリ一覧に出さない（v6、plan.md §10）。ホーム・ドック・フォルダーに置いたものはそのまま。 */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    /** 表示名（v6）。null なら元の名前。 */
    val label: String? = null,
)

/** このランチャーからの日別起動回数（plan.md §4）。最大60日分。 */
@Entity(tableName = "usage_daily", primaryKeys = ["component", "profileSerial", "epochDay"])
data class UsageDailyEntity(
    val component: String,
    val profileSerial: Long,
    /** 端末のローカル日付。 */
    val epochDay: Long,
    val count: Int,
)

/** 観測開始と最終起動（plan.md §4）。アンインストールで削除し、入れ直したら観測をやり直す。 */
@Entity(tableName = "usage_summary", primaryKeys = ["component", "profileSerial"])
data class UsageSummaryEntity(
    val component: String,
    val profileSerial: Long,
    val observedSince: Long,
    val lastLaunchAt: Long?,
)

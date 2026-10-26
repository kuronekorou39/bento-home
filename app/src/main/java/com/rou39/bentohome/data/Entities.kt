package com.rou39.bentohome.data

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
    /** [TYPE_APP] または [TYPE_WIDGET] */
    val type: String,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val component: String?,
    val profileSerial: Long?,
    val widgetId: Int?,
    val widgetProvider: String?,
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_WIDGET = "WIDGET"
    }
}

@Entity(tableName = "dock_item")
data class DockEntity(
    @PrimaryKey val slot: Int,
    val component: String,
    val profileSerial: Long,
)

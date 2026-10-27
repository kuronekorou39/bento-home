package com.rou39.bentohome.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class WorkspaceDao {
    @Query("SELECT * FROM workspace_page ORDER BY position")
    abstract suspend fun pages(): List<PageEntity>

    @Query("SELECT * FROM workspace_item")
    abstract suspend fun items(): List<ItemEntity>

    @Query("SELECT * FROM dock_item ORDER BY slot")
    abstract suspend fun dock(): List<DockEntity>

    @Query("SELECT * FROM folder_app ORDER BY itemId, position")
    abstract suspend fun folderApps(): List<FolderAppEntity>

    @Query("DELETE FROM workspace_page")
    protected abstract suspend fun deletePages()

    @Query("DELETE FROM workspace_item")
    protected abstract suspend fun deleteItems()

    @Query("DELETE FROM dock_item")
    protected abstract suspend fun deleteDock()

    @Query("DELETE FROM folder_app")
    protected abstract suspend fun deleteFolderApps()

    @Insert
    protected abstract suspend fun insertPages(pages: List<PageEntity>)

    @Insert
    protected abstract suspend fun insertItems(items: List<ItemEntity>)

    @Insert
    protected abstract suspend fun insertDock(dock: List<DockEntity>)

    @Insert
    protected abstract suspend fun insertFolderApps(apps: List<FolderAppEntity>)

    @Transaction
    open suspend fun loadAll(): WorkspaceRows = WorkspaceRows(pages(), items(), dock(), folderApps())

    /**
     * 配置全体を1トランザクションで置き換える。途中で中断しても半端な配置は残らない。
     */
    @Transaction
    open suspend fun replaceAll(rows: WorkspaceRows) {
        deleteFolderApps()
        deleteItems()
        deletePages()
        deleteDock()
        insertPages(rows.pages)
        insertItems(rows.items)
        insertDock(rows.dock)
        insertFolderApps(rows.folderApps)
    }
}

data class WorkspaceRows(
    val pages: List<PageEntity>,
    val items: List<ItemEntity>,
    val dock: List<DockEntity>,
    val folderApps: List<FolderAppEntity>,
)

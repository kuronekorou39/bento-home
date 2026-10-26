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

    @Query("DELETE FROM workspace_page")
    protected abstract suspend fun deletePages()

    @Query("DELETE FROM workspace_item")
    protected abstract suspend fun deleteItems()

    @Query("DELETE FROM dock_item")
    protected abstract suspend fun deleteDock()

    @Insert
    protected abstract suspend fun insertPages(pages: List<PageEntity>)

    @Insert
    protected abstract suspend fun insertItems(items: List<ItemEntity>)

    @Insert
    protected abstract suspend fun insertDock(dock: List<DockEntity>)

    @Transaction
    open suspend fun loadAll(): Triple<List<PageEntity>, List<ItemEntity>, List<DockEntity>> =
        Triple(pages(), items(), dock())

    /** 配置全体を1トランザクションで置き換える。途中で中断しても半端な配置は残らない。 */
    @Transaction
    open suspend fun replaceAll(pages: List<PageEntity>, items: List<ItemEntity>, dock: List<DockEntity>) {
        deleteItems()
        deletePages()
        deleteDock()
        insertPages(pages)
        insertItems(items)
        insertDock(dock)
    }
}

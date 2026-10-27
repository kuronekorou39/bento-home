package com.rou39.bentohome.data

import android.util.Log
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.CellRect
import com.rou39.bentohome.layout.DockSlot
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.Page
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.layout.WorkspaceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * 配置の読み書き。実行中の正本は ViewModel が持ち、変更のたびに最新の配置全体を保存する。
 * 保存はアプリスコープで行い、画面が閉じても書き込みを途中で捨てない。
 */
class WorkspaceRepository(private val dao: WorkspaceDao, appScope: CoroutineScope) {
    private val pendingSave = MutableStateFlow<Workspace?>(null)

    init {
        // StateFlow なので連続した変更は最新だけが書かれる
        appScope.launch(Dispatchers.IO) {
            pendingSave.filterNotNull().collect { ws ->
                runCatching { dao.replaceAll(toRows(ws)) }
                    .onFailure { Log.e(TAG, "save failed", it) }
            }
        }
    }

    suspend fun load(): Workspace {
        val rows = dao.loadAll()
        val folders = rows.folderApps.groupBy({ it.itemId }, { AppRef(it.component, it.profileSerial) })
        return Workspace(
            pages = rows.pages.map { Page(it.id, it.position, GridSpec(it.baseColumns, it.baseRows, it.subdivisions)) },
            items = rows.items.mapNotNull { toModel(it, folders[it.id].orEmpty()) },
            dock = rows.dock.map { DockSlot(it.slot, AppRef(it.component, it.profileSerial)) },
        )
    }

    fun save(ws: Workspace) {
        pendingSave.value = ws
    }

    private fun toRows(ws: Workspace) = WorkspaceRows(
        pages = ws.pages.map(::toEntity),
        items = ws.items.map(::toEntity),
        dock = ws.dock.map(::toEntity),
        folderApps = ws.items.flatMap { item ->
            (item.content as? ItemContent.Folder)?.apps.orEmpty().mapIndexed { i, app ->
                FolderAppEntity(item.id, i, app.component, app.profileSerial)
            }
        },
    )

    private fun toEntity(page: Page) = PageEntity(
        id = page.id,
        position = page.order,
        baseColumns = page.grid.baseColumns,
        baseRows = page.grid.baseRows,
        subdivisions = page.grid.subdivisions,
    )

    private fun toEntity(item: WorkspaceItem): ItemEntity {
        val r = item.rect
        return when (val c = item.content) {
            is ItemContent.App -> ItemEntity(
                item.id, item.pageId, ItemEntity.TYPE_APP, r.x, r.y, r.width, r.height,
                component = c.app.component, profileSerial = c.app.profileSerial, widgetId = null, widgetProvider = null,
            )
            is ItemContent.Widget -> ItemEntity(
                item.id, item.pageId, ItemEntity.TYPE_WIDGET, r.x, r.y, r.width, r.height,
                component = null, profileSerial = null, widgetId = c.widgetId, widgetProvider = c.provider,
            )
            is ItemContent.Shortcut -> ItemEntity(
                item.id, item.pageId, ItemEntity.TYPE_SHORTCUT, r.x, r.y, r.width, r.height,
                component = c.packageName, profileSerial = c.profileSerial, widgetId = null, widgetProvider = null,
                title = c.label, shortcutId = c.shortcutId,
            )
            is ItemContent.Folder -> ItemEntity(
                item.id, item.pageId, ItemEntity.TYPE_FOLDER, r.x, r.y, r.width, r.height,
                component = null, profileSerial = null, widgetId = null, widgetProvider = null, title = c.title,
            )
        }
    }

    private fun toEntity(slot: DockSlot) = DockEntity(slot.slot, slot.app.component, slot.app.profileSerial)

    private fun toModel(e: ItemEntity, folderApps: List<AppRef>): WorkspaceItem? {
        val content = when (e.type) {
            ItemEntity.TYPE_APP -> {
                val component = e.component ?: return null
                ItemContent.App(AppRef(component, e.profileSerial ?: 0))
            }
            ItemEntity.TYPE_WIDGET -> ItemContent.Widget(e.widgetId ?: return null, e.widgetProvider.orEmpty())
            ItemEntity.TYPE_SHORTCUT -> ItemContent.Shortcut(
                e.component ?: return null, e.shortcutId ?: return null, e.profileSerial ?: 0, e.title.orEmpty(),
            )
            ItemEntity.TYPE_FOLDER -> when (folderApps.size) {
                0 -> return null
                1 -> ItemContent.App(folderApps.single())
                else -> ItemContent.Folder(e.title ?: ItemContent.Folder.DEFAULT_TITLE, folderApps)
            }
            else -> return null
        }
        return WorkspaceItem(e.id, e.pageId, CellRect(e.cellX, e.cellY, e.spanX, e.spanY), content)
    }

    private companion object {
        const val TAG = "WorkspaceRepository"
    }
}

package com.rou39.bentohome.backup

import com.rou39.bentohome.data.AppAppearance
import com.rou39.bentohome.data.BadgeMode
import com.rou39.bentohome.data.LauncherSettings
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.CellRect
import com.rou39.bentohome.layout.DockSlot
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.Page
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.layout.WorkspaceItem
import com.rou39.bentohome.usage.AppUsage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 手動バックアップの形式（plan.md §5・docs/design.md §12）。
 * ウィジェット ID は端末ごとに違うので書かず、提供元と占有範囲だけを書く。読み込むと「再追加が必要」になる。
 * 使用履歴は既定では含めない。通知の内容は扱わない。
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val appVersion: String,
    val settings: BackupSettings,
    val pages: List<BackupPage>,
    val items: List<BackupItem>,
    val dock: List<BackupDock>,
    val appearances: List<BackupAppearance> = emptyList(),
    val usage: List<BackupUsage>? = null,
) {
    companion object {
        const val FORMAT = "bento-home-backup"
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupSettings(
    val layoutLocked: Boolean,
    val dockVisible: Boolean,
    val baseColumns: Int,
    val baseRows: Int,
    val dockSlots: Int,
    val iconSizeDp: Int,
    val iconShape: String,
    val labelVisible: Boolean,
    val labelSize: String,
    val badgeMode: String,
    val drawerSort: String,
    val grayOnHome: Boolean,
    val grayInDrawer: Boolean,
    val grayDays: Int,
)

@Serializable
data class BackupPage(val id: Long, val order: Int, val baseColumns: Int, val baseRows: Int, val subdivisions: Int)

@Serializable
data class BackupItem(
    val id: Long,
    val pageId: Long,
    val type: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val component: String? = null,
    val profileSerial: Long? = null,
    val widgetProvider: String? = null,
) {
    companion object {
        const val TYPE_APP = "app"
        const val TYPE_WIDGET = "widget"
    }
}

@Serializable
data class BackupDock(val slot: Int, val component: String, val profileSerial: Long)

@Serializable
data class BackupAppearance(
    val component: String,
    val profileSerial: Long,
    val keepColor: Boolean,
    val excludeFromRanking: Boolean,
    val badgeMode: String? = null,
)

@Serializable
data class BackupUsage(
    val component: String,
    val profileSerial: Long,
    val observedSince: Long? = null,
    val lastLaunchAt: Long? = null,
    @SerialName("daily") val daily: Map<Long, Int> = emptyMap(),
)

/** 読み込んだ内容を、この端末で使える形に直したもの。 */
data class RestorePlan(
    val workspace: Workspace,
    val settings: LauncherSettings,
    val appearances: Map<AppRef, AppAppearance>,
    val usage: Map<AppRef, AppUsage>?,
    /** この端末に無いので復元しないアプリの数（ホーム・ドック）。 */
    val skippedApps: Int,
    /** 追加し直しが必要なウィジェットの数。 */
    val widgetsToReAdd: Int,
)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** 形式が違うファイルや、新しすぎる版は読まない。 */
    fun decode(text: String): BackupFile {
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == BackupFile.FORMAT) { "Bento Home のバックアップではありません" }
        require(file.schemaVersion <= BackupFile.SCHEMA_VERSION) { "このアプリより新しい版のバックアップです" }
        return file
    }

    fun fromState(
        workspace: Workspace,
        settings: LauncherSettings,
        appearances: Map<AppRef, AppAppearance>,
        usage: Map<AppRef, AppUsage>?,
        exportedAt: Long,
        appVersion: String,
    ) = BackupFile(
        exportedAt = exportedAt,
        appVersion = appVersion,
        settings = BackupSettings(
            layoutLocked = settings.layoutLocked,
            dockVisible = settings.dockVisible,
            baseColumns = settings.baseColumns,
            baseRows = settings.baseRows,
            dockSlots = settings.dockSlots,
            iconSizeDp = settings.iconSizeDp,
            iconShape = settings.iconShape.name,
            labelVisible = settings.labelVisible,
            labelSize = settings.labelSize.name,
            badgeMode = settings.badgeMode.name,
            drawerSort = settings.drawerSort.name,
            grayOnHome = settings.grayOnHome,
            grayInDrawer = settings.grayInDrawer,
            grayDays = settings.grayDays,
        ),
        pages = workspace.orderedPages.map {
            BackupPage(it.id, it.order, it.grid.baseColumns, it.grid.baseRows, it.grid.subdivisions)
        },
        items = workspace.items.map { item ->
            val r = item.rect
            when (val c = item.content) {
                is ItemContent.App -> BackupItem(
                    item.id, item.pageId, BackupItem.TYPE_APP, r.x, r.y, r.width, r.height,
                    component = c.app.component, profileSerial = c.app.profileSerial,
                )
                is ItemContent.Widget -> BackupItem(
                    item.id, item.pageId, BackupItem.TYPE_WIDGET, r.x, r.y, r.width, r.height,
                    widgetProvider = c.provider,
                )
            }
        },
        dock = workspace.dock.map { BackupDock(it.slot, it.app.component, it.app.profileSerial) },
        appearances = appearances.map { (ref, a) ->
            BackupAppearance(ref.component, ref.profileSerial, a.keepColor, a.excludeFromRanking, a.badgeMode?.name)
        },
        usage = usage?.map { (ref, u) ->
            BackupUsage(ref.component, ref.profileSerial, u.observedSince, u.lastLaunchAt, u.daily)
        },
    )

    /**
     * 読み込んだバックアップを復元の計画にする。
     * @param isInstalled この端末にアプリがあるか。無いアプリはホーム・ドックに置かない。
     */
    fun toRestorePlan(file: BackupFile, isInstalled: (AppRef) -> Boolean, defaults: LauncherSettings): RestorePlan {
        val pages = file.pages.map { Page(it.id, it.order, GridSpec(it.baseColumns, it.baseRows, it.subdivisions)) }
        val pageIds = pages.map { it.id }.toSet()
        var skipped = 0
        var widgets = 0
        val items = file.items.mapNotNull { b ->
            if (b.pageId !in pageIds) return@mapNotNull null
            val rect = CellRect(b.x, b.y, b.width, b.height)
            when (b.type) {
                BackupItem.TYPE_APP -> {
                    val ref = AppRef(b.component ?: return@mapNotNull null, b.profileSerial ?: 0)
                    if (!isInstalled(ref)) {
                        skipped++
                        null
                    } else {
                        WorkspaceItem(b.id, b.pageId, rect, ItemContent.App(ref))
                    }
                }
                BackupItem.TYPE_WIDGET -> {
                    widgets++
                    WorkspaceItem(b.id, b.pageId, rect, ItemContent.Widget(ItemContent.Widget.NO_ID, b.widgetProvider.orEmpty()))
                }
                else -> null
            }
        }
        val dock = file.dock.mapNotNull { d ->
            val ref = AppRef(d.component, d.profileSerial)
            if (isInstalled(ref)) DockSlot(d.slot, ref) else null.also { skipped++ }
        }
        val s = file.settings
        val settings = LauncherSettings(
            layoutLocked = s.layoutLocked,
            dockVisible = s.dockVisible,
            baseColumns = s.baseColumns,
            baseRows = s.baseRows,
            dockSlots = s.dockSlots,
            iconSizeDp = s.iconSizeDp,
            iconShape = enumOrNull(s.iconShape) ?: defaults.iconShape,
            labelVisible = s.labelVisible,
            labelSize = enumOrNull(s.labelSize) ?: defaults.labelSize,
            badgeMode = enumOrNull(s.badgeMode) ?: defaults.badgeMode,
            drawerSort = enumOrNull(s.drawerSort) ?: defaults.drawerSort,
            grayOnHome = s.grayOnHome,
            grayInDrawer = s.grayInDrawer,
            grayDays = s.grayDays,
        )
        val appearances = file.appearances.associate {
            AppRef(it.component, it.profileSerial) to AppAppearance(
                it.keepColor, it.excludeFromRanking, it.badgeMode?.let { m -> enumOrNull<BadgeMode>(m) },
            )
        }
        val usage = file.usage?.associate {
            AppRef(it.component, it.profileSerial) to AppUsage(it.daily, it.observedSince, it.lastLaunchAt)
        }
        return RestorePlan(Workspace(pages, items, dock), settings, appearances, usage, skipped, widgets)
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        runCatching { enumValueOf<E>(name) }.getOrNull()
}

package com.rou39.bentohome.backup

import com.rou39.bentohome.data.AppAppearance
import com.rou39.bentohome.data.BadgeMode
import com.rou39.bentohome.data.IconShape
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val chat = AppRef("com.chat/.Main", 0)
    private val gone = AppRef("com.gone/.Main", 0)
    private val workspace = Workspace(
        pages = listOf(Page(1, 0, GridSpec(4, 6, 2)), Page(2, 1, GridSpec(4, 6, 2))),
        items = listOf(
            WorkspaceItem(1, 1, CellRect(3, 4, 2, 2), ItemContent.App(chat)),
            WorkspaceItem(2, 1, CellRect(0, 0, 4, 2), ItemContent.Widget(42, "com.duo/.Widget")),
            WorkspaceItem(3, 2, CellRect(0, 0, 2, 2), ItemContent.App(gone)),
        ),
        dock = listOf(DockSlot(0, chat), DockSlot(1, gone)),
    )
    private val settings = LauncherSettings(baseColumns = 5, iconShape = IconShape.CIRCLE, badgeMode = BadgeMode.DOT)
    private val appearances = mapOf(chat to AppAppearance(keepColor = true, badgeMode = BadgeMode.NONE))

    private fun encodeDecode(usage: Map<AppRef, AppUsage>? = null): BackupFile {
        val file = BackupCodec.fromState(workspace, settings, appearances, usage, exportedAt = 1000, appVersion = "0.1.0")
        return BackupCodec.decode(BackupCodec.encode(file))
    }

    @Test
    fun roundTripKeepsLayoutSettingsAndAppearance() {
        val plan = BackupCodec.toRestorePlan(encodeDecode(), { true }, LauncherSettings())
        assertEquals(workspace.pages, plan.workspace.pages)
        assertEquals(CellRect(3, 4, 2, 2), plan.workspace.item(1)!!.rect)
        assertEquals(settings, plan.settings)
        assertEquals(appearances, plan.appearances)
        assertEquals(workspace.dock, plan.workspace.dock)
    }

    @Test
    fun widgetIdsAreNotExportedAndRestoreAsReAdd() {
        val text = BackupCodec.encode(BackupCodec.fromState(workspace, settings, appearances, null, 1000, "0.1.0"))
        assertFalse(text.contains("42"))
        val plan = BackupCodec.toRestorePlan(BackupCodec.decode(text), { true }, LauncherSettings())
        val widget = plan.workspace.item(2)!!.content as ItemContent.Widget
        assertTrue(widget.needsReAdd)
        assertEquals("com.duo/.Widget", widget.provider)
        assertEquals(1, plan.widgetsToReAdd)
        assertTrue(plan.workspace.widgetIds.isEmpty())
    }

    @Test
    fun appsMissingOnThisDeviceAreSkippedAndCounted() {
        val plan = BackupCodec.toRestorePlan(encodeDecode(), { it != gone }, LauncherSettings())
        assertNull(plan.workspace.item(3))
        assertEquals(listOf(DockSlot(0, chat)), plan.workspace.dock)
        assertEquals(2, plan.skippedApps)
    }

    @Test
    fun usageIsOptional() {
        assertNull(BackupCodec.toRestorePlan(encodeDecode(null), { true }, LauncherSettings()).usage)
        val usage = mapOf(chat to AppUsage(mapOf(20_000L to 3), observedSince = 5, lastLaunchAt = 9))
        assertEquals(usage, BackupCodec.toRestorePlan(encodeDecode(usage), { true }, LauncherSettings()).usage)
    }

    @Test
    fun unknownFieldsAreIgnoredAndForeignFilesRejected() {
        val text = BackupCodec.encode(BackupCodec.fromState(workspace, settings, appearances, null, 1000, "0.1.0"))
            .replaceFirst("{", "{\n  \"futureField\": 1,")
        BackupCodec.decode(text)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(text.replace("bento-home-backup", "other-app"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(text.replace("\"schemaVersion\": ${BackupFile.SCHEMA_VERSION}", "\"schemaVersion\": 99"))
        }
    }

    @Test
    fun foldersRoundTripAndDropMissingApps() {
        val other = AppRef("com.other/.Main", 0)
        val ws = workspace.copy(
            items = workspace.items + listOf(
                WorkspaceItem(10, 1, CellRect(0, 4, 2, 2), ItemContent.Folder("仕事", listOf(chat, other, gone))),
                WorkspaceItem(11, 1, CellRect(2, 4, 2, 2), ItemContent.Folder("消える", listOf(chat, gone))),
            ),
        )
        val file = BackupCodec.decode(
            BackupCodec.encode(BackupCodec.fromState(ws, settings, appearances, null, exportedAt = 1, appVersion = "x")),
        )
        val plan = BackupCodec.toRestorePlan(file, { it != gone }, LauncherSettings())
        assertEquals(ItemContent.Folder("仕事", listOf(chat, other)), plan.workspace.item(10)!!.content)
        // この端末に無いアプリを除くと1つになるフォルダーはアプリに戻る
        assertEquals(ItemContent.App(chat), plan.workspace.item(11)!!.content)
    }

    @Test
    fun shortcutsRoundTripWhenProviderIsInstalled() {
        val ws = workspace.copy(
            items = workspace.items + listOf(
                WorkspaceItem(20, 1, CellRect(0, 6, 2, 2), ItemContent.Shortcut("com.chat", "web-1", 0, "ページ")),
                WorkspaceItem(21, 1, CellRect(2, 6, 2, 2), ItemContent.Shortcut("com.gone", "web-2", 0, "消える")),
            ),
        )
        val file = BackupCodec.decode(
            BackupCodec.encode(BackupCodec.fromState(ws, settings, appearances, null, exportedAt = 1, appVersion = "x")),
        )
        val plan = BackupCodec.toRestorePlan(file, { it.packageName != "com.gone" }, LauncherSettings())
        assertEquals(ItemContent.Shortcut("com.chat", "web-1", 0, "ページ"), plan.workspace.item(20)!!.content)
        assertNull(plan.workspace.item(21))
    }
}

package com.rou39.bentohome.home

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.rou39.bentohome.container
import com.rou39.bentohome.layout.ItemContent

/**
 * 他のアプリの「ホーム画面に追加」（ショートカット・ウィジェット）を受ける確認画面（plan.md §10）。
 * OS は既定のホームのこの画面を開く。「追加」で受け入れ、ホームの空いている場所に置く。
 */
class PinRequestActivity : ComponentActivity() {
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private var request: LauncherApps.PinItemRequest? = null

    /** 許可を待っているウィジェット ID。 */
    private var pendingWidgetId = NO_ID

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) acceptWidget(pendingWidgetId) else cancelWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING, NO_ID) ?: NO_ID
        val req = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        if (req == null || !req.isValid) {
            Log.w(TAG, "invalid pin request")
            finish()
            return
        }
        request = req
        val described = describe(req)
        if (described == null) {
            finish()
            return
        }
        val (label, icon) = described
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text("ホーム画面に追加") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                                Text(label, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Bento のホームの空いている場所に置きます。", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = ::accept) { Text("追加") } },
                    dismissButton = { TextButton(onClick = ::finish) { Text("キャンセル") } },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING, pendingWidgetId)
    }

    private fun describe(req: LauncherApps.PinItemRequest): Pair<String, ImageBitmap?>? {
        val density = resources.displayMetrics.densityDpi
        return when (req.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                val info = req.shortcutInfo ?: return null
                val icon = runCatching { launcherApps.getShortcutIconDrawable(info, density) }.getOrNull()
                labelOf(info) to icon?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap()
            }
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                val info = req.getAppWidgetProviderInfo(this) ?: return null
                val icon = info.loadIcon(this, density)
                info.loadLabel(packageManager) to icon?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap()
            }
            else -> null
        }
    }

    private fun labelOf(info: android.content.pm.ShortcutInfo): String =
        (info.shortLabel ?: info.longLabel ?: info.id).toString()

    private fun accept() {
        val req = request
        if (req == null) {
            finish()
            return
        }
        when (req.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                val info = req.shortcutInfo
                if (info != null && req.accept()) {
                    val serial = container.appCatalog.serialOf(info.userHandle)
                    val shortcut = ItemContent.Shortcut(info.`package`, info.id, serial, labelOf(info))
                    container.pinRequests.trySend(PinnedItem.Shortcut(shortcut))
                    Log.d(TAG, "accepted shortcut: ${info.`package`}")
                }
                finish()
            }
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                val info = req.getAppWidgetProviderInfo(this)
                if (info == null) {
                    finish()
                    return
                }
                val host = container.widgetHost
                val id = host.allocateId()
                pendingWidgetId = id
                if (host.bindIfAllowed(id, info)) acceptWidget(id) else bindLauncher.launch(host.bindPermissionIntent(id, info))
            }
            else -> finish()
        }
    }

    /** 許可を得たウィジェット ID を提供元に渡し、ホームに置く。 */
    private fun acceptWidget(id: Int) {
        pendingWidgetId = NO_ID
        if (id != NO_ID) {
            val extras = Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) }
            if (request?.accept(extras) == true) {
                container.pinRequests.trySend(PinnedItem.Widget(id))
                Log.d(TAG, "accepted widget id=$id")
            } else {
                container.widgetHost.delete(id)
            }
        }
        finish()
    }

    private fun cancelWidget() {
        if (pendingWidgetId != NO_ID) container.widgetHost.delete(pendingWidgetId)
        pendingWidgetId = NO_ID
        finish()
    }

    private companion object {
        const val TAG = "PinRequestActivity"
        const val NO_ID = -1
        const val KEY_PENDING = "pending_widget_id"
        const val ICON_PX = 144
    }
}

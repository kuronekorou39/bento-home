package com.rou39.bentohome.home

import android.app.role.RoleManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rou39.bentohome.badge.BadgeListenerService
import com.rou39.bentohome.badge.BadgeRepository
import com.rou39.bentohome.settings.SettingsActivity

class HomeActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()
    private val widgetHost get() = vm.widgetHost

    private var showPicker by mutableStateOf(false)
    private var roleHeld by mutableStateOf(true)
    private var homeResetCount by mutableIntStateOf(0)

    /** 許可・設定の途中にある仮のウィジェット ID。完了で配置し、取消で解放する。 */
    private var pendingWidgetId = NO_ID

    /** 追加し直しのときに置き換える項目。新規追加なら [NO_ITEM]。 */
    private var pendingReplaceItemId = NO_ITEM

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d(TAG, "bind result=${result.resultCode} id=$pendingWidgetId")
        if (result.resultCode == RESULT_OK) configureOrCommit(pendingWidgetId) else cancelPending()
    }

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshRole()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING, NO_ID) ?: NO_ID
        pendingReplaceItemId = savedInstanceState?.getLong(KEY_REPLACE, NO_ITEM) ?: NO_ITEM

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box {
                    HomeScreen(
                        vm = vm,
                        roleHeld = roleHeld,
                        homeResetCount = homeResetCount,
                        onRequestRole = ::requestRole,
                        onOpenWidgetPicker = { showPicker = true },
                        onOpenSettings = { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) },
                        onReconfigureWidget = { id -> widgetHost.startConfigure(this@HomeActivity, id, REQ_RECONFIGURE) },
                        onReAddWidget = ::reAddWidget,
                    )
                    if (showPicker) {
                        BackHandler { showPicker = false }
                        WidgetPicker(widgetHost, onPick = ::addWidget, onClose = { showPicker = false })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
        refreshRole()
    }

    override fun onResume() {
        super.onResume()
        BadgeRepository.onAccessChecked(BadgeListenerService.isAccessGranted(this))
        vm.refreshClock()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

    /** ホーム操作で戻ったときは一時的な状態を解除し、最初のページへ戻す（plan.md §2）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "home intent received")
        showPicker = false
        homeResetCount++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING, pendingWidgetId)
        outState.putLong(KEY_REPLACE, pendingReplaceItemId)
    }

    @Deprecated("AppWidgetHost の設定画面起動は requestCode 方式のみ")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            Log.d(TAG, "configure result=$resultCode id=$pendingWidgetId")
            if (resultCode == RESULT_OK) commitPending() else cancelPending()
        }
    }

    private fun refreshRole() {
        roleHeld = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun requestRole() {
        roleLauncher.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME))
    }

    private fun reAddWidget(itemId: Long, provider: String) {
        val info = widgetHost.providers().firstOrNull { it.provider.flattenToString() == provider }
        if (info == null) {
            android.widget.Toast.makeText(this, "このウィジェットの提供元アプリが見つかりません", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        addWidget(info, replaceItemId = itemId)
    }

    private fun addWidget(info: AppWidgetProviderInfo) = addWidget(info, NO_ITEM)

    private fun addWidget(info: AppWidgetProviderInfo, replaceItemId: Long) {
        showPicker = false
        pendingReplaceItemId = replaceItemId
        val id = widgetHost.allocateId()
        pendingWidgetId = id
        Log.d(TAG, "allocate id=$id provider=${info.provider.flattenToShortString()}")
        if (widgetHost.bindIfAllowed(id, info)) {
            configureOrCommit(id)
        } else {
            bindLauncher.launch(widgetHost.bindPermissionIntent(id, info))
        }
    }

    private fun configureOrCommit(id: Int) {
        if (id == NO_ID) return
        val info = widgetHost.info(id)
        when {
            info == null -> cancelPending()
            info.configure != null -> if (!widgetHost.startConfigure(this, id, REQ_CONFIGURE)) cancelPending()
            else -> commitPending()
        }
    }

    private fun commitPending() {
        val id = pendingWidgetId
        if (id == NO_ID) return
        pendingWidgetId = NO_ID
        val info = widgetHost.info(id)
        if (info == null) {
            widgetHost.delete(id)
            return
        }
        val replace = pendingReplaceItemId
        pendingReplaceItemId = NO_ITEM
        if (replace != NO_ITEM) vm.replaceWidget(replace, id, info) else vm.placeNewWidget(id, info)
        Log.d(TAG, "committed widget id=$id replace=$replace")
    }

    private fun cancelPending() {
        val id = pendingWidgetId
        if (id == NO_ID) return
        pendingWidgetId = NO_ID
        pendingReplaceItemId = NO_ITEM
        widgetHost.delete(id)
        Log.d(TAG, "cancelled widget id=$id")
    }

    private companion object {
        const val TAG = "HomeActivity"
        const val NO_ID = -1
        const val REQ_CONFIGURE = 101

        /** 配置済みウィジェットの設定し直し。結果に関わらず配置は変えない。 */
        const val REQ_RECONFIGURE = 102
        const val KEY_PENDING = "pending_widget_id"
        const val KEY_REPLACE = "pending_replace_item_id"
        const val NO_ITEM = -1L
    }
}

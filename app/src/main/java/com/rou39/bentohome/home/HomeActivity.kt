package com.rou39.bentohome.home

import android.app.role.RoleManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rou39.bentohome.badge.BadgeListenerService
import com.rou39.bentohome.badge.BadgeRepository
import com.rou39.bentohome.container
import com.rou39.bentohome.settings.SettingsActivity

/**
 * 段階1（技術検証）のホーム。HOME 入口・アプリ起動・ウィジェット・通知バッジを確認する。
 * 段階2でワークスペース（グリッド配置）に置き換える。
 */
class HomeActivity : ComponentActivity() {
    private val catalog by lazy { container.appCatalog }
    private val widgetHost by lazy { container.widgetHost }
    private val widgetStore by lazy { container.widgetStore }

    private var widgetIds by mutableStateOf(emptyList<Int>())
    private var showPicker by mutableStateOf(false)
    private var roleHeld by mutableStateOf(true)
    private var homeResetCount by mutableStateOf(0)

    /** 許可・設定の途中にある仮のウィジェット ID。完了で確定し、取消で解放する。 */
    private var pendingWidgetId = NO_ID

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
        loadWidgets()

        setContent {
            HomeScreen(
                catalog = catalog,
                widgetHost = widgetHost,
                widgetIds = widgetIds,
                hostIds = widgetHost.hostIds(),
                roleHeld = roleHeld,
                showPicker = showPicker,
                homeResetCount = homeResetCount,
                onRequestRole = ::requestRole,
                onOpenPicker = { showPicker = true },
                onClosePicker = { showPicker = false },
                onPickWidget = ::addWidget,
                onRemoveWidget = ::removeWidget,
                onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            )
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
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

    /** ホーム操作で戻ったときは一時的な状態を解除する（plan.md §2）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "home intent received")
        showPicker = false
        homeResetCount++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING, pendingWidgetId)
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

    /** 保存済み ID とホスト側の ID を突き合わせる。欠けた ID は消さずに「再追加が必要」と表示する。 */
    private fun loadWidgets() {
        widgetIds = widgetStore.load()
        val missing = widgetIds - widgetHost.hostIds()
        if (missing.isNotEmpty()) Log.w(TAG, "widget ids missing on host: $missing")
    }

    private fun addWidget(info: AppWidgetProviderInfo) {
        showPicker = false
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
        widgetIds = widgetIds + id
        widgetStore.save(widgetIds)
        Log.d(TAG, "committed widget id=$id")
    }

    private fun cancelPending() {
        val id = pendingWidgetId
        if (id == NO_ID) return
        pendingWidgetId = NO_ID
        widgetHost.delete(id)
        Log.d(TAG, "cancelled widget id=$id")
    }

    private fun removeWidget(id: Int) {
        widgetIds = widgetIds - id
        widgetStore.save(widgetIds)
        widgetHost.delete(id)
    }

    private companion object {
        const val TAG = "HomeActivity"
        const val NO_ID = -1
        const val REQ_CONFIGURE = 101
        const val KEY_PENDING = "pending_widget_id"
    }
}

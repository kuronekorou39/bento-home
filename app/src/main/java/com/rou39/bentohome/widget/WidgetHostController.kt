package com.rou39.bentohome.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log

/**
 * AppWidgetHost をアプリスコープで1つ持つ。View は ID ごとに1回だけ作り、
 * Compose の再描画で作り直さない（docs/design.md §7）。
 */
class WidgetHostController(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val host = object : AppWidgetHost(context, HOST_ID) {
        override fun onProvidersChanged() {
            Log.d(TAG, "providers changed")
        }
    }

    private val views = mutableMapOf<Int, AppWidgetHostView>()

    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    fun providers(): List<AppWidgetProviderInfo> =
        manager.getInstalledProvidersForProfile(Process.myUserHandle())

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)

    fun bindPermissionIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    /** 提供元の設定画面を開く。結果は Activity.onActivityResult に requestCode で届く。 */
    fun startConfigure(activity: Activity, id: Int, requestCode: Int): Boolean = try {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    } catch (e: Exception) {
        Log.w(TAG, "configure failed for $id", e)
        false
    }

    fun hostIds(): Set<Int> = host.appWidgetIds.toSet()

    fun viewFor(id: Int): AppWidgetHostView =
        views.getOrPut(id) { host.createView(context, id, info(id)) }

    fun delete(id: Int) {
        views.remove(id)
        host.deleteAppWidgetId(id)
        Log.d(TAG, "deleted widget id $id")
    }

    private companion object {
        const val TAG = "WidgetHost"
        const val HOST_ID = 1024
    }
}

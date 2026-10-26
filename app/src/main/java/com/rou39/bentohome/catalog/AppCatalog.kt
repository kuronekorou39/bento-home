package com.rou39.bentohome.catalog

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** アプリを一意に指す鍵。表示名や並び順を ID にしない（plan.md §5）。 */
data class AppKey(val component: ComponentName, val profileSerial: Long)

data class AppEntry(
    val key: AppKey,
    val label: String,
    val user: UserHandle,
    val info: LauncherActivityInfo,
) {
    val packageName: String get() = key.component.packageName
}

class AppCatalog(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    // 段階1の検証用。段階4で UsageRepository に置き換える。
    private val launchCounts = mutableMapOf<AppKey, Int>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        // 初版はメインプロファイルのみ。鍵には最初から serial を含める。
        val user = Process.myUserHandle()
        val serial = userManager.getSerialNumberForUser(user)
        _apps.value = launcherApps.getActivityList(null, user)
            .map { AppEntry(AppKey(it.componentName, serial), it.label.toString(), user, it) }
            .sortedBy { it.label.lowercase() }
        Log.d(TAG, "catalog refreshed: ${_apps.value.size} apps")
    }

    fun launch(entry: AppEntry, sourceBounds: Rect?): Boolean = try {
        launcherApps.startMainActivity(entry.key.component, entry.user, sourceBounds, null)
        val count = (launchCounts[entry.key] ?: 0) + 1
        launchCounts[entry.key] = count
        Log.d(TAG, "launched ${entry.packageName} (session count=$count)")
        true
    } catch (e: Exception) {
        Log.w(TAG, "launch failed: ${entry.packageName}", e)
        false
    }

    private companion object {
        const val TAG = "AppCatalog"
    }
}

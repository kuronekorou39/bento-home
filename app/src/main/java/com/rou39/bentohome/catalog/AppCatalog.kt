package com.rou39.bentohome.catalog

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.rou39.bentohome.layout.AppRef
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppEntry(
    val ref: AppRef,
    val component: ComponentName,
    val label: String,
    val user: UserHandle,
    val info: LauncherActivityInfo,
) {
    val packageName: String get() = component.packageName

    val isSystemApp: Boolean
        get() = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
}

sealed interface PackageEvent {
    val packageName: String

    /** アンインストール。ホーム・ドックから該当項目を外す。 */
    data class Removed(override val packageName: String, val profileSerial: Long) : PackageEvent

    /** 追加・更新・有効化など。アイコンのキャッシュを捨てる。 */
    data class Changed(override val packageName: String) : PackageEvent
}

class AppCatalog(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _byRef = MutableStateFlow<Map<AppRef, AppEntry>>(emptyMap())
    val byRef: StateFlow<Map<AppRef, AppEntry>> = _byRef.asStateFlow()

    private val _events = MutableSharedFlow<PackageEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PackageEvent> = _events.asSharedFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refresh()
            _events.tryEmit(PackageEvent.Removed(packageName, userManager.getSerialNumberForUser(user)))
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    val mainProfileSerial: Long get() = userManager.getSerialNumberForUser(Process.myUserHandle())

    private fun changed(packageName: String) {
        refresh()
        _events.tryEmit(PackageEvent.Changed(packageName))
    }

    fun refresh() {
        // 初版はメインプロファイルのみ。鍵には最初から serial を含める。
        val user = Process.myUserHandle()
        val serial = userManager.getSerialNumberForUser(user)
        val entries = launcherApps.getActivityList(null, user)
            .map { AppEntry(AppRef(it.componentName.flattenToString(), serial), it.componentName, it.label.toString(), user, it) }
            .sortedBy { it.label.lowercase() }
        _apps.value = entries
        _byRef.value = entries.associateBy { it.ref }
        Log.d(TAG, "catalog refreshed: ${entries.size} apps")
    }

    /** メインプロファイルにパッケージが入っているか。無効化されているだけなら true。 */
    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** アプリ更新で起動 Activity 名が変わったときの付け替え先。 */
    fun firstEntryOf(packageName: String, profileSerial: Long): AppEntry? =
        _apps.value.firstOrNull { it.packageName == packageName && it.ref.profileSerial == profileSerial }

    fun launch(entry: AppEntry, sourceBounds: Rect?): Boolean = try {
        launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, null)
        Log.d(TAG, "launched ${entry.packageName}")
        true
    } catch (e: Exception) {
        Log.w(TAG, "launch failed: ${entry.packageName}", e)
        false
    }

    fun openAppInfo(entry: AppEntry, sourceBounds: Rect?) {
        runCatching { launcherApps.startAppDetailsActivity(entry.component, entry.user, sourceBounds, null) }
            .onFailure { Log.w(TAG, "app info failed: ${entry.packageName}", it) }
    }

    /**
     * 長押しメニューに出すアプリショートカット（静的・動的）。既定のホームでないと取得できない。
     * 静的なものを先に、それぞれ rank 順で最大 [MAX_SHORTCUTS] 件。
     */
    fun shortcutsFor(entry: AppEntry): List<ShortcutInfo> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(entry.packageName)
            .setActivity(entry.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        return runCatching { launcherApps.getShortcuts(query, entry.user).orEmpty() }
            .onFailure { Log.w(TAG, "shortcuts failed: ${entry.packageName}", it) }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(MAX_SHORTCUTS)
    }

    fun shortcutIcon(shortcut: ShortcutInfo, density: Int): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut, density) }.getOrNull()

    fun startShortcut(shortcut: ShortcutInfo, sourceBounds: Rect?): Boolean =
        runCatching { launcherApps.startShortcut(shortcut, sourceBounds, null) }
            .onSuccess { Log.d(TAG, "shortcut launched: ${shortcut.`package`}") }
            .onFailure { Log.w(TAG, "shortcut failed: ${shortcut.`package`}", it) }
            .isSuccess

    fun uninstallIntent(entry: AppEntry): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
            .putExtra(Intent.EXTRA_USER, entry.user)

    private companion object {
        const val TAG = "AppCatalog"
        const val MAX_SHORTCUTS = 4
    }
}

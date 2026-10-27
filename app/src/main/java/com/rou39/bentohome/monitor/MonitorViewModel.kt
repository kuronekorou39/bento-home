package com.rou39.bentohome.monitor

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rou39.bentohome.badge.BadgeListenerService
import com.rou39.bentohome.container
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.monitor.priv.PrivilegedProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class PermissionRow(val name: String, val purpose: String, val granted: Boolean)

/** 透明性のページの内容（docs/monitor.md）。 */
data class Transparency(
    val internetRequested: Boolean,
    /** 起動してから Bento の uid が送受信したバイト数。 */
    val ownRxBytes: Long,
    val ownTxBytes: Long,
    val permissions: List<PermissionRow>,
    val databaseBytes: Long,
    val settingsBytes: Long,
    val cacheBytes: Long,
    val pages: Int,
    val apps: Int,
    val widgets: Int,
    val processCpuMs: Long,
    val processUptimeMs: Long,
    val pssKb: Int,
    val home: SelfCounters,
)

data class WidgetRow(val widgetId: Int, val label: String, val page: Int, val stat: WidgetStat?)

class MonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val pm = app.packageManager
    private val privileged = c.privileged
    val state = c.monitor.state
    val privilegedStatus = privileged.status
    val widgetStats = c.widgetMetrics.stats
    val widgetMetricsSince = c.widgetMetrics.since

    private val _daily = MutableStateFlow<DailyAppStats?>(null)
    private val _storage = MutableStateFlow<Map<Int, Long>>(emptyMap())
    private val _storageLoading = MutableStateFlow(false)
    val storageLoading: StateFlow<Boolean> = _storageLoading.asStateFlow()
    private val _usageGranted = MutableStateFlow(false)
    val usageGranted: StateFlow<Boolean> = _usageGranted.asStateFlow()
    private val _dumpGranted = MutableStateFlow(false)
    val dumpGranted: StateFlow<Boolean> = _dumpGranted.asStateFlow()

    private val labels = ConcurrentHashMap<String, String>()
    private val uidPackages = ConcurrentHashMap<Int, List<String>>()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    val rows: StateFlow<List<AppRow>> = combine(state, _daily, _storage) { s, daily, storage ->
        AppRows.build(s.processes, daily, storage, ::packagesForUid, ::label, app.packageName)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshPermissions() {
        _usageGranted.value = c.appStats.usageAccessGranted()
        _dumpGranted.value = c.monitor.hasDump()
    }

    fun refreshDaily() {
        viewModelScope.launch(Dispatchers.IO) {
            _daily.value = if (c.appStats.usageAccessGranted()) c.appStats.today() else null
        }
    }

    /** 全アプリの容量を調べる。数秒かかる。 */
    fun loadStorage() {
        if (_storageLoading.value) return
        _storageLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val uids = pm.getInstalledApplications(0).map { it.uid }.toSet()
            _storage.value = uids.mapNotNull { uid -> c.appStats.storageBytes(uid)?.let { uid to it } }.toMap()
            _storageLoading.value = false
        }
    }

    /** 計測用プロセス（シェル権限）で、使用状況へのアクセスと温度の読み取りを Bento に付与する。 */
    fun grantWithPrivileged() {
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = getApplication<Application>().packageName
            privileged.exec("appops", "set", pkg, "GET_USAGE_STATS", "allow")
            privileged.exec("pm", "grant", pkg, Manifest.permission.DUMP)
            withContext(Dispatchers.Main) {
                refreshPermissions()
                refreshDaily()
            }
        }
    }

    val packageName: String = app.packageName

    // ---- 棚卸し（plan.md §10） ----

    private val _inventory = MutableStateFlow(InventoryState())
    val inventory: StateFlow<InventoryState> = _inventory.asStateFlow()

    /** インストールしているアプリ（起動できるもの）。アンインストール後に行を消すのに使う。 */
    val installedApps = c.appCatalog.apps

    /** 棚卸しの値を読み直す。数秒かかる。 */
    fun loadInventory() {
        if (_inventory.value.loading) return
        _inventory.value = _inventory.value.copy(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val apps = c.appCatalog.apps.value.distinctBy { it.packageName }
            val usageOk = c.appStats.usageAccessGranted()
            val lastUsed = if (usageOk) c.appStats.lastUsed(now - INVENTORY_LAST_USED_MS) else emptyMap()
            val network = if (usageOk) c.appStats.networkByUid(now - INVENTORY_NETWORK_MS, now) else emptyMap()
            val bento = c.usageRepository.usage.first()
            val privilegedOk = privileged.service.value != null
            val battery = if (privilegedOk) {
                privileged.exec(*InventoryParsing.BATTERY_COMMAND)?.let(InventoryParsing::parseBattery).orEmpty()
            } else {
                emptyMap()
            }
            val sensitive = if (privilegedOk) {
                privileged.exec(*InventoryParsing.APPOPS_COMMAND)?.let { InventoryParsing.parseAppOps(it) }.orEmpty()
            } else {
                emptyMap()
            }
            val rows = apps.map { e ->
                val uid = e.info.applicationInfo.uid
                val bentoLast = bento.filterKeys { it.packageName == e.packageName }.values.mapNotNull { it.lastLaunchAt }.maxOrNull()
                InventoryRow(
                    packageName = e.packageName,
                    label = e.label,
                    uid = uid,
                    isSystem = e.isSystemApp,
                    lastUsedAt = lastUsed[e.packageName],
                    bentoLastLaunchAt = bentoLast,
                    storageBytes = if (usageOk) c.appStats.storageBytes(uid) else null,
                    networkBytes = network[uid]?.first ?: if (usageOk) 0L else null,
                    backgroundNetworkBytes = network[uid]?.second ?: if (usageOk) 0L else null,
                    battery = battery[uid] ?: if (privilegedOk) BatteryUse(0.0, 0.0) else null,
                    sensitive = sensitive[e.packageName] ?: if (privilegedOk) SensitiveUse() else null,
                )
            }
            _inventory.value = InventoryState(rows, false, usageOk, privilegedOk, now)
        }
    }

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))

    /** PC の adb から計測用プロセスを起動するコマンド。 */
    val startCommand = PrivilegedProtocol.startCommand(packageName)

    fun copyStartCommand() {
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("adb", startCommand))
    }

    fun forceStop(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) { privileged.exec("am", "force-stop", packageName) }
    }

    fun icon(packageName: String): ImageBitmap? = icons[packageName] ?: runCatching {
        pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
    }.getOrNull()?.also { icons[packageName] = it }

    suspend fun widgets(): List<WidgetRow> = withContext(Dispatchers.IO) {
        val ws = c.workspaceRepository.load()
        val stats = widgetStats.value
        ws.items.mapNotNull { item ->
            val w = item.content as? ItemContent.Widget ?: return@mapNotNull null
            if (w.needsReAdd) return@mapNotNull null
            val label = c.widgetHost.info(w.widgetId)?.loadLabel(pm) ?: w.provider.substringBefore('/')
            WidgetRow(w.widgetId, label, ws.orderedPages.indexOfFirst { it.id == item.pageId } + 1, stats[w.widgetId])
        }.sortedBy { it.page }
    }

    suspend fun transparency(): Transparency = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val info: PackageInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        val flags = info.requestedPermissionsFlags ?: IntArray(0)
        val permissions = requested.mapIndexed { i, name ->
            var granted = (flags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            if (name == Manifest.permission.PACKAGE_USAGE_STATS) granted = c.appStats.usageAccessGranted()
            PermissionRow(name.substringAfterLast('.'), PURPOSES[name] ?: "ライブラリが内部で使う権限", granted)
        } + listOf(
            PermissionRow(
                "通知へのアクセス",
                "通知バッジの件数を数えるため。内容は読まず、保存もしない",
                BadgeListenerService.isAccessGranted(app),
            ),
        )
        val ws = c.workspaceRepository.load()
        val mem = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        Transparency(
            internetRequested = Manifest.permission.INTERNET in requested,
            ownRxBytes = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0),
            ownTxBytes = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0),
            permissions = permissions,
            databaseBytes = app.getDatabasePath("bento.db").parentFile?.let(::sizeOf) ?: 0,
            settingsBytes = sizeOf(File(app.filesDir, "datastore")),
            cacheBytes = sizeOf(app.cacheDir),
            pages = ws.pages.size,
            apps = ws.appCount,
            widgets = ws.items.count { it.content is ItemContent.Widget },
            processCpuMs = Process.getElapsedCpuTime(),
            processUptimeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime(),
            pssKb = mem.totalPss,
            home = c.selfMetrics.homeTotals(),
        )
    }

    private fun packagesForUid(uid: Int): List<String> =
        uidPackages.getOrPut(uid) { pm.getPackagesForUid(uid)?.toList().orEmpty() }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
    }

    private fun sizeOf(f: File): Long = if (f.isDirectory) f.listFiles().orEmpty().sumOf(::sizeOf) else f.length()

    private companion object {
        /** 棚卸しで見る使用記録の期間。 */
        const val INVENTORY_LAST_USED_MS = 365L * 24 * 60 * 60 * 1000

        /** 棚卸しで見る通信量の期間。 */
        const val INVENTORY_NETWORK_MS = 30L * 24 * 60 * 60 * 1000

        const val ICON_PX = 96
        val PURPOSES = mapOf(
            Manifest.permission.REQUEST_DELETE_PACKAGES to "長押しメニューの「アンインストール」で OS の確認画面を開くため",
            Manifest.permission.EXPAND_STATUS_BAR to "ホームの背景を下にスワイプして通知パネルを開くため",
            Manifest.permission.PACKAGE_USAGE_STATS to "アプリごとの使用時間・通信量・容量を表示するため",
            Manifest.permission.DUMP to "温度センサーの値を読むため。adb で付与する",
            Manifest.permission.QUERY_ALL_PACKAGES to "プロセスの持ち主をアプリ名で表示するため",
        )
    }
}

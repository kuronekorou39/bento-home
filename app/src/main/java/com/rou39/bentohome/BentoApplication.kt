package com.rou39.bentohome

import android.app.Application
import android.content.Context
import com.rou39.bentohome.catalog.AppCatalog
import com.rou39.bentohome.catalog.PackageEvent
import com.rou39.bentohome.data.AppearanceRepository
import com.rou39.bentohome.data.BentoDatabase
import com.rou39.bentohome.data.SettingsRepository
import com.rou39.bentohome.data.WorkspaceRepository
import com.rou39.bentohome.icon.IconCache
import com.rou39.bentohome.monitor.AppStats
import com.rou39.bentohome.monitor.MonitorRepository
import com.rou39.bentohome.monitor.SelfMetrics
import com.rou39.bentohome.monitor.ShizukuBridge
import com.rou39.bentohome.monitor.WidgetMetrics
import com.rou39.bentohome.usage.UsageRepository
import com.rou39.bentohome.widget.SpikeWidgetStore
import com.rou39.bentohome.widget.WidgetHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import com.rou39.bentohome.backup.RestorePlan
import kotlinx.coroutines.launch

class BentoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 手書きの依存コンテナ（docs/design.md §2）。 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val iconCache = IconCache(context)
    val appCatalog = AppCatalog(context)
    val widgetMetrics = WidgetMetrics()
    val widgetHost = WidgetHostController(context, widgetMetrics)
    val database = BentoDatabase.create(context)
    val workspaceRepository = WorkspaceRepository(database.workspaceDao(), appScope)
    val settingsRepository = SettingsRepository(context)
    val appearanceRepository = AppearanceRepository(database.appearanceDao())
    val usageRepository = UsageRepository(database.usageDao(), appScope)
    val selfMetrics = SelfMetrics()
    val shizuku = ShizukuBridge(context)
    val monitor = MonitorRepository(context, appScope, shizuku)
    val appStats = AppStats(context)

    /** 設定画面からホームへ復元を渡す。ホームが購読していなければ設定画面が直接保存する。 */
    val restoreRequests = MutableSharedFlow<RestorePlan>()

    /** 段階1の検証で追加したウィジェット ID。初回起動時に Room へ移して空にする。 */
    val spikeWidgetStore = SpikeWidgetStore(context)

    init {
        appScope.launch {
            appCatalog.events.collect { event ->
                iconCache.invalidatePackage(event.packageName)
                if (event is PackageEvent.Removed) usageRepository.forgetPackage(event.packageName, event.profileSerial)
            }
        }
        // 新しく見つかったアプリの観測開始を記録する（plan.md §4）
        appScope.launch { appCatalog.apps.collect { apps -> usageRepository.observe(apps.map { it.ref }) } }
        usageRepository.prune()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as BentoApplication).container

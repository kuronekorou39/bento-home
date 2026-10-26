package com.rou39.bentohome

import android.app.Application
import android.content.Context
import com.rou39.bentohome.catalog.AppCatalog
import com.rou39.bentohome.data.AppearanceRepository
import com.rou39.bentohome.data.BentoDatabase
import com.rou39.bentohome.data.SettingsRepository
import com.rou39.bentohome.data.WorkspaceRepository
import com.rou39.bentohome.icon.IconCache
import com.rou39.bentohome.widget.SpikeWidgetStore
import com.rou39.bentohome.widget.WidgetHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val widgetHost = WidgetHostController(context)
    val database = BentoDatabase.create(context)
    val workspaceRepository = WorkspaceRepository(database.workspaceDao(), appScope)
    val settingsRepository = SettingsRepository(context)
    val appearanceRepository = AppearanceRepository(database.appearanceDao())

    /** 段階1の検証で追加したウィジェット ID。初回起動時に Room へ移して空にする。 */
    val spikeWidgetStore = SpikeWidgetStore(context)

    init {
        appScope.launch {
            appCatalog.events.collect { iconCache.invalidatePackage(it.packageName) }
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as BentoApplication).container

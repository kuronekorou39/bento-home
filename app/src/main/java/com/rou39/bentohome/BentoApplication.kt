package com.rou39.bentohome

import android.app.Application
import android.content.Context
import com.rou39.bentohome.catalog.AppCatalog
import com.rou39.bentohome.widget.SpikeWidgetStore
import com.rou39.bentohome.widget.WidgetHostController

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
    val appCatalog = AppCatalog(context)
    val widgetHost = WidgetHostController(context)
    val widgetStore = SpikeWidgetStore(context)
}

val Context.container: AppContainer
    get() = (applicationContext as BentoApplication).container

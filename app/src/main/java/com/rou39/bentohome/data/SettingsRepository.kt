package com.rou39.bentohome.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LauncherSettings(
    val layoutLocked: Boolean = false,
    val dockVisible: Boolean = true,
) {
    companion object {
        // 列数・行数・ドック数の設定画面は段階3で追加する。それまではこの初期値で固定。
        const val DEFAULT_BASE_COLUMNS = 4
        const val DEFAULT_BASE_ROWS = 6
        const val DEFAULT_SUBDIVISIONS = 2
        const val DOCK_SIZE = 4
    }
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val layoutLockedKey = booleanPreferencesKey("layout_locked")
    private val dockVisibleKey = booleanPreferencesKey("dock_visible")

    val settings: Flow<LauncherSettings> = context.settingsStore.data.map { p ->
        LauncherSettings(
            layoutLocked = p[layoutLockedKey] ?: false,
            dockVisible = p[dockVisibleKey] ?: true,
        )
    }

    suspend fun setLayoutLocked(locked: Boolean) {
        context.settingsStore.edit { it[layoutLockedKey] = locked }
    }

    suspend fun setDockVisible(visible: Boolean) {
        context.settingsStore.edit { it[dockVisibleKey] = visible }
    }
}

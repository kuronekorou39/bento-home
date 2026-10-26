package com.rou39.bentohome.widget

import android.content.Context
import androidx.core.content.edit

/** 段階1の検証で使った保存先。段階2の初回起動で Room の workspace_item へ移し、空にする。 */
class SpikeWidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("spike_widgets", Context.MODE_PRIVATE)

    fun load(): List<Int> =
        prefs.getString(KEY, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    fun clear() = prefs.edit { remove(KEY) }

    private companion object {
        const val KEY = "ids"
    }
}

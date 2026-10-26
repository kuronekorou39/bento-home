package com.rou39.bentohome.widget

import android.content.Context
import androidx.core.content.edit

/** 段階1の再起動検証用。段階2で Room の workspace_item に置き換える。 */
class SpikeWidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("spike_widgets", Context.MODE_PRIVATE)

    fun load(): List<Int> =
        prefs.getString(KEY, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    fun save(ids: List<Int>) = prefs.edit { putString(KEY, ids.joinToString(",")) }

    private companion object {
        const val KEY = "ids"
    }
}

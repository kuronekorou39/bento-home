package com.rou39.bentohome.home

import com.rou39.bentohome.layout.ItemContent

/** 「ホーム画面に追加」で受け入れ、ホームに置くのを待っている項目（plan.md §10）。 */
sealed interface PinnedItem {
    data class Shortcut(val content: ItemContent.Shortcut) : PinnedItem

    /** 割り当てと許可を済ませたウィジェット ID。 */
    data class Widget(val widgetId: Int) : PinnedItem
}

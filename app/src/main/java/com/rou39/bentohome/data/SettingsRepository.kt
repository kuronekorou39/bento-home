package com.rou39.bentohome.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** アイコンの形。SYSTEM は端末の設定に従う。 */
enum class IconShape(val label: String) {
    SYSTEM("端末の標準"),
    CIRCLE("円"),
    ROUNDED("角丸四角"),
    SQUARE("四角"),
}

/** 通知バッジの表示方式（plan.md §2）。 */
enum class BadgeMode(val label: String) {
    NONE("なし"),
    DOT("点"),
    NUMBER("数値"),
}

enum class LabelSize(val label: String, val sp: Int) {
    SMALL("小", 11),
    MEDIUM("中", 12),
    LARGE("大", 14),
}

/** アプリ一覧の並び順（plan.md §2）。 */
/** 灰色にする段階の日数（plan.md §10）。越えるたびに色を抜く。 */
enum class GrayStages(val days: List<Int>) {
    SHORT(listOf(7, 14, 30)),
    MEDIUM(listOf(14, 30, 60)),
    LONG(listOf(30, 60, 90)),
    ;

    val label: String get() = days.joinToString("・") + "日"

    companion object {
        /** 段階を入れる前の「薄くするまでの日数」から、始まりが近いものを選ぶ。 */
        fun fromLegacyDays(days: Int): GrayStages = when {
            days <= 7 -> SHORT
            days <= 14 -> MEDIUM
            else -> LONG
        }
    }
}

enum class DrawerSort(val label: String) {
    NAME("名前順"),
    FREQUENT("よく使う順"),
    RECENT("最近開いた順"),
}

data class LauncherSettings(
    val layoutLocked: Boolean = false,
    val dockVisible: Boolean = true,
    val baseColumns: Int = 4,
    val baseRows: Int = 6,
    val dockSlots: Int = 4,
    val iconSizeDp: Int = 56,
    val iconShape: IconShape = IconShape.SYSTEM,
    val labelVisible: Boolean = true,
    val labelSize: LabelSize = LabelSize.MEDIUM,
    val badgeMode: BadgeMode = BadgeMode.NUMBER,
    val drawerSort: DrawerSort = DrawerSort.NAME,
    val grayOnHome: Boolean = true,
    val grayInDrawer: Boolean = true,
    val grayStages: GrayStages = GrayStages.MEDIUM,
    /** ホームの背景を下にスワイプして通知パネルを開く（plan.md §10）。 */
    val swipeDownNotifications: Boolean = true,
) {
    companion object {
        const val SUBDIVISIONS = 2
        val COLUMNS_RANGE = 3..6
        val ROWS_RANGE = 4..8
        val DOCK_SLOTS_RANGE = 3..6
        val ICON_SIZE_RANGE = 40..72
    }
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val layoutLocked = booleanPreferencesKey("layout_locked")
        val dockVisible = booleanPreferencesKey("dock_visible")
        val baseColumns = intPreferencesKey("base_columns")
        val baseRows = intPreferencesKey("base_rows")
        val dockSlots = intPreferencesKey("dock_slots")
        val iconSizeDp = intPreferencesKey("icon_size_dp")
        val iconShape = stringPreferencesKey("icon_shape")
        val labelVisible = booleanPreferencesKey("label_visible")
        val labelSize = stringPreferencesKey("label_size")
        val badgeMode = stringPreferencesKey("badge_mode")
        val drawerSort = stringPreferencesKey("drawer_sort")
        val grayOnHome = booleanPreferencesKey("gray_on_home")
        val grayInDrawer = booleanPreferencesKey("gray_in_drawer")
        /** 段階を入れる前の設定。読むだけ（gray_stages が無いときの移行に使う）。 */
        val legacyGrayDays = intPreferencesKey("gray_days")
        val grayStages = stringPreferencesKey("gray_stages")
        val swipeDownNotifications = booleanPreferencesKey("swipe_down_notifications")
    }

    val settings: Flow<LauncherSettings> = context.settingsStore.data.map(::read)

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsStore.edit { p ->
            val s = transform(read(p))
            p[Keys.layoutLocked] = s.layoutLocked
            p[Keys.dockVisible] = s.dockVisible
            p[Keys.baseColumns] = s.baseColumns.coerceIn(LauncherSettings.COLUMNS_RANGE)
            p[Keys.baseRows] = s.baseRows.coerceIn(LauncherSettings.ROWS_RANGE)
            p[Keys.dockSlots] = s.dockSlots.coerceIn(LauncherSettings.DOCK_SLOTS_RANGE)
            p[Keys.iconSizeDp] = s.iconSizeDp.coerceIn(LauncherSettings.ICON_SIZE_RANGE)
            p[Keys.iconShape] = s.iconShape.name
            p[Keys.labelVisible] = s.labelVisible
            p[Keys.labelSize] = s.labelSize.name
            p[Keys.badgeMode] = s.badgeMode.name
            p[Keys.drawerSort] = s.drawerSort.name
            p[Keys.grayOnHome] = s.grayOnHome
            p[Keys.grayInDrawer] = s.grayInDrawer
            p[Keys.grayStages] = s.grayStages.name
            p[Keys.swipeDownNotifications] = s.swipeDownNotifications
        }
    }

    suspend fun setLayoutLocked(locked: Boolean) = update { it.copy(layoutLocked = locked) }

    private fun read(p: Preferences): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            layoutLocked = p[Keys.layoutLocked] ?: d.layoutLocked,
            dockVisible = p[Keys.dockVisible] ?: d.dockVisible,
            baseColumns = p[Keys.baseColumns] ?: d.baseColumns,
            baseRows = p[Keys.baseRows] ?: d.baseRows,
            dockSlots = p[Keys.dockSlots] ?: d.dockSlots,
            iconSizeDp = p[Keys.iconSizeDp] ?: d.iconSizeDp,
            iconShape = enumOr(p[Keys.iconShape], d.iconShape),
            labelVisible = p[Keys.labelVisible] ?: d.labelVisible,
            labelSize = enumOr(p[Keys.labelSize], d.labelSize),
            badgeMode = enumOr(p[Keys.badgeMode], d.badgeMode),
            drawerSort = enumOr(p[Keys.drawerSort], d.drawerSort),
            grayOnHome = p[Keys.grayOnHome] ?: d.grayOnHome,
            grayInDrawer = p[Keys.grayInDrawer] ?: d.grayInDrawer,
            grayStages = p[Keys.grayStages]?.let { runCatching { GrayStages.valueOf(it) }.getOrNull() }
                ?: p[Keys.legacyGrayDays]?.let(GrayStages::fromLegacyDays)
                ?: d.grayStages,
            swipeDownNotifications = p[Keys.swipeDownNotifications] ?: d.swipeDownNotifications,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}

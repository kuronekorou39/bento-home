package com.rou39.bentohome.data

import com.rou39.bentohome.layout.AppRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppAppearance(
    val keepColor: Boolean = false,
    val excludeFromRanking: Boolean = false,
    /** null なら全体の設定に従う。 */
    val badgeMode: BadgeMode? = null,
    /** アプリ一覧に出さない（plan.md §10）。 */
    val hidden: Boolean = false,
    /** 表示名。null なら元の名前。 */
    val label: String? = null,
    /** ホームのアイコンを隠す設定のときも、このアプリは元のアイコンで出す。 */
    val disguiseExempt: Boolean = false,
) {
    val isDefault: Boolean get() = this == AppAppearance()
}

class AppearanceRepository(private val dao: AppearanceDao) {
    val appearances: Flow<Map<AppRef, AppAppearance>> = dao.observeAll().map { rows ->
        rows.associate { e ->
            AppRef(e.component, e.profileSerial) to AppAppearance(
                keepColor = e.keepColor,
                excludeFromRanking = e.excludeFromRanking,
                badgeMode = e.badgeMode?.let { runCatching { BadgeMode.valueOf(it) }.getOrNull() },
                hidden = e.hidden,
                label = e.label,
                disguiseExempt = e.disguiseExempt,
            )
        }
    }

    suspend fun replaceAll(all: Map<AppRef, AppAppearance>) {
        dao.replaceAll(all.filterValues { !it.isDefault }.map { (ref, a) -> toEntity(ref, a) })
    }

    private fun toEntity(app: AppRef, a: AppAppearance) = AppAppearanceEntity(
        component = app.component,
        profileSerial = app.profileSerial,
        keepColor = a.keepColor,
        excludeFromRanking = a.excludeFromRanking,
        badgeMode = a.badgeMode?.name,
        hidden = a.hidden,
        label = a.label,
        disguiseExempt = a.disguiseExempt,
    )

    suspend fun set(app: AppRef, appearance: AppAppearance) {
        if (appearance.isDefault) {
            dao.delete(app.component, app.profileSerial)
        } else {
            dao.upsert(toEntity(app, appearance))
        }
    }
}

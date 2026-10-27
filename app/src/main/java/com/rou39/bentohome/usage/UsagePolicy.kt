package com.rou39.bentohome.usage

import com.rou39.bentohome.layout.AppRef
import kotlin.math.pow

/**
 * このランチャーから起動した記録（plan.md §4）。
 * @param daily ローカル日付（epochDay）ごとの起動回数。最大 [UsagePolicy.RETENTION_DAYS] 日分。
 * @param observedSince このランチャーがアプリを初めて観測した時刻（ミリ秒）。null なら観測前。
 * @param lastLaunchAt 最後に起動した時刻（ミリ秒）。
 */
data class AppUsage(
    val daily: Map<Long, Int> = emptyMap(),
    val observedSince: Long? = null,
    val lastLaunchAt: Long? = null,
)

/** 並び替えの入力。excluded は「頻度集計から除外」。 */
data class RankInput(val app: AppRef, val label: String, val usage: AppUsage?, val excluded: Boolean)

/** グレー表示の理由。長押しで表示する。level は灰色の段階（1〜、大きいほど色を抜く、plan.md §10）。 */
sealed interface GrayReason {
    val days: Int
    val level: Int

    /** 最後の起動から days 日。 */
    data class NotLaunchedSince(override val days: Int, override val level: Int) : GrayReason

    /** 観測を始めてから days 日、一度も起動していない。 */
    data class NeverLaunched(override val days: Int, override val level: Int) : GrayReason
}

object UsagePolicy {
    /** 日別件数を残す日数。 */
    const val RETENTION_DAYS = 60

    /** 頻度順で見る期間。 */
    const val WINDOW_DAYS = 28

    /** この日数で重みが半分になる。 */
    const val HALF_LIFE_DAYS = 7.0

    /** 同じアプリの起動がこの間隔より短く続いたら1回にまとめる。 */
    const val DEBOUNCE_MS = 5_000L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun shouldRecord(lastRecordedAt: Long?, now: Long): Boolean =
        lastRecordedAt == null || now - lastRecordedAt >= DEBOUNCE_MS

    /** 過去 [WINDOW_DAYS] 日の日別件数に、[HALF_LIFE_DAYS] 日で半分になる重みを掛けて合計する。 */
    fun score(daily: Map<Long, Int>, today: Long): Double =
        daily.entries
            .filter { (day, _) -> today - day in 0 until WINDOW_DAYS }
            .sumOf { (day, count) -> count * 0.5.pow((today - day) / HALF_LIFE_DAYS) }

    /** 頻度順。同点は最終起動が新しい順、次に名前順。除外したアプリは頻度0として扱う。 */
    fun sortByFrequency(items: List<RankInput>, today: Long): List<AppRef> {
        fun scoreOf(i: RankInput) = if (i.excluded) 0.0 else score(i.usage?.daily.orEmpty(), today)
        fun lastOf(i: RankInput) = if (i.excluded) null else i.usage?.lastLaunchAt
        return items.sortedWith(
            compareByDescending<RankInput> { scoreOf(it) }
                .thenByDescending { lastOf(it) ?: Long.MIN_VALUE }
                .thenBy { it.label.lowercase() },
        ).map { it.app }
    }

    /** 最近開いた順。一度も開いていない・除外したアプリは後ろに名前順。 */
    fun sortByRecent(items: List<RankInput>): List<AppRef> =
        items.sortedWith(
            compareByDescending<RankInput> { if (it.excluded) Long.MIN_VALUE else it.usage?.lastLaunchAt ?: Long.MIN_VALUE }
                .thenBy { it.label.lowercase() },
        ).map { it.app }

    /**
     * グレー表示にするか（plan.md §4）。
     * 通常色に固定したアプリ・バッジのあるアプリは対象外。観測前・観測開始から日が浅いアプリは「未使用」と断定しない。
     * stages は段階ごとの日数（昇順）。越えた数が段階になり、最初の日数に届かなければ null（plan.md §10）。
     */
    fun grayReason(usage: AppUsage?, now: Long, stages: List<Int>, keepColor: Boolean, hasBadge: Boolean): GrayReason? {
        if (keepColor || hasBadge || usage == null) return null
        val last = usage.lastLaunchAt
        val since = if (last != null) last else usage.observedSince ?: return null
        val days = daysBetween(since, now)
        val level = stages.count { days >= it }
        if (level == 0) return null
        return if (last != null) GrayReason.NotLaunchedSince(days, level) else GrayReason.NeverLaunched(days, level)
    }

    private fun daysBetween(from: Long, to: Long): Int = ((to - from).coerceAtLeast(0) / DAY_MS).toInt()
}

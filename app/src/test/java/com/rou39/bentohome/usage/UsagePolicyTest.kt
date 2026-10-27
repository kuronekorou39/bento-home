package com.rou39.bentohome.usage

import com.rou39.bentohome.layout.AppRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val today = 20_000L
    private val now = today * day

    private fun ref(name: String) = AppRef("com.$name/.Main", 0)

    @Test
    fun debounceMergesQuickRelaunch() {
        assertTrue(UsagePolicy.shouldRecord(null, 1_000))
        assertFalse(UsagePolicy.shouldRecord(1_000, 5_999))
        assertTrue(UsagePolicy.shouldRecord(1_000, 6_000))
    }

    @Test
    fun scoreHalvesEverySevenDaysAndIgnoresOldDays() {
        assertEquals(1.0, UsagePolicy.score(mapOf(today to 1), today), 1e-9)
        assertEquals(0.5, UsagePolicy.score(mapOf(today - 7 to 1), today), 1e-9)
        assertEquals(0.0, UsagePolicy.score(mapOf(today - 28 to 10), today), 1e-9)
        // 未来の日付（端末の時計が戻った場合）は数えない
        assertEquals(0.0, UsagePolicy.score(mapOf(today + 1 to 3), today), 1e-9)
    }

    @Test
    fun frequencyOrderUsesScoreThenLastLaunchThenName() {
        val items = listOf(
            RankInput(ref("b"), "B", AppUsage(mapOf(today to 1), lastLaunchAt = 100), false),
            RankInput(ref("a"), "A", AppUsage(mapOf(today to 1), lastLaunchAt = 200), false),
            RankInput(ref("c"), "C", AppUsage(mapOf(today to 3)), false),
            RankInput(ref("d"), "D", null, false),
            RankInput(ref("e"), "E", null, false),
        )
        assertEquals(listOf(ref("c"), ref("a"), ref("b"), ref("d"), ref("e")), UsagePolicy.sortByFrequency(items, today))
    }

    @Test
    fun excludedAppsAreRankedAsUnused() {
        val items = listOf(
            RankInput(ref("x"), "X", AppUsage(mapOf(today to 9), lastLaunchAt = 900), true),
            RankInput(ref("y"), "Y", AppUsage(mapOf(today to 1), lastLaunchAt = 100), false),
        )
        assertEquals(listOf(ref("y"), ref("x")), UsagePolicy.sortByFrequency(items, today))
        assertEquals(listOf(ref("y"), ref("x")), UsagePolicy.sortByRecent(items))
    }

    @Test
    fun recentOrderPutsNeverLaunchedLastByName() {
        val items = listOf(
            RankInput(ref("z"), "Z", null, false),
            RankInput(ref("old"), "Old", AppUsage(lastLaunchAt = 10), false),
            RankInput(ref("new"), "New", AppUsage(lastLaunchAt = 20), false),
            RankInput(ref("a"), "A", AppUsage(observedSince = 1), false),
        )
        assertEquals(listOf(ref("new"), ref("old"), ref("a"), ref("z")), UsagePolicy.sortByRecent(items))
    }

    @Test
    fun grayAfterThresholdSinceLastLaunch() {
        val usage = AppUsage(observedSince = now - 100 * day, lastLaunchAt = now - 30 * day)
        assertEquals(GrayReason.NotLaunchedSince(30, 1), UsagePolicy.grayReason(usage, now, STAGES, false, false))
        assertNull(UsagePolicy.grayReason(usage.copy(lastLaunchAt = now - 29 * day), now, STAGES, false, false))
    }

    @Test
    fun neverLaunchedIsGrayOnlyAfterEnoughObservation() {
        val fresh = AppUsage(observedSince = now - 3 * day)
        assertNull(UsagePolicy.grayReason(fresh, now, STAGES, false, false))
        val old = AppUsage(observedSince = now - 45 * day)
        assertEquals(GrayReason.NeverLaunched(45, 1), UsagePolicy.grayReason(old, now, STAGES, false, false))
    }

    @Test
    fun unknownHistoryIsNeverGray() {
        assertNull(UsagePolicy.grayReason(null, now, STAGES, false, false))
        assertNull(UsagePolicy.grayReason(AppUsage(), now, STAGES, false, false))
    }

    @Test
    fun keepColorAndBadgeOverrideGray() {
        val old = AppUsage(observedSince = now - 90 * day)
        assertNull(UsagePolicy.grayReason(old, now, STAGES, keepColor = true, hasBadge = false))
        assertNull(UsagePolicy.grayReason(old, now, STAGES, keepColor = false, hasBadge = true))
    }

    @Test
    fun levelGrowsWithEachStage() {
        val now = 200L * day
        fun level(daysAgo: Int) = UsagePolicy.grayReason(
            AppUsage(observedSince = 0, lastLaunchAt = now - daysAgo * day), now, STAGES, false, false,
        )?.level ?: 0
        assertEquals(listOf(0, 1, 1, 2, 3, 3), listOf(29, 30, 59, 60, 90, 150).map(::level))
    }

    private companion object {
        val STAGES = listOf(30, 60, 90)
    }
}

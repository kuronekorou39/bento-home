package com.rou39.bentohome.badge

import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeAggregatorTest {
    private fun fact(
        key: String,
        pkg: String = "com.example.chat",
        user: Long = 0,
        group: String? = null,
        summary: Boolean = false,
        ongoing: Boolean = false,
        canShowBadge: Boolean = true,
    ) = NotificationFact(key, pkg, user, group, summary, ongoing, canShowBadge)

    private val chat = BadgeKey("com.example.chat", 0)

    @Test
    fun countsEachActiveNotification() {
        val result = BadgeAggregator.aggregate(listOf(fact("a"), fact("b")))
        assertEquals(mapOf(chat to 2), result)
    }

    @Test
    fun sameKeyIsCountedOnce() {
        val result = BadgeAggregator.aggregate(listOf(fact("a"), fact("a")))
        assertEquals(mapOf(chat to 1), result)
    }

    @Test
    fun summaryWithChildrenIsNotCounted() {
        val result = BadgeAggregator.aggregate(
            listOf(fact("s", group = "g", summary = true), fact("c1", group = "g"), fact("c2", group = "g")),
        )
        assertEquals(mapOf(chat to 2), result)
    }

    @Test
    fun summaryWithoutChildrenIsCountedOnce() {
        val result = BadgeAggregator.aggregate(listOf(fact("s", group = "g", summary = true)))
        assertEquals(mapOf(chat to 1), result)
    }

    @Test
    fun ongoingAndBadgeDisabledAreExcluded() {
        val result = BadgeAggregator.aggregate(
            listOf(fact("a", ongoing = true), fact("b", canShowBadge = false), fact("c")),
        )
        assertEquals(mapOf(chat to 1), result)
    }

    @Test
    fun profilesAreNotMixed() {
        val result = BadgeAggregator.aggregate(listOf(fact("a", user = 0), fact("b", user = 10)))
        assertEquals(mapOf(chat to 1, BadgeKey("com.example.chat", 10) to 1), result)
    }

    @Test
    fun noNotificationsGivesEmptyMap() {
        assertEquals(emptyMap<BadgeKey, Int>(), BadgeAggregator.aggregate(emptyList()))
    }
}

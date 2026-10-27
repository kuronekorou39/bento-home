package com.rou39.bentohome.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仕切りの区画（plan.md §10）。 */
class PartitionTest {
    private val epsilon = 1e-4f

    private fun overlaps(a: Partition.Area, b: Partition.Area) =
        a.left < b.right - epsilon && b.left < a.right - epsilon && a.top < b.bottom - epsilon && b.top < a.bottom - epsilon

    @Test
    fun everyPresetCoversThePageWithoutOverlap() {
        for (p in Partition.entries - Partition.NONE) {
            val areas = p.areas
            assertTrue("$p", areas.all { it.left >= 0f && it.top >= 0f && it.right <= 1f && it.bottom <= 1f })
            for (i in areas.indices) for (j in i + 1 until areas.size) assertTrue("$p overlap", !overlaps(areas[i], areas[j]))
            assertEquals("$p coverage", 1f, areas.sumOf { (it.width * it.height).toDouble() }.toFloat(), epsilon)
        }
        assertEquals(emptyList<Partition.Area>(), Partition.NONE.areas)
    }

    @Test
    fun equalCompartmentsStayEqual() {
        // 3×3 は列数に関係なく同じ幅にそろう
        val widths = Partition.JUBAKO.areas.map { it.width }.distinct()
        assertEquals(1, widths.map { Math.round(it * 1000) }.distinct().size)
        assertEquals(9, Partition.JUBAKO.areas.size)
    }

    @Test
    fun unknownNameFallsBackToNone() {
        assertEquals(Partition.NONE, Partition.fromName("FUTURE"))
        assertEquals(Partition.SHOKADO, Partition.fromName("SHOKADO"))
    }
}

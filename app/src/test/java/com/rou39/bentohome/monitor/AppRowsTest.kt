package com.rou39.bentohome.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRowsTest {
    private val packages = mapOf(
        10100 to listOf("com.example.chat"),
        10200 to listOf("com.google.android.gms", "com.google.android.gsf"),
    )
    private val labels = mapOf("com.example.chat" to "チャット", "com.google.android.gms" to "Google Play 開発者サービス")

    private fun build(processes: List<ProcessLoad>, daily: DailyAppStats? = null) = AppRows.build(
        processes, daily, emptyMap(), { packages[it].orEmpty() }, { labels[it] ?: it }, "com.rou39.bentohome",
    )

    private fun proc(pid: Int, uid: Int, name: String, cpu: Float = 1f, rss: Long = 100) = ProcessLoad(pid, uid, name, cpu, rss)

    @Test
    fun processesOfOneAppAreSummed() {
        val rows = build(listOf(proc(1, 10100, "com.example.chat"), proc(2, 10100, "com.example.chat:push", 2f, 50)))
        val row = rows.single()
        assertEquals("チャット", row.label)
        assertEquals(3f, row.cpuPercent!!, 0.01f)
        assertEquals(150L, row.rssKb)
        assertEquals(2, row.processes.size)
    }

    @Test
    fun sharedUidUsesProcessName() {
        val rows = build(listOf(proc(1, 10200, "com.google.android.gsf:x")))
        assertEquals("com.google.android.gsf", rows.single().packageName)
    }

    @Test
    fun isolatedProcessIsGuessedFromName() {
        val rows = build(listOf(proc(1, 99001, "com.example.chat:sandboxed_process0")))
        assertEquals("com.example.chat", rows.single().packageName)
    }

    @Test
    fun systemProcessesAndOwnSamplerAreNamed() {
        val rows = build(
            listOf(proc(1, 1000, "/system/bin/surfaceflinger"), proc(2, 2000, "com.rou39.bentohome:monitor")),
        )
        assertEquals(listOf("surfaceflinger", "Bento の計測用プロセス"), rows.map { it.label })
        assertNull(rows[0].packageName)
    }

    @Test
    fun usageAndNetworkMergeIntoSameRow() {
        val daily = DailyAppStats(mapOf("com.example.chat" to 60_000L), mapOf(10100 to 2048L, -5 to 10L))
        val rows = build(listOf(proc(1, 10100, "com.example.chat")), daily)
        val chat = rows.first { it.packageName == "com.example.chat" }
        assertEquals(60_000L, chat.foregroundMs)
        assertEquals(2048L, chat.networkBytes)
        assertEquals("テザリング", rows.first { it.key == "uid:tethering" }.label)
    }

    @Test
    fun sortingDropsRowsWithoutTheValue() {
        val daily = DailyAppStats(mapOf("com.example.chat" to 60_000L), emptyMap())
        val rows = build(listOf(proc(1, 1000, "system_server", 5f)), daily)
        assertEquals(listOf("チャット"), AppRows.sorted(rows, AppSort.SCREEN).map { it.label })
        assertEquals(listOf("system_server"), AppRows.sorted(rows, AppSort.CPU).map { it.label })
    }
}

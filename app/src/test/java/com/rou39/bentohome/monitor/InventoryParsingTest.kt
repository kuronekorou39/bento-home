package com.rou39.bentohome.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class InventoryParsingTest {
    @Test
    fun batteryLinesByUid() {
        val text = """
              UID u0a515: 341 fg: 55.4 (24m 22s 694ms) bg: 7.26 cached: 15.2 (9h 33m 0s 607ms)
              UID u0a358: 300 bg: 300
              UID 1000: 113 bg: 113
              UID u0a515: 1.0 fg: 1.0
              UID u10a5: 2.5 fg: 0.5
        """.trimIndent()
        val b = InventoryParsing.parseBattery(text)
        assertEquals(BatteryUse(341.0, 341.0 - 55.4), b[10515])
        assertEquals(BatteryUse(300.0, 300.0), b[10358])
        assertEquals(113.0, b[1000]!!.totalMah, 0.0)
        assertEquals(BatteryUse(2.5, 2.0), b[1010005])
    }

    @Test
    fun uidNames() {
        assertEquals(10515, InventoryParsing.parseUid("u0a515"))
        assertEquals(1000, InventoryParsing.parseUid("1000"))
        assertNull(InventoryParsing.parseUid("weird"))
    }

    @Test
    fun appOpsKeepsLatestAccessPerKind() {
        val text = """
            |    Package com.android.chrome:
            |      COARSE_LOCATION (allow):
            |          Access: [top-s] 2026-10-10 20:00:32.859 (-16d22h39m27s420ms)
            |      FINE_LOCATION (allow):
            |          Access: [top-s] 2026-10-27 16:20:21.152 (-2h19m39s127ms)
            |      CAMERA (allow):
            |          Access: [top-s] 2026-09-01 01:43:33.626 (-56d16h) duration=+12m7s238ms
            |    Package com.example.quiet:
            |      CAMERA (allow):
        """.trimMargin()
        val r = InventoryParsing.parseAppOps(text, ZoneOffset.UTC)
        val chrome = r["com.android.chrome"]!!
        assertEquals(LocalDateTime.of(2026, 10, 27, 16, 20, 21, 152_000_000).toInstant(ZoneOffset.UTC).toEpochMilli(), chrome.locationAt)
        assertEquals(LocalDateTime.of(2026, 9, 1, 1, 43, 33, 626_000_000).toInstant(ZoneOffset.UTC).toEpochMilli(), chrome.cameraAt)
        assertNull(chrome.microphoneAt)
        assertNull(r["com.example.quiet"])
    }
}

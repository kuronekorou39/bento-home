package com.rou39.bentohome.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitorParsingTest {
    private fun snapshot(total: Long, idle: Long, vararg procs: String) = ProcSnapshot.parse(
        buildString {
            append("T 1000\n")
            // user nice system idle iowait irq softirq steal（合計 total、idle+iowait が idle）
            append("CPU ${total - idle} 0 0 $idle 0 0 0 0 0 0\n")
            append("NET 100 200\n")
            append("BAT 460156\n")
            procs.forEach { append(it).append('\n') }
        },
    )

    @Test
    fun parsesSnapshotLines() {
        val s = snapshot(1000, 600, "P 42 10217 500 161608 com.google.android.gm", "P 2 0 7 0 [kthreadd]")
        assertEquals(1000L, s.cpuTotal)
        assertEquals(600L, s.cpuIdle)
        assertEquals(100L, s.netRx)
        assertEquals(460156L, s.batteryCurrentUa)
        assertEquals(ProcSnapshot.ProcLine(42, 10217, 500, 161608, "com.google.android.gm"), s.processes[0])
        assertEquals("[kthreadd]", s.processes[1].name)
    }

    @Test
    fun unreadableBatteryCurrentIsNull() {
        val s = ProcSnapshot.parse("T 1\nBAT ${Long.MIN_VALUE}\n")
        assertNull(s.batteryCurrentUa)
    }

    @Test
    fun cpuPercentIsShareOfAllCores() {
        val a = snapshot(1000, 600, "P 42 10217 500 1 app")
        val b = snapshot(2000, 1100, "P 42 10217 600 1 app", "P 43 10218 50 1 new")
        val load = ProcMath.load(a, b)
        // 1000 tick のうち idle 500 → 50%
        assertEquals(50f, load.totalPercent, 0.01f)
        assertEquals(10f, load.processes.first { it.pid == 42 }.cpuPercent, 0.01f)
        // 前回いなかったプロセスは 0 から数える
        assertEquals(0f, load.processes.first { it.pid == 43 }.cpuPercent, 0.01f)
    }

    @Test
    fun reusedPidIsTreatedAsNewProcess() {
        val a = snapshot(1000, 0, "P 42 10217 500 1 old")
        val b = snapshot(2000, 0, "P 42 10300 20 1 other")
        assertEquals(0f, ProcMath.load(a, b).processes.single().cpuPercent, 0.01f)
    }

    @Test
    fun temperaturesPreferHalCurrentValues() {
        val text = """
            Thermal Status: 0
            Cached temperatures:
            	Temperature{mValue=65.0, mType=0, mName=LITTLE, mStatus=0}
            	Temperature{mValue=37.2, mType=2, mName=battery, mStatus=0}
            	Temperature{mValue=37.0, mType=3, mName=VIRTUAL-SKIN, mStatus=0}
            	Temperature{mValue=0.0, mType=4, mName=VIRTUAL-USB-UI, mStatus=0}
            HAL Ready: true
            Current temperatures from HAL:
            	Temperature{mValue=40.0, mType=0, mName=LITTLE, mStatus=0}
            	Temperature{mValue=39.0, mType=0, mName=MID, mStatus=0}
            	Temperature{mValue=32.3, mType=3, mName=VIRTUAL-SKIN, mStatus=0}
        """.trimIndent()
        val t = Temperatures.parse(text)!!
        assertEquals(32.3f, t.skin!!, 0.01f)
        assertEquals(40f, t.cpu!!, 0.01f)
        // HAL に無い電池はキャッシュから補う
        assertEquals(37.2f, t.battery!!, 0.01f)
    }

    @Test
    fun temperaturesWithoutPermissionAreNull() {
        assertNull(Temperatures.parse("Permission Denial: can't dump ThermalManagerService"))
    }
}

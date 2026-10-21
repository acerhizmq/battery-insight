package com.acer.batteryinsight.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryStatsParserTest {

    @Test
    fun `parses durations`() {
        assertEquals(3_723_000L, BatteryStatsParser.parseDurationMs("1h 2m 3s"))
        assertEquals(60_500L, BatteryStatsParser.parseDurationMs("1m 500ms"))
        assertEquals(500L, BatteryStatsParser.parseDurationMs("500ms"))
        assertEquals(86_400_000L, BatteryStatsParser.parseDurationMs("1d"))
        assertEquals(1234L, BatteryStatsParser.parseDurationMs("1234"))
    }

    @Test
    fun `parses uid formats`() {
        assertEquals(10123 to 45.6, BatteryStatsParser.parseUid("    UID u0a123: 45.6 ( cpu=12 )"))
        assertEquals(10123 to 45.6, BatteryStatsParser.parseUid("    u0_a123: 45.6"))
        assertEquals(1010123 to 2.0, BatteryStatsParser.parseUid("    u10a123: 2.0"))
        assertEquals(99005 to 1.5, BatteryStatsParser.parseUid("    u0i5: 1.5"))
        assertEquals(1000 to 12.0, BatteryStatsParser.parseUid("    1000: 12.0"))
        assertNull(BatteryStatsParser.parseUid("    Capacity: 5000"))
    }

    @Test
    fun `parses estimated power section`() {
        val lines = listOf(
            "Statistics since last charge:",
            "  Estimated power use (mAh):",
            "    Capacity: 5000, Computed drain: 1200, actual drain: 1100-1150",
            "    screen: 300 duration: 2h 10m",
            "    cell: 120",
            "    UID u0a123: 250.5 ( cpu=100 screen=50.0 (1h 5m) )",
            "    1000: 80",
            "    u0a7: 0.01",
            "Other section:",
            "    u0a999: 999",
        )
        val result = BatteryStatsParser.parse(lines, 5000.0)
        assertEquals(listOf("screen", "cellular"), result.subsystems.map { it.key })
        assertEquals(7_800_000L, result.subsystems[0].durationMs)
        assertEquals(listOf(10123, 1000), result.uids.map { it.uid })
        assertEquals(3_900_000L, result.uids[0].foregroundMs)
    }

    @Test
    fun `clamps implausible uid power`() {
        val lines = listOf("  Estimated power use (mAh):", "    u0a1: 90000")
        assertEquals(2500.0, BatteryStatsParser.parse(lines, 5000.0).uids.single().powerMah, 0.001)
    }
}

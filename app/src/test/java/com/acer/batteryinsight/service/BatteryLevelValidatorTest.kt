package com.acer.batteryinsight.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryLevelValidatorTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `single spurious 1 percent reading is rejected`() {
        val v = BatteryLevelValidator(80, t0)
        assertEquals(80, v.accept(1, t0 + 10_000) { listOf(80, 80) })
    }

    @Test
    fun `spurious reading without secondaries keeps last level until confirmed`() {
        val v = BatteryLevelValidator(80, t0)
        assertEquals(80, v.accept(1, t0 + 10_000) { emptyList() })
        assertEquals(80, v.accept(80, t0 + 20_000) { emptyList() })
        assertEquals(79, v.accept(79, t0 + 30_000) { emptyList() })
    }

    @Test
    fun `glitch after long stable period is still rejected`() {
        val v = BatteryLevelValidator(80, t0)
        // Stable readings every 10 s for 2 hours.
        var now = t0
        repeat(720) {
            now += 10_000
            v.accept(80, now) { emptyList() }
        }
        assertEquals(80, v.accept(1, now + 10_000) { emptyList() })
    }

    @Test
    fun `corroborated large jump is accepted`() {
        val v = BatteryLevelValidator(80, t0)
        assertEquals(40, v.accept(40, t0 + 10_000) { listOf(40, -1) })
    }

    @Test
    fun `repeated identical reading is accepted after confirmations`() {
        val v = BatteryLevelValidator(80, t0)
        assertEquals(80, v.accept(30, t0 + 10_000) { emptyList() })
        assertEquals(80, v.accept(30, t0 + 20_000) { emptyList() })
        assertEquals(30, v.accept(30, t0 + 30_000) { emptyList() })
    }

    @Test
    fun `plausible secondary replaces bogus primary`() {
        val v = BatteryLevelValidator(80, t0)
        assertEquals(79, v.accept(1, t0 + 10_000) { listOf(79) })
    }

    @Test
    fun `normal discharge and fast charge are accepted`() {
        val v = BatteryLevelValidator(50, t0)
        assertEquals(49, v.accept(49, t0 + 10_000) { error("not needed") })
        // 120 W charging: +6% in one minute
        assertEquals(55, v.accept(55, t0 + 70_000) { error("not needed") })
    }

    @Test
    fun `long power off allows big change`() {
        val v = BatteryLevelValidator(80, t0)
        assertEquals(20, v.accept(20, t0 + 3 * 24 * 3600_000L) { emptyList() })
    }

    @Test
    fun `first reading uses agreeing sources`() {
        val v = BatteryLevelValidator()
        assertEquals(64, v.accept(1, t0) { listOf(64, 64) })
    }

    @Test
    fun `snapshot prefers corroborated primary then fallback`() {
        assertEquals(70, BatteryLevelValidator.resolveSnapshot(70, listOf(71), 50))
        assertEquals(50, BatteryLevelValidator.resolveSnapshot(1, listOf(70), 50))
        assertEquals(70, BatteryLevelValidator.resolveSnapshot(1, listOf(70, 70), 50))
        assertEquals(70, BatteryLevelValidator.resolveSnapshot(70, emptyList(), 50))
    }
}

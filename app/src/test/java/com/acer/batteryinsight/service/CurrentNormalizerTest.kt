package com.acer.batteryinsight.service

import com.acer.batteryinsight.service.CurrentNormalizer.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentNormalizerTest {

    @Test
    fun `small micro amp readings are not mistaken for milliamps once unit is known`() {
        val n = CurrentNormalizer()
        assertEquals(350, n.toMilliAmps(350_000, Source.SYSFS))
        // Deep sleep: 5000 µA must be 5 mA, not 5000 mA.
        assertEquals(5, n.toMilliAmps(5_000, Source.SYSFS))
    }

    @Test
    fun `unit is tracked per source`() {
        val n = CurrentNormalizer()
        n.toMilliAmps(500_000, Source.SYSFS)
        assertEquals(800, n.toMilliAmps(800, Source.BATTERY_MANAGER))
    }

    @Test
    fun `unplugged is always negative`() {
        val n = CurrentNormalizer()
        assertEquals(-300, n.signed(300, plugged = false, discharging = true, charging = false, level = 50))
        assertEquals(-300, n.signed(-300, plugged = false, discharging = true, charging = false, level = 50))
    }

    @Test
    fun `net discharge while plugged shows negative once convention is learned`() {
        val n = CurrentNormalizer()
        n.signed(-400, plugged = false, discharging = true, charging = false, level = 60)
        n.signed(2000, plugged = true, discharging = false, charging = true, level = 60)
        assertEquals(1500, n.signed(1500, plugged = true, discharging = false, charging = true, level = 61))
        assertEquals(-250, n.signed(-250, plugged = true, discharging = false, charging = false, level = 80))
    }

    @Test
    fun `drivers reporting absolute values keep state based sign`() {
        val n = CurrentNormalizer()
        n.signed(400, plugged = false, discharging = true, charging = false, level = 60)
        n.signed(2000, plugged = true, discharging = false, charging = true, level = 60)
        assertEquals(400, n.signed(400, plugged = true, discharging = false, charging = false, level = 80))
    }
}

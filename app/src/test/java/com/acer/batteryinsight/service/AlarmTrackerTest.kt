package com.acer.batteryinsight.service

import com.acer.batteryinsight.service.AlarmTracker.Direction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTrackerTest {

    @Test
    fun `once fires a single time while the condition holds`() {
        val a = AlarmTracker()
        assertTrue(a.shouldFire(true, 20, 0, AlarmTracker.FREQ_ONCE, Direction.FALLING))
        assertFalse(a.shouldFire(true, 19, 60_000, AlarmTracker.FREQ_ONCE, Direction.FALLING))
        assertFalse(a.shouldFire(true, 10, 10 * 60_000, AlarmTracker.FREQ_ONCE, Direction.FALLING))
    }

    @Test
    fun `condition clearing re-arms the alarm`() {
        val a = AlarmTracker()
        assertTrue(a.shouldFire(true, 20, 0, AlarmTracker.FREQ_ONCE, Direction.FALLING))
        assertFalse(a.shouldFire(false, 25, 1, AlarmTracker.FREQ_ONCE, Direction.FALLING))
        assertTrue(a.shouldFire(true, 20, 2, AlarmTracker.FREQ_ONCE, Direction.FALLING))
    }

    @Test
    fun `percentage steps follow the level direction`() {
        val low = AlarmTracker()
        assertTrue(low.shouldFire(true, 20, 0, AlarmTracker.FREQ_EVERY_5_PCT, Direction.FALLING))
        assertFalse(low.shouldFire(true, 16, 1, AlarmTracker.FREQ_EVERY_5_PCT, Direction.FALLING))
        assertTrue(low.shouldFire(true, 15, 2, AlarmTracker.FREQ_EVERY_5_PCT, Direction.FALLING))

        val high = AlarmTracker()
        assertTrue(high.shouldFire(true, 80, 0, AlarmTracker.FREQ_EVERY_1_PCT, Direction.RISING))
        assertFalse(high.shouldFire(true, 80, 1, AlarmTracker.FREQ_EVERY_1_PCT, Direction.RISING))
        assertTrue(high.shouldFire(true, 81, 2, AlarmTracker.FREQ_EVERY_1_PCT, Direction.RISING))
    }

    @Test
    fun `five minute mode repeats on time`() {
        val a = AlarmTracker()
        assertTrue(a.shouldFire(true, 100, 0, AlarmTracker.FREQ_EVERY_5_MIN, Direction.NONE))
        assertFalse(a.shouldFire(true, 100, 4 * 60_000, AlarmTracker.FREQ_EVERY_5_MIN, Direction.NONE))
        assertTrue(a.shouldFire(true, 100, 5 * 60_000, AlarmTracker.FREQ_EVERY_5_MIN, Direction.NONE))
    }
}

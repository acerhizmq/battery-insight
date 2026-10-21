package com.acer.batteryinsight.service

/**
 * Decides when a level-based alarm should fire, honoring the user's "alarm frequency" setting.
 *
 * Frequency values match the order of the labels in the settings UI.
 * Not thread-safe: callers must serialize access.
 */
class AlarmTracker {

    companion object {
        const val FREQ_ONCE = 0
        const val FREQ_EVERY_1_PCT = 1
        const val FREQ_EVERY_5_PCT = 2
        const val FREQ_EVERY_10_PCT = 3
        const val FREQ_EVERY_5_MIN = 4

        const val REPEAT_INTERVAL_MS = 5 * 60 * 1000L

        fun stepFor(frequency: Int): Int = when (frequency) {
            FREQ_EVERY_1_PCT -> 1
            FREQ_EVERY_5_PCT -> 5
            FREQ_EVERY_10_PCT -> 10
            else -> 0
        }
    }

    enum class Direction { FALLING, RISING, NONE }

    private var armed = false
    private var firedLevel = -1
    private var firedAtMs = 0L

    /**
     * @param conditionMet whether the alarm condition currently holds.
     * @param direction the direction in which [level] progresses while the condition holds;
     *        percentage-based frequencies only re-fire when the level moves that way.
     * @return true when a notification should be shown now.
     */
    fun shouldFire(
        conditionMet: Boolean,
        level: Int,
        nowMs: Long,
        frequency: Int,
        direction: Direction,
    ): Boolean {
        if (!conditionMet) {
            reset()
            return false
        }
        if (!armed) {
            return fire(level, nowMs)
        }
        val step = stepFor(frequency)
        val refire = when {
            frequency == FREQ_EVERY_5_MIN -> nowMs - firedAtMs >= REPEAT_INTERVAL_MS
            step > 0 && direction == Direction.FALLING -> level <= firedLevel - step
            step > 0 && direction == Direction.RISING -> level >= firedLevel + step
            else -> false
        }
        return if (refire) fire(level, nowMs) else false
    }

    private fun fire(level: Int, nowMs: Long): Boolean {
        armed = true
        firedLevel = level
        firedAtMs = nowMs
        return true
    }

    fun reset() {
        armed = false
        firedLevel = -1
        firedAtMs = 0L
    }
}

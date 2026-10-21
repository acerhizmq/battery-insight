package com.acer.batteryinsight.service

import kotlin.math.abs

/**
 * Filters out spurious battery level readings (e.g. a single broadcast reporting 1% while the
 * battery is actually at 80%).
 *
 * A reading is accepted immediately when the change since the last accepted level is physically
 * plausible for the elapsed time. Implausible jumps are accepted only when an independent source
 * (BatteryManager property / sysfs capacity node) corroborates them, or when the same value is
 * reported [CONFIRMATIONS_REQUIRED] times in a row.
 *
 * Not thread-safe: callers must serialize access.
 */
class BatteryLevelValidator(
    initialLevel: Int = -1,
    initialTimestampMs: Long = 0L,
) {
    companion object {
        const val CONFIRMATIONS_REQUIRED = 3
        private const val AGREEMENT_TOLERANCE = 2

        // Even 120W chargers stay below ~7%/min; heavy discharge stays well below 2%/min.
        private const val RISE_MS_PER_PERCENT = 8_000L
        private const val DROP_MS_PER_PERCENT = 40_000L
        private const val BASE_TOLERANCE = 2

        fun isValid(level: Int): Boolean = level in 1..100

        /**
         * One-shot resolution without history (e.g. for an initial UI snapshot): prefers a primary
         * value that some secondary agrees with, then any two agreeing secondaries, then [fallback].
         */
        fun resolveSnapshot(primary: Int, secondaries: List<Int>, fallback: Int): Int {
            val valid = secondaries.filter(::isValid)
            if (isValid(primary) && (valid.isEmpty() || valid.any { abs(it - primary) <= AGREEMENT_TOLERANCE })) {
                return primary
            }
            for (i in valid.indices) {
                for (j in i + 1 until valid.size) {
                    if (abs(valid[i] - valid[j]) <= AGREEMENT_TOLERANCE) return valid[i]
                }
            }
            if (isValid(fallback)) return fallback
            if (isValid(primary)) return primary
            return valid.firstOrNull() ?: -1
        }
    }

    var lastLevel: Int = if (isValid(initialLevel)) initialLevel else -1
        private set

    var lastAcceptedAtMs: Long = initialTimestampMs
        private set

    private var pendingLevel = -1
    private var pendingCount = 0

    fun maxPlausibleDelta(elapsedMs: Long, rising: Boolean): Int {
        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val perPercent = if (rising) RISE_MS_PER_PERCENT else DROP_MS_PER_PERCENT
        return (BASE_TOLERANCE + safeElapsed / perPercent).coerceAtMost(100L).toInt()
    }

    /**
     * @param primary the reading to evaluate (usually ACTION_BATTERY_CHANGED EXTRA_LEVEL).
     * @param secondaries lazily evaluated independent readings; only queried when needed.
     * @return the level that should be displayed / used.
     */
    fun accept(primary: Int, nowMs: Long, secondaries: () -> List<Int>): Int {
        if (lastLevel == -1) {
            val resolved = resolveSnapshot(primary, secondaries(), -1)
            if (isValid(resolved)) commit(resolved, nowMs)
            return lastLevel
        }

        if (isValid(primary) && isPlausible(primary, nowMs)) {
            return commit(primary, nowMs)
        }

        val others = secondaries().filter(::isValid)
        if (isValid(primary) && others.any { abs(it - primary) <= AGREEMENT_TOLERANCE }) {
            return commit(primary, nowMs)
        }

        // A plausible independent reading is better than a stale value.
        others.filter { isPlausible(it, nowMs) }
            .minByOrNull { abs(it - lastLevel) }
            ?.let { return commit(it, nowMs) }

        val candidate = if (isValid(primary)) primary else others.firstOrNull() ?: return lastLevel
        if (pendingLevel != -1 && abs(candidate - pendingLevel) <= AGREEMENT_TOLERANCE) {
            pendingCount++
        } else {
            pendingLevel = candidate
            pendingCount = 1
        }
        if (pendingCount >= CONFIRMATIONS_REQUIRED) {
            return commit(candidate, nowMs)
        }
        return lastLevel
    }

    private fun isPlausible(level: Int, nowMs: Long): Boolean {
        val diff = level - lastLevel
        if (diff == 0) return true
        return abs(diff) <= maxPlausibleDelta(nowMs - lastAcceptedAtMs, rising = diff > 0)
    }

    private fun commit(level: Int, nowMs: Long): Int {
        // Advance on every accepted reading: tolerance must reflect the gap since the last trusted
        // sample, otherwise a long stable period would make any glitch look plausible.
        lastAcceptedAtMs = nowMs
        lastLevel = level
        pendingLevel = -1
        pendingCount = 0
        return level
    }

    fun reset(level: Int, nowMs: Long) {
        lastLevel = if (isValid(level)) level else -1
        lastAcceptedAtMs = nowMs
        pendingLevel = -1
        pendingCount = 0
    }
}

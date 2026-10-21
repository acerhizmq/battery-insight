package com.acer.batteryinsight.service

import kotlin.math.abs

/**
 * Converts raw fuel-gauge current readings to signed milliamps.
 *
 * Units: the kernel ABI and BatteryManager both specify µA, but some OEM drivers report mA.
 * Once any reading of [MICRO_AMP_PROOF] or more is seen for a source, that source is locked to µA
 * (no phone draws 20 A). Until then the legacy heuristic is used.
 *
 * Sign: conventions differ per driver. The discharge and charge signs are learned from
 * unambiguous states; only when both are known and differ is the measured sign trusted, which lets
 * a net discharge while plugged in (charge limit, heavy load) show as negative.
 *
 * Not thread-safe: callers must serialize access.
 */
class CurrentNormalizer(
    var sysfsMicroAmps: Boolean? = null,
    var batteryManagerMicroAmps: Boolean? = null,
    var dischargeSign: Int = 0,
    var chargeSign: Int = 0,
) {
    enum class Source { SYSFS, BATTERY_MANAGER }

    companion object {
        const val MICRO_AMP_PROOF = 20_000
        private const val LEGACY_THRESHOLD = 10_000
        private const val SIGN_LEARN_MIN_MA = 20
        private const val NET_DISCHARGE_MIN_MA = 20
    }

    /** Set when a persisted field changed so the owner can save it. */
    var dirty = false
        private set

    fun markSaved() {
        dirty = false
    }

    fun toMilliAmps(raw: Int, source: Source): Int {
        if (abs(raw) >= MICRO_AMP_PROOF) lockMicroAmps(source)
        val micro = when (source) {
            Source.SYSFS -> sysfsMicroAmps
            Source.BATTERY_MANAGER -> batteryManagerMicroAmps
        }
        return when (micro) {
            true -> raw / 1000
            false -> raw
            null -> if (abs(raw) > LEGACY_THRESHOLD) raw / 1000 else raw
        }
    }

    private fun lockMicroAmps(source: Source) {
        when (source) {
            Source.SYSFS -> if (sysfsMicroAmps != true) { sysfsMicroAmps = true; dirty = true }
            Source.BATTERY_MANAGER -> if (batteryManagerMicroAmps != true) { batteryManagerMicroAmps = true; dirty = true }
        }
    }

    /**
     * @param rawMa current in mA with the driver's own sign.
     * @param plugged a power source is attached.
     * @param discharging BatteryManager reports BATTERY_STATUS_DISCHARGING.
     * @param charging BatteryManager reports BATTERY_STATUS_CHARGING.
     */
    fun signed(rawMa: Int, plugged: Boolean, discharging: Boolean, charging: Boolean, level: Int): Int {
        val magnitude = abs(rawMa)
        val sign = rawMa.compareTo(0)
        if (sign != 0 && magnitude >= SIGN_LEARN_MIN_MA) {
            if (!plugged && discharging && dischargeSign != sign) {
                dischargeSign = sign
                dirty = true
            } else if (plugged && charging && level in 1..94 && chargeSign != sign) {
                chargeSign = sign
                dirty = true
            }
        }

        if (!plugged) return -magnitude
        val conventionKnown = dischargeSign != 0 && chargeSign != 0 && dischargeSign != chargeSign
        if (conventionKnown && sign == dischargeSign && magnitude >= NET_DISCHARGE_MIN_MA) {
            return -magnitude
        }
        return magnitude
    }
}

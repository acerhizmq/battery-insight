package com.acer.batteryinsight.service

/**
 * Parses the "Estimated power use" section of `dumpsys batterystats` across AOSP and OEM formats
 * (MIUI/HyperOS, OneUI, ColorOS, OxygenOS, LineageOS). Pure logic so it can be unit tested.
 */
object BatteryStatsParser {

    /** Hardware subsystem entry; [key] is one of screen, cellular, wifi, bluetooth, idle, camera, audio. */
    data class SubsystemEntry(val key: String, val powerMah: Double, val durationMs: Long)

    data class UidEntry(val uid: Int, val powerMah: Double, val foregroundMs: Long)

    data class Result(val subsystems: List<SubsystemEntry>, val uids: List<UidEntry>)

    private val uidPattern = Regex(
        """^\s*(?:UID\s+)?(?:u(\d+)_?a(\d+)|u(\d+)_?i(\d+)|(\d+)):\s*([\d.]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val globalItemPattern = Regex(
        """^\s*(screen|screen_on|mobile_radio|cellular|cell|radio|modem|wifi|wi-fi|bluetooth|bt|camera|audio|sound|video|media|idle|standby|device_idle|gnss|gps|location|sensors|sensor|wakelock|wake_lock|phone|telephony):\s*([\d.]+)(?:.*duration:\s*([^\n]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val screenTimePattern = Regex("""screen=[\d.]+\s*\(([^)]+)\)""")
    private val cpuFgPattern = Regex("""cpu:fg=[\d.]+\s*\(([^)]+)\)""")
    private val fgGenericPattern = Regex("""fg=[\d.]+\s*\(([^)]+)\)""")

    private val dayPattern = Regex("""(\d+(?:\.\d+)?)\s*d""")
    private val hourPattern = Regex("""(\d+(?:\.\d+)?)\s*h""")
    private val minutePattern = Regex("""(\d+(?:\.\d+)?)\s*m(?!s)""")
    private val secondPattern = Regex("""(\d+(?:\.\d+)?)\s*s""")
    private val millisPattern = Regex("""(\d+(?:\.\d+)?)\s*ms""")

    private const val MIN_POWER_MAH = 0.05
    private const val AID_APP_START = 10000
    private const val AID_ISOLATED_START = 99000
    private const val PER_USER_RANGE = 100000

    fun isSectionHeader(line: String): Boolean =
        line.contains("Estimated power use (mAh):", ignoreCase = true) ||
            line.contains("Estimated power use:", ignoreCase = true) ||
            line.contains("Estimated battery use:", ignoreCase = true)

    fun parseDurationMs(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        fun find(r: Regex) = r.find(durationStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        // "s" also matches the trailing "s" of "ms"; strip millisecond tokens before matching seconds.
        val withoutMs = millisPattern.replace(durationStr, " ")
        val seconds = secondPattern.find(withoutMs)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        var totalMs = 0L
        totalMs += (find(dayPattern) * 86_400_000.0).toLong()
        totalMs += (find(hourPattern) * 3_600_000.0).toLong()
        totalMs += (find(minutePattern) * 60_000.0).toLong()
        totalMs += (seconds * 1000.0).toLong()
        totalMs += find(millisPattern).toLong()

        if (totalMs == 0L) {
            durationStr.trim().toLongOrNull()?.let { return it }
        }
        return totalMs
    }

    fun subsystemKey(rawComponent: String): String? {
        val c = rawComponent.lowercase()
        return when {
            c.contains("screen") -> "screen"
            c.contains("mobile") || c.contains("cell") || c.contains("radio") || c.contains("modem") -> "cellular"
            c.contains("wifi") || c.contains("wi-fi") -> "wifi"
            c.contains("bluetooth") || c == "bt" -> "bluetooth"
            c.contains("idle") || c.contains("standby") -> "idle"
            c.contains("camera") -> "camera"
            c.contains("audio") || c.contains("sound") || c.contains("video") || c.contains("media") -> "audio"
            else -> null
        }
    }

    fun parseUid(line: String): Pair<Int, Double>? {
        val match = uidPattern.find(line) ?: return null
        val g = match.groupValues
        val uid = when {
            g[1].isNotEmpty() && g[2].isNotEmpty() -> {
                val user = g[1].toIntOrNull() ?: 0
                val app = g[2].toIntOrNull() ?: return null
                user * PER_USER_RANGE + AID_APP_START + app
            }
            g[3].isNotEmpty() && g[4].isNotEmpty() -> {
                val user = g[3].toIntOrNull() ?: 0
                val app = g[4].toIntOrNull() ?: return null
                user * PER_USER_RANGE + AID_ISOLATED_START + app
            }
            g[5].isNotEmpty() -> g[5].toIntOrNull() ?: return null
            else -> return null
        }
        val power = g[6].toDoubleOrNull() ?: return null
        return uid to power
    }

    fun parseForegroundMs(line: String): Long {
        val m = screenTimePattern.find(line) ?: cpuFgPattern.find(line) ?: fgGenericPattern.find(line)
        return m?.let { parseDurationMs(it.groupValues[1]) } ?: 0L
    }

    /**
     * @param capacityLimitMah readings above this are treated as bogus: subsystems are dropped,
     *        UIDs are clamped to half of it (matching the historical behavior).
     */
    fun parse(lines: List<String>, capacityLimitMah: Double): Result {
        val subsystems = mutableListOf<SubsystemEntry>()
        val uids = mutableListOf<UidEntry>()
        var inSection = false

        for (line in lines) {
            if (isSectionHeader(line)) {
                inSection = true
                continue
            }
            if (!inSection) continue
            if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("\t")) break

            val global = globalItemPattern.find(line)
            if (global != null) {
                val power = global.groupValues[2].toDoubleOrNull() ?: 0.0
                val key = subsystemKey(global.groupValues[1])
                if (key != null && power > MIN_POWER_MAH && power <= capacityLimitMah) {
                    val duration = global.groupValues.getOrNull(3).orEmpty()
                    subsystems += SubsystemEntry(key, power, if (duration.isNotEmpty()) parseDurationMs(duration) else 0L)
                }
                continue
            }

            val (uid, rawPower) = parseUid(line) ?: continue
            if (rawPower < MIN_POWER_MAH) continue
            val power = if (rawPower > capacityLimitMah) rawPower.coerceAtMost(capacityLimitMah * 0.5) else rawPower
            uids += UidEntry(uid, power, parseForegroundMs(line))
        }
        return Result(subsystems, uids)
    }
}

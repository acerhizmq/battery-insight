package com.acer.batteryinsight.service

import android.util.Log
import com.acer.batteryinsight.model.BatteryInsightFlowSample
import com.acer.batteryinsight.model.BatteryInsightHistoryBucket
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** JSON persistence for flow samples and history buckets in the app's files dir. */
class SessionStorage(private val dir: File) {

    companion object {
        private const val TAG = "SessionStorage"

        const val ACTIVE_FLOW = "active_flow.json"
        const val ACTIVE_BUCKETS = "active_buckets.json"
        const val LAST_CHARGE_FLOW = "last_charge_flow.json"
        const val LAST_CHARGE_BUCKETS = "last_charge_buckets.json"
        const val LAST_DISCHARGE_FLOW = "last_discharge_flow.json"
        const val LAST_DISCHARGE_BUCKETS = "last_discharge_buckets.json"
    }

    fun saveFlow(name: String, list: List<BatteryInsightFlowSample>) = write(name, flowToJson(list))

    fun saveBuckets(name: String, list: List<BatteryInsightHistoryBucket>) = write(name, bucketsToJson(list))

    fun loadFlow(name: String): List<BatteryInsightFlowSample> = jsonToFlow(read(name))

    fun loadBuckets(name: String): List<BatteryInsightHistoryBucket> = jsonToBuckets(read(name))

    /** Writes via a temp file + rename so a crash mid-write never leaves a truncated archive. */
    private fun write(name: String, content: String) {
        try {
            val target = File(dir, name)
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $name", e)
        }
    }

    private fun read(name: String): String {
        return try {
            val file = File(dir, name)
            if (file.exists() && file.canRead()) file.readText() else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun flowToJson(list: List<BatteryInsightFlowSample>): String {
        val arr = JSONArray()
        for (item in list) {
            arr.put(JSONObject().apply {
                put("t", item.timestamp)
                put("c", item.current)
                put("ch", item.isCharging)
                put("l", item.level)
                put("v", item.voltage)
                put("tp", item.temp)
            })
        }
        return arr.toString()
    }

    private fun jsonToFlow(json: String): List<BatteryInsightFlowSample> {
        if (json.isEmpty()) return emptyList()
        val list = mutableListOf<BatteryInsightFlowSample>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BatteryInsightFlowSample(
                        obj.optLong("t"),
                        obj.optInt("c"),
                        obj.optBoolean("ch"),
                        obj.optInt("l"),
                        obj.optInt("v"),
                        obj.optInt("tp"),
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun bucketsToJson(list: List<BatteryInsightHistoryBucket>): String {
        val arr = JSONArray()
        for (b in list) {
            arr.put(JSONObject().apply {
                put("d", b.epochDay)
                put("h", b.hour)
                put("sot", b.screenOnMs)
                put("dr", b.drainPercent)
                put("hdr", b.hourlyDrainPercent)
                put("hsot", b.hourlyScreenOnMs)
                put("min", b.minCurrent)
                put("max", b.maxCurrent)
                put("avg", b.avgCurrent)
                put("tp", b.temp)
            })
        }
        return arr.toString()
    }

    private fun jsonToBuckets(json: String): List<BatteryInsightHistoryBucket> {
        if (json.isEmpty()) return emptyList()
        val list = mutableListOf<BatteryInsightHistoryBucket>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(BatteryInsightHistoryBucket(obj.optInt("d"), obj.optInt("h")).apply {
                    screenOnMs = obj.optLong("sot")
                    drainPercent = obj.optInt("dr")
                    hourlyDrainPercent = obj.optInt("hdr")
                    hourlyScreenOnMs = obj.optLong("hsot")
                    minCurrent = obj.optInt("min")
                    maxCurrent = obj.optInt("max")
                    avgCurrent = obj.optInt("avg")
                    temp = obj.optInt("tp")
                })
            }
        } catch (_: Exception) {}
        return list
    }
}

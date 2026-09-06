package com.radfahrzeugs.intervalsdirect.data

import android.content.Context
import com.radfahrzeugs.intervalsdirect.engine.RingConnCsvParser.ParsedRingConnData
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

object RingConnLocalStore {
    private const val FILE_NAME = "ringconn_local_cache.json"

    @Synchronized
    fun saveAll(context: Context, data: Map<LocalDate, ParsedRingConnData>) {
        val current = getAll(context).toMutableMap()
        for ((date, item) in data) {
            val existing = current[date]
            if (existing != null) {
                if (item.sleepSecs != null) existing.sleepSecs = item.sleepSecs
                if (item.deepSleepMin != null) existing.deepSleepMin = item.deepSleepMin
                if (item.remSleepMin != null) existing.remSleepMin = item.remSleepMin
                if (item.lightSleepMin != null) existing.lightSleepMin = item.lightSleepMin
                if (item.awakeMin != null) existing.awakeMin = item.awakeMin
                if (item.onsetIso != null) existing.onsetIso = item.onsetIso
                if (item.wakeIso != null) existing.wakeIso = item.wakeIso
                if (item.restingHR != null) existing.restingHR = item.restingHR
                if (item.avgHR != null) existing.avgHR = item.avgHR
                if (item.hrv != null) existing.hrv = item.hrv
                if (item.spO2 != null) existing.spO2 = item.spO2
                if (item.steps != null) existing.steps = item.steps
            } else {
                current[date] = item
            }
        }

        val rootJson = JSONObject()
        for ((date, item) in current) {
            val obj = JSONObject()
            item.sleepSecs?.let { obj.put("sleepSecs", it) }
            item.deepSleepMin?.let { obj.put("deepSleepMin", it) }
            item.remSleepMin?.let { obj.put("remSleepMin", it) }
            item.lightSleepMin?.let { obj.put("lightSleepMin", it) }
            item.awakeMin?.let { obj.put("awakeMin", it) }
            item.onsetIso?.let { obj.put("onsetIso", it) }
            item.wakeIso?.let { obj.put("wakeIso", it) }
            item.restingHR?.let { obj.put("restingHR", it) }
            item.avgHR?.let { obj.put("avgHR", it) }
            item.hrv?.let { obj.put("hrv", it.toDouble()) }
            item.spO2?.let { obj.put("spO2", it.toDouble()) }
            item.steps?.let { obj.put("steps", it) }
            rootJson.put(date.toString(), obj)
        }

        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(rootJson.toString())
        }
    }

    @Synchronized
    fun getAll(context: Context): Map<LocalDate, ParsedRingConnData> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyMap()

        return runCatching {
            val content = file.readText()
            val root = JSONObject(content)
            val result = mutableMapOf<LocalDate, ParsedRingConnData>()
            for (key in root.keys()) {
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
                val obj = root.getJSONObject(key)
                val item = ParsedRingConnData(
                    date = date,
                    sleepSecs = if (obj.has("sleepSecs")) obj.getInt("sleepSecs") else null,
                    deepSleepMin = if (obj.has("deepSleepMin")) obj.getInt("deepSleepMin") else null,
                    remSleepMin = if (obj.has("remSleepMin")) obj.getInt("remSleepMin") else null,
                    lightSleepMin = if (obj.has("lightSleepMin")) obj.getInt("lightSleepMin") else null,
                    awakeMin = if (obj.has("awakeMin")) obj.getInt("awakeMin") else null,
                    onsetIso = if (obj.has("onsetIso")) obj.getString("onsetIso") else null,
                    wakeIso = if (obj.has("wakeIso")) obj.getString("wakeIso") else null,
                    restingHR = if (obj.has("restingHR")) obj.getInt("restingHR") else null,
                    avgHR = if (obj.has("avgHR")) obj.getInt("avgHR") else null,
                    hrv = if (obj.has("hrv")) obj.getDouble("hrv").toFloat() else null,
                    spO2 = if (obj.has("spO2")) obj.getDouble("spO2").toFloat() else null,
                    steps = if (obj.has("steps")) obj.getInt("steps") else null
                )
                result[date] = item
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun get(context: Context, date: LocalDate): ParsedRingConnData? {
        return getAll(context)[date]
    }
}

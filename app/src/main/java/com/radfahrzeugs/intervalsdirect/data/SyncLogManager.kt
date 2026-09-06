package com.radfahrzeugs.intervalsdirect.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SyncLogManager(private val context: Context) {

    private val logFile by lazy {
        File(context.filesDir, "intervals_sync_logs.json")
    }

    suspend fun addLog(entry: SyncLogEntry) = withContext(Dispatchers.IO) {
        val existing = getLogs().toMutableList()
        existing.add(0, entry) // prepend new log
        val trimmed = existing.take(50) // keep last 50

        val array = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("triggerSource", item.triggerSource)
                put("targetDates", JSONArray(item.targetDates))
                put("statusCode", item.statusCode)
                put("isSuccess", item.isSuccess)
                put("summary", item.summary)
                put("payloadJson", item.payloadJson)
            }
            array.put(obj)
        }

        try {
            logFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getLogs(): List<SyncLogEntry> = withContext(Dispatchers.IO) {
        if (!logFile.exists()) return@withContext emptyList()
        try {
            val text = logFile.readText()
            if (text.isBlank()) return@withContext emptyList()
            val array = JSONArray(text)
            val list = mutableListOf<SyncLogEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val targetDatesArray = obj.optJSONArray("targetDates")
                val targetDates = mutableListOf<String>()
                if (targetDatesArray != null) {
                    for (j in 0 until targetDatesArray.length()) {
                        targetDates.add(targetDatesArray.getString(j))
                    }
                }

                list.add(
                    SyncLogEntry(
                        id = obj.optString("id"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        triggerSource = obj.optString("triggerSource", "Manual"),
                        targetDates = targetDates,
                        statusCode = obj.optInt("statusCode", 200),
                        isSuccess = obj.optBoolean("isSuccess", true),
                        summary = obj.optString("summary", ""),
                        payloadJson = if (obj.has("payloadJson") && !obj.isNull("payloadJson")) obj.optString("payloadJson") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        if (logFile.exists()) {
            logFile.delete()
        }
    }
}

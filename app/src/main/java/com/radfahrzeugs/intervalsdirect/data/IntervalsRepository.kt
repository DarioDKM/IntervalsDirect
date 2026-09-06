package com.radfahrzeugs.intervalsdirect.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class IntervalsRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun uploadWellness(
        athleteId: String,
        apiKey: String,
        payload: IntervalsWellnessPayload
    ): Result<String> = withContext(Dispatchers.IO) {
        val dateStr = payload.id
        val url = "https://intervals.icu/api/v1/athlete/$athleteId/wellness/$dateStr"
        val jsonBody = payload.toJsonString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)
        val basicAuth = Credentials.basic("API_KEY", apiKey)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", basicAuth)
            .put(requestBody)
            .build()

        try {
            println("INTERVALS_DEBUG: Uploading to $url -> Payload: $jsonBody")
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                println("INTERVALS_DEBUG: Intervals response: HTTP ${response.code} -> $body")
                if (response.isSuccessful) {
                    Result.success("HTTP ${response.code}: Uploaded successfully for $dateStr")
                } else {
                    Result.failure(IOException("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            println("INTERVALS_DEBUG: Upload error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchWellness(
        athleteId: String,
        apiKey: String,
        date: java.time.LocalDate
    ): Result<org.json.JSONObject?> = withContext(Dispatchers.IO) {
        val dateStr = date.toString()
        val url = "https://intervals.icu/api/v1/athlete/$athleteId/wellness/$dateStr"
        val basicAuth = Credentials.basic("API_KEY", apiKey)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", basicAuth)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = if (!body.isNullOrBlank()) org.json.JSONObject(body) else null
                    Result.success(json)
                } else if (response.code == 404) {
                    Result.success(null)
                } else {
                    val body = response.body?.string() ?: ""
                    Result.failure(IOException("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWellnessRange(
        athleteId: String,
        apiKey: String,
        oldest: java.time.LocalDate,
        newest: java.time.LocalDate
    ): Result<Map<java.time.LocalDate, org.json.JSONObject>> = withContext(Dispatchers.IO) {
        val url = "https://intervals.icu/api/v1/athlete/$athleteId/wellness?oldest=$oldest&newest=$newest"
        val basicAuth = Credentials.basic("API_KEY", apiKey)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", basicAuth)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val map = mutableMapOf<java.time.LocalDate, org.json.JSONObject>()
                    if (!body.isNullOrBlank()) {
                        val jsonArray = org.json.JSONArray(body)
                        for (i in 0 until jsonArray.length()) {
                            val item = jsonArray.getJSONObject(i)
                            val idStr = item.optString("id")
                            if (idStr.isNotBlank()) {
                                runCatching {
                                    val date = java.time.LocalDate.parse(idStr)
                                    map[date] = item
                                }
                            }
                        }
                    }
                    Result.success(map)
                } else {
                    val body = response.body?.string() ?: ""
                    Result.failure(IOException("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchEvents(
        athleteId: String,
        apiKey: String,
        oldest: java.time.LocalDate,
        newest: java.time.LocalDate
    ): Result<List<org.json.JSONObject>> = withContext(Dispatchers.IO) {
        val url = "https://intervals.icu/api/v1/athlete/$athleteId/events?oldest=$oldest&newest=$newest"
        val basicAuth = Credentials.basic("API_KEY", apiKey)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", basicAuth)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val list = mutableListOf<org.json.JSONObject>()
                    if (!body.isNullOrBlank()) {
                        val jsonArray = org.json.JSONArray(body)
                        for (i in 0 until jsonArray.length()) {
                            list.add(jsonArray.getJSONObject(i))
                        }
                    }
                    Result.success(list)
                } else {
                    val body = response.body?.string() ?: ""
                    Result.failure(IOException("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchActivities(
        athleteId: String,
        apiKey: String,
        oldest: java.time.LocalDate,
        newest: java.time.LocalDate
    ): Result<List<org.json.JSONObject>> = withContext(Dispatchers.IO) {
        val url = "https://intervals.icu/api/v1/athlete/$athleteId/activities?oldest=$oldest&newest=$newest"
        val basicAuth = Credentials.basic("API_KEY", apiKey)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", basicAuth)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val list = mutableListOf<org.json.JSONObject>()
                    if (!body.isNullOrBlank()) {
                        val jsonArray = org.json.JSONArray(body)
                        for (i in 0 until jsonArray.length()) {
                            list.add(jsonArray.getJSONObject(i))
                        }
                    }
                    Result.success(list)
                } else {
                    val body = response.body?.string() ?: ""
                    Result.failure(IOException("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


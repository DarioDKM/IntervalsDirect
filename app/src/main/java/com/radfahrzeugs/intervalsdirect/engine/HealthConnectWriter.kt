package com.radfahrzeugs.intervalsdirect.engine

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.units.Percentage
import com.radfahrzeugs.intervalsdirect.data.DailyInspectionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object HealthConnectWriter {

    val REQUIRED_WRITE_PERMISSIONS = setOf(
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(RespiratoryRateRecord::class)
    )

    fun getClient(context: Context): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status != HealthConnectClient.SDK_AVAILABLE) return null
        return try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun hasPermissions(context: Context): Boolean = withContext(Dispatchers.IO) {
        val client = getClient(context) ?: return@withContext false
        try {
            val granted = client.permissionController.getGrantedPermissions()
            val basic = setOf(
                HealthPermission.getWritePermission(SleepSessionRecord::class),
                HealthPermission.getWritePermission(HeartRateRecord::class)
            )
            granted.containsAll(basic)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportToHealthConnect(
        context: Context,
        dataList: List<DailyInspectionData>
    ): String = withContext(Dispatchers.IO) {
        val client = getClient(context) ?: return@withContext "Health Connect SDK not available"

        try {
            val recordsToInsert = mutableListOf<Record>()
            var exportedDays = 0

            for (d in dataList) {
                val sm = d.sleepMetrics ?: continue
                val sleepSecs = sm.sleepSecs
                if (sleepSecs <= 0) continue

                // Determine start and end times
                val startInstant: Instant
                val endInstant: Instant

                if (sm.onsetIso != null && sm.wakeIso != null) {
                    startInstant = runCatching { Instant.parse(sm.onsetIso) }.getOrNull()
                        ?: d.date.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant()
                    endInstant = runCatching { Instant.parse(sm.wakeIso) }.getOrNull()
                        ?: startInstant.plusSeconds(sleepSecs.toLong() + (sm.awakeMinutes * 60L))
                } else {
                    startInstant = d.date.minusDays(1).atTime(23, 30).atZone(ZoneId.systemDefault()).toInstant()
                    endInstant = startInstant.plusSeconds(sleepSecs.toLong() + (sm.awakeMinutes * 60L))
                }

                val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startInstant)

                // Build stages
                val stages = mutableListOf<SleepSessionRecord.Stage>()
                var cursorInstant = startInstant

                if (sm.deepSleepMinutes > 0) {
                    val deepSecs = sm.deepSleepMinutes * 60L
                    stages.add(SleepSessionRecord.Stage(cursorInstant, cursorInstant.plusSeconds(deepSecs), SleepSessionRecord.STAGE_TYPE_DEEP))
                    cursorInstant = cursorInstant.plusSeconds(deepSecs)
                }
                if (sm.lightSleepMinutes > 0) {
                    val lightSecs = sm.lightSleepMinutes * 60L
                    stages.add(SleepSessionRecord.Stage(cursorInstant, cursorInstant.plusSeconds(lightSecs), SleepSessionRecord.STAGE_TYPE_LIGHT))
                    cursorInstant = cursorInstant.plusSeconds(lightSecs)
                }
                if (sm.remSleepMinutes > 0) {
                    val remSecs = sm.remSleepMinutes * 60L
                    stages.add(SleepSessionRecord.Stage(cursorInstant, cursorInstant.plusSeconds(remSecs), SleepSessionRecord.STAGE_TYPE_REM))
                    cursorInstant = cursorInstant.plusSeconds(remSecs)
                }
                if (sm.awakeMinutes > 0) {
                    val awakeSecs = sm.awakeMinutes * 60L
                    stages.add(SleepSessionRecord.Stage(cursorInstant, cursorInstant.plusSeconds(awakeSecs), SleepSessionRecord.STAGE_TYPE_AWAKE))
                }

                // 1. Sleep Session Record
                recordsToInsert.add(
                    SleepSessionRecord(
                        startTime = startInstant,
                        startZoneOffset = zoneOffset,
                        endTime = endInstant,
                        endZoneOffset = zoneOffset,
                        stages = stages,
                        title = "RingConn Sleep",
                        notes = "Score: ${sm.sleepScore ?: "--"}"
                    )
                )

                // 2. Resting Heart Rate Record
                d.restingHR?.let { rhr ->
                    if (rhr in 30..120) {
                        recordsToInsert.add(
                            RestingHeartRateRecord(
                                time = startInstant,
                                zoneOffset = zoneOffset,
                                beatsPerMinute = rhr.toLong()
                            )
                        )
                    }
                }

                // 3. Heart Rate Record (Sleeping HR)
                d.avgSleepingHR?.let { hr ->
                    if (hr in 30..150) {
                        val midInstant = startInstant.plusSeconds((sleepSecs / 2).toLong())
                        recordsToInsert.add(
                            HeartRateRecord(
                                startTime = startInstant,
                                startZoneOffset = zoneOffset,
                                endTime = endInstant,
                                endZoneOffset = zoneOffset,
                                samples = listOf(HeartRateRecord.Sample(time = midInstant, beatsPerMinute = hr.toLong()))
                            )
                        )
                    }
                }

                // 4. Oxygen Saturation Record (SpO2)
                d.spO2?.let { spo2 ->
                    if (spo2 in 70f..100f) {
                        val midInstant = startInstant.plusSeconds((sleepSecs / 2).toLong())
                        recordsToInsert.add(
                            OxygenSaturationRecord(
                                time = midInstant,
                                zoneOffset = zoneOffset,
                                percentage = Percentage(spo2.toDouble())
                            )
                        )
                    }
                }

                // 5. Respiratory Rate Record
                d.respiration?.let { rr ->
                    if (rr in 6f..35f) {
                        val midInstant = startInstant.plusSeconds((sleepSecs / 2).toLong())
                        recordsToInsert.add(
                            RespiratoryRateRecord(
                                time = midInstant,
                                zoneOffset = zoneOffset,
                                rate = rr.toDouble()
                            )
                        )
                    }
                }

                exportedDays++
            }

            if (recordsToInsert.isNotEmpty()) {
                client.insertRecords(recordsToInsert)
                return@withContext "Exported $exportedDays days (${recordsToInsert.size} records) to Health Connect"
            } else {
                return@withContext "No eligible records found to export"
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Health Connect export failed: ${e.localizedMessage ?: e.message}"
        }
    }
}

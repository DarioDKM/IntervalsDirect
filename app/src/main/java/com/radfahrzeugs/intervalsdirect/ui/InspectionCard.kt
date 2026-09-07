package com.radfahrzeugs.intervalsdirect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radfahrzeugs.intervalsdirect.data.*
import com.radfahrzeugs.intervalsdirect.engine.MetricCalculator
import com.radfahrzeugs.intervalsdirect.ui.theme.*
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InspectionCard(
    data: DailyInspectionData,
    modifier: Modifier = Modifier,
    onSyncThisDay: (() -> Unit)? = null
) {
    var isPayloadExpanded by remember { mutableStateOf(false) }
    var isServerDiffExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    val hasServerData = data.intervalsExistingData != null &&
                            (!data.intervalsExistingData.isNull("sleepSecs") && data.intervalsExistingData.optInt("sleepSecs", 0) > 0 ||
                             !data.intervalsExistingData.isNull("restingHR") && data.intervalsExistingData.optInt("restingHR", 0) > 0 ||
                             !data.intervalsExistingData.isNull("hrv") && data.intervalsExistingData.optDouble("hrv", 0.0) > 0.0)
                    Surface(
                        color = if (hasServerData) SuccessGreen.copy(alpha = 0.15f) else PrimaryAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (hasServerData) "Intervals.icu ✅" else "Ring Raw Data",
                            color = if (hasServerData) SuccessGreen else PrimaryAccent,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (data.sleepMetrics?.isBlanketRemoved == true) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = WarningOrange.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "✨ Blanket Cleaned (RingConn Layer Removed)",
                            color = WarningOrange,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sleep Breakdown & Timings
            if (data.sleepMetrics != null) {
                val sleep = data.sleepMetrics
                val totalMin = sleep.deepSleepMinutes + sleep.remSleepMinutes + sleep.lightSleepMinutes
                val hours = sleep.sleepSecs / 3600
                val mins = (sleep.sleepSecs % 3600) / 60

                // Format Onset & Wake
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                val onsetStr = sleep.onsetIso?.let { runCatching { timeFormatter.format(Instant.parse(it)) }.getOrNull() }
                val wakeStr = sleep.wakeIso?.let { runCatching { timeFormatter.format(Instant.parse(it)) }.getOrNull() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep",
                            tint = DeepSleepColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sleep: ${hours}h ${mins}m (${sleep.sleepSecs}s)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (onsetStr != null && wakeStr != null) {
                        Text(
                            text = "$onsetStr → $wakeStr",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Multi-color Phase Bar
                if (totalMin > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight((sleep.deepSleepMinutes.toFloat() / totalMin).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(DeepSleepColor)
                        )
                        Box(
                            modifier = Modifier
                                .weight((sleep.remSleepMinutes.toFloat() / totalMin).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(RemSleepColor)
                        )
                        Box(
                            modifier = Modifier
                                .weight((sleep.lightSleepMinutes.toFloat() / totalMin).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(LightSleepColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Phase Labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PhaseLabel("Deep", "${sleep.deepSleepMinutes}m", DeepSleepColor)
                        PhaseLabel("REM", "${sleep.remSleepMinutes}m", RemSleepColor)
                        PhaseLabel("Light", "${sleep.lightSleepMinutes}m", LightSleepColor)
                        PhaseLabel("Awake", "${sleep.awakeMinutes}m", AwakeSleepColor)
                    }
                }
            } else {
                Text(
                    text = "No sleep session recorded yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // 7-Grid of Recovery & Activity Vitals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                VitalItem(
                    icon = Icons.Default.Favorite,
                    label = "Resting HR",
                    value = data.restingHR?.let { "$it bpm" } ?: "--",
                    tint = Color(0xFFE11D48),
                    modifier = Modifier.weight(1f)
                )
                VitalItem(
                    icon = Icons.Default.Bedtime,
                    label = "Sleep HR",
                    value = data.avgSleepingHR?.let { "$it bpm" } ?: "--",
                    tint = Color(0xFF9333EA),
                    modifier = Modifier.weight(1f)
                )
                VitalItem(
                    icon = Icons.Default.MonitorHeart,
                    label = "HRV (rMSSD)",
                    value = data.hrv?.let { "${it.toInt()} ms" } ?: "--",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                VitalItem(
                    icon = Icons.Default.Air,
                    label = "SpO2",
                    value = data.spO2?.let { "${it.toInt()}%" } ?: "--",
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.weight(1f)
                )
                VitalItem(
                    icon = Icons.Default.Thermostat,
                    label = "Skin Temp",
                    value = data.skinTemp?.let { (if (it > 0) "+$it" else "$it") + "°C" } ?: "--",
                    tint = Color(0xFFEA580C),
                    modifier = Modifier.weight(1f)
                )
                VitalItem(
                    icon = Icons.Default.Waves,
                    label = "Respiration",
                    value = data.respiration?.let { "$it br/m" } ?: "--",
                    tint = Color(0xFF0D9488),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Build Payload JSON
            val payload = MetricCalculator.buildPayload(
                date = data.date,
                sleepMetrics = data.sleepMetrics,
                restingHR = data.restingHR,
                avgSleepingHR = data.avgSleepingHR,
                spO2 = data.spO2,
                respiration = data.respiration,
                skinTemp = data.skinTemp,
                hrv = data.hrv
            )
            val jsonString = runCatching { JSONObject(payload.toJsonString()).toString(2) }.getOrDefault(payload.toJsonString())

            // Toggle Row: Intervals.icu Server Check Diff Inspector
            if (data.intervalsExistingData != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isServerDiffExpanded = !isServerDiffExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Server Check",
                            tint = PrimaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Intervals.icu Server Live Check",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryAccent
                        )
                    }
                    Icon(
                        imageVector = if (isServerDiffExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = PrimaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = isServerDiffExpanded) {
                    ServerDiffInspector(
                        existing = data.intervalsExistingData,
                        pendingPayload = JSONObject(payload.toJsonString())
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Expandable Intervals.icu JSON Payload Inspector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isPayloadExpanded = !isPayloadExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Payload",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Intervals.icu API Payload",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (isPayloadExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isPayloadExpanded) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = jsonString,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Single Day Re-Sync Button (for History Screen)
            if (onSyncThisDay != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSyncThisDay,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent.copy(alpha = 0.85f))
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync ${data.date} to Intervals.icu")
                }
            }
        }
    }
}

@Composable
fun ServerDiffInspector(
    existing: JSONObject,
    pendingPayload: JSONObject
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Live Server Comparison & Diff:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            val existingKeys = existing.keys().asSequence().toList()
            val pendingKeys = pendingPayload.keys().asSequence().toList()

            val changed = mutableListOf<Triple<String, Any, Any>>()
            val identical = mutableListOf<Pair<String, Any>>()
            val added = mutableListOf<Pair<String, Any>>()

            for (key in pendingKeys) {
                if (key == "id") continue
                val newVal = pendingPayload.opt(key) ?: continue
                val oldVal = if (existing.has(key) && !existing.isNull(key)) existing.opt(key) else null

                if (oldVal == null) {
                    if (key == "steps" && (newVal == -1 || newVal == "-1")) {
                        continue
                    }
                    added.add(key to newVal)
                } else {
                    val oldNum = (oldVal as? Number)?.toDouble()
                    val newNum = (newVal as? Number)?.toDouble()
                    val isSame = if (oldNum != null && newNum != null) {
                        Math.abs(oldNum - newNum) < 0.001
                    } else {
                        oldVal.toString() == newVal.toString()
                    }

                    if (isSame) {
                        identical.add(key to newVal)
                    } else {
                        changed.add(Triple(key, oldVal, newVal))
                    }
                }
            }

            if (changed.isNotEmpty()) {
                Text(
                    text = "🔄 Changes ready to upload (${changed.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent
                )
                for ((k, oldV, newV) in changed) {
                    Text(
                        text = "   • $k: $oldV → $newV",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryAccent,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (added.isNotEmpty()) {
                Text(
                    text = "🟢 New fields (${added.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
                for ((k, v) in added) {
                    Text(
                        text = "   • $k: +$v",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (identical.isNotEmpty()) {
                Text(
                    text = "✅ Already up-to-date on server (${identical.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val identicalSummary = identical.joinToString(", ") { "${it.first}: ${it.second}" }
                Text(
                    text = "   $identicalSummary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Preserved server fields (e.g. weight, training load, notes)
            val preservedKeys = existingKeys.filter { !pendingKeys.contains(it) && it != "id" && !existing.isNull(it) && existing.opt(it) != "" && existing.opt(it) != JSONObject.NULL }
            if (preservedKeys.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "🔒 Server fields protected (Untouched):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.SemiBold
                )
                val preservedSummary = preservedKeys.take(6).joinToString(", ") { "$it: ${existing.opt(it)}" }
                Text(
                    text = "   $preservedSummary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun PhaseLabel(title: String, duration: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$title $duration",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp
        )
    }
}

@Composable
fun VitalItem(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}


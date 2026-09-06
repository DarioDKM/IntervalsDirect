package com.radfahrzeugs.intervalsdirect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radfahrzeugs.intervalsdirect.data.DailyInspectionData
import com.radfahrzeugs.intervalsdirect.data.SyncLogEntry
import com.radfahrzeugs.intervalsdirect.data.SyncState
import com.radfahrzeugs.intervalsdirect.ui.theme.PrimaryAccent
import com.radfahrzeugs.intervalsdirect.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    todayData: DailyInspectionData?,
    yesterdayData: DailyInspectionData?,
    historyData: List<DailyInspectionData>,
    isHistoryLoading: Boolean,
    syncLogs: List<SyncLogEntry>,
    athleteId: String,
    apiKey: String,
    autoSync: Boolean,
    syncIntervalHours: Int,
    autoBackup: Boolean,
    backupFolderUri: String?,
    lastSyncTimestamp: Long,
    syncState: SyncState,
    onSaveSettings: (String, String, Boolean, Int, Boolean) -> Unit,
    onManualSync: () -> Unit,
    onSyncSingleDay: (LocalDate) -> Unit,
    onSyncAllDays: () -> Unit,
    onRefreshData: () -> Unit,
    onRefreshHistory: () -> Unit,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onExportData: (format: String) -> Unit,
    onImportRingConnFile: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onTriggerGhostSync: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = PrimaryAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Intervals Direct",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onTriggerGhostSync) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = "Ghost Sync",
                            tint = Color(0xFFF59E0B)
                        )
                    }
                    IconButton(onClick = onImportRingConnFile) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Import RingConn ZIP/CSV")
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    IconButton(onClick = {
                        when (selectedTab) {
                            0 -> onRefreshData()
                            1 -> onRefreshHistory()
                            2 -> onRefreshLogs()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Today, contentDescription = "Live") },
                    label = { Text("Live Sync") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        onRefreshHistory()
                    },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "History") },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        onRefreshLogs()
                    },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "Logbook") },
                    label = { Text("Logbook") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: Live Sync & Inspection
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Permission Alert Banner
                        if (!hasPermissions) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Health Connect Permissions Required",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Grant permissions to read sleep, HR, SpO2, HRV and skin temperature.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = onRequestPermissions,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Grant")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Setup Required Banner (when API key or Athlete ID is missing)
                        if (athleteId.isBlank() || apiKey.isBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSettingsDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = PrimaryAccent
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Intervals.icu Setup Required",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tap here or open Settings (⚙️) to enter your Athlete ID and API Key.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Sync Status Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val athleteDisplay = if (athleteId.isNotBlank()) athleteId else "Not configured"
                                        Text(
                                            text = "Target Athlete: $athleteDisplay",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val lastSyncStr = if (lastSyncTimestamp > 0) {
                                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(lastSyncTimestamp))
                                        } else {
                                            "Never"
                                        }
                                        Text(
                                            text = "Last synced: $lastSyncStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onTriggerGhostSync,
                                        enabled = syncState !is SyncState.Syncing,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ghost Sync")
                                    }

                                    Button(
                                        onClick = onManualSync,
                                        enabled = syncState !is SyncState.Syncing,
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (syncState is SyncState.Syncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Upload")
                                        }
                                    }
                                }
                            }
                        }

                        // Sync Message Toast/Banner
                        when (syncState) {
                            is SyncState.Success -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "✅ ${(syncState as SyncState.Success).message}",
                                    color = SuccessGreen,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            is SyncState.Error -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "❌ ${(syncState as SyncState.Error).message}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            else -> {}
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Data Inspection Header
                        Text(
                            text = "Live Data Inspection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Extracted, cleaned & compared before sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Today's Inspection Card (Pure Ring Raw Data)
                        todayData?.let {
                            InspectionCard(data = it)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Yesterday's Inspection Card (Pure Ring Raw Data)
                        yesterdayData?.let {
                            InspectionCard(data = it)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                1 -> {
                    // TAB 1: 30-Day History Browser
                    HistoryScreen(
                        historyData = historyData,
                        isLoading = isHistoryLoading,
                        onSyncSingleDay = onSyncSingleDay,
                        onSyncAllDays = onSyncAllDays,
                        onRefreshHistory = onRefreshHistory,
                        onImportRingConnFile = onImportRingConnFile
                    )
                }
                2 -> {
                    // TAB 2: Detailed Sync Logbook
                    LogbookScreen(
                        logs = syncLogs,
                        onClearLogs = onClearLogs
                    )
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            currentAthleteId = athleteId,
            currentApiKey = apiKey,
            currentAutoSync = autoSync,
            currentIntervalHours = syncIntervalHours,
            currentAutoBackup = autoBackup,
            currentBackupFolderUri = backupFolderUri,
            onPickBackupFolder = onPickBackupFolder,
            onDismiss = { showSettingsDialog = false },
            onSave = { newAthlete, newKey, newAutoSync, newInterval, newAutoBackup ->
                onSaveSettings(newAthlete, newKey, newAutoSync, newInterval, newAutoBackup)
                showSettingsDialog = false
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Wellness Data") },
            text = {
                Text("Export all recorded Health Connect metrics (Sleep, Score, RHR, SpO2, HRV, Steps) to share or backup:")
            },
            confirmButton = {
                Button(onClick = {
                    showExportDialog = false
                    onExportData("CSV")
                }) {
                    Text("Export CSV")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showExportDialog = false
                    onExportData("JSON")
                }) {
                    Text("Export JSON")
                }
            }
        )
    }
}

@Composable
fun SettingsDialog(
    currentAthleteId: String,
    currentApiKey: String,
    currentAutoSync: Boolean,
    currentIntervalHours: Int,
    currentAutoBackup: Boolean,
    currentBackupFolderUri: String?,
    onPickBackupFolder: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Int, Boolean) -> Unit
) {
    var athleteId by remember { mutableStateOf(currentAthleteId) }
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var autoSync by remember { mutableStateOf(currentAutoSync) }
    var intervalHours by remember { mutableIntStateOf(currentIntervalHours) }
    var autoBackup by remember { mutableStateOf(currentAutoBackup) }

    val intervalOptions = listOf(1, 3, 6, 12)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings & Auto-Backup") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Intervals.icu Connection",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = athleteId,
                    onValueChange = { athleteId = it },
                    label = { Text("Athlete ID (e.g. i123456 or 0)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoSync,
                        onCheckedChange = { autoSync = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enable background auto-sync")
                }

                if (autoSync) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sync Frequency:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (opt in intervalOptions) {
                            FilterChip(
                                selected = intervalHours == opt,
                                onClick = { intervalHours = opt },
                                label = { Text("${opt}h") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Google Drive / Folder Auto-Backup Section
                Text(
                    text = "Google Drive / Folder Auto-Backup",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent
                )
                Text(
                    text = "Automatically saves cleaned CSV/JSON files to your chosen Drive or local folder on every sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoBackup,
                        onCheckedChange = { autoBackup = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enable Drive Auto-Backup")
                }

                if (autoBackup) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onPickBackupFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val folderLabel = if (currentBackupFolderUri != null) {
                            "Drive Folder Selected ✓"
                        } else {
                            "Select Google Drive Folder"
                        }
                        Text(folderLabel)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(athleteId.trim(), apiKey.trim(), autoSync, intervalHours, autoBackup) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

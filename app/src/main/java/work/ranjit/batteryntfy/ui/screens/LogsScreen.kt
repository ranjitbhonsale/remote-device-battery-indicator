package work.ranjit.batteryntfy.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.ranjit.batteryntfy.data.DiagnosticLog
import work.ranjit.batteryntfy.data.NotificationLog
import work.ranjit.batteryntfy.ui.BatteryViewModel
import java.util.Date

@Composable
fun LogsScreen(viewModel: BatteryViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Notifications, 1 = Internal Diagnostics
    val notificationLogs by viewModel.logs.collectAsState()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()
    val context = LocalContext.current

    var showAppsScriptDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Tab Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System Diagnostics & Logs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedTab == 0) "${notificationLogs.size} ntfy HTTP events" else "${diagnosticLogs.size} diagnostic logs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selectedTab == 1) {
                    IconButton(onClick = { showAppsScriptDialog = true }) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = "Apps Script Integration",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        val allText = diagnosticLogs.joinToString("\n\n") { log ->
                            "[${DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(log.timestamp))}] [${log.level}] [${log.category}] ${log.title}\n${log.message}\n${log.details}"
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("BatteryNtfy Diagnostics", allText))
                        Toast.makeText(context, "Copied diagnostic logs to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = { viewModel.clearDiagnosticLogs() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear Diagnostics",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (notificationLogs.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear History",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Tab Selector Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Notification Logs (${notificationLogs.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("System Diagnostics (${diagnosticLogs.size})", fontWeight = FontWeight.Bold) }
            )
        }

        if (selectedTab == 0) {
            // Tab 0: Outgoing ntfy HTTP Notification History
            if (notificationLogs.isEmpty()) {
                EmptyLogsView("No Notification History Yet", "Events sent to ntfy will appear here with delivery status details.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(notificationLogs, key = { it.id }) { log ->
                        LogCard(log = log)
                    }
                }
            }
        } else {
            // Tab 1: Internal Diagnostics & Apps Script Telemetry
            DiagnosticLogsTabContent(
                logs = diagnosticLogs,
                onOpenAppsScriptDialog = { showAppsScriptDialog = true }
            )
        }
    }

    if (showAppsScriptDialog) {
        AppsScriptDialog(
            viewModel = viewModel,
            onDismiss = { showAppsScriptDialog = false }
        )
    }
}

@Composable
fun DiagnosticLogsTabContent(
    logs: List<DiagnosticLog>,
    onOpenAppsScriptDialog: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "ERRORS" -> logs.filter { it.level == "ERROR" || it.level == "WARN" }
            "SERVICE" -> logs.filter { it.category == "SERVICE" }
            "NETWORK" -> logs.filter { it.category == "NETWORK" }
            "ALARM" -> logs.filter { it.category == "ALARM_WATCHDOG" }
            else -> logs
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${logs.size})") }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "ERRORS",
                    onClick = { selectedFilter = "ERRORS" },
                    label = { Text("Errors/Warnings") }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "SERVICE",
                    onClick = { selectedFilter = "SERVICE" },
                    label = { Text("Service") }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "NETWORK",
                    onClick = { selectedFilter = "NETWORK" },
                    label = { Text("Network") }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "ALARM",
                    onClick = { selectedFilter = "ALARM" },
                    label = { Text("Alarm/Doze") }
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            EmptyLogsView(
                title = "No Diagnostic Logs Recorded",
                subtitle = "App lifecycle, background Doze mode wakeups, stream state, and network events will automatically log here."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredLogs, key = { it.id }) { diag ->
                    DiagnosticLogCard(log = diag)
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogCard(log: DiagnosticLog) {
    val dateStr = DateFormat.format("MMM dd, HH:mm:ss", Date(log.timestamp)).toString()
    var expanded by remember { mutableStateOf(false) }

    val levelColor = when (log.level) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARN" -> Color(0xFFF59E0B)
        "SUCCESS" -> Color(0xFF10B981)
        else -> Color(0xFF38BDF8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = levelColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = log.level,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = levelColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = log.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = log.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (log.details.isNotBlank()) {
                if (expanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = log.details,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Tap to view stack trace / details...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AppsScriptDialog(
    viewModel: BatteryViewModel,
    onDismiss: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    var urlInput by remember { mutableStateOf(config.appScriptUrl) }
    var customNoteInput by remember { mutableStateOf("") }
    val isSending by viewModel.isSendingDiagnostic.collectAsState()
    val diagnosticResult by viewModel.diagnosticResult.collectAsState()
    val context = LocalContext.current

    val sampleAppsScriptCode = """
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["Timestamp", "Device Name", "Battery %", "Charging", "App Version", "Service Running", "Custom Note"]);
    }
    sheet.appendRow([
      new Date(data.timestamp || Date.now()),
      data.deviceName || "Unknown",
      data.batteryPercent,
      data.isCharging,
      data.appVersion,
      data.isServiceRunning,
      data.customNote || ""
    ]);
    return ContentService.createTextOutput(JSON.stringify({ status: "success" })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() })).setMimeType(ContentService.MimeType.JSON);
  }
}
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Google Apps Script Diagnostics")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Post live battery diagnostics, Doze mode status, and error logs directly to your Google Sheet via Google Apps Script Web App.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        viewModel.updateConfig(config.copy(appScriptUrl = it))
                    },
                    label = { Text("Google Apps Script Web App URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = customNoteInput,
                    onValueChange = { customNoteInput = it },
                    label = { Text("Diagnostic Report Note (Optional)") },
                    placeholder = { Text("e.g. Testing M55 tablet remote alert issue") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (diagnosticResult != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (diagnosticResult!!.startsWith("Success")) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                    ) {
                        Text(
                            text = diagnosticResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (diagnosticResult!!.startsWith("Success")) Color(0xFF047857) else Color(0xFFB91C1C),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.sendDiagnosticReport(urlInput, customNoteInput)
                    },
                    enabled = !isSending && urlInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Sending Report...")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send Diagnostic Report Now")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Google Apps Script Starter Code", sampleAppsScriptCode))
                        Toast.makeText(context, "Copied Google Apps Script code snippet to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Google Apps Script Starter Code")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun LogCard(log: NotificationLog) {
    val dateStr = DateFormat.format("MMM dd, yyyy  HH:mm:ss", Date(log.timestamp)).toString()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (log.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (log.isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = log.eventType,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "${log.batteryPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = log.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (log.isSuccess) "HTTP ${log.responseCode} OK" else log.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (log.isSuccess) Color(0xFF047857) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun EmptyLogsView(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

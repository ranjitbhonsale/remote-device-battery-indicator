package work.ranjit.batteryntfy.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.ranjit.batteryntfy.data.SubscribedDeviceState
import work.ranjit.batteryntfy.ui.BatteryViewModel
import work.ranjit.batteryntfy.ui.components.QrCodeScannerDialog
import work.ranjit.batteryntfy.util.ClipboardUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(viewModel: BatteryViewModel) {
    val config by viewModel.config.collectAsState()
    val subscribedDeviceStates by viewModel.subscribedDeviceStates.collectAsState()
    val isRefreshingRemoteDevices by viewModel.isRefreshingRemoteDevices.collectAsState()
    val refreshingDevices by viewModel.refreshingDevices.collectAsState()
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current

    var newTopicInput by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Remote Devices & Subscriptions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Monitor other phones or tablets and receive notifications when their battery is low.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.requestRefreshAllDevices() },
                enabled = !isRefreshingRemoteDevices
            ) {
                if (isRefreshingRemoteDevices) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh All Remote Devices", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Section 1: Add Remote Device Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Add Remote Device to Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Enter the ntfy topic or paste the full topic URL of the remote device you want to monitor, or scan its QR code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTopicInput,
                        onValueChange = { newTopicInput = it },
                        label = { Text("Topic or URL") },
                        placeholder = { Text("e.g. tablet-battery") },
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                        trailingIcon = {
                            if (newTopicInput.isNotBlank()) {
                                IconButton(onClick = { newTopicInput = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    IconButton(
                        onClick = { showQrScanner = true },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            if (newTopicInput.isNotBlank()) {
                                viewModel.addSubscribedTopic(newTopicInput.trim())
                                newTopicInput = ""
                            }
                        },
                        enabled = newTopicInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        // Section 2: Active Subscribed Devices List
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Monitored Devices (${config.subscribedTopics.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (config.subscribedTopics.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CellTower,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No Remote Devices Subscribed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Use the input above or scan a QR code to start tracking another device's battery level.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        config.subscribedTopics.forEach { topic ->
                            val state = subscribedDeviceStates.find { it.topic.equals(topic, ignoreCase = true) }
                            val isRefreshing = refreshingDevices.contains(topic.lowercase())
                            SubscribedDeviceDetailCard(
                                topic = topic,
                                state = state,
                                globalThreshold = config.remoteLowBatteryThreshold,
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.requestDeviceRefresh(topic) },
                                onToggleAlert = { enabled -> viewModel.toggleDeviceAlertEnabled(topic, enabled) },
                                onUpdateThreshold = { newThresh -> viewModel.updateDeviceCustomThreshold(topic, newThresh) },
                                onSnooze = { durMs -> viewModel.snoozeDeviceAlert(topic, durMs) },
                                onDelete = { deviceToDelete = topic },
                                context = context,
                                composeClipboard = composeClipboard
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Remote Alerts & Sound Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFEF4444))
                    Text(
                        text = "Remote Low Battery Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Receive Remote System Notifications", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Post Android notifications and lock screen alerts when any monitored device drops low", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.receiveNotificationsEnabled,
                        onCheckedChange = { viewModel.updateConfig(config.copy(receiveNotificationsEnabled = it)) }
                    )
                }

                if (config.receiveNotificationsEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Global Remote Low Battery Threshold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Global Alert Threshold:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${config.remoteLowBatteryThreshold}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(10, 15, 20, 25, 30).forEach { preset ->
                            val isSelected = config.remoteLowBatteryThreshold == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateConfig(config.copy(remoteLowBatteryThreshold = preset)) },
                                label = { Text("$preset%", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Test Alert Buttons
                    Text("Test Remote Alert Behavior:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.triggerTestDistinctAlertWithDelay(isLocal = false, delaySeconds = 3) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lock Screen (in 3s)", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.triggerTestDistinctAlert(isLocal = false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play Alert Now", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section 4: Delivery & Fallback Polling Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Delivery & Background Polling",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = if (config.receiverPollIntervalMinutes == 0)
                        "⚡ Real-time SSE stream is active. Updates arrive instantly (<300ms) with zero periodic polling, saving server load and battery."
                    else
                        "⚡ Real-time SSE stream is active. Syncs cached device status every ${config.receiverPollIntervalMinutes} min as a fallback if the app was suspended.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        0 to "SSE Only",
                        5 to "5 min",
                        15 to "15 min",
                        30 to "30 min",
                        60 to "60 min"
                    ).forEach { (minsVal, labelText) ->
                        val isSelected = config.receiverPollIntervalMinutes == minsVal
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateConfig(config.copy(receiverPollIntervalMinutes = minsVal)) },
                            label = { Text(labelText, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // QR Scanner Dialog
    if (showQrScanner) {
        QrCodeScannerDialog(
            onDismiss = { showQrScanner = false },
            onTopicScanned = { scannedTopic ->
                showQrScanner = false
                viewModel.addSubscribedTopic(scannedTopic)
            }
        )
    }

    // Delete Confirmation Dialog
    deviceToDelete?.let { topicToRemove ->
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Device Subscription?") },
            text = { Text("Are you sure you want to stop monitoring '$topicToRemove'? You can add it back anytime.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeSubscribedTopic(topicToRemove)
                        deviceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SubscribedDeviceDetailCard(
    topic: String,
    state: SubscribedDeviceState?,
    globalThreshold: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleAlert: (Boolean) -> Unit,
    onUpdateThreshold: (Int) -> Unit,
    onSnooze: (Long) -> Unit,
    onDelete: () -> Unit,
    context: Context,
    composeClipboard: androidx.compose.ui.platform.ClipboardManager
) {
    val effectiveThreshold = state?.customLowBatteryThreshold ?: globalThreshold
    val batteryPercent = state?.batteryPercent ?: -1
    val isCharging = state?.isCharging ?: false
    val isAlertEnabled = state?.isAlertEnabled ?: true
    val deviceName = state?.deviceName?.ifBlank { topic } ?: topic

    val isLow = batteryPercent in 0..effectiveThreshold
    val statusColor = when {
        batteryPercent < 0 -> MaterialTheme.colorScheme.outline
        isLow -> Color(0xFFEF4444)
        batteryPercent <= 35 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Name, Topic, Battery Gauge Pill, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable {
                                ClipboardUtil.copyToClipboard(context, composeClipboard, "Topic", topic, "Copied '$topic' to clipboard!")
                            }
                        ) {
                            Text(
                                text = topic,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Battery Badge
                if (batteryPercent >= 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$batteryPercent%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                            if (isCharging) {
                                Icon(Icons.Default.Bolt, contentDescription = "Charging", tint = statusColor, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Device", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Row 2: Status & Time Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastUpdatedStr = if (state != null && state.lastUpdatedTimestamp > 0) {
                    val diff = System.currentTimeMillis() - state.lastUpdatedTimestamp
                    val mins = (diff / 60000).toInt()
                    when {
                        mins < 1 -> "Just now"
                        mins < 60 -> "${mins}m ago"
                        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.lastUpdatedTimestamp))
                    }
                } else {
                    "Awaiting first update"
                }

                Text(
                    text = "Sync: $lastUpdatedStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isAlertEnabled) "Alerts Active" else "Muted",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAlertEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Switch(
                        checked = isAlertEnabled,
                        onCheckedChange = onToggleAlert,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}

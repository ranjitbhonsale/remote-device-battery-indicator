package work.ranjit.batteryntfy.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import work.ranjit.batteryntfy.data.NtfyConfig
import work.ranjit.batteryntfy.ui.BatteryViewModel
import work.ranjit.batteryntfy.ui.components.QrCodeDisplayDialog
import work.ranjit.batteryntfy.ui.components.QrCodeScannerDialog
import work.ranjit.batteryntfy.util.ClipboardUtil
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtfySettingsScreen(viewModel: BatteryViewModel) {
    val config by viewModel.config.collectAsState()
    val isSendingTest by viewModel.isSendingTest.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current

    var deviceName by remember(config.deviceName) { mutableStateOf(config.deviceName) }
    var serverUrl by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var topic by remember(config.topic) { mutableStateOf(config.topic) }
    var authToken by remember(config.authToken) { mutableStateOf(config.authToken) }
    var defaultPriority by remember(config.defaultPriority) { mutableIntStateOf(config.defaultPriority) }
    var newSubTopicInput by remember { mutableStateOf("") }

    var showQrScanner by remember { mutableStateOf(false) }
    var showQrDisplayDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "ntfy Endpoint Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Section 1: Subscribed Remote Devices (Receiver Mode Settings)
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
                    Icon(Icons.Default.CellTower, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Subscribed Devices (Receiver Mode)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Add or scan ntfy topics of remote phones or tablets to receive their low battery alerts and show their battery states on your dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Input + Add Button + Scan QR Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubTopicInput,
                        onValueChange = { newSubTopicInput = it },
                        label = { Text("Remote ntfy Topic") },
                        placeholder = { Text("e.g. ranjit1024, work_tablet") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    IconButton(
                        onClick = { showQrScanner = true },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR Code", tint = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = {
                            if (newSubTopicInput.isNotBlank()) {
                                viewModel.addSubscribedTopic(newSubTopicInput)
                                newSubTopicInput = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Add")
                    }
                }

                // Subscribed Topics List Chips
                if (config.subscribedTopics.isNotEmpty()) {
                    Text(
                        text = "Active Subscriptions:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        config.subscribedTopics.forEach { subTopic ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                ClipboardUtil.copyToClipboard(
                                                    context = context,
                                                    composeClipboard = composeClipboard,
                                                    label = "Subscribed Topic",
                                                    text = subTopic,
                                                    toastMessage = "Copied topic '$subTopic' to clipboard!"
                                                )
                                            }
                                    ) {
                                        Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = subTopic,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                ClipboardUtil.copyToClipboard(
                                                    context = context,
                                                    composeClipboard = composeClipboard,
                                                    label = "Subscribed Topic",
                                                    text = subTopic,
                                                    toastMessage = "Copied topic '$subTopic' to clipboard!"
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Topic", modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.removeSubscribedTopic(subTopic) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Receiver Notification Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Receive System Notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Post local Android notifications when a remote device sends a low battery alert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = config.receiveNotificationsEnabled,
                        onCheckedChange = {
                            viewModel.updateConfig(config.copy(receiveNotificationsEnabled = it))
                        }
                    )
                }
            }
        }

        // Section 2: Distinct Low Battery Alert Settings Card
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
                        text = "Distinct Low Battery Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Triggers an urgent alarm sound, 3-pulse vibration, and heads-up banner when ANY device battery drops below preset low threshold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Local Device Low Battery Threshold Preset Chips
                Text(
                    text = "This Device Low Battery Threshold: ${config.lowBatteryThreshold}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(10, 15, 20, 25, 30).forEach { thresholdVal ->
                        val isSelected = config.lowBatteryThreshold == thresholdVal
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateConfig(config.copy(lowBatteryThreshold = thresholdVal)) },
                            label = {
                                Text(
                                    text = "$thresholdVal%",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Remote Devices Low Battery Threshold Preset Chips
                Text(
                    text = "Remote Devices Low Battery Threshold: ${config.remoteLowBatteryThreshold}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(10, 15, 20, 25, 30).forEach { thresholdVal ->
                        val isSelected = config.remoteLowBatteryThreshold == thresholdVal
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateConfig(config.copy(remoteLowBatteryThreshold = thresholdVal)) },
                            label = {
                                Text(
                                    text = "$thresholdVal%",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Test Alert Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Test Lock Screen Alerts & Sound:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "Lock your screen NOW! Alert will wake screen in 3s...", Toast.LENGTH_LONG).show()
                                viewModel.triggerTestDistinctAlertWithDelay(isLocal = false, delaySeconds = 3)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Lock Screen (in 3s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.triggerTestDistinctAlert(isLocal = false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Alert Now", fontSize = 11.sp)
                        }
                    }

                    Text(
                        text = "Tip: Tap 'Test Lock Screen (in 3s)', then press your device's power button to lock it. In 3 seconds, the screen will turn on and display the mild battery notification card (adheres to DND settings).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 3: Charging Status & Power Notifications Card
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
                    Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = Color(0xFF10B981))
                    Text(
                        text = "Charging Status & Power Events",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Broadcasts charger plug/unplug events and continuous battery charging progress updates to your ntfy topic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Charger Connected / Disconnected Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Send Charger Connected / Disconnected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Publishes status as soon as charger cable is plugged in or disconnected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.notifyOnPowerEvents,
                        onCheckedChange = { viewModel.updateConfig(config.copy(notifyOnPowerEvents = it)) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Full Battery Alert Toggle & Threshold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full Battery Alert (${config.fullBatteryThreshold}%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Publishes alert when device finishes charging", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.notifyOnFullBattery,
                        onCheckedChange = { viewModel.updateConfig(config.copy(notifyOnFullBattery = it)) }
                    )
                }

                if (config.notifyOnFullBattery) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(80, 85, 90, 95, 100).forEach { thresholdVal ->
                            val isSelected = config.fullBatteryThreshold == thresholdVal
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateConfig(config.copy(fullBatteryThreshold = thresholdVal)) },
                                label = {
                                    Text("$thresholdVal%", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Continuous Charging Progress Toggle & Step Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Continuous Charging Progress Step", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Publishes live battery progress at preset step increments while plugged into charger", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.notifyOnChargingProgress,
                        onCheckedChange = { viewModel.updateConfig(config.copy(notifyOnChargingProgress = it)) }
                    )
                }

                if (config.notifyOnChargingProgress) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(5 to "Every 5%", 10 to "Every 10%", 15 to "Every 15%", 20 to "Every 20%").forEach { (stepVal, stepLabel) ->
                            val isSelected = config.chargingProgressStepPercent == stepVal
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateConfig(config.copy(chargingProgressStepPercent = stepVal)) },
                                label = {
                                    Text(stepLabel, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Sender Mode & Device Nickname Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Source Device Identification (Sender Mode)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Specify a nickname so receiving devices know which phone or tablet sent the battery low alert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = {
                        deviceName = it
                        viewModel.updateConfig(config.copy(deviceName = it))
                    },
                    label = { Text("Device Nickname") },
                    placeholder = { Text("e.g. Work Tablet, Bedroom Phone") },
                    leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Server & Topic Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Server URL input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        viewModel.updateConfig(config.copy(serverUrl = it))
                    },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://ntfy.sh") },
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Topic input with generator & copy
                OutlinedTextField(
                    value = topic,
                    onValueChange = {
                        topic = it
                        viewModel.updateConfig(config.copy(topic = it))
                    },
                    label = { Text("ntfy Topic Name (This Device Publish Topic)") },
                    placeholder = { Text("my-battery-topic") },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val newTopic = "battery-" + UUID.randomUUID().toString().take(6)
                                    topic = newTopic
                                    viewModel.updateConfig(config.copy(topic = newTopic))
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = "Random Topic", modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = {
                                    val topicToCopy = topic.ifBlank { config.topic }.trim()
                                    ClipboardUtil.copyToClipboard(
                                        context = context,
                                        composeClipboard = composeClipboard,
                                        label = "ntfy Topic",
                                        text = topicToCopy,
                                        toastMessage = "Copied topic '$topicToCopy' to clipboard!"
                                    )
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Topic", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Full target URL display & Share / QR / Copy Buttons
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val fullUrl = config.getFullTopicUrl()
                                    ClipboardUtil.copyToClipboard(
                                        context = context,
                                        composeClipboard = composeClipboard,
                                        label = "ntfy Topic URL",
                                        text = fullUrl,
                                        toastMessage = "Copied URL to clipboard!"
                                    )
                                }
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Full Destination Publish URL (Tap to copy):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = config.getFullTopicUrl(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val fullUrl = config.getFullTopicUrl()
                                    ClipboardUtil.copyToClipboard(
                                        context = context,
                                        composeClipboard = composeClipboard,
                                        label = "ntfy Topic URL",
                                        text = fullUrl,
                                        toastMessage = "Copied URL to clipboard!"
                                    )
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Full URL", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val fullUrl = config.getFullTopicUrl()
                                    ClipboardUtil.copyToClipboard(
                                        context = context,
                                        composeClipboard = composeClipboard,
                                        label = "ntfy Topic URL",
                                        text = fullUrl,
                                        toastMessage = "Copied topic URL to clipboard!"
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy URL")
                            }

                            Button(
                                onClick = { showQrDisplayDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("QR Code")
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "BatteryNtfy Topic")
                                        putExtra(Intent.EXTRA_TEXT, "Subscribe to my phone battery updates on ntfy: ${config.getFullTopicUrl()}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share ntfy Topic Link"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }

        // Priority & Auth Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Notification Priority",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        1 to "Min",
                        2 to "Low",
                        3 to "Default",
                        4 to "High",
                        5 to "Urgent"
                    ).forEach { (priLevel, priLabel) ->
                        val isSelected = defaultPriority == priLevel
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                defaultPriority = priLevel
                                viewModel.updateConfig(config.copy(defaultPriority = priLevel))
                            },
                            label = {
                                Text(
                                    text = priLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Auth Token
                OutlinedTextField(
                    value = authToken,
                    onValueChange = {
                        authToken = it
                        viewModel.updateConfig(config.copy(authToken = it))
                    },
                    label = { Text("Auth Token / Password (Optional)") },
                    placeholder = { Text("tk_123456789... or Bearer token") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Action Buttons Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.sendTestNotification() },
                    enabled = !isSendingTest && topic.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSendingTest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending Test...")
                    } else {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Test Notification")
                    }
                }

                testResult?.let { resultMsg ->
                    Surface(
                        color = if (resultMsg.contains("Success")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resultMsg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section 7: Google Apps Script & Remote Diagnostic Logging Card
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Google Apps Script Remote Logging",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Send live diagnostics & errors to Google Sheets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                var scriptUrl by remember(config.appScriptUrl) { mutableStateOf(config.appScriptUrl) }
                OutlinedTextField(
                    value = scriptUrl,
                    onValueChange = {
                        scriptUrl = it
                        viewModel.updateConfig(config.copy(appScriptUrl = it))
                    },
                    label = { Text("Google Apps Script Web App URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "When configured, errors and diagnostic health reports are posted to your Google Apps Script endpoint automatically or on-demand from the System Diagnostics tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // QR Code Scanner Dialog
    if (showQrScanner) {
        QrCodeScannerDialog(
            onDismiss = { showQrScanner = false },
            onTopicScanned = { scannedTopic ->
                viewModel.addSubscribedTopic(scannedTopic)
            }
        )
    }

    // QR Code Display Dialog
    if (showQrDisplayDialog) {
        QrCodeDisplayDialog(
            deviceName = config.deviceName,
            topicUrl = config.getFullTopicUrl(),
            onDismiss = { showQrDisplayDialog = false }
        )
    }
}

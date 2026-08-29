package work.ranjit.batteryntfy.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.ranjit.batteryntfy.data.BatteryInfo
import work.ranjit.batteryntfy.data.SubscribedDeviceState
import work.ranjit.batteryntfy.ui.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: BatteryViewModel) {
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isSendingTest by viewModel.isSendingTest.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val isIgnoringOpt by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    val config by viewModel.config.collectAsState()
    val subscribedDeviceStates by viewModel.subscribedDeviceStates.collectAsState()
    val isRefreshingRemoteDevices by viewModel.isRefreshingRemoteDevices.collectAsState()
    val context = LocalContext.current

    var showAddTopicDialog by remember { mutableStateOf(false) }
    var newTopicInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Subscribed Remote Devices (Receiver Mode)
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
                    Icons.Default.CellTower,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Subscribed Remote Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { viewModel.refreshSubscribedDevices() },
                    enabled = !isRefreshingRemoteDevices
                ) {
                    if (isRefreshingRemoteDevices) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Remote Devices")
                    }
                }

                IconButton(
                    onClick = { showAddTopicDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Remote Device", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (subscribedDeviceStates.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.DevicesOther,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No Subscribed Devices Yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add a remote device ntfy topic (e.g. tablet or second phone) to receive and display its live battery state right here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                    Button(
                        onClick = { showAddTopicDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Subscribed Remote Device")
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                subscribedDeviceStates.forEach { remoteDevice ->
                    RemoteDeviceCard(
                        deviceState = remoteDevice,
                        onDelete = { viewModel.removeSubscribedTopic(remoteDevice.topic) }
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Section 2: Local Device Battery Telemetry (Sender Mode)
        Text(
            text = "This Device (${config.deviceName})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Battery Arc Gauge Header Card
        BatteryGaugeCard(batteryInfo = batteryInfo)

        // Background Monitoring Service Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isServiceRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                        ) {
                            Icon(
                                imageVector = if (isServiceRunning) Icons.Default.PlayArrow else Icons.Default.Stop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Dual Mode (Send & Receive)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isServiceRunning) "Broadcasting local & receiving remote alerts" else "Service is currently stopped",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { viewModel.toggleService() }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Source Device Nickname",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = config.deviceName.ifBlank { "Unassigned" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target ntfy Send Topic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = config.topic,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = { viewModel.sendImmediateUpdate() },
                        enabled = !isSendingTest,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isSendingTest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Publishing...")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Push Status Now")
                        }
                    }
                }
            }
        }

        // Level Filter Active Warning Banner
        if (config.onlySendWhenBelowLevelEnabled && batteryInfo.levelPercent > config.onlySendBelowLevelThreshold) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.FilterAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Level Filter Active (${config.onlySendBelowLevelThreshold}%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Routine background pings are suppressed while battery (${batteryInfo.levelPercent}%) is above ${config.onlySendBelowLevelThreshold}%. Manual test & push buttons bypass this filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Test notification alert banner feedback if present
        testResult?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.contains("Success") || msg.contains("pushed")) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (msg.contains("Success") || msg.contains("pushed")) Color(0xFF047857) else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearTestResult() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Battery Optimization Warning Card
        if (!isIgnoringOpt) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.BatterySaver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Disable Battery Optimization",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "To keep monitoring active continuously in the background, exclude app from Android battery restrictions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    TextButton(
                        onClick = { viewModel.requestDisableBatteryOptimization(context) }
                    ) {
                        Text("Fix", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2x2 Grid of Battery Telemetry Cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(
                title = "State & Source",
                value = if (batteryInfo.isCharging) "Charging" else "Discharging",
                subtitle = batteryInfo.pluggedType,
                icon = if (batteryInfo.isCharging) Icons.Default.Power else Icons.Default.BatteryStd,
                iconColor = if (batteryInfo.isCharging) Color(0xFF10B981) else Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                title = "Health",
                value = batteryInfo.health,
                subtitle = "Battery Condition",
                icon = Icons.Default.Favorite,
                iconColor = Color(0xFFEC4899),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(
                title = "Temperature",
                value = "${batteryInfo.temperatureCelsius}°C",
                subtitle = if (batteryInfo.temperatureCelsius > 40) "Hot" else "Normal",
                icon = Icons.Default.Thermostat,
                iconColor = if (batteryInfo.temperatureCelsius > 40) Color(0xFFEF4444) else Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                title = "Voltage",
                value = "${batteryInfo.voltageVolts} V",
                subtitle = batteryInfo.technology,
                icon = Icons.Default.ElectricBolt,
                iconColor = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Add Remote Topic Modal Dialog
    if (showAddTopicDialog) {
        AlertDialog(
            onDismissRequest = { showAddTopicDialog = false },
            title = { Text("Add Subscribed Remote Device", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the ntfy topic name of the remote phone or tablet you want to monitor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newTopicInput,
                        onValueChange = { newTopicInput = it },
                        label = { Text("Remote ntfy Topic Name") },
                        placeholder = { Text("e.g. ranjit1024, work_tablet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTopicInput.isNotBlank()) {
                            viewModel.addSubscribedTopic(newTopicInput)
                            newTopicInput = ""
                            showAddTopicDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add & Subscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTopicDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RemoteDeviceCard(
    deviceState: SubscribedDeviceState,
    onDelete: () -> Unit
) {
    val level = deviceState.batteryPercent.coerceIn(0, 100)
    val cardColor = when {
        deviceState.isCharging -> Color(0xFF10B981) // Green
        level <= 15 -> Color(0xFFEF4444) // Red
        level <= 30 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFF10B981)
    }

    val timeFormatted = remember(deviceState.lastUpdatedTimestamp) {
        val sdf = SimpleDateFormat("hh:mm a • MMM dd", Locale.getDefault())
        sdf.format(Date(deviceState.lastUpdatedTimestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cardColor.copy(alpha = 0.15f)
                    ) {
                        Icon(
                            imageVector = if (deviceState.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = cardColor,
                            modifier = Modifier.padding(8.dp).size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = deviceState.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Topic: ${deviceState.topic}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Unsubscribe",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Progress Bar & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { level / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(CircleShape),
                    color = cardColor,
                    trackColor = cardColor.copy(alpha = 0.18f)
                )

                Text(
                    text = "$level%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cardColor
                )
            }

            // Stats Sub-row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cardColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (deviceState.isCharging) "🔌 ${deviceState.pluggedType}" else "🔋 Discharging",
                        style = MaterialTheme.typography.labelSmall,
                        color = cardColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Updated: $timeFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BatteryGaugeCard(batteryInfo: BatteryInfo) {
    val level = batteryInfo.levelPercent.coerceIn(0, 100)
    val animatedProgress by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = tween(durationMillis = 800),
        label = "BatteryProgress"
    )

    val gaugeColor by animateColorAsState(
        targetValue = when {
            batteryInfo.isCharging -> Color(0xFF10B981) // Emerald Green
            level <= 15 -> Color(0xFFEF4444) // Red
            level <= 30 -> Color(0xFFF59E0B) // Amber
            else -> Color(0xFF10B981) // Green
        },
        animationSpec = tween(durationMillis = 500),
        label = "GaugeColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(190.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()

                    // Background Track
                    drawArc(
                        color = gaugeColor.copy(alpha = 0.15f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Progress Arc
                    drawArc(
                        color = gaugeColor,
                        startAngle = 135f,
                        sweepAngle = 270f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        contentDescription = null,
                        tint = gaugeColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "$level%",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (batteryInfo.isCharging) "Charging (${batteryInfo.pluggedType})" else "Discharging",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = gaugeColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = batteryInfo.getStatusSummary(),
                    style = MaterialTheme.typography.labelLarge,
                    color = gaugeColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TelemetryCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconColor.copy(alpha = 0.15f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.padding(6.dp).size(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

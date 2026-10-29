package work.ranjit.batteryntfy.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.ranjit.batteryntfy.data.BatteryInfo
import work.ranjit.batteryntfy.data.SubscribedDeviceState
import work.ranjit.batteryntfy.ui.BatteryViewModel
import work.ranjit.batteryntfy.ui.components.QrCodeDisplayDialog
import work.ranjit.batteryntfy.util.ClipboardUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BatteryViewModel,
    onNavigateToSubscriptions: () -> Unit = {}
) {
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isSendingTest by viewModel.isSendingTest.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val config by viewModel.config.collectAsState()
    val subscribedDeviceStates by viewModel.subscribedDeviceStates.collectAsState()
    val isRefreshingRemoteDevices by viewModel.isRefreshingRemoteDevices.collectAsState()
    val refreshingDevices by viewModel.refreshingDevices.collectAsState()
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current

    var showQrDisplayDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: This Device Battery Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Device Name and Service Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = config.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isServiceRunning) "Background service active" else "Service paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                        )
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { viewModel.toggleService() }
                    )
                }

                // Battery Circular Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(170.dp)
                        .padding(8.dp)
                ) {
                    val percent = batteryInfo.levelPercent
                    val sweepAngle = (percent / 100f) * 360f
                    val animatedAngle by animateFloatAsState(
                        targetValue = sweepAngle,
                        animationSpec = tween(durationMillis = 800),
                        label = "BatteryAngle"
                    )

                    val batteryColor = when {
                        batteryInfo.isCharging -> Color(0xFF10B981)
                        percent <= 20 -> Color(0xFFEF4444)
                        percent <= 40 -> Color(0xFFF59E0B)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val animatedColor by animateColorAsState(targetValue = batteryColor, label = "BatteryColor")
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = trackColor,
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = animatedColor,
                            startAngle = -90f,
                            sweepAngle = animatedAngle,
                            useCenter = false,
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$percent",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (batteryInfo.isCharging) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = "Charging",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Charging (${batteryInfo.pluggedType})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981)
                                )
                            } else {
                                Text(
                                    text = "Discharging",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Battery Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricItem(label = "Health", value = batteryInfo.health)
                    MetricItem(label = "Temperature", value = "${batteryInfo.temperatureCelsius}°C")
                    MetricItem(label = "Voltage", value = String.format(Locale.US, "%.2f V", batteryInfo.voltageVolts))
                }

                // Immediate broadcast button
                Button(
                    onClick = { viewModel.sendImmediateUpdate() },
                    enabled = !isSendingTest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSendingTest) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Broadcasting...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Broadcast Live Status to ntfy", fontSize = 13.sp)
                    }
                }
            }
        }

        // Section 2: This Device's Topic Share Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Broadcast Topic",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = config.topic,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scan this from another device to monitor this phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            ClipboardUtil.copyToClipboard(context, composeClipboard, "Topic", config.topic, "Copied topic '${config.topic}' to clipboard!")
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Topic", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { showQrDisplayDialog = true }
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = "Show QR Code", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Section 3: Subscribed Remote Devices Overview
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
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = onNavigateToSubscriptions) {
                        Text("Manage", fontWeight = FontWeight.Bold)
                    }
                }

                if (config.subscribedTopics.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CellTower, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Text("No Remote Devices Added", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Add your tablet or secondary phone to track its battery level from this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = onNavigateToSubscriptions,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Remote Device")
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        config.subscribedTopics.forEach { topic ->
                            val state = subscribedDeviceStates.find { it.topic.equals(topic, ignoreCase = true) }
                            val isRefreshing = refreshingDevices.contains(topic.lowercase())
                            CompactDeviceCard(
                                topic = topic,
                                state = state,
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.requestDeviceRefresh(topic) }
                            )
                        }
                    }
                }
            }
        }
    }

    // QR Code Display Dialog
    if (showQrDisplayDialog) {
        QrCodeDisplayDialog(
            topicUrl = config.getFullTopicUrl(),
            deviceName = config.deviceName,
            onDismiss = { showQrDisplayDialog = false }
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CompactDeviceCard(
    topic: String,
    state: SubscribedDeviceState?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val batteryPercent = state?.batteryPercent ?: -1
    val isCharging = state?.isCharging ?: false
    val deviceName = state?.deviceName?.ifBlank { topic } ?: topic

    val statusColor = when {
        batteryPercent < 0 -> MaterialTheme.colorScheme.outline
        batteryPercent <= 20 -> Color(0xFFEF4444)
        batteryPercent <= 40 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                    Text(text = deviceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(text = topic, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                } else {
                    Text("No data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

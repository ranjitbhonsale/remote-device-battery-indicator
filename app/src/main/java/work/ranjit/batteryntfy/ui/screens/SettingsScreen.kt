package work.ranjit.batteryntfy.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.ranjit.batteryntfy.ui.BatteryViewModel
import work.ranjit.batteryntfy.ui.components.QrCodeDisplayDialog
import work.ranjit.batteryntfy.util.ClipboardUtil
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BatteryViewModel) {
    val config by viewModel.config.collectAsState()
    val isIgnoringOpt by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    val isSendingTest by viewModel.isSendingTest.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var deviceName by remember(config.deviceName) { mutableStateOf(config.deviceName) }
    var serverUrl by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var topic by remember(config.topic) { mutableStateOf(config.topic) }
    var authToken by remember(config.authToken) { mutableStateOf(config.authToken) }
    var appScriptUrl by remember(config.appScriptUrl) { mutableStateOf(config.appScriptUrl) }

    var showQrDisplayDialog by remember { mutableStateOf(false) }
    var serverHealthStatus by remember { mutableStateOf<String?>(null) }
    var isTestingHealth by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "App & Server Configuration",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Configure your ntfy server connection, broadcasting identity, and service behavior.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: This Device Broadcast Identity Card
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
                    Icon(Icons.Default.PermIdentity, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Device Identity & Outgoing Topic",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "This device publishes its battery status to this ntfy topic. Other devices subscribe to this topic to monitor you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Device Nickname
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = {
                        deviceName = it
                        viewModel.updateConfig(config.copy(deviceName = it))
                    },
                    label = { Text("Device Nickname") },
                    placeholder = { Text("e.g. My Phone, Living Room Tablet") },
                    leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Outgoing Topic Name
                OutlinedTextField(
                    value = topic,
                    onValueChange = {
                        topic = it
                        viewModel.updateConfig(config.copy(topic = it))
                    },
                    label = { Text("ntfy Topic Name") },
                    placeholder = { Text("e.g. my-battery-topic") },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val randomTopic = "battery-" + UUID.randomUUID().toString().take(6)
                                    topic = randomTopic
                                    viewModel.updateConfig(config.copy(topic = randomTopic))
                                }
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = "Randomize Topic", modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = {
                                    ClipboardUtil.copyToClipboard(context, composeClipboard, "Topic", topic, "Copied topic to clipboard!")
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Topic", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick Share Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Full Topic URL: ${config.getFullTopicUrl()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    ClipboardUtil.copyToClipboard(context, composeClipboard, "ntfy URL", config.getFullTopicUrl(), "Copied URL to clipboard!")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy URL", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { showQrDisplayDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Show QR", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Battery Monitor Topic")
                                        putExtra(Intent.EXTRA_TEXT, config.getFullTopicUrl())
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share ntfy Topic"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 2: ntfy Server Configuration Card
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
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "ntfy Server Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Server URL input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        viewModel.updateConfig(config.copy(serverUrl = it))
                    },
                    label = { Text("Server Base URL") },
                    placeholder = { Text("https://ntfy.sh or http://192.168.1.20") },
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Server Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val publicPreset = "https://ntfy.sh"
                    val localPreset = "http://192.168.1.20"

                    FilterChip(
                        selected = serverUrl.trim().removeSuffix("/") == publicPreset,
                        onClick = {
                            serverUrl = publicPreset
                            viewModel.updateConfig(config.copy(serverUrl = publicPreset))
                        },
                        label = { Text("ntfy.sh (Public)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = serverUrl.trim().removeSuffix("/").contains("192.168.1.20"),
                        onClick = {
                            serverUrl = localPreset
                            viewModel.updateConfig(config.copy(serverUrl = localPreset))
                        },
                        label = { Text("Local (192.168.1.20)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Auth Token
                OutlinedTextField(
                    value = authToken,
                    onValueChange = {
                        authToken = it
                        viewModel.updateConfig(config.copy(authToken = it))
                    },
                    label = { Text("Auth Token / Password (Optional)") },
                    placeholder = { Text("tk_... or leave empty if open") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Test Server Connection Button & Health Result
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            isTestingHealth = true
                            serverHealthStatus = null
                            coroutineScope.launch {
                                val clean = serverUrl.trim().removeSuffix("/")
                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        val url = URL("$clean/v1/health")
                                        val conn = (url.openConnection() as HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 6000
                                            readTimeout = 6000
                                            setRequestProperty("User-Agent", "BatteryNtfy/1.0")
                                        }
                                        val code = conn.responseCode
                                        conn.disconnect()
                                        if (code in 200..299) "Connected (HTTP $code)" else "Server returned HTTP $code"
                                    } catch (e: Exception) {
                                        "Error: ${e.localizedMessage ?: "Connection failed"}"
                                    }
                                }
                                serverHealthStatus = result
                                isTestingHealth = false
                            }
                        },
                        enabled = !isTestingHealth,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTestingHealth) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connecting...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Server Connection", fontSize = 12.sp)
                        }
                    }

                    serverHealthStatus?.let { status ->
                        val isOk = status.startsWith("Connected")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOk) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isOk) Color(0xFF047857) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Payload Format Selector
                Text("Payload Format:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "standard" to "Standard ntfy",
                        "pingme_json" to "PingMe JSON",
                        "raw_text" to "Raw Text"
                    ).forEach { (formatKey, formatLabel) ->
                        val isSelected = config.payloadFormat == formatKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateConfig(config.copy(payloadFormat = formatKey)) },
                            label = { Text(formatLabel, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section 3: Android System & Background Service Card
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
                    Icon(Icons.Default.SettingsApplications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Android System & Background Service",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Auto-start on boot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start Service on Boot", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Automatically starts the background battery monitoring service after device restarts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.autoStartOnBoot,
                        onCheckedChange = { viewModel.updateConfig(config.copy(autoStartOnBoot = it)) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Battery Optimization Exemption
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery Optimization Exemption", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isIgnoringOpt) "Active: Android will not kill the background listener during sleep" else "Restricted: Device may throttle real-time alerts when locked",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isIgnoringOpt) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        )
                    }
                    if (!isIgnoringOpt) {
                        Button(
                            onClick = { viewModel.requestDisableBatteryOptimization(context) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Exempt", fontSize = 12.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF10B981))
                    }
                }
            }
        }

        // Section 4: Advanced Webhooks (Google Apps Script)
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
                    Icon(Icons.Default.Webhook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Advanced: Webhook Logging",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Optionally forward battery telemetry and diagnostic events to a Google Apps Script Web App or custom webhook URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = appScriptUrl,
                    onValueChange = {
                        appScriptUrl = it
                        viewModel.updateConfig(config.copy(appScriptUrl = it))
                    },
                    label = { Text("Webhook URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Section 5: App Info
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("BatteryNtfy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Version 4.4 (Build 35)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "RELEASE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    // QR Code Dialog
    if (showQrDisplayDialog) {
        QrCodeDisplayDialog(
            topicUrl = config.getFullTopicUrl(),
            deviceName = config.deviceName,
            onDismiss = { showQrDisplayDialog = false }
        )
    }
}

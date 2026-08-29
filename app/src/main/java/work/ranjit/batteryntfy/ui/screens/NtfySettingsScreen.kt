package work.ranjit.batteryntfy.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.ranjit.batteryntfy.data.NtfyConfig
import work.ranjit.batteryntfy.ui.BatteryViewModel
import work.ranjit.batteryntfy.ui.components.QrCodeDisplayDialog
import work.ranjit.batteryntfy.ui.components.QrCodeScannerDialog
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtfySettingsScreen(viewModel: BatteryViewModel) {
    val config by viewModel.config.collectAsState()
    val isSendingTest by viewModel.isSendingTest.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val context = LocalContext.current

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
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = subTopic,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.removeSubscribedTopic(subTopic) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
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

        // Section 2: Sender Mode & Device Nickname Card
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
                        Row {
                            IconButton(onClick = {
                                val newTopic = "battery-" + UUID.randomUUID().toString().take(6)
                                topic = newTopic
                                viewModel.updateConfig(config.copy(topic = newTopic))
                            }) {
                                Icon(Icons.Default.Autorenew, contentDescription = "Random Topic")
                            }
                            IconButton(onClick = {
                                val fullUrl = config.getFullTopicUrl()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("ntfy Topic URL", fullUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied topic URL to clipboard!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Topic")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Full target URL display & Share / QR Buttons
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Full Destination Publish URL:",
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
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showQrDisplayDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Show QR Code")
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
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Link")
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

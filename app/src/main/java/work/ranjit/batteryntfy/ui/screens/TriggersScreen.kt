package work.ranjit.batteryntfy.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.ranjit.batteryntfy.ui.BatteryViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersScreen(viewModel: BatteryViewModel) {
    val config by viewModel.config.collectAsState()
    val isIgnoringOpt by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    val context = LocalContext.current

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Notification Rules & Triggers",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Master Filter Rule: Only send alerts when battery is below pre-selected level
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (config.onlySendWhenBelowLevelEnabled) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FilterAlt,
                            contentDescription = null,
                            tint = if (config.onlySendWhenBelowLevelEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text("Send Alerts ONLY Below Selected Level", fontWeight = FontWeight.Bold)
                            Text(
                                "Silence all remote ntfy alerts when battery level is above threshold",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = config.onlySendWhenBelowLevelEnabled,
                        onCheckedChange = {
                            viewModel.updateConfig(config.copy(onlySendWhenBelowLevelEnabled = it))
                        }
                    )
                }

                if (config.onlySendWhenBelowLevelEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Only Send When Battery Is Below:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${config.onlySendBelowLevelThreshold}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Slider(
                        value = config.onlySendBelowLevelThreshold.toFloat(),
                        onValueChange = {
                            viewModel.updateConfig(config.copy(onlySendBelowLevelThreshold = it.roundToInt()))
                        },
                        valueRange = 5f..50f,
                        steps = 44
                    )
                }
            }
        }

        // Rule 1: Power Events
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Power,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text("Charger Plugged / Unplugged", fontWeight = FontWeight.Bold)
                        Text(
                            "Send ntfy notification when power cable is connected or disconnected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = config.notifyOnPowerEvents,
                    onCheckedChange = {
                        viewModel.updateConfig(config.copy(notifyOnPowerEvents = it))
                    }
                )
            }
        }

        // Rule 2: Low Battery Warning Threshold
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text("Low Battery Warning Alert", fontWeight = FontWeight.Bold)
                            Text(
                                "Send urgent alert when battery drops below threshold",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = config.notifyOnLowBattery,
                        onCheckedChange = {
                            viewModel.updateConfig(config.copy(notifyOnLowBattery = it))
                        }
                    )
                }

                if (config.notifyOnLowBattery) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Low Battery Threshold:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${config.lowBatteryThreshold}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Slider(
                        value = config.lowBatteryThreshold.toFloat(),
                        onValueChange = {
                            viewModel.updateConfig(config.copy(lowBatteryThreshold = it.roundToInt()))
                        },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                }
            }
        }

        // Rule 3: Full Battery Charged Alert Threshold
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text("Full Battery Charged Alert", fontWeight = FontWeight.Bold)
                            Text(
                                "Send alert when charging reaches target level",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = config.notifyOnFullBattery,
                        onCheckedChange = {
                            viewModel.updateConfig(config.copy(notifyOnFullBattery = it))
                        }
                    )
                }

                if (config.notifyOnFullBattery) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Target Battery Charged Level:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${config.fullBatteryThreshold}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = config.fullBatteryThreshold.toFloat(),
                        onValueChange = {
                            viewModel.updateConfig(config.copy(fullBatteryThreshold = it.roundToInt()))
                        },
                        valueRange = 70f..100f,
                        steps = 29
                    )
                }
            }
        }

        // Rule 4: Periodic Status Update Ping Interval
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text("Periodic Status Ping Interval", fontWeight = FontWeight.Bold)
                        Text(
                            "Send scheduled telemetry update to remote device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val options = listOf(
                    0 to "Disabled (On-demand / Event only)",
                    15 to "Every 15 Minutes",
                    30 to "Every 30 Minutes",
                    60 to "Every 1 Hour",
                    120 to "Every 2 Hours",
                    360 to "Every 6 Hours"
                )

                var expanded by remember { mutableStateOf(false) }
                val currentOptionLabel = options.find { it.first == config.periodicIntervalMinutes }?.second ?: "Every 30 Minutes"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = currentOptionLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { (mins, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.updateConfig(config.copy(periodicIntervalMinutes = mins))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Rule 5: Auto Start on Boot & Background Optimizations
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text("Auto-Start Service on Boot", fontWeight = FontWeight.Bold)
                            Text(
                                "Automatically resume background monitoring when device powers on",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = config.autoStartOnBoot,
                        onCheckedChange = {
                            viewModel.updateConfig(config.copy(autoStartOnBoot = it))
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery Saver Optimization", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isIgnoringOpt) "Excluded (App will run continuously)" else "Not excluded (Android may sleep app)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isIgnoringOpt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.requestDisableBatteryOptimization(context) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isIgnoringOpt) "Settings" else "Ignore Optimization")
                    }
                }
            }
        }
    }
}

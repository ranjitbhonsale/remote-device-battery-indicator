package work.ranjit.batteryntfy

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import work.ranjit.batteryntfy.ui.BatteryViewModel
import work.ranjit.batteryntfy.ui.screens.DashboardScreen
import work.ranjit.batteryntfy.ui.screens.LogsScreen
import work.ranjit.batteryntfy.ui.screens.NtfySettingsScreen
import work.ranjit.batteryntfy.ui.screens.TriggersScreen

enum class AppTab(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.BatteryChargingFull),
    Settings("ntfy Settings", Icons.Default.Cloud),
    Triggers("Triggers", Icons.Default.Tune),
    Logs("Logs", Icons.Default.History)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val viewModel: BatteryViewModel = viewModel()
    var selectedTab by remember { mutableStateOf(AppTab.Dashboard) }
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "BatteryNtfy",
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isServiceRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                        ) {
                            Text(
                                text = if (isServiceRunning) "ACTIVE" else "STOPPED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.Dashboard -> DashboardScreen(viewModel = viewModel)
                AppTab.Settings -> NtfySettingsScreen(viewModel = viewModel)
                AppTab.Triggers -> TriggersScreen(viewModel = viewModel)
                AppTab.Logs -> LogsScreen(viewModel = viewModel)
            }
        }
    }
}

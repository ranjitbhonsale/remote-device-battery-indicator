package work.ranjit.batteryntfy.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import work.ranjit.batteryntfy.data.BatteryInfo
import work.ranjit.batteryntfy.data.NotificationLog
import work.ranjit.batteryntfy.data.NtfyConfig
import work.ranjit.batteryntfy.data.PreferencesRepository
import work.ranjit.batteryntfy.network.NtfyPublisher
import work.ranjit.batteryntfy.service.BatteryMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    private val ntfyPublisher = NtfyPublisher()

    val batteryInfo: StateFlow<BatteryInfo> = BatteryMonitorService.currentBatteryInfo
    val isServiceRunning: StateFlow<Boolean> = BatteryMonitorService.isServiceRunning

    private val _config = MutableStateFlow(prefsRepo.getConfig())
    val config: StateFlow<NtfyConfig> = _config.asStateFlow()

    private val _logs = MutableStateFlow(prefsRepo.getLogs())
    val logs: StateFlow<List<NotificationLog>> = _logs.asStateFlow()

    private val _isSendingTest = MutableStateFlow(false)
    val isSendingTest: StateFlow<Boolean> = _isSendingTest.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(checkBatteryOptimization())
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    fun toggleService() {
        val context = getApplication<Application>()
        if (isServiceRunning.value) {
            BatteryMonitorService.stop(context)
        } else {
            BatteryMonitorService.start(context)
        }
    }

    fun updateConfig(newConfig: NtfyConfig) {
        _config.value = newConfig
        prefsRepo.saveConfig(newConfig)
    }

    fun refreshLogs() {
        _logs.value = prefsRepo.getLogs()
    }

    fun clearLogs() {
        prefsRepo.clearLogs()
        _logs.value = emptyList()
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            _isSendingTest.value = true
            _testResult.value = null

            val currentInfo = batteryInfo.value
            val currentConfig = config.value

            val log = ntfyPublisher.publishNotification(
                config = currentConfig,
                eventType = "Manual Test",
                batteryInfo = currentInfo,
                customTitle = "🧪 BatteryNtfy Test Ping",
                customMessage = "Test notification from ${currentConfig.topic}! Current battery level is ${currentInfo.levelPercent}%.",
                priorityOverride = currentConfig.defaultPriority,
                tags = listOf("test", "battery", "rocket")
            )

            prefsRepo.addLog(log)
            refreshLogs()

            _isSendingTest.value = false
            if (log.isSuccess) {
                _testResult.value = "Success! Sent to topic '${currentConfig.topic}'"
            } else {
                _testResult.value = "Failed: ${log.errorMessage}"
            }
        }
    }

    fun sendImmediateUpdate() {
        viewModelScope.launch {
            _isSendingTest.value = true
            val currentInfo = batteryInfo.value
            val currentConfig = config.value

            val log = ntfyPublisher.publishNotification(
                config = currentConfig,
                eventType = "Manual Push",
                batteryInfo = currentInfo,
                priorityOverride = currentConfig.defaultPriority,
                tags = listOf("battery", "refresh")
            )

            prefsRepo.addLog(log)
            refreshLogs()
            _isSendingTest.value = false
            _testResult.value = if (log.isSuccess) "Status pushed to ntfy!" else "Push failed: ${log.errorMessage}"
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun checkBatteryOptimization(): Boolean {
        return try {
            val context = getApplication<Application>()
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            _isIgnoringBatteryOptimizations.value = isIgnoring
            isIgnoring
        } catch (e: Exception) {
            true
        }
    }

    fun requestDisableBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

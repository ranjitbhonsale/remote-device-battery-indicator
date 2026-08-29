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
import work.ranjit.batteryntfy.data.SubscribedDeviceState
import work.ranjit.batteryntfy.network.NtfyPublisher
import work.ranjit.batteryntfy.network.NtfySubscriber
import work.ranjit.batteryntfy.service.BatteryMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    private val ntfyPublisher = NtfyPublisher()
    private val ntfySubscriber = NtfySubscriber()

    val batteryInfo: StateFlow<BatteryInfo> = BatteryMonitorService.currentBatteryInfo
    val isServiceRunning: StateFlow<Boolean> = BatteryMonitorService.isServiceRunning

    val subscribedDeviceStates: StateFlow<List<SubscribedDeviceState>> = BatteryMonitorService.subscribedDeviceStates

    private val _config = MutableStateFlow(prefsRepo.getConfig())
    val config: StateFlow<NtfyConfig> = _config.asStateFlow()

    private val _logs = MutableStateFlow(prefsRepo.getLogs())
    val logs: StateFlow<List<NotificationLog>> = _logs.asStateFlow()

    private val _isSendingTest = MutableStateFlow(false)
    val isSendingTest: StateFlow<Boolean> = _isSendingTest.asStateFlow()

    private val _isRefreshingRemoteDevices = MutableStateFlow(false)
    val isRefreshingRemoteDevices: StateFlow<Boolean> = _isRefreshingRemoteDevices.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(checkBatteryOptimization())
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    init {
        // Load initial subscribed device states on startup
        refreshSubscribedDevices()
    }

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
        refreshSubscribedDevices()
    }

    fun addSubscribedTopic(newTopic: String) {
        val cleanTopic = newTopic.trim()
        if (cleanTopic.isBlank()) return
        val currentConfig = config.value
        if (!currentConfig.subscribedTopics.contains(cleanTopic)) {
            val updatedTopics = currentConfig.subscribedTopics + cleanTopic
            val updatedConfig = currentConfig.copy(subscribedTopics = updatedTopics)
            updateConfig(updatedConfig)
            pollSingleRemoteDevice(cleanTopic)
        }
    }

    fun removeSubscribedTopic(topicToRemove: String) {
        val currentConfig = config.value
        val updatedTopics = currentConfig.subscribedTopics.filterNot { it.equals(topicToRemove, ignoreCase = true) }
        val updatedConfig = currentConfig.copy(subscribedTopics = updatedTopics)
        updateConfig(updatedConfig)

        prefsRepo.removeSubscribedDeviceState(topicToRemove)
        val remainingStates = prefsRepo.getSubscribedDeviceStates()
        BatteryMonitorService.updateSubscribedStates(remainingStates)
    }

    fun refreshSubscribedDevices() {
        viewModelScope.launch {
            _isRefreshingRemoteDevices.value = true
            val currentConfig = config.value
            val currentStates = prefsRepo.getSubscribedDeviceStates().toMutableList()

            for (topic in currentConfig.subscribedTopics) {
                val newState = ntfySubscriber.fetchLatestDeviceState(currentConfig, topic)
                if (newState != null) {
                    val index = currentStates.indexOfFirst { it.topic.equals(topic, ignoreCase = true) }
                    if (index >= 0) {
                        currentStates[index] = newState
                    } else {
                        currentStates.add(0, newState)
                    }
                    prefsRepo.saveSubscribedDeviceState(newState)
                }
            }
            BatteryMonitorService.updateSubscribedStates(currentStates)
            _isRefreshingRemoteDevices.value = false
        }
    }

    private fun pollSingleRemoteDevice(topic: String) {
        viewModelScope.launch {
            _isRefreshingRemoteDevices.value = true
            val newState = ntfySubscriber.fetchLatestDeviceState(config.value, topic)
            if (newState != null) {
                prefsRepo.saveSubscribedDeviceState(newState)
                val currentStates = prefsRepo.getSubscribedDeviceStates()
                BatteryMonitorService.updateSubscribedStates(currentStates)
            }
            _isRefreshingRemoteDevices.value = false
        }
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

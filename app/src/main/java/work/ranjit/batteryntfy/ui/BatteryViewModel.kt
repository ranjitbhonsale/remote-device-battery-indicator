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
import kotlinx.coroutines.delay
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

    private val _refreshingDevices = MutableStateFlow<Set<String>>(emptySet())
    val refreshingDevices: StateFlow<Set<String>> = _refreshingDevices.asStateFlow()

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
        val updatedTopics = if (!currentConfig.subscribedTopics.contains(cleanTopic)) {
            currentConfig.subscribedTopics + cleanTopic
        } else {
            currentConfig.subscribedTopics
        }
        val updatedConfig = currentConfig.copy(subscribedTopics = updatedTopics)
        updateConfig(updatedConfig)

        // Immediately insert placeholder state so UI updates instantly
        val existingIndex = prefsRepo.getSubscribedDeviceStates().indexOfFirst { it.topic.equals(cleanTopic, ignoreCase = true) }
        if (existingIndex < 0) {
            val placeholder = SubscribedDeviceState(
                topic = cleanTopic,
                deviceName = cleanTopic,
                batteryPercent = 50,
                isCharging = false,
                triggerEvent = "Connecting to device..."
            )
            prefsRepo.saveSubscribedDeviceState(placeholder)
            BatteryMonitorService.updateSubscribedStates(prefsRepo.getSubscribedDeviceStates())
        }

        pollSingleRemoteDevice(cleanTopic)
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

    /**
     * Sends an on-demand refresh message to a specific remote device and polls for the response
     */
    fun requestDeviceRefresh(topic: String) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isBlank()) return

        viewModelScope.launch {
            _refreshingDevices.value = _refreshingDevices.value + cleanTopic
            val currentConfig = config.value

            // 1. Dispatch on-demand refresh command to the remote device
            val log = ntfyPublisher.publishRefreshRequest(currentConfig, cleanTopic)
            prefsRepo.addLog(log)
            refreshLogs()

            // 2. Poll for updated state from the transmitter
            for (attempt in 1..4) {
                delay(attempt * 1000L)
                val newState = ntfySubscriber.fetchLatestDeviceState(currentConfig, cleanTopic)
                if (newState != null) {
                    prefsRepo.saveSubscribedDeviceState(newState)
                    BatteryMonitorService.updateSubscribedStates(prefsRepo.getSubscribedDeviceStates())
                    break
                }
            }

            _refreshingDevices.value = _refreshingDevices.value - cleanTopic
        }
    }

    /**
     * Sends on-demand refresh messages to all subscribed devices
     */
    fun requestRefreshAllDevices() {
        val topics = config.value.subscribedTopics
        if (topics.isEmpty()) return

        viewModelScope.launch {
            _isRefreshingRemoteDevices.value = true
            _refreshingDevices.value = _refreshingDevices.value + topics.toSet()
            val currentConfig = config.value

            // 1. Send refresh requests to all remote topics
            topics.forEach { subTopic ->
                val log = ntfyPublisher.publishRefreshRequest(currentConfig, subTopic)
                prefsRepo.addLog(log)
            }
            refreshLogs()

            // 2. Poll for fresh data
            for (attempt in 1..3) {
                delay(attempt * 1200L)
                val currentStates = prefsRepo.getSubscribedDeviceStates().toMutableList()
                for (subTopic in topics) {
                    val newState = ntfySubscriber.fetchLatestDeviceState(currentConfig, subTopic)
                    if (newState != null) {
                        val idx = currentStates.indexOfFirst { it.topic.equals(subTopic, ignoreCase = true) }
                        if (idx >= 0) {
                            currentStates[idx] = newState
                        } else {
                            currentStates.add(0, newState)
                        }
                        prefsRepo.saveSubscribedDeviceState(newState)
                    }
                }
                BatteryMonitorService.updateSubscribedStates(currentStates)
            }

            _refreshingDevices.value = _refreshingDevices.value - topics.toSet()
            _isRefreshingRemoteDevices.value = false
        }
    }

    fun refreshSubscribedDevices() {
        requestRefreshAllDevices()
    }

    private fun pollSingleRemoteDevice(topic: String) {
        viewModelScope.launch {
            _isRefreshingRemoteDevices.value = true
            val newState = ntfySubscriber.fetchLatestDeviceState(config.value, topic)
            val currentStates = prefsRepo.getSubscribedDeviceStates().toMutableList()
            val index = currentStates.indexOfFirst { it.topic.equals(topic, ignoreCase = true) }

            if (newState != null) {
                if (index >= 0) {
                    currentStates[index] = newState
                } else {
                    currentStates.add(0, newState)
                }
                prefsRepo.saveSubscribedDeviceState(newState)
            } else if (index < 0) {
                val placeholder = SubscribedDeviceState(
                    topic = topic,
                    deviceName = topic,
                    batteryPercent = 50,
                    triggerEvent = "Waiting for status update..."
                )
                currentStates.add(0, placeholder)
                prefsRepo.saveSubscribedDeviceState(placeholder)
            }
            BatteryMonitorService.updateSubscribedStates(currentStates)
            _isRefreshingRemoteDevices.value = false
        }
    }

    fun triggerTestDistinctAlert(isLocal: Boolean) {
        val context = getApplication<Application>()
        val deviceName = if (isLocal) config.value.deviceName else "Remote Tablet"
        val percent = if (isLocal) config.value.lowBatteryThreshold else config.value.remoteLowBatteryThreshold
        BatteryMonitorService.triggerTestDistinctAlert(context, isLocal, deviceName, percent)
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

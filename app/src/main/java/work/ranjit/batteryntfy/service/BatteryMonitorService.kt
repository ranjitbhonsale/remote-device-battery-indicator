package work.ranjit.batteryntfy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import work.ranjit.batteryntfy.MainActivity
import work.ranjit.batteryntfy.R
import work.ranjit.batteryntfy.data.BatteryInfo
import work.ranjit.batteryntfy.data.NtfyConfig
import work.ranjit.batteryntfy.data.PreferencesRepository
import work.ranjit.batteryntfy.data.SubscribedDeviceState
import work.ranjit.batteryntfy.network.NtfyPublisher
import work.ranjit.batteryntfy.network.NtfySubscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val prefsRepo by lazy { PreferencesRepository(this) }
    private val ntfyPublisher = NtfyPublisher()
    private val ntfySubscriber = NtfySubscriber()

    private var periodicJob: Job? = null
    private var receiverJob: Job? = null
    private var streamJob: Job? = null

    private var lastSentLowBatteryLevel = -1
    private var lastSentChargingPercent = -1
    private var lastFullBatteryFired = false
    private var lastRefreshResponseTime = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)
                Intent.ACTION_POWER_CONNECTED -> handlePowerEvent(true)
                Intent.ACTION_POWER_DISCONNECTED -> handlePowerEvent(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        registerBatteryReceiver()
        _isServiceRunning.value = true
        prefsRepo.setServiceEnabled(true)
        loadSubscribedDeviceStates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createServiceNotification(_currentBatteryInfo.value)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        restartPeriodicTimer()
        restartReceiverLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBatteryReceiver()
        periodicJob?.cancel()
        receiverJob?.cancel()
        streamJob?.cancel()
        _isServiceRunning.value = false
        prefsRepo.setServiceEnabled(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadSubscribedDeviceStates() {
        val savedStates = prefsRepo.getSubscribedDeviceStates()
        _subscribedDeviceStates.value = savedStates
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val pluggedType = BatteryInfo.parsePluggedType(plugged)

        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = BatteryInfo.parseHealth(healthInt)
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempCelsius = tempRaw / 10.0f
        val voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val voltageVolts = voltageRaw / 1000.0f
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        val newInfo = BatteryInfo(
            levelPercent = percent,
            isCharging = isCharging,
            pluggedType = pluggedType,
            health = health,
            temperatureCelsius = tempCelsius,
            voltageVolts = voltageVolts,
            technology = tech
        )

        _currentBatteryInfo.value = newInfo
        updateServiceNotification(newInfo)

        val config = prefsRepo.getConfig()

        // Continuous Low Battery Alert Trigger: Broadcasts every drop below threshold (e.g. 20% -> 19% -> 18%)
        if (config.notifyOnLowBattery && percent <= config.lowBatteryThreshold) {
            if (!isCharging && percent != lastSentLowBatteryLevel) {
                lastSentLowBatteryLevel = percent
                postDistinctLowBatteryNotification(
                    deviceName = config.deviceName,
                    batteryPercent = percent,
                    isCharging = isCharging,
                    pluggedType = pluggedType,
                    isLocalDevice = true,
                    triggerEvent = "Local Battery Warning ($percent%)"
                )
                sendNtfyNotification("Low Battery Alert ($percent%)", newInfo, priority = 5, tags = listOf("warning", "battery", "zap"))
            }
        } else if (percent > config.lowBatteryThreshold + 2 || isCharging) {
            lastSentLowBatteryLevel = -1
        }

        // Full Battery Alert Trigger
        if (config.notifyOnFullBattery && percent >= config.fullBatteryThreshold) {
            if (!lastFullBatteryFired && isCharging) {
                lastFullBatteryFired = true
                sendNtfyNotification("Full Battery Alert", newInfo, priority = 4, tags = listOf("battery", "check"))
            }
        } else if (percent < config.fullBatteryThreshold - 3) {
            lastFullBatteryFired = false
        }

        // Continuous Charging Progress Alert Trigger
        if (config.notifyOnChargingProgress && isCharging) {
            val step = config.chargingProgressStepPercent
            if (step > 0 && percent % step == 0 && percent != lastSentChargingPercent) {
                lastSentChargingPercent = percent
                sendNtfyNotification("Charging Progress ($percent%)", newInfo, priority = 3, tags = listOf("electric_plug", "battery", "zap"))
            }
        } else if (!isCharging) {
            lastSentChargingPercent = -1
        }
    }

    private fun handlePowerEvent(pluggedIn: Boolean) {
        val config = prefsRepo.getConfig()
        val info = _currentBatteryInfo.value
        if (config.notifyOnPowerEvents) {
            val batteryStatusIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val freshInfo = if (batteryStatusIntent != null) {
                val level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else info.levelPercent
                val status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val plugged = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val pluggedType = BatteryInfo.parsePluggedType(plugged)
                info.copy(levelPercent = percent, isCharging = isCharging, pluggedType = pluggedType)
            } else {
                info.copy(isCharging = pluggedIn)
            }
            _currentBatteryInfo.value = freshInfo

            val eventName = if (pluggedIn) "Charger Connected (${freshInfo.pluggedType})" else "Charger Disconnected"
            val tags = if (pluggedIn) listOf("electric_plug", "battery", "bolt") else listOf("unplugged", "battery")
            sendNtfyNotification(eventName, freshInfo, priority = 3, tags = tags)
        }
    }

    private fun restartPeriodicTimer() {
        periodicJob?.cancel()
        val config = prefsRepo.getConfig()
        val intervalMins = config.periodicIntervalMinutes
        if (intervalMins <= 0) return

        periodicJob = serviceScope.launch {
            while (isActive) {
                delay(intervalMins * 60 * 1000L)
                if (isActive) {
                    sendNtfyNotification(
                        "Periodic Status Update",
                        _currentBatteryInfo.value,
                        priority = config.defaultPriority,
                        tags = listOf("clock", "battery")
                    )
                }
            }
        }
    }

    /**
     * Receiver & Command Loop: Periodically polls and streams battery telemetry and listens for on-demand refresh commands
     */
    private fun restartReceiverLoop() {
        receiverJob?.cancel()
        streamJob?.cancel()

        val config = prefsRepo.getConfig()
        val allTopics = (listOf(config.topic) + config.subscribedTopics).filter { it.isNotBlank() }.distinct()
        if (allTopics.isEmpty()) return

        // 1. Stream real-time events via SSE
        streamJob = serviceScope.launch {
            while (isActive) {
                val currentConfig = prefsRepo.getConfig()
                val currentTopics = (listOf(currentConfig.topic) + currentConfig.subscribedTopics).filter { it.isNotBlank() }.distinct()
                try {
                    ntfySubscriber.streamSubscribedTopics(
                        config = currentConfig,
                        topics = currentTopics,
                        onStateReceived = { state ->
                            handleRemoteDeviceStateReceived(state, currentConfig)
                        },
                        onRefreshRequested = { reqTopic ->
                            handleIncomingRefreshRequest(reqTopic)
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000L)
            }
        }

        // 2. Periodic polling loop for both incoming refresh requests on own topic and subscribed topics
        receiverJob = serviceScope.launch {
            while (isActive) {
                val currentConfig = prefsRepo.getConfig()

                // Check for incoming refresh requests on local broadcast topic
                if (currentConfig.topic.isNotBlank()) {
                    try {
                        val hasRefreshRequest = ntfySubscriber.checkTopicForRefreshRequest(currentConfig, currentConfig.topic, "1m")
                        if (hasRefreshRequest) {
                            handleIncomingRefreshRequest(currentConfig.topic)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Poll remote devices if receiver notifications enabled
                if (currentConfig.receiveNotificationsEnabled && currentConfig.subscribedTopics.isNotEmpty()) {
                    for (subTopic in currentConfig.subscribedTopics) {
                        if (!isActive) break
                        try {
                            val state = ntfySubscriber.fetchLatestDeviceState(currentConfig, subTopic)
                            if (state != null) {
                                handleRemoteDeviceStateReceived(state, currentConfig)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                // Poll remote device topics and check commands every 25 seconds
                delay(25_000L)
            }
        }
    }

    private fun handleIncomingRefreshRequest(topic: String) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshResponseTime < 2000L) return
        lastRefreshResponseTime = now

        // Get exact live battery info directly from system OS
        val batteryStatusIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val liveInfo = if (batteryStatusIntent != null) {
            val level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else _currentBatteryInfo.value.levelPercent
            val status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val plugged = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val pluggedType = BatteryInfo.parsePluggedType(plugged)
            _currentBatteryInfo.value.copy(
                levelPercent = percent,
                isCharging = isCharging,
                pluggedType = pluggedType
            )
        } else {
            _currentBatteryInfo.value
        }
        _currentBatteryInfo.value = liveInfo

        val config = prefsRepo.getConfig()
        sendNtfyNotification(
            eventType = "On-Demand Refresh (${liveInfo.levelPercent}%)",
            batteryInfo = liveInfo,
            priority = config.defaultPriority,
            tags = listOf("battery", "refresh_response")
        )
    }

    private fun handleRemoteDeviceStateReceived(state: SubscribedDeviceState, config: NtfyConfig) {
        val currentStates = _subscribedDeviceStates.value.toMutableList()
        val existingIndex = currentStates.indexOfFirst { it.topic.equals(state.topic, ignoreCase = true) || it.deviceName.equals(state.deviceName, ignoreCase = true) }
        val oldState = if (existingIndex >= 0) currentStates[existingIndex] else null

        if (existingIndex >= 0) {
            currentStates[existingIndex] = state
        } else {
            currentStates.add(0, state)
        }
        _subscribedDeviceStates.value = currentStates
        prefsRepo.saveSubscribedDeviceStates(currentStates)

        // Post Local Android System Notification if remote battery level drops below preset threshold
        if (config.notifyOnRemoteLowBattery) {
            val isLow = state.batteryPercent in 1..config.remoteLowBatteryThreshold
            val stateChanged = oldState == null || oldState.batteryPercent != state.batteryPercent || oldState.isCharging != state.isCharging
            if (isLow && stateChanged && !state.isCharging) {
                postDistinctLowBatteryNotification(
                    deviceName = state.deviceName,
                    batteryPercent = state.batteryPercent,
                    isCharging = state.isCharging,
                    pluggedType = state.pluggedType,
                    isLocalDevice = false,
                    triggerEvent = state.triggerEvent
                )
            }
        }
    }

    private fun postDistinctLowBatteryNotification(
        deviceName: String,
        batteryPercent: Int,
        isCharging: Boolean,
        pluggedType: String,
        isLocalDevice: Boolean,
        triggerEvent: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notificationId = if (isLocalDevice) 9999 else deviceName.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val chargingText = if (isCharging) "Charging ($pluggedType)" else "Discharging"
        val title = if (isLocalDevice) {
            "🚨 THIS DEVICE LOW BATTERY: $batteryPercent%"
        } else {
            "🪫 REMOTE LOW BATTERY: [$deviceName] is at $batteryPercent%"
        }

        val text = if (isLocalDevice) {
            "This device ($deviceName) is $chargingText. Battery level has dropped to $batteryPercent%. Please plug in charger."
        } else {
            "Remote device ($deviceName) is $chargingText. Battery level has dropped to $batteryPercent% ($triggerEvent)."
        }

        val notification = NotificationCompat.Builder(this, DISTINCT_LOW_BATTERY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (isLocalDevice) "Local Battery Alert" else "Remote Battery Alert")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }

    private fun sendNtfyNotification(
        eventType: String,
        batteryInfo: BatteryInfo,
        priority: Int,
        tags: List<String>
    ) {
        serviceScope.launch {
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "BatteryNtfy:NetworkWakeLock")
            try {
                wakeLock?.acquire(10000L)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val config = prefsRepo.getConfig()

                val isCriticalOrManual = eventType.contains("Low Battery") || eventType.contains("Full Battery") || eventType.contains("Charger") || eventType.contains("Charging") || eventType.contains("Manual") || eventType.contains("Test") || eventType.contains("Refresh")
                if (!isCriticalOrManual && config.onlySendWhenBelowLevelEnabled && batteryInfo.levelPercent > config.onlySendBelowLevelThreshold) {
                    val skippedLog = work.ranjit.batteryntfy.data.NotificationLog(
                        eventType = "$eventType (Filtered)",
                        batteryPercent = batteryInfo.levelPercent,
                        title = "Alert Filtered",
                        message = "Skipped remote notification because battery level (${batteryInfo.levelPercent}%) is above filter threshold (${config.onlySendBelowLevelThreshold}%).",
                        isSuccess = false,
                        responseCode = 0,
                        errorMessage = "Filter Active: Level (${batteryInfo.levelPercent}%) > Threshold (${config.onlySendBelowLevelThreshold}%)"
                    )
                    prefsRepo.addLog(skippedLog)
                    return@launch
                }

                val log = ntfyPublisher.publishNotification(
                    config = config,
                    eventType = eventType,
                    batteryInfo = batteryInfo,
                    priorityOverride = priority,
                    tags = tags
                )
                prefsRepo.addLog(log)
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (e: Exception) {}
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows continuous battery status for remote ntfy publishing"
            }
            nm.createNotificationChannel(monitorChannel)

            val remoteChannel = NotificationChannel(
                REMOTE_CHANNEL_ID,
                "Remote Subscribed Device Battery Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a subscribed remote phone or tablet has low battery"
                enableVibration(true)
            }
            nm.createNotificationChannel(remoteChannel)

            val distinctLowChannel = NotificationChannel(
                DISTINCT_LOW_BATTERY_CHANNEL_ID,
                "🚨 Distinct Low Battery Warning Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts with distinct alarm sound & vibration when any local or remote device battery drops below preset threshold"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                val audioAttrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttrs)
            }
            nm.createNotificationChannel(distinctLowChannel)
        }
    }

    private fun createServiceNotification(info: BatteryInfo): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = if (info.levelPercent > 0) {
            "${info.levelPercent}% - ${if (info.isCharging) "Charging (${info.pluggedType})" else "Discharging"}"
        } else {
            "Monitoring battery state..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery ntfy Monitor Active")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateServiceNotification(info: BatteryInfo) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createServiceNotification(info))
    }

    companion object {
        const val CHANNEL_ID = "battery_ntfy_monitor_channel"
        const val REMOTE_CHANNEL_ID = "battery_ntfy_remote_alerts_channel"
        const val DISTINCT_LOW_BATTERY_CHANNEL_ID = "battery_ntfy_distinct_low_battery_channel"
        const val NOTIFICATION_ID = 1001

        private val _currentBatteryInfo = MutableStateFlow(BatteryInfo())
        val currentBatteryInfo: StateFlow<BatteryInfo> = _currentBatteryInfo.asStateFlow()

        private val _subscribedDeviceStates = MutableStateFlow<List<SubscribedDeviceState>>(emptyList())
        val subscribedDeviceStates: StateFlow<List<SubscribedDeviceState>> = _subscribedDeviceStates.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun triggerTestDistinctAlert(context: Context, isLocal: Boolean, deviceName: String, percent: Int) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                8888,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val title = if (isLocal) "🚨 TEST: THIS DEVICE LOW BATTERY ($percent%)" else "🪫 TEST: REMOTE [$deviceName] LOW BATTERY ($percent%)"
            val text = "Test Alert: Distinct sound and vibration triggered for device $deviceName at $percent%."

            val notification = NotificationCompat.Builder(context, DISTINCT_LOW_BATTERY_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("Distinct Alert Test")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(8888, notification)
        }

        fun updateSubscribedStates(states: List<SubscribedDeviceState>) {
            _subscribedDeviceStates.value = states
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, BatteryMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, BatteryMonitorService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

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
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import work.ranjit.batteryntfy.MainActivity
import work.ranjit.batteryntfy.R
import work.ranjit.batteryntfy.data.BatteryInfo
import work.ranjit.batteryntfy.data.PreferencesRepository
import work.ranjit.batteryntfy.network.NtfyPublisher
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

    private var periodicJob: Job? = null
    private var lastLowBatteryFired = false
    private var lastFullBatteryFired = false

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
        createNotificationChannel()
        registerBatteryReceiver()
        _isServiceRunning.value = true
        prefsRepo.setServiceEnabled(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createServiceNotification(_currentBatteryInfo.value)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (e: Exception) {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } catch (e2: Exception) {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        restartPeriodicTimer()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBatteryReceiver()
        periodicJob?.cancel()
        _isServiceRunning.value = false
        prefsRepo.setServiceEnabled(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

        // Check Low Battery Alert Trigger
        if (config.notifyOnLowBattery && percent <= config.lowBatteryThreshold) {
            if (!lastLowBatteryFired && !isCharging) {
                lastLowBatteryFired = true
                sendNtfyNotification("Low Battery Alert", newInfo, priority = 5, tags = listOf("warning", "battery", "zap"))
            }
        } else if (percent > config.lowBatteryThreshold + 3) {
            lastLowBatteryFired = false
        }

        // Check Full Battery Alert Trigger
        if (config.notifyOnFullBattery && percent >= config.fullBatteryThreshold) {
            if (!lastFullBatteryFired && isCharging) {
                lastFullBatteryFired = true
                sendNtfyNotification("Full Battery Alert", newInfo, priority = 4, tags = listOf("battery", "check"))
            }
        } else if (percent < config.fullBatteryThreshold - 3) {
            lastFullBatteryFired = false
        }
    }

    private fun handlePowerEvent(pluggedIn: Boolean) {
        val config = prefsRepo.getConfig()
        if (config.notifyOnPowerEvents) {
            val eventName = if (pluggedIn) "Charger Connected" else "Charger Disconnected"
            val tags = if (pluggedIn) listOf("electric_plug", "battery") else listOf("unplugged", "battery")
            sendNtfyNotification(eventName, _currentBatteryInfo.value, priority = 3, tags = tags)
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

                // Check if user enabled "Only send alerts when battery is below threshold"
                // Exempt Low Battery, Full Battery, and Manual Test alerts from filter
                val isCriticalOrManual = eventType.contains("Low Battery") || eventType.contains("Full Battery") || eventType.contains("Manual") || eventType.contains("Test")
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows continuous battery status for remote ntfy publishing"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
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
        const val NOTIFICATION_ID = 1001

        private val _currentBatteryInfo = MutableStateFlow(BatteryInfo())
        val currentBatteryInfo: StateFlow<BatteryInfo> = _currentBatteryInfo.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

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

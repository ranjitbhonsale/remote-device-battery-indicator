package work.ranjit.batteryntfy.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import work.ranjit.batteryntfy.data.BatteryInfo
import work.ranjit.batteryntfy.data.PreferencesRepository
import work.ranjit.batteryntfy.network.NtfyPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repo = PreferencesRepository(context)
        if (!repo.isServiceEnabled()) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatteryNtfy:WatchdogWakeLock")
        try {
            wakeLock?.acquire(10000L)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1. Ensure Foreground Service is alive
        if (!BatteryMonitorService.isServiceRunning.value) {
            BatteryMonitorService.start(context)
        }

        // 2. Poll live battery state from system intent and push watchdog update if service was sleeping in Doze Mode
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val batteryStatusIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (batteryStatusIntent != null) {
                    val level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

                    val status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val plugged = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    val pluggedType = BatteryInfo.parsePluggedType(plugged)
                    val healthInt = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                    val health = BatteryInfo.parseHealth(healthInt)
                    val tempRaw = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    val tempCelsius = tempRaw / 10.0f
                    val voltageRaw = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                    val voltageVolts = voltageRaw / 1000.0f
                    val tech = batteryStatusIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

                    val info = BatteryInfo(
                        levelPercent = percent,
                        isCharging = isCharging,
                        pluggedType = pluggedType,
                        health = health,
                        temperatureCelsius = tempCelsius,
                        voltageVolts = voltageVolts,
                        technology = tech
                    )

                    val config = repo.getConfig()
                    val publisher = NtfyPublisher()
                    publisher.publishNotification(
                        config = config,
                        eventType = "Watchdog Keep-Alive ($percent%)",
                        batteryInfo = info,
                        priorityOverride = config.defaultPriority,
                        tags = listOf("clock", "battery", "heartbeat")
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                scheduleNextWatchdog(context)
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (e: Exception) {}
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L // 15 Minutes

        fun scheduleNextWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    999,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAtMs = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancelWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    999,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

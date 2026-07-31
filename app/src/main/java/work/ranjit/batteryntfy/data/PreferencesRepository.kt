package work.ranjit.batteryntfy.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("battery_ntfy_prefs", Context.MODE_PRIVATE)

    fun saveConfig(config: NtfyConfig) {
        prefs.edit().apply {
            putString(KEY_SERVER_URL, config.serverUrl)
            putString(KEY_TOPIC, config.topic)
            putString(KEY_AUTH_TOKEN, config.authToken)
            putInt(KEY_DEFAULT_PRIORITY, config.defaultPriority)
            putBoolean(KEY_NOTIFY_POWER_EVENTS, config.notifyOnPowerEvents)
            putBoolean(KEY_NOTIFY_LOW_BATTERY, config.notifyOnLowBattery)
            putInt(KEY_LOW_BATTERY_THRESHOLD, config.lowBatteryThreshold)
            putBoolean(KEY_NOTIFY_FULL_BATTERY, config.notifyOnFullBattery)
            putInt(KEY_FULL_BATTERY_THRESHOLD, config.fullBatteryThreshold)
            putInt(KEY_PERIODIC_INTERVAL, config.periodicIntervalMinutes)
            putBoolean(KEY_AUTO_START_BOOT, config.autoStartOnBoot)
            putBoolean(KEY_ONLY_BELOW_LEVEL_ENABLED, config.onlySendWhenBelowLevelEnabled)
            putInt(KEY_ONLY_BELOW_LEVEL_THRESHOLD, config.onlySendBelowLevelThreshold)
            apply()
        }
    }

    fun getConfig(): NtfyConfig {
        val defaultConfig = NtfyConfig()
        return NtfyConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, defaultConfig.serverUrl) ?: defaultConfig.serverUrl,
            topic = prefs.getString(KEY_TOPIC, defaultConfig.topic) ?: defaultConfig.topic,
            authToken = prefs.getString(KEY_AUTH_TOKEN, defaultConfig.authToken) ?: defaultConfig.authToken,
            defaultPriority = prefs.getInt(KEY_DEFAULT_PRIORITY, defaultConfig.defaultPriority),
            notifyOnPowerEvents = prefs.getBoolean(KEY_NOTIFY_POWER_EVENTS, defaultConfig.notifyOnPowerEvents),
            notifyOnLowBattery = prefs.getBoolean(KEY_NOTIFY_LOW_BATTERY, defaultConfig.notifyOnLowBattery),
            lowBatteryThreshold = prefs.getInt(KEY_LOW_BATTERY_THRESHOLD, defaultConfig.lowBatteryThreshold),
            notifyOnFullBattery = prefs.getBoolean(KEY_NOTIFY_FULL_BATTERY, defaultConfig.notifyOnFullBattery),
            fullBatteryThreshold = prefs.getInt(KEY_FULL_BATTERY_THRESHOLD, defaultConfig.fullBatteryThreshold),
            periodicIntervalMinutes = prefs.getInt(KEY_PERIODIC_INTERVAL, defaultConfig.periodicIntervalMinutes),
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_BOOT, defaultConfig.autoStartOnBoot),
            onlySendWhenBelowLevelEnabled = prefs.getBoolean(KEY_ONLY_BELOW_LEVEL_ENABLED, defaultConfig.onlySendWhenBelowLevelEnabled),
            onlySendBelowLevelThreshold = prefs.getInt(KEY_ONLY_BELOW_LEVEL_THRESHOLD, defaultConfig.onlySendBelowLevelThreshold)
        )
    }

    fun saveLogs(logs: List<NotificationLog>) {
        val jsonArray = JSONArray()
        // Keep max 100 recent logs
        logs.take(100).forEach { log ->
            val obj = JSONObject().apply {
                put("id", log.id)
                put("timestamp", log.timestamp)
                put("eventType", log.eventType)
                put("batteryPercent", log.batteryPercent)
                put("title", log.title)
                put("message", log.message)
                put("isSuccess", log.isSuccess)
                put("responseCode", log.responseCode)
                put("errorMessage", log.errorMessage)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_LOGS_JSON, jsonArray.toString()).apply()
    }

    fun getLogs(): List<NotificationLog> {
        val rawJson = prefs.getString(KEY_LOGS_JSON, null) ?: return emptyList()
        val list = mutableListOf<NotificationLog>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    NotificationLog(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        eventType = obj.optString("eventType", "Unknown"),
                        batteryPercent = obj.optInt("batteryPercent", 0),
                        title = obj.optString("title", ""),
                        message = obj.optString("message", ""),
                        isSuccess = obj.optBoolean("isSuccess", false),
                        responseCode = obj.optInt("responseCode", 0),
                        errorMessage = obj.optString("errorMessage", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addLog(log: NotificationLog) {
        val currentLogs = getLogs().toMutableList()
        currentLogs.add(0, log) // Add to top
        saveLogs(currentLogs)
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS_JSON).apply()
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOPIC = "topic"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_DEFAULT_PRIORITY = "default_priority"
        private const val KEY_NOTIFY_POWER_EVENTS = "notify_power_events"
        private const val KEY_NOTIFY_LOW_BATTERY = "notify_low_battery"
        private const val KEY_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
        private const val KEY_NOTIFY_FULL_BATTERY = "notify_full_battery"
        private const val KEY_FULL_BATTERY_THRESHOLD = "full_battery_threshold"
        private const val KEY_PERIODIC_INTERVAL = "periodic_interval"
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_ONLY_BELOW_LEVEL_ENABLED = "only_below_level_enabled"
        private const val KEY_ONLY_BELOW_LEVEL_THRESHOLD = "only_below_level_threshold"
        private const val KEY_LOGS_JSON = "logs_json"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }
}

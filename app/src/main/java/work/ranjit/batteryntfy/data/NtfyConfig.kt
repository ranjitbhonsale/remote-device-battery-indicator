package work.ranjit.batteryntfy.data

import android.os.Build

data class NtfyConfig(
    val deviceName: String = Build.MODEL ?: "My Android Device",
    val serverUrl: String = "https://ntfy.sh",
    val topic: String = defaultTopicForDevice(Build.MODEL ?: "My Android Device"),
    val authToken: String = "", // Optional Bearer token or Basic auth
    val defaultPriority: Int = 3, // 1: Min, 2: Low, 3: Default, 4: High, 5: Urgent
    val notifyOnPowerEvents: Boolean = true, // Plugged in / Unplugged
    val notifyOnLowBattery: Boolean = true,
    val lowBatteryThreshold: Int = 15,
    val notifyOnFullBattery: Boolean = true,
    val fullBatteryThreshold: Int = 90,
    val notifyOnChargingProgress: Boolean = true,
    val chargingProgressStepPercent: Int = 10, // Broadcasts every 5%, 10%, 15%, 20% while charging
    val periodicIntervalMinutes: Int = 30, // 0 = disabled, 15, 30, 60, 120, etc.
    val autoStartOnBoot: Boolean = true,
    val onlySendWhenBelowLevelEnabled: Boolean = false,
    val onlySendBelowLevelThreshold: Int = 20,
    val payloadFormat: String = "standard", // "standard" (ntfy Headers + Text), "pingme_json" (PingMe/Webhook JSON), "raw_text" (Body Text)

    // Receiver Mode (Subscribed Remote Devices) Settings
    val subscribedTopics: List<String> = emptyList(),
    val receiveNotificationsEnabled: Boolean = true,
    val notifyOnRemoteLowBattery: Boolean = true,
    val remoteLowBatteryThreshold: Int = 20
) {
    fun getFullTopicUrl(): String {
        val cleanServer = serverUrl.trim().removeSuffix("/")
        val cleanTopic = topic.trim()
        return "$cleanServer/$cleanTopic"
    }

    fun getFullSubscribedTopicUrl(subTopic: String): String {
        val cleanServer = serverUrl.trim().removeSuffix("/")
        val cleanTopic = subTopic.trim()
        return "$cleanServer/$cleanTopic"
    }

    companion object {
        fun defaultTopicForDevice(deviceName: String): String {
            val formatted = deviceName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            return if (formatted.isNotBlank()) formatted else "my-android-device"
        }

        fun priorityName(priority: Int): String {
            return when (priority) {
                1 -> "Min (1)"
                2 -> "Low (2)"
                3 -> "Default (3)"
                4 -> "High (4)"
                5 -> "Urgent (5)"
                else -> "Default (3)"
            }
        }
    }
}

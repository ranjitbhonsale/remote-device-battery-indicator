package work.ranjit.batteryntfy.data

import java.util.UUID

data class NtfyConfig(
    val serverUrl: String = "https://ntfy.sh",
    val topic: String = "battery-mon-" + UUID.randomUUID().toString().take(6),
    val authToken: String = "", // Optional Bearer token or Basic auth
    val defaultPriority: Int = 3, // 1: Min, 2: Low, 3: Default, 4: High, 5: Urgent
    val notifyOnPowerEvents: Boolean = true, // Plugged in / Unplugged
    val notifyOnLowBattery: Boolean = true,
    val lowBatteryThreshold: Int = 15,
    val notifyOnFullBattery: Boolean = true,
    val fullBatteryThreshold: Int = 90,
    val periodicIntervalMinutes: Int = 30, // 0 = disabled, 15, 30, 60, 120, etc.
    val autoStartOnBoot: Boolean = true,
    val onlySendWhenBelowLevelEnabled: Boolean = false,
    val onlySendBelowLevelThreshold: Int = 20
) {
    fun getFullTopicUrl(): String {
        val cleanServer = serverUrl.trim().removeSuffix("/")
        val cleanTopic = topic.trim()
        return "$cleanServer/$cleanTopic"
    }

    companion object {
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

package work.ranjit.batteryntfy.data

import org.json.JSONObject

data class SubscribedDeviceState(
    val topic: String,
    val deviceName: String = "Remote Device",
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val pluggedType: String = "Unplugged",
    val health: String = "Good",
    val temperatureCelsius: Float = 0f,
    val voltageVolts: Float = 0f,
    val triggerEvent: String = "Status Update",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val rawTitle: String = "",
    val rawMessage: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("topic", topic)
            put("deviceName", deviceName)
            put("batteryPercent", batteryPercent)
            put("isCharging", isCharging)
            put("pluggedType", pluggedType)
            put("health", health)
            put("temperatureCelsius", temperatureCelsius.toDouble())
            put("voltageVolts", voltageVolts.toDouble())
            put("triggerEvent", triggerEvent)
            put("lastUpdatedTimestamp", lastUpdatedTimestamp)
            put("rawTitle", rawTitle)
            put("rawMessage", rawMessage)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SubscribedDeviceState {
            return SubscribedDeviceState(
                topic = json.optString("topic", ""),
                deviceName = json.optString("deviceName", "Remote Device"),
                batteryPercent = json.optInt("batteryPercent", 0),
                isCharging = json.optBoolean("isCharging", false),
                pluggedType = json.optString("pluggedType", "Unplugged"),
                health = json.optString("health", "Good"),
                temperatureCelsius = json.optDouble("temperatureCelsius", 0.0).toFloat(),
                voltageVolts = json.optDouble("voltageVolts", 0.0).toFloat(),
                triggerEvent = json.optString("triggerEvent", "Status Update"),
                lastUpdatedTimestamp = json.optLong("lastUpdatedTimestamp", System.currentTimeMillis()),
                rawTitle = json.optString("rawTitle", ""),
                rawMessage = json.optString("rawMessage", "")
            )
        }

        /**
         * Checks if an incoming payload is a command to refresh battery status
         */
        fun isRefreshRequest(
            title: String,
            message: String,
            tags: List<String> = emptyList()
        ): Boolean {
            val fullText = "$title\n$message"
            return fullText.contains("REFRESH_REQUEST", ignoreCase = true) ||
                    fullText.contains("BATTERY_REFRESH_REQUEST", ignoreCase = true) ||
                    tags.contains("refresh_request") ||
                    tags.contains("cmd_refresh")
        }

        /**
         * Parse incoming ntfy payload text or JSON into a SubscribedDeviceState object.
         * Returns null if the payload is a command (e.g. refresh request) rather than telemetry.
         */
        fun parseFromNtfyPayload(
            topic: String,
            title: String,
            message: String,
            tags: List<String> = emptyList(),
            timestamp: Long = System.currentTimeMillis()
        ): SubscribedDeviceState? {
            if (isRefreshRequest(title, message, tags)) {
                return null
            }

            var deviceName = "Remote Device"
            var batteryPercent = -1
            var isCharging = false
            var pluggedType = "Unplugged"
            var health = "Good"
            var temperatureCelsius = 0f
            var voltageVolts = 0f
            var triggerEvent = "Status Update"

            // 1. Try parsing JSON payload (PingMe or custom JSON)
            if (message.trim().startsWith("{") && message.trim().endsWith("}")) {
                try {
                    val jsonObj = JSONObject(message.trim())
                    val action = jsonObj.optString("action", "")
                    if (action.equals("REFRESH_REQUEST", ignoreCase = true)) {
                        return null
                    }
                    deviceName = jsonObj.optString("device", jsonObj.optString("deviceName", deviceName))
                    val bodyText = jsonObj.optString("body", jsonObj.optString("text", message))
                    return parseFromTextAndHeaders(topic, title.ifBlank { jsonObj.optString("title") }, bodyText, deviceName, timestamp)
                } catch (e: Exception) {
                    // Fallback to text parsing
                }
            }

            return parseFromTextAndHeaders(topic, title, message, deviceName, timestamp)
        }

        private fun parseFromTextAndHeaders(
            topic: String,
            title: String,
            message: String,
            defaultDeviceName: String,
            timestamp: Long
        ): SubscribedDeviceState? {
            var deviceName = defaultDeviceName
            var batteryPercent = -1
            var isCharging = false
            var pluggedType = "Unplugged"
            var health = "Good"
            var temperatureCelsius = 0f
            var voltageVolts = 0f
            var triggerEvent = "Status Update"

            val fullText = "$title\n$message"

            // Extract Device Nickname from title format: "🔌 [Work Tablet] Battery: 85%"
            val bracketMatch = Regex("\\[([^\\]]+)\\]").find(title)
            if (bracketMatch != null) {
                deviceName = bracketMatch.groupValues[1].trim()
            } else {
                val sourceMatch = Regex("Source Device:\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(message)
                if (sourceMatch != null) {
                    deviceName = sourceMatch.groupValues[1].trim()
                }
            }

            // Extract Battery Percentage
            val levelMatch = Regex("Level:\\s*(\\d+)%", RegexOption.IGNORE_CASE).find(fullText)
                ?: Regex("Battery:\\s*(\\d+)%", RegexOption.IGNORE_CASE).find(fullText)
                ?: Regex("(\\d+)%").find(fullText)
            if (levelMatch != null) {
                batteryPercent = levelMatch.groupValues[1].toIntOrNull() ?: 0
            }

            // Extract Charging Status
            if (fullText.contains("Charging", ignoreCase = true) || fullText.contains("🔌")) {
                isCharging = true
                pluggedType = when {
                    fullText.contains("AC", ignoreCase = true) -> "AC Charger"
                    fullText.contains("USB", ignoreCase = true) -> "USB Cable"
                    fullText.contains("Wireless", ignoreCase = true) -> "Wireless Pad"
                    else -> "Charging"
                }
            } else {
                isCharging = false
                pluggedType = "Unplugged"
            }

            // Extract Health
            val healthMatch = Regex("Health:\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(message)
            if (healthMatch != null) {
                health = healthMatch.groupValues[1].trim()
            }

            // Extract Temperature
            val tempMatch = Regex("Temp:\\s*([0-9.]+)", RegexOption.IGNORE_CASE).find(message)
            if (tempMatch != null) {
                temperatureCelsius = tempMatch.groupValues[1].toFloatOrNull() ?: 0f
            }

            // Extract Voltage
            val voltMatch = Regex("Voltage:\\s*([0-9.]+)", RegexOption.IGNORE_CASE).find(message)
            if (voltMatch != null) {
                voltageVolts = voltMatch.groupValues[1].toFloatOrNull() ?: 0f
            }

            // Extract Trigger Event
            val triggerMatch = Regex("Trigger:\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(message)
            if (triggerMatch != null) {
                triggerEvent = triggerMatch.groupValues[1].trim()
            } else if (title.contains("(")) {
                val parenMatch = Regex("\\(([^)]+)\\)").find(title)
                if (parenMatch != null) {
                    triggerEvent = parenMatch.groupValues[1].trim()
                }
            }

            return SubscribedDeviceState(
                topic = topic,
                deviceName = if (deviceName.isNotBlank()) deviceName else topic,
                batteryPercent = if (batteryPercent in 0..100) batteryPercent else 50,
                isCharging = isCharging,
                pluggedType = pluggedType,
                health = health,
                temperatureCelsius = temperatureCelsius,
                voltageVolts = voltageVolts,
                triggerEvent = triggerEvent,
                lastUpdatedTimestamp = timestamp,
                rawTitle = title,
                rawMessage = message
            )
        }
    }
}

package work.ranjit.batteryntfy.data

import org.json.JSONObject

data class DiagnosticLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // "INFO", "SUCCESS", "WARN", "ERROR"
    val category: String = "GENERAL", // "SERVICE", "NETWORK", "SUBSCRIBER", "ALARM_WATCHDOG", "BATTERY", "PERMISSIONS"
    val title: String,
    val message: String,
    val details: String = "",
    val deviceName: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("level", level)
            put("category", category)
            put("title", title)
            put("message", message)
            put("details", details)
            put("deviceName", deviceName)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): DiagnosticLog {
            return DiagnosticLog(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                level = json.optString("level", "INFO"),
                category = json.optString("category", "GENERAL"),
                title = json.optString("title", ""),
                message = json.optString("message", ""),
                details = json.optString("details", ""),
                deviceName = json.optString("deviceName", "")
            )
        }
    }
}

package work.ranjit.batteryntfy.network

import work.ranjit.batteryntfy.data.BatteryInfo
import work.ranjit.batteryntfy.data.NotificationLog
import work.ranjit.batteryntfy.data.NtfyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class NtfyPublisher {

    suspend fun publishNotification(
        config: NtfyConfig,
        eventType: String,
        batteryInfo: BatteryInfo,
        customTitle: String? = null,
        customMessage: String? = null,
        priorityOverride: Int? = null,
        tags: List<String> = listOf("battery")
    ): NotificationLog = withContext(Dispatchers.IO) {
        val targetUrl = config.getFullTopicUrl()
        val title = customTitle ?: buildTitle(eventType, batteryInfo)
        val message = customMessage ?: buildMessage(eventType, batteryInfo)
        val priority = priorityOverride ?: config.defaultPriority

        var responseCode = -1
        var isSuccess = false
        var errorMessage = ""

        try {
            val url = URL(targetUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000

                // Set ntfy headers
                setRequestProperty("Title", title)
                setRequestProperty("Priority", priority.toString())
                if (tags.isNotEmpty()) {
                    setRequestProperty("Tags", tags.joinToString(","))
                }
                if (config.authToken.isNotBlank()) {
                    val auth = if (config.authToken.startsWith("Bearer ") || config.authToken.startsWith("Basic ")) {
                        config.authToken
                    } else {
                        "Bearer ${config.authToken}"
                    }
                    setRequestProperty("Authorization", auth)
                }

                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(message)
                writer.flush()
            }

            responseCode = connection.responseCode
            isSuccess = responseCode in 200..299
            if (!isSuccess) {
                errorMessage = "HTTP Error $responseCode: ${connection.responseMessage ?: "Unknown Error"}"
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
            isSuccess = false
            responseCode = 0
            errorMessage = e.localizedMessage ?: e.message ?: "Network error"
        }

        return@withContext NotificationLog(
            eventType = eventType,
            batteryPercent = batteryInfo.levelPercent,
            title = title,
            message = message,
            isSuccess = isSuccess,
            responseCode = responseCode,
            errorMessage = errorMessage
        )
    }

    private fun buildTitle(eventType: String, batteryInfo: BatteryInfo): String {
        val icon = when {
            batteryInfo.isCharging -> "🔌"
            batteryInfo.levelPercent <= 15 -> "🪫"
            batteryInfo.levelPercent >= 90 -> "🔋"
            else -> "📱"
        }
        return "$icon Device Battery: ${batteryInfo.levelPercent}% ($eventType)"
    }

    private fun buildMessage(eventType: String, batteryInfo: BatteryInfo): String {
        val state = if (batteryInfo.isCharging) "Charging via ${batteryInfo.pluggedType}" else "Discharging"
        return """
            Status: $state
            Level: ${batteryInfo.levelPercent}%
            Health: ${batteryInfo.health}
            Temp: ${batteryInfo.temperatureCelsius}°C
            Voltage: ${batteryInfo.voltageVolts}V
            Trigger: $eventType
        """.trimIndent()
    }
}

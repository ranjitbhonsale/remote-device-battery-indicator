package work.ranjit.batteryntfy.network

import work.ranjit.batteryntfy.data.BatteryInfo
import work.ranjit.batteryntfy.data.NotificationLog
import work.ranjit.batteryntfy.data.NtfyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true

                // Headers for ntfy.sh and HTTP webhooks
                setRequestProperty("User-Agent", "BatteryNtfy/1.0 (Android)")
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

                // If target URL or server is JSON API / PingMe / Webhook, JSON format works seamlessly
                if (targetUrl.endsWith("/json") || targetUrl.contains("ping") || targetUrl.contains("webhook")) {
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                } else {
                    setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                }
            }

            // Create JSON or Plain payload body
            val payloadText = if (targetUrl.endsWith("/json") || targetUrl.contains("ping") || targetUrl.contains("webhook")) {
                JSONObject().apply {
                    put("topic", config.topic)
                    put("title", title)
                    put("message", message)
                    put("priority", priority)
                    put("tags", JSONArray(tags))
                }.toString()
            } else {
                message
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payloadText)
                writer.flush()
            }

            responseCode = connection.responseCode
            isSuccess = responseCode in 200..299
            if (!isSuccess) {
                val errorStream = connection.errorStream
                val errText = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                errorMessage = "HTTP Error $responseCode: ${connection.responseMessage ?: "Unknown"}${if (errText.isNotBlank()) " ($errText)" else ""}"
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
            isSuccess = false
            responseCode = 0
            val rawErr = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            errorMessage = when {
                rawErr.contains("Cleartext", ignoreCase = true) -> "Cleartext HTTP denied by network security policy"
                rawErr.contains("Unable to resolve host", ignoreCase = true) -> "No Internet / Host unreachable"
                rawErr.contains("timeout", ignoreCase = true) -> "Connection Timed Out"
                else -> rawErr
            }
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

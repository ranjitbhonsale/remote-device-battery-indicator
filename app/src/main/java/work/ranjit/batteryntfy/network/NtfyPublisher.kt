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
        val title = customTitle ?: buildTitle(eventType, batteryInfo)
        val detailsMessage = customMessage ?: buildMessage(eventType, batteryInfo)
        val priority = priorityOverride ?: config.defaultPriority
        val format = config.payloadFormat

        // Handle topic formatting (PingMe app uses 'work_ranjit_' namespace prefix)
        val rawTopic = config.topic.trim()
        val topicClean = if (format == "pingme_json" && !rawTopic.startsWith("work_ranjit_")) {
            "work_ranjit_$rawTopic"
        } else {
            rawTopic
        }

        // Determine destination target URL
        val cleanServer = config.serverUrl.trim().removeSuffix("/")
        val targetUrl = "$cleanServer/$topicClean"

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

                // Always send standard User-Agent & ntfy HTTP headers for backward compatibility
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

                when (format) {
                    "pingme_json" -> {
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    else -> {
                        setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    }
                }
            }

            // Construct payload content based on chosen format
            val payloadText = when (format) {
                "pingme_json" -> {
                    // PingMe UI filters by message matching target ID (rawTopic)
                    JSONObject().apply {
                        put("topic", topicClean)
                        put("title", title)
                        put("message", rawTopic) // Matches PingMe senderOrReceiver filter!
                        put("text", "$title\n\n$detailsMessage")
                        put("body", detailsMessage)
                        put("priority", priority)
                        put("tags", JSONArray(tags))
                    }.toString()
                }
                "raw_text" -> {
                    "$title\n\n$detailsMessage"
                }
                else -> {
                    // Standard ntfy Direct Mobile Format (headers + plain text body)
                    detailsMessage
                }
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
            message = detailsMessage,
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

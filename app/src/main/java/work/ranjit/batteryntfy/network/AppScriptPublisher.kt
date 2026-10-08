package work.ranjit.batteryntfy.network

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import work.ranjit.batteryntfy.data.PreferencesRepository
import work.ranjit.batteryntfy.service.BatteryMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object AppScriptPublisher {

    suspend fun sendDiagnosticReport(
        context: Context,
        targetUrl: String,
        customNote: String = ""
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = targetUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Pair(false, "Apps Script URL is empty")
        }

        val repo = PreferencesRepository(context)
        val config = repo.getConfig()
        val currentBattery = BatteryMonitorService.currentBatteryInfo.value
        val recentLogs = repo.getDiagnosticLogs().take(30)
        val notificationsLogs = repo.getLogs().take(15)

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBatteryOpt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val payload = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("deviceName", config.deviceName)
            put("androidVersion", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("appVersion", "3.7 (Build 28)")
            put("customNote", customNote)

            put("batteryPercent", currentBattery.levelPercent)
            put("isCharging", currentBattery.isCharging)
            put("pluggedType", currentBattery.pluggedType)

            put("serviceEnabled", repo.isServiceEnabled())
            put("isServiceRunning", BatteryMonitorService.isServiceRunning.value)
            put("notificationsPermissionEnabled", areNotificationsEnabled)
            put("batteryOptimizationIgnored", isIgnoringBatteryOpt)

            put("serverUrl", config.serverUrl)
            put("localTopic", config.topic)
            put("subscribedTopicsCount", config.subscribedTopics.size)
            put("lowBatteryThreshold", config.lowBatteryThreshold)

            val logsArray = JSONArray()
            recentLogs.forEach { log -> logsArray.put(log.toJson()) }
            put("diagnosticLogs", logsArray)

            val ntfyArray = JSONArray()
            notificationsLogs.forEach { log ->
                val obj = JSONObject().apply {
                    put("id", log.id)
                    put("timestamp", log.timestamp)
                    put("eventType", log.eventType)
                    put("batteryPercent", log.batteryPercent)
                    put("isSuccess", log.isSuccess)
                    put("responseCode", log.responseCode)
                    put("errorMessage", log.errorMessage)
                }
                ntfyArray.put(obj)
            }
            put("notificationLogs", ntfyArray)
        }

        try {
            val url = URL(cleanUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "BatteryNtfy/1.0 (Android Diagnostic)")
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            return@withContext if (responseCode in 200..399) {
                connection.disconnect()
                Pair(true, "Successfully sent diagnostic report to Apps Script (HTTP $responseCode)")
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorText = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                connection.disconnect()
                Pair(false, "Apps Script HTTP Error $responseCode: $errorText")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Pair(false, "Apps Script Connection Error: ${e.localizedMessage}")
        }
    }
}

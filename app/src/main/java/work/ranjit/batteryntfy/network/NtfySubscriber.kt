package work.ranjit.batteryntfy.network

import work.ranjit.batteryntfy.data.NtfyConfig
import work.ranjit.batteryntfy.data.SubscribedDeviceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class NtfySubscriber {

    /**
     * Poll latest messages from ntfy server for a specific subscribed topic
     */
    suspend fun fetchLatestDeviceState(
        config: NtfyConfig,
        subTopic: String
    ): SubscribedDeviceState? = withContext(Dispatchers.IO) {
        val cleanServer = config.serverUrl.trim().removeSuffix("/")
        val rawTopic = subTopic.trim()
        if (rawTopic.isBlank()) return@withContext null

        // Try both raw topic and PingMe prefixed topic
        val topicsToTry = if (!rawTopic.startsWith("work_ranjit_")) {
            listOf(rawTopic, "work_ranjit_$rawTopic")
        } else {
            listOf(rawTopic)
        }

        for (topicName in topicsToTry) {
            val targetUrl = "$cleanServer/$topicName/json?since=24h&poll=1"
            try {
                val url = URL(targetUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "BatteryNtfy/1.0 (Android)")
                    if (config.authToken.isNotBlank()) {
                        val auth = if (config.authToken.startsWith("Bearer ") || config.authToken.startsWith("Basic ")) {
                            config.authToken
                        } else {
                            "Bearer ${config.authToken}"
                        }
                        setRequestProperty("Authorization", auth)
                    }
                }

                if (connection.responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    var latestState: SubscribedDeviceState? = null
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                                try {
                                    val jsonObj = JSONObject(trimmed)
                                    val event = jsonObj.optString("event", "message")
                                    if (event == "message") {
                                        val title = jsonObj.optString("title", "")
                                        val message = jsonObj.optString("message", jsonObj.optString("text", ""))
                                        val tagsArr = jsonObj.optJSONArray("tags")
                                        val tagsList = mutableListOf<String>()
                                        if (tagsArr != null) {
                                            for (i in 0 until tagsArr.length()) {
                                                tagsList.add(tagsArr.optString(i))
                                            }
                                        }
                                        val msgTimeSec = jsonObj.optLong("time", 0)
                                        val msgTimestamp = if (msgTimeSec > 0) msgTimeSec * 1000L else System.currentTimeMillis()
                                        val state = SubscribedDeviceState.parseFromNtfyPayload(
                                            topic = rawTopic,
                                            title = title,
                                            message = message,
                                            tags = tagsList,
                                            timestamp = msgTimestamp
                                        )
                                        if (state != null) {
                                            if (latestState == null || state.lastUpdatedTimestamp >= latestState!!.lastUpdatedTimestamp) {
                                                latestState = state
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    connection.disconnect()
                    if (latestState != null) {
                        return@withContext latestState
                    }
                } else {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext null
    }

    /**
     * Poll a topic to check if an on-demand refresh request was recently published
     */
    suspend fun checkTopicForRefreshRequest(
        config: NtfyConfig,
        topic: String,
        sinceWindow: String = "1m"
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanServer = config.serverUrl.trim().removeSuffix("/")
        val rawTopic = topic.trim()
        if (rawTopic.isBlank()) return@withContext false

        val topicsToTry = if (!rawTopic.startsWith("work_ranjit_")) {
            listOf(rawTopic, "work_ranjit_$rawTopic")
        } else {
            listOf(rawTopic)
        }

        for (topicName in topicsToTry) {
            val targetUrl = "$cleanServer/$topicName/json?since=$sinceWindow&poll=1"
            try {
                val url = URL(targetUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "BatteryNtfy/1.0 (Android)")
                    if (config.authToken.isNotBlank()) {
                        val auth = if (config.authToken.startsWith("Bearer ") || config.authToken.startsWith("Basic ")) {
                            config.authToken
                        } else {
                            "Bearer ${config.authToken}"
                        }
                        setRequestProperty("Authorization", auth)
                    }
                }

                if (connection.responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    var foundRefresh = false
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                                try {
                                    val jsonObj = JSONObject(trimmed)
                                    val event = jsonObj.optString("event", "message")
                                    if (event == "message") {
                                        val title = jsonObj.optString("title", "")
                                        val message = jsonObj.optString("message", jsonObj.optString("text", ""))
                                        val tagsArr = jsonObj.optJSONArray("tags")
                                        val tagsList = mutableListOf<String>()
                                        if (tagsArr != null) {
                                            for (i in 0 until tagsArr.length()) {
                                                tagsList.add(tagsArr.optString(i))
                                            }
                                        }
                                        if (SubscribedDeviceState.isRefreshRequest(title, message, tagsList)) {
                                            foundRefresh = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    connection.disconnect()
                    if (foundRefresh) return@withContext true
                } else {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext false
    }

    /**
     * Streams incoming real-time notifications for subscribed topics via ntfy SSE/JSON line stream
     */
    suspend fun streamSubscribedTopics(
        config: NtfyConfig,
        topics: List<String>,
        onStateReceived: (SubscribedDeviceState) -> Unit,
        onRefreshRequested: ((topic: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (topics.isEmpty()) return@withContext
        val cleanServer = config.serverUrl.trim().removeSuffix("/")
        val joinedTopics = topics.joinToString(",") { it.trim() }
        val targetUrl = "$cleanServer/$joinedTopics/json"

        try {
            val url = URL(targetUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 0 // Continuous streaming
                setRequestProperty("User-Agent", "BatteryNtfy/1.0 (Android)")
                if (config.authToken.isNotBlank()) {
                    val auth = if (config.authToken.startsWith("Bearer ") || config.authToken.startsWith("Basic ")) {
                        config.authToken
                    } else {
                        "Bearer ${config.authToken}"
                    }
                    setRequestProperty("Authorization", auth)
                }
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        try {
                            val jsonObj = JSONObject(trimmed)
                            val event = jsonObj.optString("event", "message")
                            if (event == "message") {
                                val msgTopic = jsonObj.optString("topic", "")
                                val title = jsonObj.optString("title", "")
                                val message = jsonObj.optString("message", jsonObj.optString("text", ""))
                                val tagsArr = jsonObj.optJSONArray("tags")
                                val tagsList = mutableListOf<String>()
                                if (tagsArr != null) {
                                    for (i in 0 until tagsArr.length()) {
                                        tagsList.add(tagsArr.optString(i))
                                    }
                                }

                                if (SubscribedDeviceState.isRefreshRequest(title, message, tagsList)) {
                                    onRefreshRequested?.invoke(msgTopic.ifBlank { topics.first() })
                                } else {
                                    val state = SubscribedDeviceState.parseFromNtfyPayload(
                                        topic = msgTopic.ifBlank { topics.first() },
                                        title = title,
                                        message = message,
                                        tags = tagsList
                                    )
                                    if (state != null) {
                                        onStateReceived(state)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

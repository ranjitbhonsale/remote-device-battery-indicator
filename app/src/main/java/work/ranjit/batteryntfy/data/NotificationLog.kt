package work.ranjit.batteryntfy.data

data class NotificationLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // e.g. "Low Battery", "Power Connected", "Periodic Ping", "Test"
    val batteryPercent: Int,
    val title: String,
    val message: String,
    val isSuccess: Boolean,
    val responseCode: Int,
    val errorMessage: String = ""
)

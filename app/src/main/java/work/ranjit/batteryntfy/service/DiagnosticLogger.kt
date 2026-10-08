package work.ranjit.batteryntfy.service

import android.content.Context
import work.ranjit.batteryntfy.data.DiagnosticLog
import work.ranjit.batteryntfy.data.PreferencesRepository
import work.ranjit.batteryntfy.network.AppScriptPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object DiagnosticLogger {

    private val _logsFlow = MutableStateFlow<List<DiagnosticLog>>(emptyList())
    val logsFlow: StateFlow<List<DiagnosticLog>> = _logsFlow.asStateFlow()

    fun init(context: Context) {
        val repo = PreferencesRepository(context)
        _logsFlow.value = repo.getDiagnosticLogs()
    }

    fun log(
        context: Context,
        level: String, // "INFO", "SUCCESS", "WARN", "ERROR"
        category: String, // "SERVICE", "NETWORK", "SUBSCRIBER", "ALARM_WATCHDOG", "BATTERY", "PERMISSIONS"
        title: String,
        message: String,
        details: String = ""
    ) {
        val repo = PreferencesRepository(context)
        val config = repo.getConfig()
        val deviceName = config.deviceName

        val newLog = DiagnosticLog(
            level = level,
            category = category,
            title = title,
            message = message,
            details = details,
            deviceName = deviceName
        )

        repo.addDiagnosticLog(newLog)
        _logsFlow.value = repo.getDiagnosticLogs()

        // If Apps Script URL is configured and this is an ERROR or WARN event, auto-send diagnostic report
        if (config.appScriptUrl.isNotBlank() && (level == "ERROR" || level == "WARN")) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppScriptPublisher.sendDiagnosticReport(
                        context = context,
                        targetUrl = config.appScriptUrl,
                        customNote = "Auto Error Diagnostic Report: $title"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun clear(context: Context) {
        val repo = PreferencesRepository(context)
        repo.clearDiagnosticLogs()
        _logsFlow.value = emptyList()
    }
}

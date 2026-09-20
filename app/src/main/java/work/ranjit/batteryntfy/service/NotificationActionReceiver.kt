package work.ranjit.batteryntfy.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import work.ranjit.batteryntfy.data.PreferencesRepository

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationId != 0) {
            nm.cancel(notificationId)
        }

        when (intent.action) {
            ACTION_SNOOZE_ALERT -> {
                val durationMs = intent.getLongExtra(EXTRA_SNOOZE_DURATION_MS, 30 * 60 * 1000L) // Default 30 mins
                val repo = PreferencesRepository(context)
                val states = repo.getSubscribedDeviceStates().toMutableList()
                val index = states.indexOfFirst { it.topic.equals(topic, ignoreCase = true) }
                if (index >= 0) {
                    val updatedState = states[index].copy(
                        snoozedUntilTimestamp = System.currentTimeMillis() + durationMs
                    )
                    states[index] = updatedState
                    repo.saveSubscribedDeviceStates(states)
                    BatteryMonitorService.updateSubscribedStates(states)
                }
            }
            ACTION_DISMISS_ALERT -> {
                // Just dismiss notification
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_ALERT = "work.ranjit.batteryntfy.ACTION_SNOOZE_ALERT"
        const val ACTION_DISMISS_ALERT = "work.ranjit.batteryntfy.ACTION_DISMISS_ALERT"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_SNOOZE_DURATION_MS = "extra_snooze_duration_ms"
    }
}

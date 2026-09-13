package work.ranjit.batteryntfy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import work.ranjit.batteryntfy.data.PreferencesRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = PreferencesRepository(context)
        val config = repo.getConfig()

        val isBoot = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
                intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (isBoot && config.autoStartOnBoot) {
            repo.setServiceEnabled(true)
            BatteryMonitorService.start(context)
            AlarmReceiver.scheduleNextWatchdog(context)
        } else if (repo.isServiceEnabled()) {
            BatteryMonitorService.start(context)
            AlarmReceiver.scheduleNextWatchdog(context)
        }
    }
}

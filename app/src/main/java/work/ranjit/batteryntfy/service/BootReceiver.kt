package work.ranjit.batteryntfy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import work.ranjit.batteryntfy.data.PreferencesRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val repo = PreferencesRepository(context)
            val config = repo.getConfig()
            if (config.autoStartOnBoot && repo.isServiceEnabled()) {
                BatteryMonitorService.start(context)
            }
        }
    }
}

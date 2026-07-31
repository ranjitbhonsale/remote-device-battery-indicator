package work.ranjit.batteryntfy.data

import android.os.BatteryManager

data class BatteryInfo(
    val levelPercent: Int = 0,
    val isCharging: Boolean = false,
    val pluggedType: String = "Battery", // AC, USB, Wireless, Battery
    val health: String = "Unknown",
    val temperatureCelsius: Float = 0f,
    val voltageVolts: Float = 0f,
    val technology: String = "Li-ion",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getStatusSummary(): String {
        val chargeState = if (isCharging) "Charging ($pluggedType)" else "Discharging"
        return "$levelPercent% - $chargeState"
    }

    companion object {
        fun parseHealth(healthInt: Int): String {
            return when (healthInt) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }
        }

        fun parsePluggedType(pluggedInt: Int): String {
            return when (pluggedInt) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "Unplugged"
            }
        }
    }
}

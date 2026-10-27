package work.ranjit.batteryntfy.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import work.ranjit.batteryntfy.MainActivity
import work.ranjit.batteryntfy.data.PreferencesRepository
import work.ranjit.batteryntfy.service.BatteryMonitorService
import work.ranjit.batteryntfy.service.NotificationActionReceiver
import work.ranjit.batteryntfy.theme.BatteryNtfyTheme
import kotlinx.coroutines.delay

class LockScreenAlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isCloseReceiverRegistered = false

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val incomingId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1
            val currentId = this@LockScreenAlarmActivity.intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2000)
            if (incomingId == -1 || incomingId == currentId) {
                stopAlarm()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn on screen and show over Android keyguard/lock screen (without unlocking device)
        wakeUpAndUnlock()

        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Remote Device"
        val batteryPercent = intent.getIntExtra(EXTRA_BATTERY_PERCENT, 15)
        val isCharging = intent.getBooleanExtra(EXTRA_IS_CHARGING, false)
        val pluggedType = intent.getStringExtra(EXTRA_PLUGGED_TYPE) ?: ""
        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2000)
        val isLocal = intent.getBooleanExtra(EXTRA_IS_LOCAL, false)

        try {
            val filter = IntentFilter(NotificationActionReceiver.ACTION_CLOSE_ALARM_ACTIVITY)
            ContextCompat.registerReceiver(this, closeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            isCloseReceiverRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startAlarmSoundAndVibration()

        setContent {
            BatteryNtfyTheme(darkTheme = true) {
                LockScreenAlarmScreen(
                    deviceName = deviceName,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    pluggedType = pluggedType,
                    isLocal = isLocal,
                    onSnooze = { minutes ->
                        stopAlarm()
                        snoozeAlert(topic, notificationId, minutes)
                        finish()
                    },
                    onDismiss = {
                        stopAlarm()
                        dismissAlert(topic, notificationId)
                        finish()
                    },
                    onTimeout = {
                        stopAlarm()
                        finish()
                    },
                    onOpenApp = {
                        stopAlarm()
                        dismissAlert(topic, notificationId)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            keyguardManager?.requestDismissKeyguard(this@LockScreenAlarmActivity, null)
                        }
                        val appIntent = Intent(this@LockScreenAlarmActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(appIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun wakeUpAndUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startAlarmSoundAndVibration() {
        // If DND or Silent mode is active on device, do NOT play sound or vibrate
        if (BatteryMonitorService.isDndOrSilentActive(this)) {
            return
        }

        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        // 1. Play mild single notification chime non-blockingly (not an alarm, not looping)
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (notificationUri != null) {
                    ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)?.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            audioAttributes = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        }
                        play()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Play gentle single vibration (two soft taps, no looping)
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 120, 80, 120)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) // -1 = play once
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun snoozeAlert(topic: String, notificationId: Int, minutes: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)

        if (topic.isNotBlank()) {
            val durationMs = minutes * 60 * 1000L
            val repo = PreferencesRepository(this)
            val states = repo.getSubscribedDeviceStates().toMutableList()
            val index = states.indexOfFirst { it.topic.equals(topic, ignoreCase = true) }
            if (index >= 0) {
                val updatedState = states[index].copy(
                    snoozedUntilTimestamp = System.currentTimeMillis() + durationMs,
                    isLowBatteryDismissed = false
                )
                states[index] = updatedState
                repo.saveSubscribedDeviceStates(states)
                BatteryMonitorService.updateSubscribedStates(states)
            }
        }
    }

    private fun dismissAlert(topic: String, notificationId: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)

        if (topic.isNotBlank()) {
            val repo = PreferencesRepository(this)
            val states = repo.getSubscribedDeviceStates().toMutableList()
            val index = states.indexOfFirst { it.topic.equals(topic, ignoreCase = true) }
            if (index >= 0) {
                val updatedState = states[index].copy(
                    isLowBatteryDismissed = true,
                    snoozedUntilTimestamp = 0L
                )
                states[index] = updatedState
                repo.saveSubscribedDeviceStates(states)
                BatteryMonitorService.updateSubscribedStates(states)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isCloseReceiverRegistered) {
            try {
                unregisterReceiver(closeReceiver)
                isCloseReceiverRegistered = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        stopAlarm()
    }

    companion object {
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_BATTERY_PERCENT = "extra_battery_percent"
        const val EXTRA_IS_CHARGING = "extra_is_charging"
        const val EXTRA_PLUGGED_TYPE = "extra_plugged_type"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_LOCAL = "extra_is_local"
    }
}

@Composable
fun LockScreenAlarmScreen(
    deviceName: String,
    batteryPercent: Int,
    isCharging: Boolean,
    pluggedType: String,
    isLocal: Boolean,
    onSnooze: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
    onTimeout: () -> Unit,
    onOpenApp: () -> Unit
) {
    // Auto-timeout safeguard after 20 seconds of display
    LaunchedEffect(Unit) {
        delay(20_000L)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD90B1120)) // Calm dark translucent slate backdrop (no red tones)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E293B), // Modern dark slate
            border = BorderStroke(1.dp, Color(0xFF334155)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(22.dp)
            ) {
                // Calm, simple battery icon badge (no pulsing, no red!)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF0F172A), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8), // Calming light sky blue
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isLocal) "BATTERY NOTICE" else "REMOTE BATTERY NOTICE",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = deviceName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                // Battery percentage & charging info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$batteryPercent%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8) // Soft sky blue
                    )
                    Text(
                        text = if (isCharging) "Charging ($pluggedType)" else "Discharging",
                        color = if (isCharging) Color(0xFF34D399) else Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isLocal) "Please connect charger soon" else "Consider plugging in $deviceName",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Action Buttons (small, simple, modern)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Snooze row (compact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSnooze(30) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Snooze 30m", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = { onSnooze(60) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Snooze 1h", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Primary Dismiss button (calm modern blue, NO RED!)
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Dismiss", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    // Open App (subtle link)
                    TextButton(
                        onClick = onOpenApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Open BatteryNtfy App", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

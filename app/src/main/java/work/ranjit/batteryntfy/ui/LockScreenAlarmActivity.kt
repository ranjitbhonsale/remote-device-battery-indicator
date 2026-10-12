package work.ranjit.batteryntfy.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
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
import work.ranjit.batteryntfy.MainActivity
import work.ranjit.batteryntfy.data.PreferencesRepository
import work.ranjit.batteryntfy.service.BatteryMonitorService
import work.ranjit.batteryntfy.theme.BatteryNtfyTheme
import kotlinx.coroutines.delay

class LockScreenAlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn on screen and show over Android keyguard/lock screen
        wakeUpAndUnlock()

        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Remote Device"
        val batteryPercent = intent.getIntExtra(EXTRA_BATTERY_PERCENT, 15)
        val isCharging = intent.getBooleanExtra(EXTRA_IS_CHARGING, false)
        val pluggedType = intent.getStringExtra(EXTRA_PLUGGED_TYPE) ?: ""
        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2000)
        val isLocal = intent.getBooleanExtra(EXTRA_IS_LOCAL, false)

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
                        dismissAlert(notificationId)
                        finish()
                    },
                    onOpenApp = {
                        stopAlarm()
                        dismissAlert(notificationId)
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
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startAlarmSoundAndVibration() {
        // 1. Play continuous alarm ringtone audio
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Play continuous pulsing vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 600, 300, 600, 300, 600, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = loop
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
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
                    snoozedUntilTimestamp = System.currentTimeMillis() + durationMs
                )
                states[index] = updatedState
                repo.saveSubscribedDeviceStates(states)
                BatteryMonitorService.updateSubscribedStates(states)
            }
        }
    }

    private fun dismissAlert(notificationId: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
    }

    override fun onDestroy() {
        super.onDestroy()
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
    onOpenApp: () -> Unit
) {
    // Pulse animation for critical alarm badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Auto-timeout safeguard after 45 seconds of continuous ringing
    LaunchedEffect(Unit) {
        delay(45_000L)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E0A0A),
                        Color(0xFF0F0505),
                        Color(0xFF000000)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Flashing Alarm Badge Icon
            Box(
                modifier = Modifier
                    .scale(pulseScale)
                    .size(96.dp)
                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFEF4444), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            // Headline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isLocal) "🚨 LOCAL LOW BATTERY" else "🚨 REMOTE LOW BATTERY ALARM",
                    color = Color(0xFFF87171),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = deviceName,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }

            // Battery Percentage Display Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF261212),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFDC2626)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$batteryPercent%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFEF4444)
                    )
                    Text(
                        text = if (isCharging) "Charging via $pluggedType" else "Discharging • Critical Level",
                        color = if (isCharging) Color(0xFF34D399) else Color(0xFFFCA5A5),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isLocal) "Plug in this device immediately" else "Connect charger to $deviceName now",
                        color = Color(0xFF9CA3AF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Snooze 30 mins
                Button(
                    onClick = { onSnooze(30) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Snooze Alarm for 30 Min", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // Snooze 1 hour
                OutlinedButton(
                    onClick = { onSnooze(60) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Snooze for 1 Hour", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // Dismiss
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dismiss Alarm", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }

                // Open App
                TextButton(
                    onClick = onOpenApp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open BatteryNtfy App", color = Color(0xFF9CA3AF), fontSize = 14.sp)
                }
            }
        }
    }
}

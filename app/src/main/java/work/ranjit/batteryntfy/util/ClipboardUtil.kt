package work.ranjit.batteryntfy.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.ClipboardManager as ComposeClipboardManager
import androidx.compose.ui.text.AnnotatedString

object ClipboardUtil {

    /**
     * Copies text to the Android clipboard using both the Jetpack Compose
     * ClipboardManager and the Android System ClipboardManager to ensure
     * complete reliability across all Android versions and OEM devices.
     */
    fun copyToClipboard(
        context: Context,
        composeClipboard: ComposeClipboardManager? = null,
        label: String = "BatteryNtfy",
        text: String,
        toastMessage: String? = "Copied to clipboard!"
    ) {
        if (text.isBlank()) return

        // 1. Set via Jetpack Compose ClipboardManager (handles Compose window focus)
        try {
            composeClipboard?.setText(AnnotatedString(text))
        } catch (_: Throwable) {
            // Safe fallback
        }

        // 2. Set via Android System ClipboardManager
        try {
            val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            systemClipboard?.setPrimaryClip(clip)
        } catch (_: Throwable) {
            // Safe fallback
        }

        // 3. User feedback
        if (!toastMessage.isNullOrBlank()) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

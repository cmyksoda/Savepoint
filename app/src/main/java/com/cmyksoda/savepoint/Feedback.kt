package com.cmyksoda.savepoint

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

@Composable
fun rememberActionFeedback(): (Boolean) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { strong ->
            // CLOCK_TICK is Android's faintest texture tick, which many phones barely play; this is the keyboard's click.
            if (!strong) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            else if (
                view.isHapticFeedbackEnabled &&
                    Settings.System.getInt(
                        view.context.contentResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        1,
                    ) != 0
            ) {
                val vibrator =
                    if (Build.VERSION.SDK_INT >= 31)
                        view.context.getSystemService(VibratorManager::class.java).defaultVibrator
                    else view.context.getSystemService(Vibrator::class.java)
                val effect = VibrationEffect.createOneShot(35, 255)
                if (Build.VERSION.SDK_INT >= 33)
                    vibrator.vibrate(
                        effect,
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
                    )
                else
                    vibrator.vibrate(
                        effect,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build(),
                    )
            }
        }
    }
}

/** [clickable] that answers with the same light click as every Savepoint button. */
fun Modifier.feedbackClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier =
    composed {
        val feedback = rememberActionFeedback()
        clickable(enabled = enabled, role = role) {
            feedback(false)
            onClick()
        }
    }

/** [toggleable] that answers with the same light click as every Savepoint button. */
fun Modifier.feedbackToggleable(
    value: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    onValueChange: (Boolean) -> Unit,
): Modifier = composed {
    val feedback = rememberActionFeedback()
    toggleable(value, enabled = enabled, role = role) {
        feedback(false)
        onValueChange(it)
    }
}

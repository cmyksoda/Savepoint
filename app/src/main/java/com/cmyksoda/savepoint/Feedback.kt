package com.cmyksoda.savepoint

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
fun rememberActionFeedback(): (Boolean) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { strong ->
            if (
                view.isHapticFeedbackEnabled &&
                    Settings.System.getInt(
                        view.context.contentResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        1,
                    ) != 0
            ) {
                if (!strong) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                else {
                    val vibrator =
                        if (Build.VERSION.SDK_INT >= 31)
                            view.context
                                .getSystemService(VibratorManager::class.java)
                                .defaultVibrator
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
}

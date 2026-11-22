package com.mi.onextbox.ui.common

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

@Composable
fun rememberHapticClick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

internal object SwitchHaptics {
    const val Key = "app_switch_haptics"
    fun preferences(context: Context) = context.getSharedPreferences("onextbox_prefs", Context.MODE_PRIVATE)
    fun enabled(context: Context) = preferences(context).getBoolean(Key, true)
}

@Composable
internal fun rememberHapticToggle(onCheckedChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val view = LocalView.current
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    return remember(view) {
        { checked ->
            currentOnChange(checked)
            if (SwitchHaptics.enabled(view.context)) {
                val effect = if (checked) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
                view.performHapticFeedback(effect)
            }
        }
    }
}

@Composable
fun rememberColorOsHapticTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

@Composable
fun rememberHapticLongPress(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

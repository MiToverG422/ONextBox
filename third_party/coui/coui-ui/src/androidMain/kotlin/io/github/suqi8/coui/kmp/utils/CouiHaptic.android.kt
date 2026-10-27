// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.utils

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

// OPLUS/ColorOS linear-motor feedback constants observed in the real COUI widgets
// (com.coui.appcompat.*). View.performHapticFeedback accepts arbitrary ints; ColorOS framework
// maps these to dedicated waveforms, while AOSP builds ignore unknown constants (return false).
private const val OPLUS_HAPTIC_SWITCH = 302
private const val OPLUS_HAPTIC_STRENGTH = 308

// AOSP fallbacks (HapticFeedbackConstants) for non-ColorOS devices.
private const val AOSP_CONTEXT_CLICK = 6 // HapticFeedbackConstants.CONTEXT_CLICK
private const val AOSP_CLOCK_TICK = 4 // HapticFeedbackConstants.CLOCK_TICK

@Composable
actual fun rememberCouiHaptic(): (CouiHapticEffect) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { effect: CouiHapticEffect ->
            val (oplus, fallback) = when (effect) {
                CouiHapticEffect.Switch -> OPLUS_HAPTIC_SWITCH to AOSP_CONTEXT_CLICK
                CouiHapticEffect.Strength -> OPLUS_HAPTIC_STRENGTH to AOSP_CLOCK_TICK
            }
            // Try the OPLUS constant first; if the device doesn't recognise it, fall back to AOSP.
            if (!view.performHapticFeedback(oplus)) {
                view.performHapticFeedback(fallback)
            }
        }
    }
}

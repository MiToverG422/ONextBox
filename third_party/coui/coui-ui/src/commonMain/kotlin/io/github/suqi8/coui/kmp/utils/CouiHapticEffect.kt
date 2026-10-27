// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.utils

import androidx.compose.runtime.Composable

/**
 * A COUI (ColorOS) haptic effect, mapped to OPLUS linear-motor feedback constants on Android and to
 * the closest standard haptic on every other platform.
 *
 * The numeric values mirror the OEM `performHapticFeedback` constants used by the real COUI widgets:
 * - [Switch]: tap/confirm on toggle (302)
 * - [Strength]: a discrete step/tick used by pickers and steppers (308)
 */
enum class CouiHapticEffect {
    /** Switch / button / checkbox toggle confirm (OPLUS 302). */
    Switch,

    /** Stepper / picker discrete tick (OPLUS 308). */
    Strength,
}

/**
 * Performs a [CouiHapticEffect]. On OPLUS/ColorOS devices this invokes the matching linear-motor
 * waveform through `View.performHapticFeedback`; on other Android builds and on non-Android targets
 * it falls back to the closest standard Compose haptic.
 *
 * Returns a callback so the effect can be triggered from outside the composition (e.g. inside an
 * animation or gesture callback) without re-resolving platform handles each time.
 */
@Composable
expect fun rememberCouiHaptic(): (CouiHapticEffect) -> Unit

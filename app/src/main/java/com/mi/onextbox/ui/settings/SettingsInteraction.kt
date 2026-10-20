package com.mi.onextbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * `enabled` describes availability and may change colors. `interactionEnabled` is a temporary
 * write lock: block repeated input without fading text, recoloring controls or replacing content.
 * Popup callbacks must check the same flag because their window is outside this modifier.
 */
@Composable
internal fun Modifier.settingsInteractionGate(interactionEnabled: Boolean): Modifier {
    val currentEnabled by rememberUpdatedState(interactionEnabled)
    // Keep the modifier nodes attached while saving, including during an in-flight gesture.
    return this
        .semantics { if (!interactionEnabled) disabled() }
        .onPreviewKeyEvent { event -> !currentEnabled && event.key != Key.Back && event.key != Key.Escape }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (!currentEnabled) event.changes.forEach { it.consume() }
                }
            }
        }
}

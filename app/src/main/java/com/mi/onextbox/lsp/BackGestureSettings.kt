package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object BackGestureSettings {
    const val AOSP = "systemui_aosp_back_panel"
    const val STYLE = "systemui_back_gesture_style"
    const val HAPTICS = "systemui_back_gesture_haptics"
    const val RELEASE = "systemui_back_gesture_release_haptic"
    const val PREDICTIVE = "systemui_back_gesture_predictive_style"
    const val AREA = "systemui_back_gesture_custom_area"
    const val LEFT_HEIGHT = "systemui_back_gesture_left_height"
    const val LEFT_POSITION = "systemui_back_gesture_left_position"
    const val RIGHT_HEIGHT = "systemui_back_gesture_right_height"
    const val RIGHT_POSITION = "systemui_back_gesture_right_position"
    val booleans = setOf(AOSP, RELEASE, AREA)
    val integers = mapOf(STYLE to 1, HAPTICS to -1, PREDICTIVE to 0,
        LEFT_HEIGHT to 100, LEFT_POSITION to 50, RIGHT_HEIGHT to 100, RIGHT_POSITION to 50)
    val keys = booleans + integers.keys

    data class State(val aosp: Boolean = false, val haptics: Int = -1,
        val release: Boolean = false, val predictive: Int = 0, val area: Boolean = false,
        val leftHeight: Int = 100, val leftPosition: Int = 50, val rightHeight: Int = 100, val rightPosition: Int = 50) {
        val selectedStyle: Int get() = if (aosp) 0 else 1
        val selectedHaptics: Int get() = if (haptics in 0..1) haptics else if (aosp) 0 else 1
        val active: Boolean get() = aosp || haptics in 0..1 || release || predictive == 1 || area
    }

    fun state(context: Context): State {
        val prefs = LspPreferenceStore.prefs(context)
        return State(LspConfig.isAospBackPanelEnabled(context),
            prefs.getInt(HAPTICS, -1), prefs.getBoolean(RELEASE, false), prefs.getInt(PREDICTIVE, 0).coerceIn(0, 1),
            prefs.getBoolean(AREA, false), prefs.getInt(LEFT_HEIGHT, 100), prefs.getInt(LEFT_POSITION, 50),
            prefs.getInt(RIGHT_HEIGHT, 100), prefs.getInt(RIGHT_POSITION, 50))
    }

    fun xposed(): State = State(LspConfig.isAospBackPanelEnabledXposed(),
        HookConfigSnapshot.int(HAPTICS, -1) ?: -1,
        HookConfigSnapshot.boolean(RELEASE, false) == true, (HookConfigSnapshot.int(PREDICTIVE, 0) ?: 0).coerceIn(0, 1),
        HookConfigSnapshot.boolean(AREA, false) == true, HookConfigSnapshot.int(LEFT_HEIGHT, 100) ?: 100,
        HookConfigSnapshot.int(LEFT_POSITION, 50) ?: 50, HookConfigSnapshot.int(RIGHT_HEIGHT, 100) ?: 100,
        HookConfigSnapshot.int(RIGHT_POSITION, 50) ?: 50)

    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in booleans)
        if (key == AOSP) LspConfig.setAospBackPanelEnabled(context, enabled)
        else ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }

    fun set(context: Context, key: String, value: Int) {
        require(key in integers)
        require(when (key) { STYLE, PREDICTIVE -> value in 0..1; HAPTICS -> value in -1..1; else -> value in 0..100 })
        ModernXposedPreferenceSync.setIntAndSync(context, key, value)
    }

    fun reset(context: Context) {
        (booleans - AOSP).forEach { set(context, it, false) }
        integers.forEach { (key, value) -> set(context, key, value) }
    }
}

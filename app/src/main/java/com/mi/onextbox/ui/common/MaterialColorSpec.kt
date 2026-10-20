package com.mi.onextbox.ui.common

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class MaterialColorSpec(val year: Int) {
    SPEC_2026(2026), SPEC_2025(2025), SPEC_2021(2021);

    companion object {
        fun fromYear(year: Int): MaterialColorSpec = entries.firstOrNull { it.year == year } ?: SPEC_2026
    }
}

private const val MATERIAL_SPEC_KEY = "app_md3e_color_spec"

private fun SharedPreferences.materialColorSpec(): MaterialColorSpec =
    MaterialColorSpec.fromYear((all[MATERIAL_SPEC_KEY] as? Int) ?: 2026)

fun setMaterialColorSpec(context: Context, spec: MaterialColorSpec) {
    context.getSharedPreferences("onextbox_prefs", Context.MODE_PRIVATE)
        .edit().putInt(MATERIAL_SPEC_KEY, spec.year).apply()
}

/** Observe the persisted choice in both the theme provider and the settings preview. */
@Composable
fun rememberMaterialColorSpec(): MaterialColorSpec {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("onextbox_prefs", Context.MODE_PRIVATE) }
    var spec by remember(prefs) { mutableStateOf(prefs.materialColorSpec()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == MATERIAL_SPEC_KEY) spec = prefs.materialColorSpec()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        spec = prefs.materialColorSpec()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return spec
}

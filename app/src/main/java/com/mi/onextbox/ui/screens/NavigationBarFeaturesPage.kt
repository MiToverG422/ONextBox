package com.mi.onextbox.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.ImmersiveNavigationRules
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun NavigationBarFeaturesPage() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    var immersiveEnabled by remember(context) { mutableStateOf(LspConfig.isImmersiveNavigationEnabled(context)) }
    var autoHideEnabled by remember(context) { mutableStateOf(LspConfig.isNavigationHandleAutoHideEnabled(context)) }
    var customHandleEnabled by remember(context) {
        mutableStateOf(LspConfig.isNavigationHandleCustomLengthEnabled(context))
    }
    var lengthDp by remember(context) { mutableStateOf(LspConfig.getNavigationHandleLengthDp(context)) }
    var opacity by remember(context) { mutableStateOf(LspConfig.getNavigationHandleOpacity(context)) }
    val configuration = LocalConfiguration.current
    val defaultLengthDp = remember(context, configuration) { systemHandleLengthDp(context) }
    val lengthPreference = ImmersiveNavigationRules.lengthPreferenceFromSlider(lengthDp, defaultLengthDp)
    val opacityPreference = ImmersiveNavigationRules.opacityPreferenceFromSlider(opacity)
    val displayedLength = if (lengthPreference == ImmersiveNavigationRules.SYSTEM_DEFAULT) {
        defaultLengthDp ?: ImmersiveNavigationRules.DEFAULT_LENGTH_DP
    } else {
        lengthDp
    }
    fun saveLength(valueDp: Int) {
        scope.launch {
            writeLock.withLock {
                withContext(Dispatchers.IO) { LspConfig.setNavigationHandleLengthDp(context, valueDp) }
            }
        }
    }
    fun saveOpacity(value: Int) {
        scope.launch {
            writeLock.withLock {
                withContext(Dispatchers.IO) { LspConfig.setNavigationHandleOpacity(context, value) }
            }
        }
    }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                immersiveEnabled = LspConfig.isImmersiveNavigationEnabled(context)
                autoHideEnabled = LspConfig.isNavigationHandleAutoHideEnabled(context)
                customHandleEnabled = LspConfig.isNavigationHandleCustomLengthEnabled(context)
                lengthDp = LspConfig.getNavigationHandleLengthDp(context)
                opacity = LspConfig.getNavigationHandleOpacity(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_navigation_handle_length_title),
            summary = "",
            checked = customHandleEnabled,
            hasDividerBelow = true,
            onCheckedChange = { checked ->
                customHandleEnabled = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) {
                            LspConfig.setNavigationHandleCustomLengthEnabled(context, checked)
                        }
                    }
                }
            },
        )
        SettingsDivider()
        FeatureSliderRow(
            heading = stringResource(R.string.feature_navigation_handle_length_label),
            currentValue = if (lengthPreference == ImmersiveNavigationRules.SYSTEM_DEFAULT) {
                stringResource(R.string.feature_slider_current_default)
            } else {
                stringResource(R.string.feature_slider_current_dp, displayedLength)
            },
            resetDescription = stringResource(R.string.feature_navigation_handle_length_reset),
            value = displayedLength.toFloat(),
            valueRange = ImmersiveNavigationRules.MIN_LENGTH_DP.toFloat()..ImmersiveNavigationRules.MAX_LENGTH_DP.toFloat(),
            steps = ImmersiveNavigationRules.MAX_LENGTH_DP - ImmersiveNavigationRules.MIN_LENGTH_DP - 1,
            enabled = customHandleEnabled,
            hasDividerBelow = true,
            onValueChange = {
                lengthDp = ImmersiveNavigationRules.lengthPreferenceFromSlider(it.roundToInt(), defaultLengthDp)
            },
            onValueChangeFinished = {
                saveLength(ImmersiveNavigationRules.lengthPreferenceFromSlider(lengthDp, defaultLengthDp))
            },
            onReset = {
                lengthDp = ImmersiveNavigationRules.SYSTEM_DEFAULT
                saveLength(lengthDp)
            },
        )
        SettingsDivider()
        FeatureSliderRow(
            heading = stringResource(R.string.feature_navigation_handle_opacity_label),
            currentValue = if (opacityPreference == ImmersiveNavigationRules.SYSTEM_DEFAULT) {
                stringResource(R.string.feature_slider_current_default)
            } else {
                stringResource(R.string.feature_navigation_handle_current_opacity, opacity)
            },
            resetDescription = stringResource(R.string.feature_navigation_handle_opacity_reset),
            value = if (opacityPreference == ImmersiveNavigationRules.SYSTEM_DEFAULT) 100f else opacity.toFloat(),
            valueRange = ImmersiveNavigationRules.MIN_OPACITY_PERCENT.toFloat()..ImmersiveNavigationRules.MAX_OPACITY_PERCENT.toFloat(),
            steps = ImmersiveNavigationRules.MAX_OPACITY_PERCENT - ImmersiveNavigationRules.MIN_OPACITY_PERCENT - 1,
            enabled = customHandleEnabled,
            hasDividerBelow = false,
            onValueChange = { opacity = ImmersiveNavigationRules.opacityPreferenceFromSlider(it.roundToInt()) },
            onValueChangeFinished = {
                saveOpacity(ImmersiveNavigationRules.opacityPreferenceFromSlider(opacity))
            },
            onReset = {
                opacity = ImmersiveNavigationRules.SYSTEM_DEFAULT
                saveOpacity(opacity)
            },
        )
    }
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_immersive_navigation_title),
            summary = stringResource(R.string.feature_immersive_navigation_summary),
            checked = immersiveEnabled,
            enabled = customHandleEnabled,
            hasDividerBelow = true,
            onCheckedChange = { checked ->
                if (customHandleEnabled) {
                    immersiveEnabled = checked
                    scope.launch {
                        writeLock.withLock {
                            withContext(Dispatchers.IO) {
                                LspConfig.setImmersiveNavigationEnabled(context, checked)
                            }
                        }
                    }
                }
            },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_navigation_handle_auto_hide_title),
            summary = stringResource(R.string.feature_navigation_handle_auto_hide_summary),
            checked = autoHideEnabled,
            enabled = customHandleEnabled,
            hasDividerAbove = true,
            onCheckedChange = { checked ->
                if (customHandleEnabled) {
                    autoHideEnabled = checked
                    scope.launch {
                        writeLock.withLock {
                            withContext(Dispatchers.IO) {
                                LspConfig.setNavigationHandleAutoHideEnabled(context, checked)
                            }
                        }
                    }
                }
            },
        )
    }
}


@SuppressLint("DiscouragedApi")
private fun systemHandleLengthDp(context: Context): Int? = runCatching {
    val resources = context.packageManager.getResourcesForApplication("com.android.systemui")
    val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val oplusName = if (landscape) "navigation_gesture_view_landscape_width" else "navigation_gesture_view_width"
    val id = resources.getIdentifier(oplusName, "dimen", "oplus").takeIf { it != 0 }
        ?: resources.getIdentifier("navigation_home_handle_width", "dimen", "com.android.systemui")
    if (id == 0) return@runCatching null
    val density = resources.displayMetrics.density
    if (density <= 0f) return@runCatching null
    (resources.getDimension(id) / density).takeIf { it.isFinite() && it > 0f }?.roundToInt()
}.getOrNull()

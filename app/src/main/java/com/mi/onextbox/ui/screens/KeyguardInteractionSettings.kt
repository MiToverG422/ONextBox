package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspConfig.KeyguardFeature
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun KeyguardInteractionSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val writeLock = remember { Mutex() }
    val feature = KeyguardFeature.FaceTapUnlock
    var enabled by remember { mutableStateOf(LspConfig.isKeyguardFeatureEnabled(context, feature)) }
    var animationEnabled by remember {
        mutableStateOf(LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.FaceTapAnimation))
    }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = LspConfig.isKeyguardFeatureEnabled(context, feature)
                animationEnabled = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.FaceTapAnimation)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsSection(stringResource(R.string.keyguard_interaction_group))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.keyguard_face_tap_title),
            summary = stringResource(R.string.keyguard_face_tap_summary),
            checked = enabled,
            hasDividerBelow = true,
            onCheckedChange = { checked ->
                enabled = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) {
                            LspConfig.setKeyguardFeatureEnabled(context, feature, checked)
                        }
                    }
                }
            },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.keyguard_face_tap_animation_title),
            summary = stringResource(R.string.keyguard_face_tap_animation_summary),
            checked = animationEnabled,
            enabled = enabled,
            hasDividerAbove = true,
            onCheckedChange = { checked ->
                animationEnabled = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) {
                            LspConfig.setKeyguardFeatureEnabled(context, KeyguardFeature.FaceTapAnimation, checked)
                        }
                    }
                }
            },
        )
    }
}

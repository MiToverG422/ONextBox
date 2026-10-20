package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspConfig.KeyguardFeature
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
    var highlightBlocked by remember {
        mutableStateOf(LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.BlockPasswordHighlight))
    }
    var swipePinEnabled by remember {
        mutableStateOf(LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.SwipePinInput))
    }
    var savingSwipePin by remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = LspConfig.isKeyguardFeatureEnabled(context, feature)
                animationEnabled = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.FaceTapAnimation)
                highlightBlocked = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.BlockPasswordHighlight)
                swipePinEnabled = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.SwipePinInput)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsSection(stringResource(R.string.keyguard_interaction_group))
    FeatureAnimatedGroup {
        row(0) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.keyguard_face_tap_title),
                summary = stringResource(R.string.keyguard_face_tap_summary),
                checked = enabled,
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
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(1, visible = enabled) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.keyguard_face_tap_animation_title),
                summary = stringResource(R.string.keyguard_face_tap_animation_summary),
                checked = animationEnabled,
                enabled = enabled,
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
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row("swipe-pin") { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.keyguard_swipe_pin_title),
                summary = stringResource(R.string.keyguard_swipe_pin_summary),
                checked = swipePinEnabled,
                interactionEnabled = !savingSwipePin,
                onCheckedChange = { checked ->
                    if (!savingSwipePin) {
                        savingSwipePin = true
                        swipePinEnabled = checked
                        val pending = com.mi.onextbox.ui.settings.FeatureSettingWriteQueue.application.enqueue {
                            LspConfig.setKeyguardFeatureEnabled(context, KeyguardFeature.SwipePinInput, checked)
                        }
                        scope.launch {
                            try { pending.await() }
                            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                            catch (_: Exception) {
                                android.widget.Toast.makeText(context, R.string.ota_save_failed, android.widget.Toast.LENGTH_LONG).show()
                            } finally {
                                swipePinEnabled = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.SwipePinInput)
                                savingSwipePin = false
                            }
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(2) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.keyguard_block_password_highlight_title),
                summary = stringResource(R.string.keyguard_block_password_highlight_summary),
                checked = highlightBlocked,
                onCheckedChange = { checked ->
                    highlightBlocked = checked
                    scope.launch {
                        writeLock.withLock {
                            withContext(Dispatchers.IO) {
                                LspConfig.setKeyguardFeatureEnabled(
                                    context, KeyguardFeature.BlockPasswordHighlight, checked,
                                )
                            }
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
    }
}

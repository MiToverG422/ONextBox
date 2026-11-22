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
private fun KeyguardDisplaySwitch(
    feature: KeyguardFeature, title: Int, above: Boolean, below: Boolean, summary: String = "",
) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val prefs = remember(app) { com.mi.onextbox.lsp.LspPreferenceStore.prefs(app) }
    var enabled by remember(feature) { mutableStateOf(LspConfig.isKeyguardFeatureEnabled(app, feature)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(prefs, feature) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == feature.key) enabled = LspConfig.isKeyguardFeatureEnabled(app, feature)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        enabled = LspConfig.isKeyguardFeatureEnabled(app, feature)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    SettingsToggleRow(title = stringResource(title), summary = summary, checked = enabled, interactionEnabled = !saving,
        hasDividerAbove = above, hasDividerBelow = below, onCheckedChange = { checked ->
            saving = true
            enabled = checked
            val pending = com.mi.onextbox.ui.settings.FeatureSettingWriteQueue.application.enqueue {
                LspConfig.setKeyguardFeatureEnabled(app, feature, checked)
            }
            scope.launch {
                try { pending.await() }
                catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) {
                    android.widget.Toast.makeText(app, R.string.ota_save_failed, android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    enabled = LspConfig.isKeyguardFeatureEnabled(app, feature)
                    saving = false
                }
            }
        })
}

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
    var shufflePinEnabled by remember {
        mutableStateOf(LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.ShufflePinKeys))
    }
    var savingShufflePin by remember { mutableStateOf(false) }
    var emergencyHidden by remember {
        mutableStateOf(LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.HidePasswordEmergency))
    }
    var savingEmergency by remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = LspConfig.isKeyguardFeatureEnabled(context, feature)
                animationEnabled = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.FaceTapAnimation)
                highlightBlocked = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.BlockPasswordHighlight)
                swipePinEnabled = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.SwipePinInput)
                shufflePinEnabled = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.ShufflePinKeys)
                emergencyHidden = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.HidePasswordEmergency)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsSection(stringResource(R.string.keyguard_display_group))
    FeatureAnimatedGroup {
        row("face-auth-capsule") { above, below ->
            KeyguardDisplaySwitch(KeyguardFeature.FaceAuthCapsule, R.string.keyguard_face_capsule_title,
                above, below, stringResource(R.string.keyguard_face_capsule_summary))
        }
        row(KeyguardFeature.GestureHandle.key) { above, below ->
            KeyguardDisplaySwitch(KeyguardFeature.GestureHandle, R.string.keyguard_gesture_handle_title, above, below)
        }
    }

    SettingsSection(stringResource(R.string.keyguard_fingerprint_group))
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
    }

    SettingsSection(stringResource(R.string.keyguard_password_group))
    FeatureAnimatedGroup {
        row("shuffle-pin") { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.keyguard_shuffle_pin_title),
                summary = stringResource(R.string.keyguard_shuffle_pin_summary),
                checked = shufflePinEnabled,
                interactionEnabled = !savingShufflePin,
                onCheckedChange = { checked ->
                    if (!savingShufflePin) {
                        savingShufflePin = true
                        shufflePinEnabled = checked
                        val pending = com.mi.onextbox.ui.settings.FeatureSettingWriteQueue.application.enqueue {
                            LspConfig.setKeyguardFeatureEnabled(context, KeyguardFeature.ShufflePinKeys, checked)
                        }
                        scope.launch {
                            try { pending.await() }
                            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                            catch (_: Exception) {
                                android.widget.Toast.makeText(context, R.string.ota_save_failed, android.widget.Toast.LENGTH_LONG).show()
                            } finally {
                                shufflePinEnabled = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.ShufflePinKeys)
                                savingShufflePin = false
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
        row("hide-password-emergency") { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.keyguard_hide_emergency_title),
                summary = stringResource(R.string.keyguard_hide_emergency_summary),
                checked = emergencyHidden,
                interactionEnabled = !savingEmergency,
                onCheckedChange = { checked ->
                    if (!savingEmergency) {
                        savingEmergency = true
                        emergencyHidden = checked
                        val pending = com.mi.onextbox.ui.settings.FeatureSettingWriteQueue.application.enqueue {
                            LspConfig.setKeyguardFeatureEnabled(context, KeyguardFeature.HidePasswordEmergency, checked)
                        }
                        scope.launch {
                            try { pending.await() }
                            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                            catch (_: Exception) {
                                android.widget.Toast.makeText(context, R.string.ota_save_failed, android.widget.Toast.LENGTH_LONG).show()
                            } finally {
                                emergencyHidden = LspConfig.isKeyguardFeatureEnabled(context, KeyguardFeature.HidePasswordEmergency)
                                savingEmergency = false
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

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
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun KeyboardFeaturesPage() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    var enabled by remember(context) { mutableStateOf(LspConfig.isKeyboardMaterialUnlocked(context)) }
    var flagEmojiEnabled by remember(context) { mutableStateOf(LspConfig.isKeyboardFlagEmojiEnabled(context)) }
    var bottomLiftEnabled by remember(context) { mutableStateOf(LspConfig.isKeyboardBottomLiftFixEnabled(context)) }
    var dismissHidden by remember(context) { mutableStateOf(LspConfig.isKeyboardDismissHidden(context)) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = LspConfig.isKeyboardMaterialUnlocked(context)
                flagEmojiEnabled = LspConfig.isKeyboardFlagEmojiEnabled(context)
                bottomLiftEnabled = LspConfig.isKeyboardBottomLiftFixEnabled(context)
                dismissHidden = LspConfig.isKeyboardDismissHidden(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val extended = com.mi.onextbox.lsp.SystemAdaptationDetector.current() == com.mi.onextbox.lsp.SystemAdaptationVersion.ColorOs17
    if (extended) KeyboardPictorialOptions(com.mi.onextbox.lsp.KeyboardPictorialSettings.keyboard.filter { it.group == "配色" })
    val materialRow: @Composable () -> Unit = {
        SettingsToggleRow(
            title = stringResource(R.string.keyboard_material_unlock_title),
            summary = stringResource(R.string.keyboard_material_unlock_summary),
            checked = enabled,
            hasDividerBelow = extended,
            onCheckedChange = { checked ->
                enabled = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) { LspConfig.setKeyboardMaterialUnlocked(context, checked) }
                    }
                }
            },
        )
    }
    if (extended) {
        KeyboardPictorialOptions(com.mi.onextbox.lsp.KeyboardPictorialSettings.keyboard.filter { it.group == "材质" }, leading = materialRow)
    } else {
        SettingsSection(title = stringResource(R.string.keyboard_material_group))
        SettingsGroup { materialRow() }
    }
    SettingsSection(title = stringResource(R.string.keyboard_layout_group))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.keyboard_bottom_lift_fix_title),
            summary = stringResource(R.string.keyboard_bottom_lift_fix_summary),
            checked = bottomLiftEnabled,
            hasDividerBelow = true,
            onCheckedChange = { checked ->
                bottomLiftEnabled = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) { LspConfig.setKeyboardBottomLiftFixEnabled(context, checked) }
                    }
                }
            },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.keyboard_hide_dismiss_title),
            summary = stringResource(R.string.keyboard_hide_dismiss_summary),
            checked = dismissHidden,
            hasDividerAbove = true,
            onCheckedChange = { checked ->
                dismissHidden = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) { LspConfig.setKeyboardDismissHidden(context, checked) }
                    }
                }
            },
        )
    }
    if (extended) KeyboardPictorialOptions(com.mi.onextbox.lsp.KeyboardPictorialSettings.keyboard.filter { it.group in setOf("工具栏", "按键", "角标") })
    if (KeyboardFeatureVisibility.emojiGroupVisible) {
        SettingsSection(title = stringResource(R.string.keyboard_emoji_group))
        SettingsGroup {
            SettingsToggleRow(
                title = stringResource(R.string.keyboard_flag_emoji_title),
                summary = stringResource(R.string.keyboard_flag_emoji_summary),
                checked = flagEmojiEnabled,
                onCheckedChange = { checked ->
                    flagEmojiEnabled = checked
                    scope.launch {
                        writeLock.withLock {
                            withContext(Dispatchers.IO) { LspConfig.setKeyboardFlagEmojiEnabled(context, checked) }
                        }
                    }
                },
            )
        }
    }
}

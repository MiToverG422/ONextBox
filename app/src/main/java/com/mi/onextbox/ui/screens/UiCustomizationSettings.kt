package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.*
import com.mi.onextbox.ui.settings.*
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class CustomizationEditor<T>(private val context: Context, private val scope: CoroutineScope,
    private val read: (Context) -> T) {
    var state by mutableStateOf(read(context))
    var savingKey by mutableStateOf<String?>(null)
    fun refresh() { if (savingKey == null) state = read(context) }
    fun save(key: String, next: T, write: () -> Unit) {
        if (savingKey != null) return
        state = next
        savingKey = key
        val pending = FeatureSettingWriteQueue.application.enqueue(write)
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally {
                state = read(context)
                savingKey = null
            }
        }
    }
}

@Composable
private fun <T> rememberCustomization(keys: Set<String>, read: (Context) -> T): CustomizationEditor<T> {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val editor = remember(context) { CustomizationEditor(context, scope, read) }
    DisposableEffect(context, owner, editor) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in keys) editor.refresh()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) editor.refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); owner.lifecycle.removeObserver(observer) }
    }
    return editor
}

@Composable
internal fun SettingsAccountCardSizeGroup() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(SettingsAppearanceSettings.keys, SettingsAppearanceSettings::state)
    val value = editor.state
    SettingsSection(title = stringResource(R.string.settings_account_size_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row(SettingsAppearanceSettings.ACCOUNT_SIZE) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.settings_account_size_title),
                summary = stringResource(R.string.settings_account_size_summary), checked = value.accountSize,
                interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { next -> editor.save(SettingsAppearanceSettings.ACCOUNT_SIZE, value.copy(accountSize = next)) {
                    SettingsAppearanceSettings.setEnabled(context, SettingsAppearanceSettings.ACCOUNT_SIZE, next)
                } })
        }
        row(SettingsAppearanceSettings.ACCOUNT_PERCENT, visible = value.accountSize) { above, below ->
            FeatureSliderRow(heading = stringResource(R.string.settings_account_size_percent),
                currentValue = "${value.accountPercent}%", value = value.accountPercent.toFloat(),
                valueRange = SettingsAppearanceSettings.MIN_PERCENT.toFloat()..SettingsAppearanceSettings.MAX_PERCENT.toFloat(),
                steps = 17, enabled = value.accountSize,
                interactionEnabled = value.accountSize && editor.savingKey == null,
                onValueChange = { editor.state = editor.state.copy(accountPercent = it.roundToInt()) },
                onValueChangeFinished = {
                    val selected = editor.state
                    editor.save(SettingsAppearanceSettings.ACCOUNT_PERCENT, selected) {
                        SettingsAppearanceSettings.setPercent(context, selected.accountPercent)
                    }
                },
                onReset = { editor.save(SettingsAppearanceSettings.ACCOUNT_PERCENT,
                    value.copy(accountPercent = SettingsAppearanceSettings.DEFAULT_PERCENT)) {
                    SettingsAppearanceSettings.setPercent(context, SettingsAppearanceSettings.DEFAULT_PERCENT)
                } }, hasDividerAbove = above, hasDividerBelow = below)
        }
    }
}

@Composable
internal fun CameraFeaturesPage() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(CameraGallerySettings.keys, CameraGallerySettings::state)
    val feature = SharedFeatureDefinitions.cameraDefaultGallery
    SettingsSection(title = stringResource(R.string.camera_preview_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row(CameraGallerySettings.FOLLOW_DEFAULT) { above, below ->
            SettingsToggleRow(title = stringResource(feature.title), summary = stringResource(feature.summary),
                checked = editor.state[CameraGallerySettings.FOLLOW_DEFAULT] == true,
                interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { enabled -> editor.save(CameraGallerySettings.FOLLOW_DEFAULT,
                    editor.state + (CameraGallerySettings.FOLLOW_DEFAULT to enabled)) {
                    CameraGallerySettings.set(context, enabled)
                } })
        }
    }
}

@Composable
internal fun TranslationFeaturesPage() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(ScreenTranslationSettings.keys, ScreenTranslationSettings::state)
    SettingsSection(title = stringResource(R.string.translation_screen_group))
    FeatureAnimatedGroup(animateChanges = true) {
        SharedFeatureDefinitions.translationRows.forEach { (key, feature) ->
            row(key) { above, below ->
                SettingsToggleRow(title = stringResource(feature.title), summary = stringResource(feature.summary),
                    checked = editor.state[key] == true,
                    interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below,
                    onCheckedChange = { enabled -> editor.save(key, editor.state + (key to enabled)) {
                        ScreenTranslationSettings.set(context, key, enabled)
                    } })
            }
        }
    }
}

@Composable
internal fun InstallerCleanupPage(page: FeaturePageMode, onOpen: (FeaturePageMode) -> Unit) {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(InstallerCleanupSettings.keys + InstallerCleanupSettings.legacyKeys, InstallerCleanupSettings::state)
    val rows = if (page == FeaturePageMode.AppInstaller) SharedFeatureDefinitions.appInstallerRows
        else SharedFeatureDefinitions.packageInstallerRows
    SettingsSection(title = stringResource(R.string.installer_cleanup_group))
    FeatureAnimatedGroup(animateChanges = true) {
        rows.forEach { (key, feature) ->
            row(key) { above, below ->
                SettingsToggleRow(title = stringResource(feature.title),
                    summary = if (page == FeaturePageMode.AppInstaller) "" else stringResource(feature.summary),
                    checked = editor.state[key] == true, interactionEnabled = editor.savingKey == null,
                    hasDividerAbove = above, hasDividerBelow = below,
                    onCheckedChange = { enabled -> editor.save(key, editor.state + (key to enabled)) {
                        InstallerCleanupSettings.set(context, key, enabled)
                    } })
            }
        }
    }
    if (page == FeaturePageMode.PackageInstaller) InstallerManagementGroup(onOpen)
}

@Composable
internal fun InstallerManagementGroup(onOpen: (FeaturePageMode) -> Unit) {
    val entries = SharedFeatureDefinitions.installerSubPages
    SettingsSection(title = stringResource(R.string.installer_group))
    FeatureAnimatedGroup(animateChanges = true) {
        entries.forEach { (page, title) ->
            row(page.name) { above, below ->
                SettingsCardRow(title = stringResource(title), summary = "", showArrow = true,
                    onClick = { onOpen(page) }, hasDividerAbove = above, hasDividerBelow = below)
            }
        }
    }
}

@Composable
internal fun SettingsTitleCollapseGroup() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(SettingsAppearanceSettings.keys, SettingsAppearanceSettings::state)
    val value = editor.state
    SettingsSection(title = stringResource(R.string.feature_group_settings_interface))
    FeatureAnimatedGroup(animateChanges = true) {
        row(SettingsAppearanceSettings.TITLE_COLLAPSED) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.feature_settings_title_collapsed_title),
                summary = stringResource(R.string.feature_settings_title_collapsed_summary), checked = value.titleCollapsed,
                interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { next -> editor.save(SettingsAppearanceSettings.TITLE_COLLAPSED, value.copy(titleCollapsed = next)) {
                    LspConfig.setSettingsTitleCollapsedEnabled(context, next)
                } })
        }
        row(SettingsAppearanceSettings.TITLE_LOCK, visible = value.titleCollapsed) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.settings_title_lock_title),
                summary = stringResource(R.string.settings_title_lock_summary), checked = value.titleLocked,
                interactionEnabled = value.titleCollapsed && editor.savingKey == null,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { next -> editor.save(SettingsAppearanceSettings.TITLE_LOCK, value.copy(titleLocked = next)) {
                    SettingsAppearanceSettings.setEnabled(context, SettingsAppearanceSettings.TITLE_LOCK, next)
                } })
        }
    }
}

@Composable
internal fun QsTileCornerSettingsGroup(onOpenCorners: () -> Unit, onOpenPercentage: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(QsTileCornerSettings.keys, QsTileCornerSettings::state)
    val percentage = rememberCustomization(QsSliderPercentageSettings.keys, QsSliderPercentageSettings::state)
    val value = editor.state
    SettingsSection(title = stringResource(R.string.qs_tile_corner_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row(QsTileCornerSettings.ENABLED) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.qs_tile_corner_title), summary = stringResource(R.string.qs_tile_corner_summary),
                checked = value.enabled, interactionEnabled = editor.savingKey != QsTileCornerSettings.ENABLED,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { enabled -> editor.save(QsTileCornerSettings.ENABLED, value.copy(enabled = enabled)) {
                    QsTileCornerSettings.setEnabled(context, enabled)
                } })
        }
        row("corner-settings", visible = value.enabled) { above, below ->
            SettingsCardRow(title = stringResource(R.string.qs_tile_corner_settings_title),
                summary = "", showArrow = true,
                hasDividerAbove = above, hasDividerBelow = below, onClick = onOpenCorners)
        }
        row(QsSliderPercentageSettings.ENABLED) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.qs_slider_percentage_title),
                summary = stringResource(R.string.qs_slider_percentage_summary),
                checked = percentage.state.enabled, interactionEnabled = percentage.savingKey == null,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { enabled ->
                    percentage.save(QsSliderPercentageSettings.ENABLED, percentage.state.copy(enabled = enabled)) {
                        QsSliderPercentageSettings.setEnabled(context, enabled)
                    }
                })
        }
        row("percentage-settings", visible = percentage.state.enabled) { above, below ->
            SettingsCardRow(title = stringResource(R.string.qs_slider_percentage_properties),
                summary = "", showArrow = true,
                hasDividerAbove = above, hasDividerBelow = below, onClick = onOpenPercentage)
        }
    }
}

@Composable
internal fun QsSliderPercentageSettingsPage(sideVolume: Boolean = false) {
    val context = LocalContext.current.applicationContext
    val keys = if (sideVolume) SideVolumePercentageSettings.keys else QsSliderPercentageSettings.keys
    val read = if (sideVolume) SideVolumePercentageSettings::state else QsSliderPercentageSettings::state
    val sizeKey = if (sideVolume) SideVolumePercentageSettings.SIZE else QsSliderPercentageSettings.SIZE
    val colorKey = if (sideVolume) SideVolumePercentageSettings.COLOR else QsSliderPercentageSettings.COLOR
    val offsetKey = if (sideVolume) SideVolumePercentageSettings.OFFSET else QsSliderPercentageSettings.OFFSET
    val editor = rememberCustomization(keys, read)
    val value = editor.state
    fun save(key: String, next: QsSliderPercentageSettings.State) = editor.save(key, next) {
        if (sideVolume) SideVolumePercentageSettings.save(context, key, next)
        else QsSliderPercentageSettings.save(context, key, next)
    }
    SettingsSection(title = stringResource(if (sideVolume) R.string.side_volume_percentage_title else R.string.qs_slider_percentage_title))
    FeatureAnimatedGroup(animateChanges = true) {
        row(sizeKey) { above, below ->
            FeatureSliderRow(
                heading = stringResource(R.string.qs_slider_percentage_size),
                currentValue = stringResource(R.string.feature_slider_current_value, "${value.sizeSp} sp"),
                value = value.sizeSp.toFloat(),
                valueRange = QsSliderPercentageRules.MIN_SIZE_SP.toFloat()..QsSliderPercentageRules.MAX_SIZE_SP.toFloat(),
                steps = QsSliderPercentageRules.MAX_SIZE_SP - QsSliderPercentageRules.MIN_SIZE_SP - 1,
                interactionEnabled = editor.savingKey == null,
                onValueChange = { editor.state = editor.state.copy(sizeSp = it.roundToInt()) },
                onValueChangeFinished = { save(sizeKey, editor.state) },
                onReset = { save(sizeKey, value.copy(sizeSp = QsSliderPercentageRules.DEFAULT_SIZE_SP)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row(colorKey) { above, below ->
            SettingsColorPicker(
                title = stringResource(R.string.qs_slider_percentage_color),
                selectedColor = value.color,
                onColorChange = { save(colorKey, value.copy(color = it)) },
                interactionEnabled = editor.savingKey == null,
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row(offsetKey) { above, below ->
            FeatureSliderRow(
                heading = stringResource(R.string.qs_slider_percentage_offset),
                currentValue = stringResource(R.string.feature_slider_current_value, "${value.offset}%"),
                value = value.offset.toFloat(), valueRange = 0f..100f, steps = 99,
                interactionEnabled = editor.savingKey == null,
                onValueChange = { editor.state = editor.state.copy(offset = it.roundToInt()) },
                onValueChangeFinished = { save(offsetKey, editor.state) },
                onReset = { save(offsetKey, value.copy(offset = QsSliderPercentageRules.DEFAULT_OFFSET)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
    }
}

@Composable
internal fun SideVolumePercentageSettingsGroup(onOpenProperties: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(SideVolumePercentageSettings.keys, SideVolumePercentageSettings::state)
    val appearance = rememberCustomization(SideVolumeAppearanceSettings.keys, SideVolumeAppearanceSettings::state)
    val value = editor.state
    SettingsSection(title = stringResource(R.string.side_volume_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row(SideVolumePercentageSettings.ENABLED) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.side_volume_percentage_title),
                summary = stringResource(R.string.side_volume_percentage_summary),
                checked = value.enabled, interactionEnabled = editor.savingKey == null,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { enabled ->
                    editor.save(SideVolumePercentageSettings.ENABLED, value.copy(enabled = enabled)) {
                        SideVolumePercentageSettings.setEnabled(context, enabled)
                    }
                })
        }
        row("side-volume-properties", visible = value.enabled) { above, below ->
            SettingsCardRow(title = stringResource(R.string.qs_slider_percentage_properties),
                summary = "", showArrow = true,
                hasDividerAbove = above, hasDividerBelow = below, onClick = onOpenProperties)
        }
        row(SideVolumeAppearanceSettings.KEEP_WIDE) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.side_volume_keep_wide_title), summary = "",
                checked = appearance.state.keepWide, interactionEnabled = appearance.savingKey == null,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { enabled ->
                    appearance.save(SideVolumeAppearanceSettings.KEEP_WIDE,
                        appearance.state.copy(keepWide = enabled)) {
                        SideVolumeAppearanceSettings.setKeepWide(context, enabled)
                    }
                })
        }
    }
}

@Composable
internal fun QsTileCornerSettingsPage() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(QsTileCornerSettings.keys, QsTileCornerSettings::state)
    val value = editor.state
    SettingsSection(title = stringResource(R.string.qs_tile_corner_radius))
    FeatureAnimatedGroup(animateChanges = true) {
        QsTileCornerSettings.Size.entries.forEach { size ->
            row(size.key) { above, below ->
                val radius = value.radius(size)
                FeatureSliderRow(
                    heading = stringResource(R.string.qs_tile_corner_size_radius, size.label),
                    currentValue = if (radius == QsTileCornerRules.SYSTEM_DEFAULT)
                        stringResource(R.string.feature_slider_current_default)
                        else stringResource(R.string.feature_slider_current_value, "$radius%"),
                    value = radius.toFloat(),
                    valueRange = QsTileCornerRules.SYSTEM_DEFAULT.toFloat()..QsTileCornerRules.MAX_PERCENT.toFloat(),
                    steps = QsTileCornerRules.MAX_PERCENT,
                    enabled = value.enabled,
                    interactionEnabled = editor.savingKey != size.key,
                    onValueChange = { editor.state = editor.state.withRadius(size, it.roundToInt()) },
                    onValueChangeFinished = {
                        val selected = editor.state
                        editor.save(size.key, selected) {
                            QsTileCornerSettings.setRadius(context, size, selected.radius(size))
                        }
                    },
                    onReset = {
                        editor.save(size.key, value.withRadius(size, QsTileCornerRules.SYSTEM_DEFAULT)) {
                            QsTileCornerSettings.setRadius(context, size, QsTileCornerRules.SYSTEM_DEFAULT)
                        }
                    },
                    hasDividerAbove = above, hasDividerBelow = below,
                )
            }
        }
    }
}

@Composable
internal fun StatusBarLayoutSettingsGroup(onOpenOrder: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(setOf(StatusBarLayoutSettings.ORDER_ENABLED, StatusBarLayoutSettings.ORDER,
        StatusBarLayoutSettings.MARGINS_ENABLED, StatusBarLayoutSettings.LEFT, StatusBarLayoutSettings.RIGHT,
        StatusBarLayoutSettings.VERTICAL_MARGINS_ENABLED, StatusBarLayoutSettings.TOP, StatusBarLayoutSettings.BOTTOM,
        StatusBarLayoutSettings.SIM_ENABLED, StatusBarLayoutSettings.SIM_ORDER), StatusBarLayoutSettings::state)
    val value = editor.state
    fun save(key: String, next: StatusBarLayoutSettings.State) = editor.save(key, next) { StatusBarLayoutSettings.save(context, next) }
    SettingsSection(title = stringResource(R.string.statusbar_layout_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row("order") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.statusbar_order_title), summary = "",
                checked = value.orderEnabled, interactionEnabled = editor.savingKey != StatusBarLayoutSettings.ORDER_ENABLED,
                onCheckedChange = { next -> editor.save(StatusBarLayoutSettings.ORDER_ENABLED, value.copy(orderEnabled = next)) {
                    StatusBarLayoutSettings.setOrderEnabled(context, next)
                } },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row("order-settings", visible = value.orderEnabled) { above, below ->
            SettingsCardRow(title = stringResource(R.string.statusbar_order_settings_title),
                summary = "", showArrow = true,
                hasDividerAbove = above, hasDividerBelow = below, onClick = onOpenOrder)
        }
        row("margins") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.statusbar_margins_title), summary = "",
                checked = value.marginsEnabled, interactionEnabled = editor.savingKey != StatusBarLayoutSettings.MARGINS_ENABLED,
                onCheckedChange = { save(StatusBarLayoutSettings.MARGINS_ENABLED, value.copy(marginsEnabled = it)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        listOf(true, false).forEach { left ->
            val key = if (left) StatusBarLayoutSettings.LEFT else StatusBarLayoutSettings.RIGHT
            val amount = if (left) value.left else value.right
            row(key, visible = value.marginsEnabled) { above, below ->
                FeatureSliderRow(heading = stringResource(if (left) R.string.statusbar_margin_left else R.string.statusbar_margin_right),
                    currentValue = "$amount dp", value = amount.toFloat(), valueRange = -16f..40f, steps = 55,
                    interactionEnabled = editor.savingKey != key,
                    onValueChange = { next -> editor.state = if (left) editor.state.copy(left = next.roundToInt()) else editor.state.copy(right = next.roundToInt()) },
                    onValueChangeFinished = { save(key, editor.state) },
                    onReset = { save(key, if (left) value.copy(left = 0) else value.copy(right = 0)) },
                    hasDividerAbove = above, hasDividerBelow = below)
            }
        }
        row("vertical-margins") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.statusbar_vertical_margins_title), summary = "",
                checked = value.verticalMarginsEnabled, interactionEnabled = editor.savingKey != StatusBarLayoutSettings.VERTICAL_MARGINS_ENABLED,
                onCheckedChange = { next -> editor.save(StatusBarLayoutSettings.VERTICAL_MARGINS_ENABLED,
                    value.copy(verticalMarginsEnabled = next)) { StatusBarLayoutSettings.setVerticalMarginsEnabled(context, next) } },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        listOf(true, false).forEach { top ->
            val key = if (top) StatusBarLayoutSettings.TOP else StatusBarLayoutSettings.BOTTOM
            val amount = if (top) value.top else value.bottom
            row(key, visible = value.verticalMarginsEnabled) { above, below ->
                FeatureSliderRow(heading = stringResource(if (top) R.string.statusbar_margin_top else R.string.statusbar_margin_bottom),
                    currentValue = "$amount dp", value = amount.toFloat(), valueRange = -16f..40f, steps = 55,
                    interactionEnabled = editor.savingKey != key,
                    onValueChange = { next -> editor.state = if (top) editor.state.copy(top = next.roundToInt()) else editor.state.copy(bottom = next.roundToInt()) },
                    onValueChangeFinished = { save(key, editor.state) },
                    onReset = { save(key, if (top) value.copy(top = 0) else value.copy(bottom = 0)) },
                    hasDividerAbove = above, hasDividerBelow = below)
            }
        }
    }
}

@Composable
internal fun StatusBarIconOrderPage() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(setOf(StatusBarLayoutSettings.ORDER_ENABLED, StatusBarLayoutSettings.ORDER,
        StatusBarLayoutSettings.SIM_ENABLED, StatusBarLayoutSettings.SIM_ORDER), StatusBarLayoutSettings::state)
    val value = editor.state
    // Keep other rows' arrow tint stable during a queued write. The handlers still
    // serialize every edit; unrelated controls need not pulse into a disabled state.
    val editable = value.orderEnabled
    SettingsSection(title = stringResource(R.string.statusbar_order_groups))
    FeatureAnimatedGroup(animateChanges = true) {
        row("reset") { above, below ->
            SettingsCardRow(title = stringResource(R.string.statusbar_order_reset), summary = "",
                hasDividerAbove = above, hasDividerBelow = below,
                onClick = {
                    if (editor.savingKey == null) editor.save(StatusBarLayoutSettings.ORDER,
                        value.copy(order = StatusBarLayoutRules.defaultOrder, simEnabled = false, simOrder = StatusBarLayoutRules.defaultSimOrder)) {
                        StatusBarLayoutSettings.resetOrder(context)
                    }
                })
        }
        value.order.forEachIndexed { index, slot ->
            row(slot) { above, below ->
                fun move(target: Int) {
                    if (!editable || editor.savingKey != null || target !in value.order.indices) return
                    val next = value.order.toMutableList().apply { add(target, removeAt(index)) }
                    editor.save(StatusBarLayoutSettings.ORDER, value.copy(order = next)) { StatusBarLayoutSettings.saveOrder(context, next) }
                }
                StatusBarOrderRow("${index + 1}. ${stringResource(slotTitle(slot))}", editable && index > 0, editable && index < value.order.lastIndex,
                    { move(index - 1) }, { move(index + 1) }, above, below)
            }
            if (slot == "mobile") {
                row("sim-enabled") { above, below ->
                    SettingsToggleRow(title = stringResource(R.string.statusbar_sim_order_title), summary = "",
                        checked = value.simEnabled, switchEnabled = value.orderEnabled,
                        interactionEnabled = editor.savingKey != StatusBarLayoutSettings.SIM_ENABLED,
                        hasDividerAbove = above, hasDividerBelow = below,
                        onCheckedChange = { next -> editor.save(StatusBarLayoutSettings.SIM_ENABLED, value.copy(simEnabled = next)) {
                            StatusBarLayoutSettings.saveSimOrder(context, next, value.simOrder)
                        } })
                }
                value.simOrder.forEachIndexed { simIndex, sim -> row("sim-$sim", value.orderEnabled && value.simEnabled) { above, below ->
                    fun move(target: Int) {
                        if (!editable || editor.savingKey != null || target !in value.simOrder.indices) return
                        val next = value.simOrder.toMutableList().apply { add(target, removeAt(simIndex)) }
                        editor.save(StatusBarLayoutSettings.SIM_ORDER, value.copy(simOrder = next)) {
                            StatusBarLayoutSettings.saveSimOrder(context, value.simEnabled, next)
                        }
                    }
                    StatusBarOrderRow(stringResource(if (sim == 0) R.string.statusbar_sim1 else R.string.statusbar_sim2),
                        editable && simIndex > 0, editable && simIndex < value.simOrder.lastIndex,
                        { move(simIndex - 1) }, { move(simIndex + 1) }, above, below)
                } }
            }
        }
    }
}

private fun slotTitle(slot: String) = when (slot) {
    "mobile" -> R.string.statusbar_icon_mobile
    "wifi" -> R.string.statusbar_icon_wifi
    "bluetooth" -> R.string.statusbar_icon_bluetooth
    "mute" -> R.string.statusbar_icon_mute
    "network_speed" -> R.string.statusbar_icon_network_speed
    "airplane" -> R.string.statusbar_icon_airplane
    "hotspot" -> R.string.statusbar_icon_hotspot
    "vpn" -> R.string.statusbar_icon_vpn
    "volume" -> R.string.statusbar_icon_volume
    "alarm_clock" -> R.string.statusbar_icon_alarm
    "location" -> R.string.statusbar_icon_location
    "rotate" -> R.string.statusbar_icon_rotate
    "nfc" -> R.string.statusbar_icon_nfc
    "logging" -> R.string.statusbar_icon_logging
    "data_saver" -> R.string.statusbar_icon_data_saver
    "headset" -> R.string.statusbar_icon_headset
    "managed_profile" -> R.string.statusbar_icon_work_profile
    "zen" -> R.string.statusbar_icon_zen
    "nosim_all" -> R.string.statusbar_icon_no_sim
    "dynamic_info" -> R.string.statusbar_icon_dynamic_info
    else -> R.string.statusbar_icon_cast
}

@Composable
private fun StatusBarOrderRow(title: String, up: Boolean, down: Boolean, onUp: () -> Unit, onDown: () -> Unit, above: Boolean, below: Boolean) {
    val tint = COUITheme.colorScheme.onSurfaceVariantActions
    val upLabel = stringResource(R.string.statusbar_order_up)
    val downLabel = stringResource(R.string.statusbar_order_down)
    val actions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = onUp, enabled = up) { Icon(Icons.Rounded.KeyboardArrowUp, upLabel, tint = tint.copy(alpha = if (up) 1f else 0.38f)) }
        IconButton(onClick = onDown, enabled = down) { Icon(Icons.Rounded.KeyboardArrowDown, downLabel, tint = tint.copy(alpha = if (down) 1f else 0.38f)) }
    }
    run {
        BasicComponent(title = title, summary = null, insideMargin = SettingsTokens.RowInsideMargin, endActions = actions)
    }
}

private data class LauncherIconMenuState(val disableSecondary: Boolean, val widgets: Boolean, val iconLabelSpacingEnabled: Boolean, val iconLabelSpacingOffset: Int)

@Composable
internal fun LauncherIconMenuSettingsGroup(availableTitles: Set<Int>? = null) {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(setOf(LauncherGestureSettings.MENU, LauncherGestureSettings.WIDGETS,
        LauncherGestureSettings.SPACING_ENABLED, LauncherGestureSettings.SPACING_OFFSET)) {
        LauncherIconMenuState(LauncherGestureSettings.menu(it), LauncherGestureSettings.widgets(it),
            LauncherGestureSettings.iconLabelSpacingEnabled(it), LauncherGestureSettings.iconLabelSpacingOffset(it))
    }
    val value = editor.state
    SettingsSection(title = stringResource(R.string.launcher_icon_interaction_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row("secondary-menu", visible = availableTitles == null || R.string.launcher_disable_icon_menu_title in availableTitles) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.launcher_disable_icon_menu_title), summary = stringResource(R.string.launcher_disable_icon_menu_summary),
                checked = value.disableSecondary, interactionEnabled = editor.savingKey != LauncherGestureSettings.MENU,
                onCheckedChange = { next -> editor.save(LauncherGestureSettings.MENU, value.copy(disableSecondary = next)) {
                    LauncherGestureSettings.save(context, LauncherGestureSettings.MENU, next)
                } }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("widgets-menu", visible = availableTitles == null || R.string.launcher_widgets_menu_title in availableTitles) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.launcher_widgets_menu_title), summary = stringResource(R.string.launcher_widgets_menu_summary),
                checked = value.widgets, interactionEnabled = editor.savingKey != LauncherGestureSettings.WIDGETS,
                onCheckedChange = { next -> editor.save(LauncherGestureSettings.WIDGETS, value.copy(widgets = next)) {
                    LauncherGestureSettings.save(context, LauncherGestureSettings.WIDGETS, next)
                } }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("icon-label-spacing", visible = availableTitles == null || R.string.launcher_icon_label_spacing_title in availableTitles) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.launcher_icon_label_spacing_title), summary = stringResource(R.string.launcher_icon_label_spacing_summary),
                checked = value.iconLabelSpacingEnabled, interactionEnabled = editor.savingKey != LauncherGestureSettings.SPACING_ENABLED,
                onCheckedChange = { next -> editor.save(LauncherGestureSettings.SPACING_ENABLED, value.copy(iconLabelSpacingEnabled = next)) {
                    LauncherGestureSettings.save(context, LauncherGestureSettings.SPACING_ENABLED, next)
                } }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("icon-label-spacing-offset", visible = value.iconLabelSpacingEnabled &&
            (availableTitles == null || R.string.launcher_icon_label_spacing_title in availableTitles)) { above, below ->
            FeatureSliderRow(
                heading = stringResource(R.string.launcher_icon_label_spacing_offset),
                currentValue = "${value.iconLabelSpacingOffset} dp",
                value = value.iconLabelSpacingOffset.toFloat(),
                valueRange = LauncherGestureRules.MIN_ICON_LABEL_SPACING.toFloat()..LauncherGestureRules.MAX_ICON_LABEL_SPACING.toFloat(),
                steps = LauncherGestureRules.MAX_ICON_LABEL_SPACING - LauncherGestureRules.MIN_ICON_LABEL_SPACING - 1,
                interactionEnabled = editor.savingKey != LauncherGestureSettings.SPACING_OFFSET,
                onValueChange = { editor.state = editor.state.copy(iconLabelSpacingOffset = it.roundToInt()) },
                onValueChangeFinished = { val next = editor.state; editor.save(LauncherGestureSettings.SPACING_OFFSET, next) {
                    LauncherGestureSettings.saveIconLabelSpacingOffset(context, next.iconLabelSpacingOffset)
                } },
                onReset = { editor.save(LauncherGestureSettings.SPACING_OFFSET, value.copy(iconLabelSpacingOffset = 0)) {
                    LauncherGestureSettings.saveIconLabelSpacingOffset(context, 0)
                } },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
    }
}

private data class LauncherTopState(val enabled: Boolean, val width: Int)

@Composable
internal fun LauncherSwipeTopSettingsGroup() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(setOf(LauncherGestureSettings.TOP, LauncherGestureSettings.WIDTH)) {
        LauncherTopState(LauncherGestureSettings.top(it), LauncherGestureSettings.width(it))
    }
    val value = editor.state
    SettingsSection(title = stringResource(R.string.launcher_swipe_gesture_group))
    FeatureAnimatedGroup {
        row("top") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.launcher_swipe_top_title), summary = stringResource(R.string.launcher_swipe_top_summary),
                checked = value.enabled, interactionEnabled = editor.savingKey != LauncherGestureSettings.TOP,
                onCheckedChange = { next -> editor.save(LauncherGestureSettings.TOP, value.copy(enabled = next)) { LauncherGestureSettings.save(context, LauncherGestureSettings.TOP, next) } },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row("width", visible = value.enabled) { above, below ->
            FeatureSliderRow(heading = stringResource(R.string.launcher_float_trigger_width), currentValue = "${value.width}%",
                value = value.width.toFloat(), valueRange = 50f..100f, steps = 49, interactionEnabled = editor.savingKey != LauncherGestureSettings.WIDTH,
                onValueChange = { editor.state = editor.state.copy(width = it.roundToInt()) },
                onValueChangeFinished = { val next = editor.state; editor.save(LauncherGestureSettings.WIDTH, next) { LauncherGestureSettings.saveWidth(context, next.width) } },
                onReset = { editor.save(LauncherGestureSettings.WIDTH, value.copy(width = 100)) { LauncherGestureSettings.saveWidth(context, 100) } },
                hasDividerAbove = above, hasDividerBelow = below)
        }
    }
}

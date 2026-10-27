package com.mi.onextbox.ui.screens

import android.widget.Toast
import android.content.SharedPreferences
import com.mi.onextbox.lsp.LspPreferenceStore
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.Wallpaper3DForceSettings
import com.mi.onextbox.lsp.WallpaperMotionSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun WallpaperMotionGroup(showForceEnable: Boolean = true) {
    val context = LocalContext.current
    val application = context.applicationContext
    val scope = rememberCoroutineScope()
    var forceEnabled by remember { mutableStateOf(Wallpaper3DForceSettings.enabled(context)) }
    var enabled by remember { mutableStateOf(WallpaperMotionSettings.enabled(context)) }
    var amount by remember { mutableIntStateOf(WallpaperMotionSettings.amount(context)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(application) {
        val prefs = LspPreferenceStore.prefs(application)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!saving && (key == null || key == Wallpaper3DForceSettings.ENABLED ||
                    key == WallpaperMotionSettings.ENABLED || key == WallpaperMotionSettings.AMOUNT)) {
                forceEnabled = Wallpaper3DForceSettings.enabled(application)
                enabled = WallpaperMotionSettings.enabled(application)
                amount = WallpaperMotionSettings.amount(application)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun saveForce(nextEnabled: Boolean) {
        forceEnabled = nextEnabled
        saving = true
        val write = FeatureSettingWriteQueue.application.enqueue {
            Wallpaper3DForceSettings.save(application, nextEnabled)
        }
        scope.launch {
            try {
                write.await()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                forceEnabled = Wallpaper3DForceSettings.enabled(application)
                Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_SHORT).show()
            } finally {
                saving = false
                forceEnabled = Wallpaper3DForceSettings.enabled(application)
                enabled = WallpaperMotionSettings.enabled(application)
                amount = WallpaperMotionSettings.amount(application)
            }
        }
    }
    fun save(nextEnabled: Boolean, nextAmount: Int) {
        enabled = nextEnabled
        amount = nextAmount
        saving = true
        val write = FeatureSettingWriteQueue.application.enqueue {
            WallpaperMotionSettings.save(application, nextEnabled, nextAmount)
        }
        scope.launch {
            try {
                write.await()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                enabled = WallpaperMotionSettings.enabled(application)
                amount = WallpaperMotionSettings.amount(application)
                Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_SHORT).show()
            } finally {
                saving = false
                forceEnabled = Wallpaper3DForceSettings.enabled(application)
                enabled = WallpaperMotionSettings.enabled(application)
                amount = WallpaperMotionSettings.amount(application)
            }
        }
    }
    SettingsSection(title = stringResource(R.string.wallpaper_motion_group))
    FeatureAnimatedGroup {
        row(0, visible = showForceEnable) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.wallpaper_3d_force_title),
                summary = stringResource(R.string.wallpaper_3d_force_summary),
                checked = forceEnabled,
                interactionEnabled = !saving,
                onCheckedChange = { saveForce(it) },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(1) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.wallpaper_motion_title),
                summary = stringResource(R.string.wallpaper_motion_summary),
                checked = enabled,
                interactionEnabled = !saving,
                onCheckedChange = { save(it, amount) },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(2, visible = enabled) { above, below ->
            FeatureSliderRow(
                heading = stringResource(R.string.wallpaper_motion_amount),
                currentValue = "$amount%",
                value = amount.toFloat(),
                valueRange = 50f..200f,
                steps = 29,
                enabled = enabled, interactionEnabled = !saving,
                onValueChange = { amount = it.roundToInt() },
                onValueChangeFinished = { save(enabled, amount) },
                onReset = { save(enabled, 100) },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
    }
}

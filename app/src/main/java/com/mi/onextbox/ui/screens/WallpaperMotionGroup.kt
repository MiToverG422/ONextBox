package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.WallpaperMotionSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun WallpaperMotionGroup() {
    val context = LocalContext.current
    val application = context.applicationContext
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(WallpaperMotionSettings.enabled(context)) }
    var amount by remember { mutableIntStateOf(WallpaperMotionSettings.amount(context)) }
    var saving by remember { mutableStateOf(false) }
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
            }
        }
    }
    SettingsSection(title = stringResource(R.string.wallpaper_motion_group))
    FeatureAnimatedGroup {
        row(0) { above, below ->
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
        row(1, visible = enabled) { above, below ->
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

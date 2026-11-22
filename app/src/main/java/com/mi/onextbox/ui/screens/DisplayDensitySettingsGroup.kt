package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.boot.DisplayDensity
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun DisplayDensitySettingsGroup() {
    val ctx = LocalContext.current.applicationContext
    val density = LocalConfiguration.current.densityDpi
    var state by remember { mutableStateOf<DisplayDensity.State?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        state = withContext(Dispatchers.IO) { runCatching { DisplayDensity.read(ctx) }.getOrNull() }
    }
    LaunchedEffect(density) { refresh() }
    fun save(onFailure: () -> Unit = {}, block: () -> Unit) {
        if (busy) return
        busy = true
        val write = FeatureSettingWriteQueue.application.enqueue(block)
        scope.launch {
            try { write.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                onFailure()
                Toast.makeText(ctx, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show()
            }
            finally { busy = false }
            refresh()
        }
    }
    SettingsSection(title = stringResource(R.string.display_density_title))
    FeatureAnimatedGroup {
        row("density") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.display_density_title),
                summary = state?.let { "${it.effective} dpi" }.orEmpty(), checked = state?.enabled == true,
                interactionEnabled = state != null && !busy, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { on -> save { if (on) DisplayDensity.enable(ctx) else DisplayDensity.restore(ctx) } })
        }
        row("dpi", state?.enabled == true) { above, below ->
            val current = state ?: return@row
            var selected by remember(current) { mutableIntStateOf(current.effective) }
            FeatureSliderRow(heading = stringResource(R.string.display_density_value), currentValue = "$selected dpi",
                value = selected.toFloat(), valueRange = current.range.first.toFloat()..current.range.last.toFloat(),
                steps = 0, interactionEnabled = !busy, onValueChange = { selected = it.roundToInt() },
                onValueChangeFinished = {
                    val value = selected
                    save(onFailure = { selected = current.effective }) { DisplayDensity.set(ctx, value) }
                },
                onReset = { save { DisplayDensity.restore(ctx) } }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("keep", state?.enabled == true) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.display_density_keep), summary = "",
                checked = state?.keep == true, interactionEnabled = !busy,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { keep -> save { DisplayDensity.keep(ctx, keep) } })
        }
    }
}

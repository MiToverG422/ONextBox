package com.mi.onextbox.ui.screens

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.refresh.RefreshRateControllerClient
import com.mi.onextbox.refresh.RefreshRatePreferences
import com.mi.onextbox.refresh.SavedRefreshRateMode
import com.mi.onextbox.ui.common.ShellLogger
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsToggleRow
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class DisplayModeInfo(
    val id: Int,
    val surfaceFlingerModeIndex: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val isCurrent: Boolean,
)

private sealed interface RefreshRateAction {
    data class Apply(val mode: DisplayModeInfo) : RefreshRateAction
    data object RestoreDefault : RefreshRateAction
}

@Composable
fun RefreshRatePage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    val cachedModes = remember(context) { readCachedDisplayModes(context) }
    var modes by remember(context) { mutableStateOf(cachedModes) }
    var modesLoaded by remember(context) { mutableStateOf(cachedModes.isNotEmpty()) }
    LaunchedEffect(context, refreshKey) {
        // SurfaceFlinger's dumpsys can take around one second. Keep it off the
        // composition thread so applying a mode never freezes the page.
        val refreshedModes = withContext(Dispatchers.IO) { readDisplayModes(context) }
        if (refreshedModes.isNotEmpty()) {
            modes = refreshedModes
            saveCachedDisplayModes(context, refreshedModes)
        }
        modesLoaded = true
    }
    var autoStartEnabled by remember(context) {
        mutableStateOf(readAutoStartEnabled(context))
    }
    var showRefreshRate by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        showRefreshRate = withContext(Dispatchers.IO) {
            readForceRefreshEnabled(context)
        }
    }
    var selectedModeId by remember(context, modes) {
        val savedModeId = readSavedModeId(context)
            ?.takeIf { savedId -> modes.any { mode -> mode.id == savedId } }
        mutableStateOf(savedModeId)
    }
    var applying by remember { mutableStateOf(false) }
    val applyFailedText = stringResource(R.string.refresh_rate_apply_failed)
    val restoreSuccessText = stringResource(R.string.refresh_rate_restore_success)
    val restoreFailedText = stringResource(R.string.refresh_rate_restore_failed)
    var queuedAction by remember { mutableStateOf<RefreshRateAction?>(null) }

    fun enqueueRefreshAction(action: RefreshRateAction) {
        when (action) {
            is RefreshRateAction.Apply -> selectedModeId = action.mode.id
            RefreshRateAction.RestoreDefault -> Unit
        }
        if (applying) {
            // Keep only the user's latest intent while SurfaceFlinger is applying.
            queuedAction = action
            return
        }
        applying = true
        scope.launch {
            var nextAction: RefreshRateAction? = action
            while (nextAction != null) {
                when (nextAction) {
                    is RefreshRateAction.Apply -> {
                        val mode = nextAction.mode
                        val success = withContext(Dispatchers.IO) {
                            applyPreferredMode(context, mode)
                        }
                        if (success) {
                            saveSelectedMode(context, mode)
                        } else {
                            Toast.makeText(context, applyFailedText, Toast.LENGTH_SHORT).show()
                        }
                    }

                    RefreshRateAction.RestoreDefault -> {
                        val success = withContext(Dispatchers.IO) { clearPreferredMode(context) }
                        Toast.makeText(
                            context,
                            if (success) restoreSuccessText else restoreFailedText,
                            Toast.LENGTH_SHORT,
                        ).show()
                        selectedModeId = null
                        clearSavedModeId(context)
                    }
                }
                refreshKey++
                nextAction = queuedAction
                queuedAction = null
            }
            applying = false
        }
    }

    if (modes.isEmpty() && modesLoaded) {
        SettingsGroup {
            Text(
                text = stringResource(R.string.refresh_rate_no_modes),
                style = COUITheme.textStyles.body1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        return
    }

    if (modes.isEmpty()) return

    SettingsSection(title = stringResource(R.string.refresh_rate_table_refresh_rate))
    SettingsGroup {
        modes.forEachIndexed { index, mode ->
            if (index > 0) SettingsDivider()
            val detail = "ID ${mode.surfaceFlingerModeIndex} · " +
                stringResource(R.string.refresh_rate_resolution_format, mode.width, mode.height)
            SettingsCardRow(
                title = formatRefreshRate(mode.refreshRate),
                summary = if (mode.id == selectedModeId) {
                    "${stringResource(R.string.refresh_rate_selected)} · $detail"
                } else detail,
                hasDividerAbove = index > 0,
                hasDividerBelow = true,
                onClick = { enqueueRefreshAction(RefreshRateAction.Apply(mode)) },
            )
        }
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.refresh_rate_restore_default),
            summary = "",
            hasDividerAbove = true,
            onClick = { enqueueRefreshAction(RefreshRateAction.RestoreDefault) },
        )
    }

    SettingsSection(title = stringResource(R.string.tab_settings))
    SettingsGroup {
        RefreshRateToggleRow(
            title = stringResource(R.string.refresh_rate_auto_start),
            checked = autoStartEnabled,
            hasDividerBelow = true,
            onCheckedChange = { enabled ->
                autoStartEnabled = enabled
                setAutoStartEnabled(context, enabled)
            },
        )
        SettingsDivider()
        RefreshRateToggleRow(
            title = stringResource(R.string.refresh_rate_show_refresh_rate),
            checked = showRefreshRate,
            hasDividerAbove = true,
            onCheckedChange = { enabled ->
                showRefreshRate = enabled
                scope.launch {
                    if (!withContext(Dispatchers.IO) { setForceRefreshEnabled(context, enabled) }) {
                        showRefreshRate = !enabled
                        Toast.makeText(context, applyFailedText, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

}

@Composable
private fun RefreshRateToggleRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsToggleRow(
        title = title,
        summary = summary.orEmpty(),
        checked = checked,
        onCheckedChange = onCheckedChange,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
    )
}

private suspend fun readDisplayModes(context: Context): List<DisplayModeInfo> {
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
    val modes = readSurfaceFlingerDisplayModes(context, display)
        .ifEmpty { readHiddenDisplayModes(display) ?: readPublicDisplayModes(display) }
    // Transaction 1035 uses this complete native mode list. Do not filter,
    // deduplicate, or reorder it, otherwise the configuration index changes.
    return modes
}

private fun readCachedDisplayModes(context: Context): List<DisplayModeInfo> {
    val serialized = context
        .getSharedPreferences(RefreshRatePreferences.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(RefreshRatePreferences.KEY_CACHED_DISPLAY_MODES, null)
        .orEmpty()
    return serialized
        .split(';')
        .mapNotNull { entry ->
            val values = entry.split('|')
            if (values.size != 5) return@mapNotNull null
            runCatching {
                DisplayModeInfo(
                    id = values[0].toInt(),
                    surfaceFlingerModeIndex = values[1].toInt(),
                    width = values[2].toInt(),
                    height = values[3].toInt(),
                    refreshRate = values[4].toFloat(),
                    isCurrent = false,
                )
            }.getOrNull()
        }
}

private fun saveCachedDisplayModes(context: Context, modes: List<DisplayModeInfo>) {
    val serialized = modes.joinToString(separator = ";") { mode ->
        listOf(
            mode.id,
            mode.surfaceFlingerModeIndex,
            mode.width,
            mode.height,
            mode.refreshRate,
        ).joinToString(separator = "|")
    }
    context
        .getSharedPreferences(RefreshRatePreferences.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(RefreshRatePreferences.KEY_CACHED_DISPLAY_MODES, serialized)
        .apply()
}

private suspend fun readSurfaceFlingerDisplayModes(
    context: Context,
    display: Display,
): List<DisplayModeInfo> {
    val currentMode = display.mode
    val controllerModes = RefreshRateControllerClient.getSupportedModes(context).orEmpty()
    val output = controllerModes.ifEmpty {
        ShellLogger.exec(
            "RefreshRate list modes fallback",
            surfaceFlingerListCommand(context),
        ).out
    }
    val modePattern = Regex(
        """id=(-?\d+), config=(-?\d+), (\d+)x(\d+)@([0-9.]+)""",
    )
    return output.mapNotNull { line ->
        val match = modePattern.find(line) ?: return@mapNotNull null
        val (id, config, width, height, refreshRate) = match.destructured
        DisplayModeInfo(
            id = id.toInt(),
            surfaceFlingerModeIndex = config.toInt(),
            width = width.toInt(),
            height = height.toInt(),
            refreshRate = refreshRate.toFloat(),
            isCurrent = width.toInt() == currentMode.physicalWidth &&
                height.toInt() == currentMode.physicalHeight &&
                abs(refreshRate.toFloat() - currentMode.refreshRate) < 0.01f,
        )
    }
}

private fun readHiddenDisplayModes(display: Display): List<DisplayModeInfo>? {
    val currentMode = display.mode
    val currentModeId = currentMode.modeId
    return runCatching {
        val displayInfoClass = Class.forName("android.view.DisplayInfo")
        val displayInfo = displayInfoClass.getDeclaredConstructor().newInstance()
        val getDisplayInfo = Display::class.java.getDeclaredMethod("getDisplayInfo", displayInfoClass)
        getDisplayInfo.isAccessible = true
        val hasInfo = getDisplayInfo.invoke(display, displayInfo) as? Boolean ?: false
        if (!hasInfo) return@runCatching null

        val supportedModes = listOf("supportedDisplayModes", "supportedModes")
            .firstNotNullOfOrNull { fieldName ->
                runCatching {
                    displayInfoClass.getDeclaredField(fieldName).apply { isAccessible = true }.get(displayInfo)
                }.getOrNull()
            }
            ?.let { modes ->
                when (modes) {
                    is Array<*> -> modes.filterNotNull()
                    else -> emptyList()
                }
            }
            .orEmpty()

        val displayModes = supportedModes
            .mapIndexedNotNull { index, mode ->
                val id = readIntValue(mode, "id", "modeId", "mModeId")
                    ?: callIntValue(mode, "getModeId")
                    ?: return@mapIndexedNotNull null
                val width = readIntValue(mode, "width", "physicalWidth", "mWidth")
                    ?: callIntValue(mode, "getPhysicalWidth")
                    ?: return@mapIndexedNotNull null
                val height = readIntValue(mode, "height", "physicalHeight", "mHeight")
                    ?: callIntValue(mode, "getPhysicalHeight")
                    ?: return@mapIndexedNotNull null
                val refreshRate = readFloatValue(mode, "refreshRate", "mRefreshRate")
                    ?: callFloatValue(mode, "getRefreshRate")
                    ?: return@mapIndexedNotNull null
                DisplayModeInfo(
                    id = id,
                    surfaceFlingerModeIndex = index,
                    width = width,
                    height = height,
                    refreshRate = refreshRate,
                    isCurrent = id == currentModeId ||
                        (width == currentMode.physicalWidth &&
                            height == currentMode.physicalHeight &&
                            abs(refreshRate - currentMode.refreshRate) < 0.01f),
                )
            }

        displayModes.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

private fun readPublicDisplayModes(display: Display): List<DisplayModeInfo> {
    val currentModeId = display.mode.modeId
    return display.supportedModes
        .mapIndexed { index, mode ->
            DisplayModeInfo(
                id = mode.modeId,
                surfaceFlingerModeIndex = index,
                width = mode.physicalWidth,
                height = mode.physicalHeight,
                refreshRate = mode.refreshRate,
                isCurrent = mode.modeId == currentModeId,
            )
        }
}

private fun readIntValue(instance: Any, vararg names: String): Int? {
    return names.firstNotNullOfOrNull { name ->
        runCatching {
            instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance) as? Int
        }.getOrNull()
    }
}

private fun readFloatValue(instance: Any, vararg names: String): Float? {
    return names.firstNotNullOfOrNull { name ->
        runCatching {
            when (val value = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)) {
                null -> null
                is Float -> value
                is Double -> value.toFloat()
                is Number -> value.toFloat()
                else -> null
            }
        }.getOrNull()
    }
}

private fun callIntValue(instance: Any, name: String): Int? {
    return runCatching {
        instance.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(instance) as? Int
    }.getOrNull()
}

private fun callFloatValue(instance: Any, name: String): Float? {
    return runCatching {
        when (val value = instance.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(instance)) {
            null -> null
            is Float -> value
            is Double -> value.toFloat()
            is Number -> value.toFloat()
            else -> null
        }
    }.getOrNull()
}

private fun readSavedModeId(context: Context): Int? = RefreshRatePreferences.readSelectedModeId(context)

private fun saveSelectedMode(context: Context, mode: DisplayModeInfo) {
    RefreshRatePreferences.saveSelectedMode(
        context,
        SavedRefreshRateMode(
            id = mode.id,
            surfaceFlingerModeIndex = mode.surfaceFlingerModeIndex,
            width = mode.width,
            height = mode.height,
            refreshRate = mode.refreshRate,
        )
    )
}

private fun clearSavedModeId(context: Context) {
    RefreshRatePreferences.clearSelectedMode(context)
}

private suspend fun applyPreferredMode(context: Context, mode: DisplayModeInfo): Boolean {
    val appliedByController = RefreshRateControllerClient.setRefreshRateMode(
        context,
        mode.surfaceFlingerModeIndex
    )
    if (appliedByController == true) return true
    return ShellLogger.exec(
        "RefreshRate apply fallback ${formatRefreshRate(mode.refreshRate)}",
        surfaceFlingerModeCommand(context, mode.surfaceFlingerModeIndex),
    ).isSuccess
}

private suspend fun clearPreferredMode(context: Context): Boolean {
    if (RefreshRateControllerClient.resetRefreshRateMode(context) == true) return true
    return ShellLogger.exec(
        "RefreshRate clear fallback",
        surfaceFlingerResetCommand(context),
    ).isSuccess
}

private fun readAutoStartEnabled(context: Context): Boolean =
    RefreshRatePreferences.readAutoStartEnabled(context)

private fun setAutoStartEnabled(context: Context, enabled: Boolean) {
    RefreshRatePreferences.setAutoStartEnabled(context, enabled)
}

private suspend fun readForceRefreshEnabled(context: Context): Boolean {
    RefreshRateControllerClient.isRefreshRateDisplayEnabled(context)?.let { return it }
    val result = ShellLogger.exec(
        "RefreshRate force status fallback",
        surfaceFlingerForceStatusCommand(context),
    )
    return result.isSuccess && result.out.lastOrNull()?.trim() == "1"
}

private suspend fun setForceRefreshEnabled(context: Context, enabled: Boolean): Boolean {
    if (RefreshRateControllerClient.setRefreshRateDisplayEnabled(context, enabled) == true) return true
    val result = ShellLogger.exec(
        "RefreshRate force fallback enabled=$enabled",
        surfaceFlingerForceEnabledCommand(context, enabled),
    )
    return result.isSuccess
}

private fun surfaceFlingerModeCommand(context: Context, modeIndex: Int): String {
    val apkPath = context.applicationInfo.sourceDir
    return "CLASSPATH=${shellQuote(apkPath)} app_process /system/bin com.mi.onextbox.tools.SurfaceFlingerModeTool set $modeIndex"
}

private fun surfaceFlingerForceEnabledCommand(context: Context, enabled: Boolean): String {
    val apkPath = context.applicationInfo.sourceDir
    val value = if (enabled) 1 else 0
    return "CLASSPATH=${shellQuote(apkPath)} app_process /system/bin com.mi.onextbox.tools.SurfaceFlingerModeTool force $value"
}

private fun surfaceFlingerForceStatusCommand(context: Context): String {
    val apkPath = context.applicationInfo.sourceDir
    return "CLASSPATH=${shellQuote(apkPath)} app_process /system/bin com.mi.onextbox.tools.SurfaceFlingerModeTool force-status"
}

private fun surfaceFlingerListCommand(context: Context): String {
    val apkPath = context.applicationInfo.sourceDir
    return "CLASSPATH=${shellQuote(apkPath)} app_process /system/bin com.mi.onextbox.tools.SurfaceFlingerModeTool list"
}

private fun surfaceFlingerResetCommand(context: Context): String {
    val apkPath = context.applicationInfo.sourceDir
    return "CLASSPATH=${shellQuote(apkPath)} app_process /system/bin com.mi.onextbox.tools.SurfaceFlingerModeTool reset"
}

private fun shellQuote(value: String): String {
    return "'${value.replace("'", "'\"'\"'")}'"
}

private fun formatRefreshRate(refreshRate: Float): String {
    val rounded = refreshRate.roundToInt()
    val text = if (abs(refreshRate - rounded) < 0.01f) {
        rounded.toString()
    } else {
        String.format(Locale.US, "%.2f", refreshRate).trimEnd('0').trimEnd('.')
    }
    return "$text Hz"
}

package com.mi.onextbox.ui.screens

import android.widget.Toast
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
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FluidCloudFeaturesPage() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember(context) { mutableStateOf(LspConfig.isFluidCloudMaterialEnabled(context)) }
    var applying by remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !applying) {
                enabled = LspConfig.isFluidCloudMaterialEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsSection(title = stringResource(R.string.feature_fluid_cloud_card_group))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_fluid_cloud_material_title),
            summary = "",
            checked = enabled,
            enabled = !applying,
            onCheckedChange = { checked ->
                applying = true
                enabled = checked
                scope.launch {
                    try {
                        val success = withContext(Dispatchers.IO) {
                            LspConfig.setFluidCloudMaterialEnabled(context, checked)
                            LspConfig.isFluidCloudMaterialEnabled(context) == checked
                        }
                        enabled = LspConfig.isFluidCloudMaterialEnabled(context)
                        if (!success) {
                            Toast.makeText(context, R.string.feature_fluid_cloud_material_failed, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        applying = false
                    }
                }
            },
        )
    }
}

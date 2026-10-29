package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.ClockPeriodSettings
import com.mi.onextbox.lsp.SystemAdaptationVersion
import com.mi.onextbox.ui.common.rememberSystemAdaptationVersion
import com.mi.onextbox.ui.settings.SettingsRecommendedLinks
import io.github.suqi8.coui.kmp.preference.RecommendedItem

internal object FeatureRecommendations {
    private val titlesByPage = mapOf(
        FeaturePageMode.Screenshot to listOf(R.string.feature_screen_capture_title),
        FeaturePageMode.ScreenRecording to listOf(R.string.feature_screen_capture_title),
        FeaturePageMode.SystemUiSmallWindow to listOf(
            R.string.translation_allow_floating_title,
            R.string.hide_screenshot_floating_title,
        ),
        FeaturePageMode.SystemUiFluidCloud to listOf(
            R.string.phone_manager_risk_cloud_title,
            R.string.secure_pay_hide_risk_cloud_title,
            R.string.native_live_update_title,
        ),
        FeaturePageMode.SystemUiNotificationCenter to listOf(
            R.string.clock_period_shade,
            R.string.notification_batch_title,
        ),
        FeaturePageMode.Aod to listOf(R.string.keyguard_aod_screenshot_title),
        FeaturePageMode.AppInstaller to listOf(
            R.string.installer_native_ui_title,
            R.string.installer_title,
        ),
        FeaturePageMode.PackageInstaller to listOf(
            R.string.installer_skip_detection_title,
            R.string.installer_remove_ads_title,
        ),
    )

    fun destinations(page: FeaturePageMode, version: SystemAdaptationVersion): List<FeatureSearchIndex.Entry> {
        val titles = titlesByPage[page] ?: return emptyList()
        val available = featureSearchEntries(version).associateBy { it.title }
        return titles.mapNotNull(available::get).filter { it.page != page }.distinctBy { it.title }.take(5)
    }
}

@Composable
internal fun FeatureRecommendedLinks(page: FeaturePageMode, onOpenSubPage: (FeaturePageMode) -> Unit) {
    val version = rememberSystemAdaptationVersion()
    val destinations = remember(page, version) { FeatureRecommendations.destinations(page, version) }
    if (destinations.isEmpty()) return
    val context = LocalContext.current
    val spotlight = LocalFeatureSpotlight.current
    val openPage by rememberUpdatedState(onOpenSubPage)
    val items = destinations.map { destination ->
        val title = stringResource(destination.title)
        remember(title, destination, context, spotlight) {
            RecommendedItem(title) {
                val targetTitle = when {
                    destination.title == R.string.installer_title -> R.string.installer_enable
                    destination.title == R.string.clock_period_shade && !ClockPeriodSettings.state(context).enabled ->
                        R.string.clock_period_title
                    else -> destination.title
                }
                if (destination.page != FeaturePageMode.NotificationBatch) {
                    spotlight?.show(destination.copy(title = targetTitle))
                }
                openPage(destination.page)
            }
        }
    }
    SettingsRecommendedLinks(items)
}

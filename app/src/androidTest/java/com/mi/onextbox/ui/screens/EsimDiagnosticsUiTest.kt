package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mi.onextbox.R
import com.mi.onextbox.lsp.EsimDiagnosticsSnapshot
import com.mi.onextbox.lsp.EsimProfileDiagnostic
import com.mi.onextbox.ui.common.AnimatedDataContent
import com.mi.onextbox.ui.settings.SettingsGroup
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EsimDiagnosticsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun waitingAndRefreshedDiagnosticsKeepRowsInVerticalOrder() {
        val snapshot = mutableStateOf<EsimDiagnosticsSnapshot?>(null)
        lateinit var waiting: String
        lateinit var refresh: String
        lateinit var readyRows: List<String>
        compose.setContent {
            waiting = stringResource(R.string.feature_esim_diagnostics_waiting_title)
            refresh = stringResource(R.string.feature_esim_diagnostics_refresh_title)
            readyRows = listOf(
                stringResource(R.string.feature_esim_diagnostics_euicc_title),
                stringResource(R.string.feature_esim_diagnostics_profiles_title),
                stringResource(R.string.feature_esim_diagnostics_profile_item_title, 1),
                stringResource(R.string.feature_esim_diagnostics_profile_item_title, 2),
                stringResource(R.string.feature_esim_diagnostics_environment_title),
                stringResource(R.string.feature_esim_diagnostics_module_title),
                refresh,
            )
            COUITheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsGroup {
                        AnimatedDataContent(snapshot.value) { displayed ->
                            EsimDiagnosticsContent(displayed, false, false, false, onRefresh = {})
                        }
                    }
                }
            }
        }

        assertVerticalOrder(listOf(waiting, refresh))
        val ready = EsimDiagnosticsSnapshot(hookReady = true, profiles = listOf(
            EsimProfileDiagnostic(displayName = "Profile A"),
            EsimProfileDiagnostic(displayName = "Profile B"),
        ))
        compose.runOnIdle { snapshot.value = ready }
        assertVerticalOrder(readyRows)
        compose.runOnIdle { snapshot.value = ready.copy(profiles = emptyList()) }
        assertVerticalOrder(readyRows.filterIndexed { index, _ -> index !in 2..3 })
    }

    private fun assertVerticalOrder(titles: List<String>) {
        val rows = titles.map { compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode() }
        rows.zipWithNext().forEach { (previous, next) ->
            val bottom = previous.positionInRoot.y + previous.size.height
            assertTrue("Expected the next diagnostics title below the previous one",
                next.positionInRoot.y >= bottom)
        }
    }
}

package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BatteryFeaturesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun batteryInformationRowsFollowCycleCountWithoutDescriptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val titles = listOf(
            R.string.battery_show_cycle_count_title, R.string.battery_show_health_status_title,
            R.string.battery_show_full_charge_capacity_title, R.string.battery_show_rated_capacity_title,
            R.string.battery_show_typical_capacity_title,
            R.string.battery_show_calculated_health_title,
        ).map(context::getString)
        compose.setContent { COUITheme { Column { BatteryFeaturesPage() } } }
        val bounds = titles.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
        bounds.zipWithNext().forEach { (above, below) -> assertTrue(above.bottom <= below.top) }
        val text = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().let { root ->
            fun count(node: androidx.compose.ui.semantics.SemanticsNode): Int =
                (node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }).size +
                    node.children.sumOf(::count)
            count(root)
        }
        // Two headings, nine titles and three optimization descriptions.
        assertTrue(text == 14)
    }
}

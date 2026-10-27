package com.mi.onextbox.ui.settings

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Guard the shared controls as well as their callers: saving must never become a visual state. */
class SettingsInteractionContractTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private val ui = File(app, "src/main/java/com/mi/onextbox/ui")
    private fun source(path: String) = File(ui, "$path.kt").readText()

    @Test fun preferenceWritesUseInteractionLocksInsteadOfDisabledPalettes() {
        val controls = Regex("\\b(?:SettingsToggleRow|SettingsSwitchPreference|SettingsWindowDropdownPreference|OptionDropdownRow|FeatureSliderRow|FeatureSettingsSlider|ColorOsSettingsSliderRow|OutlinedTextField|CouiTextField)\\s*\\(")
        val visualArgument = Regex("(?<![\\w.])(?:enabled|switchEnabled)\\s*=\\s*([^,;\\r\\n]+)")
        val transientState = Regex("\\b(?:saving\\w*|enhancementSaving|changing|applying)\\b", RegexOption.IGNORE_CASE)
        val violations = mutableListOf<String>()
        ui.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            controls.findAll(text).forEach { call ->
                val arguments = balancedArguments(text, call.range.last)
                visualArgument.findAll(arguments).forEach { argument ->
                    if (transientState.containsMatchIn(argument.groupValues[1])) {
                        violations += "${file.relativeTo(ui)}: ${argument.value.trim()}"
                    }
                }
            }
        }
        assertTrue("Temporary writes must use interactionEnabled:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test fun gateKeepsItsNodesAndBlocksTouchKeyboardAndAccessibilityWithoutChangingColors() {
        val gate = source("settings/SettingsInteraction")
        listOf("rememberUpdatedState(interactionEnabled)", ".pointerInput(Unit)", "PointerEventPass.Initial",
            "it.consume()", ".onPreviewKeyEvent", "Key.Back", "Key.Escape", "disabled()").forEach {
            assertTrue(it, gate.contains(it))
        }
        listOf(".alpha(", "graphicsLayer", "Color.", "if (!interactionEnabled) return", "key(interactionEnabled)")
            .forEach { assertFalse(it, gate.contains(it)) }
        val switches = source("settings/SettingsPreferenceRows").substringBefore("internal fun SettingsWindowDropdownPreference")
        assertEquals(1, Regex("enabled = enabled && switchEnabled,").findAll(switches).count())
        assertEquals(1, Regex("modifier = Modifier.settingsInteractionGate\\(interactionEnabled\\)").findAll(switches).count())
        assertTrue(switches.contains("if (currentInteractionEnabled && currentSwitchEnabled) currentOnCheckedChange(it)"))
        assertFalse(switches.contains("enabled && switchEnabled && interactionEnabled"))
    }

    @Test fun popupCallbacksGuardTheSeparateWindowAndPreserveUnavailableItems() {
        val rows = source("settings/SettingsPreferenceRows")
        assertTrue(rows.contains("ColorOsWindowDropdownPreference("))
        val coui = source("settings/ColorOsDropdownPreference")
        assertTrue(coui.contains("if (interactionEnabled && actualEnabled)"))
        assertTrue(coui.contains("takeIf { it.enabled }"))
    }

    @Test fun onlyCouiPreferenceRowsRemain() {
        val row = source("settings/SettingsPreferenceRows")
        assertTrue(row.contains("ColorOsSwitch("))
        assertFalse(row.contains("Material3ExpressivePreferenceRow"))
    }

    @Test fun groupAnimationRetainsSemanticKeysAndOnlyFadesVisibilityTransitions() {
        val groups = source("screens/FeatureAnimatedGroup")
        assertTrue(groups.contains("key(identity)"))
        assertTrue(groups.contains("remember { MutableTransitionState(scope.visibility.getValue(identity)) }"))
        assertTrue(groups.contains("visibleState = transitions[rowIndex]"))
        assertFalse(Regex("(?:key|remember)\\([^)]*(?:saving|checked)").containsMatchIn(groups))
    }

    private fun balancedArguments(text: String, opening: Int): String {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in opening until text.length) {
            val char = text[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == quote) quote = null
            } else when (char) {
                '"', '\'' -> quote = char
                '(' -> depth++
                ')' -> if (--depth == 0) return text.substring(opening + 1, index)
            }
        }
        error("Unclosed settings control at $opening")
    }
}

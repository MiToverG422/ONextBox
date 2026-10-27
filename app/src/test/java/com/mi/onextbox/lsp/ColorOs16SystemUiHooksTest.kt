package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOs16SystemUiHooksTest {
    private val allFeatures = ColorOs16SystemUiHooks.Feature.entries.toSet()

    @Test fun verifiedContractsDispatchWithoutInvokingOrConstructingTargets() {
        val installed = mutableListOf<ColorOs16SystemUiHooks.Feature>()
        val failures = mutableListOf<ColorOs16SystemUiHooks.Feature>()
        ColorOs16SystemUiHooks.dispatch(classes()::getValue, installed::add) { feature, _ ->
            failures.add(feature)
        }
        assertEquals(allFeatures, installed.toSet())
        assertEquals(allFeatures.size, installed.size)
        assertTrue(failures.isEmpty())
    }

    @Test fun eachMissingClassOnlySkipsTheFeatureThatUsesIt() {
        val owners = mapOf(
            ColorOs16SystemUiHooks.CLEAR_CONTROLLER to ColorOs16SystemUiHooks.Feature.HideNotificationClear,
            ColorOs16SystemUiHooks.CLEAR_BUTTON to ColorOs16SystemUiHooks.Feature.HideNotificationClear,
            ColorOs16SystemUiHooks.ROTATION_CONTROLLER to ColorOs16SystemUiHooks.Feature.HideRotationSuggestion,
            ColorOs16SystemUiHooks.BLUETOOTH_POLICY to ColorOs16SystemUiHooks.Feature.HideDisconnectedBluetooth,
            ColorOs16SystemUiHooks.ICON_CONTROLLER to ColorOs16SystemUiHooks.Feature.HideDisconnectedBluetooth,
            ColorOs16SystemUiHooks.ICON_CONTROLLER_IMPL to ColorOs16SystemUiHooks.Feature.HideDisconnectedBluetooth,
        )
        owners.forEach { (missing, feature) ->
            assertOnlyFeatureRejected(feature, classes() - missing)
        }
    }

    @Test fun notificationRejectsWrongParametersReturnTypeAndStaticMethods() {
        listOf(
            ClearWrongParameters::class.java,
            ClearWrongButton::class.java,
            ClearWrongReturn::class.java,
            ClearStaticMethod::class.java,
        ).forEach { target ->
            assertOnlyFeatureRejected(ColorOs16SystemUiHooks.Feature.HideNotificationClear,
                classes() + (ColorOs16SystemUiHooks.CLEAR_CONTROLLER to target))
        }
    }

    @Test fun rotationRejectsBoxedBooleansReturnTypeAndStaticMethods() {
        listOf(RotationBoxedParameter::class.java, RotationWrongReturn::class.java,
            RotationStaticMethod::class.java).forEach { target ->
            assertOnlyFeatureRejected(ColorOs16SystemUiHooks.Feature.HideRotationSuggestion,
                classes() + (ColorOs16SystemUiHooks.ROTATION_CONTROLLER to target))
        }
    }

    @Test fun bluetoothRequiresTheObservedUpdateEntryAndTypedInstanceFields() {
        listOf(
            BluetoothUnverifiedEntry::class.java,
            BluetoothWrongReturn::class.java,
            BluetoothWrongConnected::class.java,
            BluetoothWrongSlot::class.java,
            BluetoothWrongController::class.java,
            BluetoothStaticConnected::class.java,
        ).forEach { target ->
            assertOnlyFeatureRejected(ColorOs16SystemUiHooks.Feature.HideDisconnectedBluetooth,
                classes() + (ColorOs16SystemUiHooks.BLUETOOTH_POLICY to target))
        }
    }

    @Test fun bluetoothRejectsAnUnrelatedImplementationEvenWithTheSameMethod() {
        assertOnlyFeatureRejected(ColorOs16SystemUiHooks.Feature.HideDisconnectedBluetooth,
            classes() + (ColorOs16SystemUiHooks.ICON_CONTROLLER_IMPL to UnrelatedIconController::class.java))
    }

    @Test fun bluetoothRejectsWrongVisibilityReturnAndStaticMethods() {
        listOf(IconControllerWrongReturn::class.java, IconControllerStaticMethod::class.java)
            .forEach { target ->
                assertOnlyFeatureRejected(ColorOs16SystemUiHooks.Feature.HideDisconnectedBluetooth,
                    classes() + (ColorOs16SystemUiHooks.ICON_CONTROLLER_IMPL to target))
            }
    }

    @Test fun anInstallerFailureStillDispatchesEveryOtherCompatibleFeature() {
        allFeatures.forEach { rejected ->
            val installed = mutableListOf<ColorOs16SystemUiHooks.Feature>()
            val failures = mutableListOf<ColorOs16SystemUiHooks.Feature>()
            ColorOs16SystemUiHooks.dispatch(classes()::getValue, { feature ->
                if (feature == rejected) error("Installer failure")
                installed.add(feature)
            }) { feature, _ -> failures.add(feature) }
            assertEquals(allFeatures - rejected, installed.toSet())
            assertEquals(listOf(rejected), failures)
        }
    }

    @Test fun aFailureReporterCannotDisableTheRemainingFeatures() {
        val installed = mutableListOf<ColorOs16SystemUiHooks.Feature>()
        ColorOs16SystemUiHooks.dispatch(
            (classes() - ColorOs16SystemUiHooks.CLEAR_CONTROLLER)::getValue,
            installed::add,
        ) { _, _ -> error("Reporter failure") }
        assertEquals(allFeatures - ColorOs16SystemUiHooks.Feature.HideNotificationClear, installed.toSet())
    }

    private fun assertOnlyFeatureRejected(
        rejected: ColorOs16SystemUiHooks.Feature,
        targets: Map<String, Class<*>>,
    ) {
        val installed = mutableListOf<ColorOs16SystemUiHooks.Feature>()
        val failures = mutableListOf<ColorOs16SystemUiHooks.Feature>()
        ColorOs16SystemUiHooks.dispatch(targets::getValue, installed::add) { feature, _ ->
            failures.add(feature)
        }
        assertEquals(targets.toString(), allFeatures - rejected, installed.toSet())
        assertEquals(listOf(rejected), failures)
    }

    private fun classes(): Map<String, Class<*>> = mapOf(
        ColorOs16SystemUiHooks.CLEAR_CONTROLLER to ClearController::class.java,
        ColorOs16SystemUiHooks.CLEAR_BUTTON to ClearButton::class.java,
        ColorOs16SystemUiHooks.ROTATION_CONTROLLER to RotationController::class.java,
        ColorOs16SystemUiHooks.BLUETOOTH_POLICY to BluetoothPolicy::class.java,
        ColorOs16SystemUiHooks.ICON_CONTROLLER to IconController::class.java,
        ColorOs16SystemUiHooks.ICON_CONTROLLER_IMPL to NativeIconController::class.java,
    )

    // Valid fixtures throw on construction and invocation: successful metadata checks must do neither.
    private class ClearButton
    private class ClearController {
        init { error("Must not construct a target") }
        fun setVisible(button: ClearButton, visible: Boolean, animated: Boolean): Unit = error("Must not invoke")
    }
    private class RotationController {
        init { error("Must not construct a target") }
        fun setRotateSuggestionButtonState(visible: Boolean, immediate: Boolean): Unit = error("Must not invoke")
    }
    private interface IconController
    private class NativeIconController : IconController {
        init { error("Must not construct a target") }
        fun setIconVisibility(slot: String, visible: Boolean): Unit = error("Must not invoke")
    }
    private class BluetoothPolicy {
        init { error("Must not construct a target") }
        var bluetoothConnected: Boolean = false
        val slotBluetooth: String = "bluetooth"
        val statusBarIconController: IconController? = null
        fun `updateBluetooth$1`(): Unit = error("Must not invoke")
    }

    private class ClearWrongParameters {
        fun setVisible(button: ClearButton, visible: Boolean) = Unit
    }
    private class ClearWrongButton {
        fun setVisible(button: Any, visible: Boolean, animated: Boolean) = Unit
    }
    private class ClearWrongReturn {
        fun setVisible(button: ClearButton, visible: Boolean, animated: Boolean): Int = 0
    }
    private class ClearStaticMethod {
        companion object {
            @JvmStatic fun setVisible(button: ClearButton, visible: Boolean, animated: Boolean) = Unit
        }
    }
    private class RotationBoxedParameter {
        fun setRotateSuggestionButtonState(visible: Boolean?, immediate: Boolean) = Unit
    }
    private class RotationWrongReturn {
        fun setRotateSuggestionButtonState(visible: Boolean, immediate: Boolean): Boolean = false
    }
    private class RotationStaticMethod {
        companion object {
            @JvmStatic fun setRotateSuggestionButtonState(visible: Boolean, immediate: Boolean) = Unit
        }
    }
    private class BluetoothUnverifiedEntry {
        fun updateBluetooth() = Unit
    }
    private class BluetoothWrongReturn {
        fun `updateBluetooth$1`(): Boolean = false
    }
    private class BluetoothWrongConnected {
        val bluetoothConnected: Int = 0
        fun `updateBluetooth$1`() = Unit
    }
    private class BluetoothWrongSlot {
        val bluetoothConnected: Boolean = false
        val slotBluetooth: Int = 0
        fun `updateBluetooth$1`() = Unit
    }
    private class BluetoothWrongController {
        val bluetoothConnected: Boolean = false
        val slotBluetooth: String = "bluetooth"
        val statusBarIconController: Any? = null
        fun `updateBluetooth$1`() = Unit
    }
    private class BluetoothStaticConnected {
        fun `updateBluetooth$1`() = Unit
        companion object {
            @JvmField val bluetoothConnected: Boolean = false
        }
    }
    private class UnrelatedIconController {
        fun setIconVisibility(slot: String, visible: Boolean) = Unit
    }
    private class IconControllerWrongReturn : IconController {
        fun setIconVisibility(slot: String, visible: Boolean): Boolean = false
    }
    private class IconControllerStaticMethod : IconController {
        companion object {
            @JvmStatic fun setIconVisibility(slot: String, visible: Boolean) = Unit
        }
    }
}

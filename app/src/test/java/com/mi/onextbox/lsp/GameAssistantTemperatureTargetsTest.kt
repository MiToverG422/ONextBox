package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class GameAssistantTemperatureTargetsTest {
    @Test fun coldStartDiscoveryDoesNotInitializeContextDependentClasses() {
        val targets = GameAssistantTemperatureTargets(
            DeferredModel::class.java,
            RenamedHelper::class.java.getDeclaredMethod("q"),
        )
        assertEquals(0, HostState.modelInitializations)
        assertEquals(0, HostState.helperInitializations)

        HostState.applicationReady = true
        val labels = targets.readLabels()!!
        assertEquals(2, labels.index)
        assertEquals(listOf("Battery", "%", "--"), labels.battery)
        assertEquals(listOf("Temperature", " °C", "--"), labels.temperature)
        assertEquals(36.5f, targets.readCelsius(), 0f)
        assertEquals(1, HostState.modelInitializations)
        assertEquals(1, HostState.helperInitializations)

        assertEquals(labels, targets.readLabels())
        assertEquals(36.5f, targets.readCelsius(), 0f)
        assertEquals(1, HostState.modelInitializations)
        assertEquals(1, HostState.helperInitializations)
    }

    @Test fun anEarlyStaticReadPoisonsTheClassEvenAfterTheContextBecomesReady() {
        val field = PoisonedModel::class.java.getField("INSTANCE")
        assertThrows(ExceptionInInitializerError::class.java) { field.get(null) }
        PoisonState.applicationReady = true
        assertThrows(NoClassDefFoundError::class.java) { field.get(null) }
    }

    @Test fun missingBatterySlotRetainsNativeLayout() {
        val targets = GameAssistantTemperatureTargets(
            NoBatteryModel::class.java,
            StaticHelper::class.java.getDeclaredMethod("read"),
        )
        assertNull(targets.readLabels())
    }

    @Test fun nativeSingletonAndStaticTemperatureReadersAreSupported() {
        val singleton = GameAssistantTemperatureTargets(
            NoBatteryModel::class.java,
            SingletonHelper::class.java.getDeclaredMethod("read"),
        )
        val static = GameAssistantTemperatureTargets(
            NoBatteryModel::class.java,
            StaticHelper::class.java.getDeclaredMethod("read"),
        )
        assertEquals(34f, singleton.readCelsius(), 0f)
        assertEquals(35f, static.readCelsius(), 0f)
    }

    @Test fun incompatibleMembersAreRejectedWithoutInitializingTheModel() {
        assertThrows(NoSuchMethodException::class.java) {
            GameAssistantTemperatureTargets(
                UnsupportedModel::class.java,
                StaticHelper::class.java.getDeclaredMethod("read"),
            )
        }
        assertEquals(0, UnsupportedState.initializations)
    }

    @Test fun disabledDisplaysDoNotInitializeTheNewStaticTemperatureHelper() {
        val targets = GameAssistantTemperatureTargets(
            DeferredStaticModel::class.java,
            DeferredStaticHelper::class.java.getDeclaredMethod("a"),
        )
        assertNull(targets.updateArguments(listOf("battery", "42", true), false))
        assertNull(targets.toolbarArguments(listOf("42", 123, " %", "Battery", false), 0, false))
        assertEquals(0, StaticHostState.modelInitializations)
        assertEquals(0, StaticHostState.helperInitializations)

        StaticHostState.applicationReady = true
        assertEquals("35", targets.readDisplayValue())
        assertEquals(1, StaticHostState.helperInitializations)
        assertEquals(0, StaticHostState.modelInitializations)
        assertNotNull(targets.readLabels())
        assertEquals(1, StaticHostState.modelInitializations)
    }

    @Test fun toolbarFormatterSupportsBothParameterOrdersWithoutChangingNativeColors() {
        val targets = toolbarTargets()
        for ((parameters, original) in listOf(
            arrayOf<Class<*>>(String::class.java, Integer.TYPE, String::class.java, String::class.java, java.lang.Boolean.TYPE) to
                listOf("42", 0xFF12BBBB.toInt(), " %", "Battery", false),
            arrayOf<Class<*>>(Integer.TYPE, String::class.java, String::class.java, String::class.java, java.lang.Boolean.TYPE) to
                listOf(0xFF12BBBB.toInt(), "42", " %", "Battery", false),
        )) {
            val valueIndex = GameAssistantTemperatureTargets.toolbarValueIndex(parameters)!!
            val input = java.util.Collections.unmodifiableList(original)
            val result = targets.toolbarArguments(input, valueIndex, true)!!
            assertEquals("35", result[valueIndex])
            assertEquals(original[1 - valueIndex], result[1 - valueIndex])
            assertEquals(" °C", result[2])
            assertEquals("Temperature", result[3])
            assertEquals(false, result[4])
            assertEquals("42", input[valueIndex])
            assertEquals(" %", input[2])
        }
        assertNull(GameAssistantTemperatureTargets.toolbarValueIndex(arrayOf(
            String::class.java, String::class.java, String::class.java, String::class.java, java.lang.Boolean.TYPE,
        )))
    }

    @Test fun chargingAndCachedBatteryLabelsCannotOverwriteTheToolbarTemperature() {
        val targets = toolbarTargets()
        for (title in listOf("Battery", "Temperature", "Bypass charging")) {
            for (unit in listOf(" %", " °C")) {
                val result = targets.toolbarArguments(listOf("18", 123, unit, title, true), 0, true)!!
                assertEquals(listOf("35", 123, " °C", "Temperature", false), result.toList())
            }
        }
    }

    @Test fun unrelatedToolbarValuesAndDisabledOptionsRetainTheNativeOutput() {
        val targets = GameAssistantTemperatureTargets(
            ToolbarModel::class.java, UnreadableHelper::class.java.getDeclaredMethod("read"),
        )
        for (unit in listOf("fps", " fps", "ms", " ms", "")) {
            assertNull(targets.toolbarArguments(listOf("55", 123, unit, "Native", false), 0, true))
        }
        assertNull(targets.toolbarArguments(listOf("42", 123, " %", "Battery", false), 0, false))
        assertNull(targets.updateArguments(listOf("FPS", "55", false), true))
        assertNull(targets.updateArguments(listOf("network", "20.7", false), true))
        assertNull(targets.updateArguments(listOf("battery", "42", true), false))
    }

    @Test fun batteryUpdatesCarryTemperatureBeforeNativeDispatch() {
        val targets = toolbarTargets()
        val original = listOf("battery", "18", true)
        val queued = targets.updateArguments(original, true)!!.toList()
        assertEquals(listOf("battery", "35", false), queued)
        assertEquals(listOf("battery", "18", true), original)
        assertNull(targets.updateArguments(original, false))
    }

    @Test fun invalidTemperatureReadingsUseAPlaceholderInsteadOfBatteryPercentages() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 101f)) {
            assertEquals("--", GameAssistantTemperatureTargets.formatCelsius(value))
        }
        assertEquals("36", GameAssistantTemperatureTargets.formatCelsius(36.9f))
        assertEquals("0", GameAssistantTemperatureTargets.formatCelsius(0f))
        assertEquals("100", GameAssistantTemperatureTargets.formatCelsius(100f))
    }

    @Test fun unavailableSensorNeverDisplaysBatteryPercentageAsTemperature() {
        val targets = GameAssistantTemperatureTargets(
            ToolbarModel::class.java, UnreadableHelper::class.java.getDeclaredMethod("read"),
        )
        assertEquals(listOf("battery", "--", false),
            targets.updateArguments(listOf("battery", "42", true), true)!!.toList())
        assertEquals(listOf("--", 123, " °C", "Temperature", false),
            targets.toolbarArguments(listOf("42", 123, " %", "Battery", false), 0, true)!!.toList())
    }

    private fun toolbarTargets() = GameAssistantTemperatureTargets(
        ToolbarModel::class.java, StaticHelper::class.java.getDeclaredMethod("read"),
    )

    class ToolbarModel {
        fun getStatusList() = listOf("network", "FPS", "battery")
        fun getBatteryStatus() = arrayOf("Battery", "%", "--")
        fun getSmallTemperatureStatus() = arrayOf("Temperature", " °C", "--")
        companion object { @JvmField val INSTANCE = ToolbarModel() }
    }

    object UnreadableHelper { @JvmStatic fun read(): Float = error("Temperature reader must not run") }

    object StaticHostState {
        var applicationReady = false
        var modelInitializations = 0
        var helperInitializations = 0
    }

    class DeferredStaticModel {
        fun getStatusList() = listOf("network", "FPS", "battery")
        fun getBatteryStatus() = arrayOf("Battery", "%", "--")
        fun getSmallTemperatureStatus() = arrayOf("Temperature", " °C", "--")
        companion object {
            @JvmField val INSTANCE = DeferredStaticModel().also {
                check(StaticHostState.applicationReady)
                StaticHostState.modelInitializations++
            }
        }
    }

    class DeferredStaticHelper {
        companion object {
            init {
                check(StaticHostState.applicationReady)
                StaticHostState.helperInitializations++
            }
            @JvmStatic fun a() = 35f
        }
    }

    object HostState {
        var applicationReady = false
        var modelInitializations = 0
        var helperInitializations = 0
    }

    class DeferredModel private constructor() {
        init {
            check(HostState.applicationReady) { "applicationContext has not been init." }
            HostState.modelInitializations++
        }

        fun getStatusList() = listOf("network", "FPS", "battery")
        fun getBatteryStatus() = arrayOf("Battery", "%", "--")
        fun getSmallTemperatureStatus() = arrayOf("Temperature", " °C", "--")

        companion object {
            @JvmField val INSTANCE = DeferredModel()
        }
    }

    class RenamedHelper private constructor() {
        init {
            check(HostState.applicationReady) { "applicationContext has not been init." }
            HostState.helperInitializations++
        }

        fun q() = 36.5f

        class Factory {
            private val singleton by lazy { RenamedHelper() }
            fun a(): RenamedHelper = singleton
        }

        companion object {
            @JvmField val z = Factory()
        }
    }

    object PoisonState { var applicationReady = false }

    class PoisonedModel {
        companion object {
            @JvmField val INSTANCE = PoisonedModel().also {
                check(PoisonState.applicationReady) { "applicationContext has not been init." }
            }
        }
    }

    class NoBatteryModel {
        fun getStatusList() = listOf("network", "FPS")
        fun getBatteryStatus(): Array<String> = error("No battery slot")
        fun getSmallTemperatureStatus(): Array<String> = error("No battery slot")

        companion object {
            @JvmField val INSTANCE = NoBatteryModel()
        }
    }

    object SingletonHelper { fun read() = 34f }
    object StaticHelper { @JvmStatic fun read() = 35f }
    object UnsupportedState { var initializations = 0 }
    class UnsupportedModel {
        companion object {
            @JvmField val INSTANCE = UnsupportedModel().also { UnsupportedState.initializations++ }
        }
    }
}

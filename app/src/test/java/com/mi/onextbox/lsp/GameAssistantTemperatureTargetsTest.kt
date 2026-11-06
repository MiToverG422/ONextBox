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

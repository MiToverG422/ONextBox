package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSearchTargetsTest {
    private interface Search

    private class NamedManager : Search {
        companion object {
            @JvmField val INSTANCE = NamedManager()
            @JvmField var mBottomSearchProxy: Search? = null
        }
    }

    private class ObfuscatedManager : Search {
        companion object {
            @JvmField val INSTANCE = ObfuscatedManager()
            @JvmField var a: Search? = null
        }
    }

    private class AmbiguousManager : Search {
        companion object {
            @JvmField val INSTANCE = AmbiguousManager()
            @JvmField var a: Search? = null
            @JvmField var b: Search? = null
        }
    }

    @Test fun namedApiResolvesProxyInsteadOfAssignableManagerSingleton() {
        assertEquals("mBottomSearchProxy", LauncherSearchTargets.ColorOs16
            .resolveProxyField(NamedManager::class.java, Search::class.java)?.name)
    }

    @Test fun obfuscatedApiResolvesProxyInsteadOfAssignableManagerSingleton() {
        assertEquals("a", LauncherSearchTargets.ColorOs17
            .resolveProxyField(ObfuscatedManager::class.java, Search::class.java)?.name)
    }

    @Test fun namedApiRejectsUnexpectedSchemaInsteadOfGuessing() {
        assertNull(LauncherSearchTargets.ColorOs16
            .resolveProxyField(ObfuscatedManager::class.java, Search::class.java))
    }

    @Test fun obfuscatedApiRejectsAmbiguousProxyFields() {
        assertNull(LauncherSearchTargets.ColorOs17
            .resolveProxyField(AmbiguousManager::class.java, Search::class.java))
    }

    @Test fun availabilityAcceptsBothSchemasWithInheritedRefreshWithoutConstructingInstances() {
        ProbeConstructions.count = 0
        for (targets in listOf(LauncherSearchTargets.ColorOs16, LauncherSearchTargets.ColorOs17)) {
            val classes = probeClasses(targets)
            assertTrue(targets.manager, targets.isAvailable(classes::getValue))
            assertEquals(0, ProbeConstructions.count)
        }
    }

    @Test fun availabilityRejectsAnImplementationWithoutTheRefreshMethod() {
        ProbeConstructions.count = 0
        for (targets in listOf(LauncherSearchTargets.ColorOs16, LauncherSearchTargets.ColorOs17)) {
            val classes = probeClasses(targets) +
                ("com.android.launcher.bottomsearch.ExportBottomSearch" to MissingRefresh::class.java)
            assertFalse(targets.manager, targets.isAvailable(classes::getValue))
            assertEquals(0, ProbeConstructions.count)
        }
    }

    @Test fun availabilityRejectsAnInstanceSwitchReaderWhereAStaticMethodIsRequired() {
        for (targets in listOf(LauncherSearchTargets.ColorOs16, LauncherSearchTargets.ColorOs17)) {
            val classes = probeClasses(targets) + (targets.utils to InstanceSwitchReader::class.java)
            assertFalse(targets.manager, targets.isAvailable(classes::getValue))
        }
    }

    @Test fun availabilityRejectsAStaticSwitchReaderWithTheWrongReturnType() {
        for (targets in listOf(LauncherSearchTargets.ColorOs16, LauncherSearchTargets.ColorOs17)) {
            val classes = probeClasses(targets) + (targets.utils to WrongSwitchReturnType::class.java)
            assertFalse(targets.manager, targets.isAvailable(classes::getValue))
        }
    }

    @Test fun availabilityRejectsMissingClassesAndLinkageFailures() {
        for (targets in listOf(LauncherSearchTargets.ColorOs16, LauncherSearchTargets.ColorOs17)) {
            val classes = probeClasses(targets)
            assertFalse(targets.isAvailable { name ->
                if (name == targets.utils) throw ClassNotFoundException(name)
                classes.getValue(name)
            })
            assertFalse(targets.isAvailable { name ->
                if (name == "com.android.launcher.bottomsearch.ExportBottomSearch") {
                    throw NoClassDefFoundError("Missing launcher dependency")
                }
                classes.getValue(name)
            })
        }
    }

    private fun probeClasses(targets: LauncherSearchTargets): Map<String, Class<*>> = mapOf(
        targets.manager to if (targets.namedApi) NamedProbeManager::class.java else ObfuscatedProbeManager::class.java,
        targets.implementation to Search::class.java,
        targets.injector to if (targets.namedApi) NamedProbeInjector::class.java else ObfuscatedProbeInjector::class.java,
        targets.utils to ProbeUtils::class.java,
        targets.domesticSupport to ProbeDomesticSupport::class.java,
        "android.content.Context" to ProbeContext::class.java,
        "com.android.launcher3.Launcher" to ProbeLauncher::class.java,
        "com.android.launcher.bottomsearch.DomesticBottomSearch" to ProbeDomesticSearch::class.java,
        "com.android.launcher.bottomsearch.ExportBottomSearch" to ProbeExportSearch::class.java,
    )

    private object ProbeConstructions {
        var count = 0
    }

    private class ProbeContext
    private class ProbeLauncher

    private class NamedProbeManager : Search {
        init { ProbeConstructions.count++ }

        companion object {
            @JvmField val INSTANCE = NamedProbeManager()
            @JvmField var mBottomSearchProxy: Search? = null
            @JvmStatic fun isBottomSearchEnabled(context: ProbeContext): Boolean =
                error("The availability probe must not invoke launcher methods")
        }
    }

    private class ObfuscatedProbeManager : Search {
        init { ProbeConstructions.count++ }

        companion object {
            @JvmField val INSTANCE = ObfuscatedProbeManager()
            @JvmField var a: Search? = null
            @JvmStatic fun r(context: ProbeContext): Boolean =
                error("The availability probe must not invoke launcher methods")
        }
    }

    private class NamedProbeInjector {
        fun init(context: ProbeContext, previousVersion: String) {
            error("The availability probe must not initialize the launcher")
        }
    }

    private class ObfuscatedProbeInjector {
        companion object {
            @JvmStatic fun a(context: ProbeContext, previousVersion: String) {
                error("The availability probe must not initialize the launcher")
            }
        }
    }

    private class ProbeUtils {
        companion object {
            @JvmStatic fun isSearchSwitchOn(context: ProbeContext): Boolean =
                error("The availability probe must not read launcher state")

            @JvmStatic fun o(context: ProbeContext): Boolean =
                error("The availability probe must not read launcher state")

            @JvmStatic fun saveSearchSwitch(context: ProbeContext, value: Int, refresh: Boolean) {
                error("The availability probe must not change launcher state")
            }

            @JvmStatic fun t(context: ProbeContext, value: Int, refresh: Boolean) {
                error("The availability probe must not change launcher state")
            }
        }
    }

    private class ProbeDomesticSupport {
        fun isSupportSeries(): Boolean = error("The availability probe must not invoke product checks")
        fun b(): Boolean = error("The availability probe must not invoke product checks")
    }

    private abstract class ProbeSearchBase : Search {
        init { ProbeConstructions.count++ }

        fun oplusAddOrDeleteBottomWidget(launcher: ProbeLauncher, remove: Boolean) {
            error("The availability probe must not refresh the launcher")
        }

        fun o(launcher: ProbeLauncher, remove: Boolean) {
            error("The availability probe must not refresh the launcher")
        }
    }

    private class ProbeDomesticSearch : ProbeSearchBase()
    private class ProbeExportSearch : ProbeSearchBase()

    private class MissingRefresh : Search {
        init { ProbeConstructions.count++ }
    }

    private class InstanceSwitchReader {
        fun isSearchSwitchOn(context: ProbeContext): Boolean = error("Must not be invoked")
        fun o(context: ProbeContext): Boolean = error("Must not be invoked")

        companion object {
            @JvmStatic fun saveSearchSwitch(context: ProbeContext, value: Int, refresh: Boolean) {
                error("Must not be invoked")
            }

            @JvmStatic fun t(context: ProbeContext, value: Int, refresh: Boolean) {
                error("Must not be invoked")
            }
        }
    }

    private class WrongSwitchReturnType {
        companion object {
            @JvmStatic fun isSearchSwitchOn(context: ProbeContext): Int = error("Must not be invoked")
            @JvmStatic fun o(context: ProbeContext): Int = error("Must not be invoked")

            @JvmStatic fun saveSearchSwitch(context: ProbeContext, value: Int, refresh: Boolean) {
                error("Must not be invoked")
            }

            @JvmStatic fun t(context: ProbeContext, value: Int, refresh: Boolean) {
                error("Must not be invoked")
            }
        }
    }
}

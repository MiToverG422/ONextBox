package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMaterialHookContractTest {
    @Test fun disabledFreshScopeSkipsDexKitAndHookInstallation() {
        val source = source("lsp/KeyboardMaterialHooker.kt")
        val guard = source.indexOf("if (!LspConfig.isKeyboardMaterialUnlockedXposed() && saved == null) return")
        assertTrue(guard >= 0)
        assertTrue(guard < source.indexOf("resolve(loader, apkPath)"))
        assertTrue(guard < source.indexOf("ModernHookRegistry.installFast("))
    }

    @Test fun membershipOverrideIsIdentityScopedAndDoesNotReplaceNativeState() {
        val source = source("lsp/KeyboardMaterialHooker.kt")
        assertTrue(source.contains("chain.thisObject === allowlist"))
        assertTrue(source.contains("true else chain.proceed()"))
        assertTrue(!source.contains(".set(null"))
        assertTrue(!source.contains("EditorInfo("))
    }

    @Test fun reloadRetainsOnlyHostTargetsAndReinstallsTheRuntimeGate() {
        val source = source("lsp/KeyboardMaterialHooker.kt")
        assertTrue(source.contains("DexTargetCache.restoredState(stateKey)"))
        assertTrue(source.contains("HotReloadLifecycle.register(stateKey, capture = { targets })"))
        assertTrue(source.contains("DexKitBridge.create(apkPath).use"))
        assertTrue(source.contains("deoptimize(it as Method)"))
        assertTrue(ModuleHotReloadPolicy.supportedPackages.contains(KeyboardMaterialRules.PACKAGE_NAME))
    }

    @Test fun keyboardIsDispatchedWithItsApkAndOnlyItsOwnRestartTarget() {
        assertTrue(source("lsp/OosLspModuleEntry.kt").contains("KeyboardMaterialHooker.hook(classLoader, target.sourceDir)"))
        assertTrue(source("ui/screens/FeaturesScreen.kt").contains("FeaturePageMode.Keyboard -> listOf(\"com.oplus.keyboard\")"))
        assertTrue(source("ui/Root.kt").contains("entry<RootRoute.FeatureKeyboard>"))
    }

    private fun source(path: String): String = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()
}

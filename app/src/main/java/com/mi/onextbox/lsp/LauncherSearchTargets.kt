package com.mi.onextbox.lsp

import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal data class LauncherSearchTargets(
    val manager: String,
    val implementation: String,
    val injector: String,
    val injectorMethod: String,
    val utils: String,
    val domesticSupport: String,
    val domesticSupportMethod: String,
    val enabledMethod: String,
    val switchOnMethod: String,
    val saveSwitchMethod: String,
    val refreshMethod: String,
    val removePreference: String,
    val addPreference: String,
    val proxyField: String? = null,
) {
    val namedApi: Boolean get() = proxyField != null

    fun resolveProxyField(managerClass: Class<*>, implementationClass: Class<*>): Field? {
        val candidates = managerClass.declaredFields.filter { field ->
            Modifier.isStatic(field.modifiers) &&
                implementationClass.isAssignableFrom(field.type) &&
                !managerClass.isAssignableFrom(field.type)
        }
        return if (proxyField != null) candidates.singleOrNull { it.name == proxyField }
        else candidates.singleOrNull()
    }

    fun isAvailable(loader: ClassLoader): Boolean = isAvailable { Class.forName(it, false, loader) }

    internal fun isAvailable(findClass: (String) -> Class<*>): Boolean = runCatching {
        val managerType = findClass(manager)
        val implementationType = findClass(implementation)
        val context = findClass("android.content.Context")
        val launcher = findClass("com.android.launcher3.Launcher")
        val boolean = Boolean::class.javaPrimitiveType!!
        val int = Int::class.javaPrimitiveType!!
        val proxy = resolveProxyField(managerType, implementationType)
        check(proxy != null && !Modifier.isFinal(proxy.modifiers))
        fun method(type: Class<*>, name: String, result: Class<*>, static: Boolean?, vararg args: Class<*>) {
            val method = type.getMethod(name, *args)
            check(method.returnType == result)
            if (static != null) check(Modifier.isStatic(method.modifiers) == static)
        }
        method(findClass(injector), injectorMethod, Void.TYPE, null, context, String::class.java)
        method(managerType, enabledMethod, boolean, true, context)
        val utilsType = findClass(utils)
        method(utilsType, switchOnMethod, boolean, true, context)
        method(utilsType, saveSwitchMethod, Void.TYPE, true, context, int, boolean)
        method(findClass(domesticSupport), domesticSupportMethod, boolean, null)
        for (name in listOf("DomesticBottomSearch", "ExportBottomSearch")) {
            val type = findClass("com.android.launcher.bottomsearch.$name")
            check(implementationType.isAssignableFrom(type))
            check(!Modifier.isAbstract(type.modifiers))
            type.getDeclaredConstructor()
            method(type, refreshMethod, Void.TYPE, false, launcher, boolean)
        }
        true
    }.getOrDefault(false)

    companion object {
        fun forVersion(version: SystemAdaptationVersion): LauncherSearchTargets = when (version) {
            SystemAdaptationVersion.ColorOs16 -> ColorOs16
            SystemAdaptationVersion.ColorOs17 -> ColorOs17
        }

        val ColorOs16 = LauncherSearchTargets(
            manager = "com.android.launcher.bottomsearch.BottomSearchManager",
            implementation = "com.android.launcher.bottomsearch.IBottomSearch",
            injector = "com.android.launcher.bottomsearch.BottomSearchManagerInjector",
            injectorMethod = "init",
            utils = "com.android.launcher.bottomsearch.BottomSearchUtils",
            domesticSupport = "com.android.launcher.bottomsearch.DomesticBottomSearch\$Companion",
            domesticSupportMethod = "isSupportSeries",
            enabledMethod = "isBottomSearchEnabled",
            switchOnMethod = "isSearchSwitchOn",
            saveSwitchMethod = "saveSearchSwitch",
            refreshMethod = "oplusAddOrDeleteBottomWidget",
            removePreference = "removePreference",
            addPreference = "addPreference",
            proxyField = "mBottomSearchProxy",
        )
        val ColorOs17 = LauncherSearchTargets(
            manager = "com.android.launcher.bottomsearch.i",
            implementation = "com.android.launcher.bottomsearch.e",
            injector = "com.android.launcher.bottomsearch.j",
            injectorMethod = "a",
            utils = "com.android.launcher.bottomsearch.k",
            domesticSupport = "com.android.launcher.bottomsearch.DomesticBottomSearch\$a",
            domesticSupportMethod = "b",
            enabledMethod = "r",
            switchOnMethod = "o",
            saveSwitchMethod = "t",
            refreshMethod = "o",
            removePreference = "f",
            addPreference = "b",
        )
    }
}

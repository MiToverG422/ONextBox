package com.mi.onextbox.lsp

import java.lang.reflect.Modifier

/** Build only a lazy dependency graph, never call SysUI init/start/inject. */
internal object AospVolumeGraph {
    private const val PREFIX = "com.android.systemui.dagger.DaggerReferenceGlobalRootComponent\$"
    private val optionalNames = listOf("setPip", "setSplitScreen", "setOneHanded", "setBubbles",
        "setTaskViewFactory", "setStartingSurface", "setDisplayAreaHelper", "setRecentTasks",
        "setBackAnimation", "setDesktopMode", "setAppZoomOut", "setAppHandles", "setScrollToTop")
    // C16 R8 removed only these empty module constructors; the AOSP graph still accepts them.
    // Never use constructor-free allocation for a controller, service, or unknown class.
    private val strippedC16Modules = setOf(
        "com.android.systemui.dagger.GlobalModule", "com.android.systemui.dagger.AndroidInternalsModule",
        "com.android.systemui.dagger.FrameworkServicesModule", "com.android.systemui.util.kotlin.GlobalCoroutinesModule",
        "com.android.systemui.unfold.UnfoldTransitionModule", "com.android.systemui.unfold.UnfoldSharedModule",
        "com.android.systemui.unfold.UnfoldSharedInternalModule", "com.android.systemui.unfold.UnfoldRotationProviderInternalModule",
        "com.android.systemui.unfold.HingeAngleProviderInternalModule", "com.android.systemui.unfold.FoldStateProviderModule",
        "com.android.systemui.util.leak.LeakModule", "com.android.systemui.dagger.SharedLibraryModule",
        "com.android.systemui.mediaprojection.devicepolicy.MediaProjectionDevicePolicyModule",
        "com.android.systemui.util.kotlin.SysUICoroutinesModule", "com.android.systemui.dagger.PerDisplayRepositoriesModule",
        "com.android.systemui.unfold.SysUIUnfoldModule", "com.android.systemui.rotationlock.DeviceStateAutoRotateModule",
        "com.android.systemui.statusbar.notification.row.icon.AppIconProviderModule",
        "com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProviderModule")

    fun create(host: Any): Any {
        val graph = createGraph(host, 23, optionalNames)
        return requireNotNull(graph.javaClass.getDeclaredMethod("volumeDialogPlugin").apply { isAccessible = true }.invoke(graph))
    }

    fun createColorOs16(host: Any): Any {
        val graph = createGraph(host, 24, optionalNames.take(11))
        return requireNotNull(graph.javaClass.getDeclaredMethod("volumeDialog").apply { isAccessible = true }.invoke(graph))
            .also { require(it.javaClass.name == "com.android.systemui.volume.VolumeDialogImpl") }
    }

    private fun createGraph(host: Any, parameterCount: Int, optionals: List<String>): Any {
        val loader = host.javaClass.classLoader!!
        val hostRoot = requireNotNull(host.javaClass.declaredFields.single {
            it.type.name.endsWith("\$OplusReferenceGlobalRootComponentImpl")
        }.apply { isAccessible = true }.get(host))
        val rootType = Class.forName(PREFIX + "ReferenceGlobalRootComponentImpl", false, loader)
        val rootConstructor = rootType.declaredConstructors.single()
        val root = rootConstructor.apply { isAccessible = true }.newInstance(
            *rootConstructor.parameterTypes.map { argument(hostRoot, it, parameterCount == 24) }.toTypedArray())
        shareProviders(hostRoot, root)
        val type = Class.forName(PREFIX + "ReferenceSysUIComponentImpl", false, loader)
        val constructor = type.declaredConstructors.single()
        // Each audited graph has ordered Optional values; never infer their positions.
        require(constructor.parameterCount == parameterCount && constructor.parameterTypes[0] == rootType)
        var optional = 0
        val args = constructor.parameterTypes.mapIndexed { index, parameter ->
            when {
                index == 0 -> root
                parameter == java.util.Optional::class.java -> boundArgument(host, optionals[optional++], parameterCount == 24)
                else -> argument(host, parameter, parameterCount == 24)
            }
        }.toTypedArray()
        require(optional == optionals.size)
        val graph = constructor.apply { isAccessible = true }.newInstance(*args)
        shareProviders(host, graph)
        // Hard requirement: both UIs must drive the very same native audio controller.
        require(field(graph, "volumeDialogControllerImplProvider") === field(host, "volumeDialogControllerImplProvider"))
        return graph
    }

    private fun argument(host: Any, type: Class<*>, colorOs16: Boolean): Any {
        if (colorOs16) {
            val shell = when (type.name) {
                "com.android.wm.shell.sysui.ShellInterface" -> "setShell"
                "com.android.wm.shell.shared.ShellTransitions" -> "setShellTransitions"
                "com.android.wm.shell.keyguard.KeyguardTransitions" -> "setKeyguardTransitions"
                else -> null
            }
            if (shell != null) return boundArgument(host, shell, true).also { require(type.isInstance(it)) }
        }
        if (type.name == "android.content.Context") {
            val provider = field(host, "provideApplicationContextProvider")
            return requireNotNull(provider.javaClass.getMethod("get").apply { isAccessible = true }.invoke(provider))
        }
        val matches = host.javaClass.declaredFields.filter {
            !Modifier.isStatic(it.modifiers) && it.type == type
        }
        if (matches.size == 1) {
            val value = matches.single().apply { isAccessible = true }.get(host)
            if (value != null) return value
        }
        // Dagger module constructors have no service registration; never construct a
        // second service/controller as a fallback for an unknown dependency.
        require(type.name.startsWith("com.android.systemui.") && type.simpleName.endsWith("Module")) {
            "Unresolved volume graph argument: ${type.name}"
        }
        type.declaredConstructors.singleOrNull { it.parameterCount == 0 }?.let {
            return it.apply { isAccessible = true }.newInstance()
        }
        require(colorOs16 && type.name in strippedC16Modules && type.superclass == Any::class.java &&
            Modifier.isFinal(type.modifiers) && type.declaredConstructors.isEmpty() &&
            type.declaredFields.none { !Modifier.isStatic(it.modifiers) }) {
            "Volume graph module has unaudited state: ${type.name}"
        }
        val unsafe = Class.forName("sun.misc.Unsafe")
        val allocator = unsafe.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafe.getMethod("allocateInstance", Class::class.java).invoke(allocator, type)
    }

    private fun boundArgument(host: Any, name: String, colorOs16: Boolean): Any {
        if (!colorOs16) return field(host, name)
        // C16 retains constructor-bound values only in InstanceFactory, not raw fields.
        // This returns an existing value; never evaluate an arbitrary service provider here.
        val provider = field(host, "${name}Provider")
        require(provider.javaClass.name == "dagger.internal.InstanceFactory")
        return requireNotNull(provider.javaClass.getMethod("get").invoke(provider))
    }

    internal fun shareableProvider(name: String): Boolean = name.endsWith("Provider") &&
        !name.startsWith("factoryProvider") && !name.startsWith("provideVolumeDialog") &&
        name != "volumeDialogComponentProvider"

    internal fun shareProviders(host: Any, target: Any) {
        val original = host.javaClass.declaredFields.associateBy { it.name }
        target.javaClass.declaredFields.filter { shareableProvider(it.name) }.forEach { to ->
            val from = original[to.name] ?: return@forEach
            if (from.type != to.type || Modifier.isStatic(from.modifiers)) return@forEach
            val value = from.apply { isAccessible = true }.get(host) ?: return@forEach
            to.apply { isAccessible = true }.set(target, value)
        }
    }

    internal fun field(instance: Any, name: String): Any = requireNotNull(instance.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(instance))
}

package com.mi.onextbox.lsp

import android.content.Context
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook
import io.github.libxposed.api.XposedInterface

internal object AospLockscreenHooker {
    fun hook(loader: ClassLoader) = runCatching {
        fun type(name: String) = HostClassLookup.load(name, false, loader)
        val bridge = PixelClockBridge(loader)
        val geometry = AospClockGeometry(loader).also { it.install(loader) }
        probeDataProviders(loader)
        val settings = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings")
        val stateFlow = type("kotlinx.coroutines.flow.StateFlowKt").getMethod("MutableStateFlow", Any::class.java)
        val readState = type("kotlinx.coroutines.flow.StateFlow").getMethod("getValue")
        val controllerType = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockController")
        val faceType = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController")
        val getFaceLayout = faceType.getMethod("getLayout")
        val getFaceViews = getFaceLayout.returnType.getMethod("getViews")
        val getFaceView = faceType.getMethod("getView")
        val getSmallClock = controllerType.getMethod("getSmallClock")
        val getLargeClock = controllerType.getMethod("getLargeClock")
        val nativeSection = type("com.android.systemui.keyguard.ui.view.layout.sections.ClockSection")
        val presentation = AospKeyguardClockPresentation(loader)
        presentation.install(nativeSection, type("androidx.constraintlayout.widget.ConstraintLayout"))
        val ambient = if (AospUiSettings.enabledXposed(AospUiSettings.AOD_LAYOUT)) runCatching {
            AospAmbientClock(loader, bridge, presentation::showAmbientClock).also { it.install() }
        }.onFailure { HookLog.w("ONextBox-AospClock", "Ambient layout unavailable; keeping ColorOS AOD", it) }.getOrNull() else null
        var hasPublishedClock = false
        val prepared = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val repository = type("com.android.systemui.keyguard.data.repository.KeyguardClockRepositoryImpl")
        ModernHookBridge.hookAllConstructors(repository, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!AospUiSettings.enabledXposed(AospUiSettings.LOCKSCREEN)) return
                val owner = param.thisObject ?: return
                runCatching {
                    val context = ModernReflect.getObjectField(owner, "context") as Context
                    val events = ModernReflect.getObjectField(owner, "clockEventController") ?: return
                    val buffers = ModernReflect.getObjectField(events, "clockBuffers") ?: return
                    val clockSettings = settings.getDeclaredConstructor().newInstance()
                    val clock = bridge.create(context, buffers, clockSettings) ?: return
                    check(getFaceView.invoke(getSmallClock.invoke(clock)) is View)
                    check(getFaceView.invoke(getLargeClock.invoke(clock)) is View)
                    val current = stateFlow.invoke(null, clock)
                    val id = stateFlow.invoke(null, AospUiSettings.clockXposed())
                    val original = ModernReflect.getObjectField(owner, "currentClock")
                    val originalId = ModernReflect.getObjectField(owner, "currentClockId")
                    val originalClock = ModernReflect.getObjectField(events, "clock")
                    val connect = events.javaClass.getMethod("setClock", controllerType)
                    try {
                        connect.invoke(events, clock)
                        ModernReflect.setObjectField(owner, "currentClock", current)
                        ModernReflect.setObjectField(owner, "currentClockId", id)
                    } catch (error: Throwable) {
                        ModernReflect.setObjectField(owner, "currentClock", original)
                        ModernReflect.setObjectField(owner, "currentClockId", originalId)
                        runCatching { connect.invoke(events, originalClock) }
                        throw error
                    }
                    prepared.add(clock)
                    ambient?.useClock(buffers, clockSettings, events)
                    hasPublishedClock = true
                    HookLog.i("ONextBox-AospClock", "Prepared native clock repository: ${AospUiSettings.clockXposed()}")
                }.onFailure { HookLog.w("ONextBox-AospClock", "Clock preparation failed; keeping OEM clock section", it) }
            }
        })
        var hasReplacedSections = false
        for (name in listOf("DefaultKeyguardBlueprint", "SplitShadeKeyguardBlueprint")) {
            val blueprint = runCatching { type("com.android.systemui.keyguard.ui.view.layout.blueprints.$name") }.getOrNull() ?: continue
            fun addClock(sections: List<*>, clock: Any): List<*> {
                val hostClock = sections.firstOrNull(presentation.oemClock::isInstance) ?: return sections
                val model = ModernReflect.getObjectField(clock, "keyguardClockViewModel") ?: return sections
                val selected = readState.invoke(ModernReflect.getObjectField(model, "currentClock"))
                if (selected !in prepared) return sections
                val existingBottom = sections.firstOrNull(presentation.oemBottom::isInstance)
                val bottom = existingBottom ?: presentation.bottomArea(loader) ?: return sections
                val context = ModernReflect.getObjectField(clock, "context") as Context
                geometry.select(clock, model, context, bridge.referenceResources(context))
                val nativeViews = listOf(getSmallClock.invoke(selected), getLargeClock.invoke(selected)).flatMap { face ->
                    val children = getFaceViews.invoke(getFaceLayout.invoke(face)) as List<*>
                    (children + getFaceView.invoke(face)).filterIsInstance<View>()
                }.distinct()
                presentation.prepare(clock, hostClock, nativeViews)
                ambient?.useDeviceEntry(sections)
                val next = buildList {
                    for (section in sections) {
                        if (section == null || section === clock || section.javaClass.simpleName == "DefaultShortcutsSection") continue
                        add(section)
                        if (section === hostClock) add(clock)
                    }
                    if (existingBottom == null) add(bottom)
                }
                hasReplacedSections = true
                HookLog.i("ONextBox-AospClock", "Native clock section selected in $name with ColorOS clock lifecycle and bottom area")
                return next
            }
            val sectionsField = blueprint.declaredFields.singleOrNull {
                it.name == "sections" && List::class.java.isAssignableFrom(it.type)
            }?.apply { isAccessible = true }
            val lazyClock = java.util.WeakHashMap<Any, Any>()
            val layouts = java.util.WeakHashMap<Any, List<*>>()
            for (constructor in blueprint.declaredConstructors) {
                val clockIndex = constructor.parameterTypes.indexOf(nativeSection)
                if (clockIndex < 0) continue
                ModernHookRegistry.installFast("aosp-clock:blueprint:$constructor", constructor, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    val owner = chain.thisObject ?: return@Hooker result
                    val clock = chain.getArg(clockIndex) ?: return@Hooker result
                    runCatching {
                        if (sectionsField == null) lazyClock[owner] = clock
                        else sectionsField.set(owner, addClock(sectionsField.get(owner) as List<*>, clock))
                    }.onFailure { HookLog.w("ONextBox-AospClock", "Could not attach clock to $name", it) }
                    result
                })
            }
            if (sectionsField == null) {
                val getSections = blueprint.getMethod("getSections")
                ModernHookRegistry.installFast("aosp-clock:sections:$name", getSections, XposedInterface.Hooker { chain ->
                    val owner = chain.thisObject ?: return@Hooker chain.proceed()
                    layouts[owner]?.let { return@Hooker it }
                    val original = chain.proceed() as List<*>
                    val clock = lazyClock[owner] ?: return@Hooker original
                    runCatching { addClock(original, clock) }
                        .onFailure { HookLog.w("ONextBox-AospClock", "Could not attach clock to $name", it) }
                        .getOrDefault(original).also { if (it !== original) layouts[owner] = it }
                })
            }
        }
        HotReloadLifecycle.register("aosp-native-clocks", canPrepare = { !hasReplacedSections && !hasPublishedClock },
            canReload = { !hasReplacedSections && !hasPublishedClock })
    }.onFailure { HookLog.w("ONextBox-AospClock", "Native lockscreen adapter unavailable", it) }

    private fun probeDataProviders(loader: ClassLoader) {
        runCatching {
            val smartspace = HostClassLookup.load("com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController", false, loader)
            ModernHookBridge.hookAllConstructors(smartspace, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    val flags = listOf("plugin", "datePlugin", "weatherPlugin").joinToString { field ->
                        "$field=${ModernReflect.getObjectField(owner, field) != null}"
                    }
                    HookLog.i("ONextBox-AospClock", "Smartspace providers: $flags")
                }
            })
            val controller = HostClassLookup.load("com.android.keyguard.ClockEventController", false, loader)
            ModernHookBridge.hookAllMethods(controller, "setClock", object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    HookLog.i("ONextBox-AospClock", "Clock weather data present=${ModernReflect.getObjectField(owner, "weatherData") != null}")
                }
            })
        }.onFailure { HookLog.i("ONextBox-AospClock", "Data capability inspection unavailable: $it") }
    }
}

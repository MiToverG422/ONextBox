package com.mi.onextbox.lsp

import android.app.KeyguardManager
import android.os.HandlerThread
import android.os.Looper
import com.mi.onextbox.lsp.LspConfig.KeyguardFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Enumeration

/** C17 opt-in capture policies, with no global keyguard, permission or FLAG_SECURE override. */
internal object KeyguardCaptureHooker {
    private const val TAG = "ONextBox-Capture"
    private const val EVENT_PREFIX = "com.oplus.screenrecorder.common.event."
    private const val QUICK_PANEL = "com.oplus.screenrecorder.floatwindow.services.QuickPanelService"

    private fun recordingEnabled() =
        LspConfig.isKeyguardFeatureEnabledXposed(KeyguardFeature.ScreenOffRecording)

    fun hookSystemServer(loader: ClassLoader) {
        if (LspConfig.isKeyguardFeatureEnabledXposed(KeyguardFeature.AodScreenshot)) {
            install("native AOD screenshots") { installAodScreenshot(loader) }
        }
        if (recordingEnabled()) {
            install("projection keyguard exceptions") { installProjection(loader) }
        }
    }

    fun hookSystemUi(loader: ClassLoader) {
        if (recordingEnabled()) install("recorder tile keyguard exception") { installRecorderTile(loader) }
    }

    fun hookRecorder(loader: ClassLoader) {
        if (!recordingEnabled()) return
        install("recorder screen-off events") { installRecorderEvents(loader) }
        install("recorder quick panel") { installRecorderQuickPanel() }
    }

    private inline fun install(label: String, block: () -> Unit) {
        runCatching(block)
            .onSuccess { HookLog.i(TAG, "Installed $label") }
            .onFailure { HookLog.w(TAG, "Unavailable $label, stock handling retained", it) }
    }

    private fun installAodScreenshot(loader: ClassLoader) {
        val manager = Reflect.findClass("com.android.server.policy.OplusAODScreenshotManager", loader)
        val handlerClass = Reflect.findClass(manager.name + "\$PolicyHandler", loader)
        // Validate the complete stock initialization contract before installing any hook.
        val constructor = handlerClass.getDeclaredConstructor(manager, Looper::class.java)
            .apply { isAccessible = true }
        val enabledField = manager.getDeclaredField("mAODScreenshotEnabled").apply { isAccessible = true }
        val pwmField = manager.getDeclaredField("mPWManager").apply { isAccessible = true }
        val handlerField = manager.getDeclaredField("mHandler").apply { isAccessible = true }
        val init = manager.declaredMethods.single {
            it.name == "init" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0].name == "com.android.server.policy.PhoneWindowManager" &&
                it.parameterTypes[1] == HandlerThread::class.java
        }
        ModernHookRegistry.installCompat("capture:aod:" + init.toGenericString(), init, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null ||
                    !LspConfig.isKeyguardFeatureEnabledXposed(KeyguardFeature.AodScreenshot)) return
                val owner = param.thisObject ?: return
                if (enabledField.getBoolean(owner)) return // Already supported by the ROM.
                val pwm = param.args[0] ?: return
                val thread = param.args[1] as? HandlerThread ?: return
                val handler = constructor.newInstance(owner, thread.looper)
                pwmField.set(owner, pwm)
                handlerField.set(owner, handler)
                enabledField.setBoolean(owner, true) // Publish only after both dependencies exist.
                HookLog.i(TAG, "Enabled stock AOD power + volume-down screenshot flow")
            }
        })
    }

    private fun installProjection(loader: ClassLoader) {
        val controller = Reflect.findClass("com.android.server.media.projection.MediaProjectionStopController", loader)
        val projection = Reflect.findClass(
            "com.android.server.media.projection.MediaProjectionManagerService\$MediaProjection", loader,
        )
        val packageField = projection.getDeclaredField("packageName").apply { isAccessible = true }
        val start = controller.getDeclaredMethod("isStartForbidden", projection)
        val stop = controller.getDeclaredMethod("isExemptFromStopping", projection, Int::class.javaPrimitiveType)
        fast(start) { chain ->
            val grant = chain.getArg(0)
            val packageName = grant?.let { packageField.get(it) as? String }
            if (KeyguardCaptureRules.allowProjectionStart(recordingEnabled(), packageName)) false
            else chain.proceed()
        }
        fast(stop) { chain ->
            val grant = chain.getArg(0)
            val packageName = grant?.let { packageField.get(it) as? String }
            val reason = chain.getArg(1) as? Int
            if (reason != null &&
                KeyguardCaptureRules.allowProjectionToContinue(recordingEnabled(), packageName, reason)) true
            else chain.proceed()
        }
        // Do not hook stop(), permission/consent checks, display removal or user switching.
    }

    private fun installRecorderTile(loader: ClassLoader) {
        val tile = Reflect.findClass("com.oplus.systemui.qs.tiles.ScreenRecorderTile", loader)
        val repository = Reflect.findClass("com.android.systemui.keyguard.data.repository.KeyguardRepositoryImpl", loader)
        val getters = repository.declaredMethods.filter {
            it.name.contains("isKeyguardShowing") && it.parameterTypes.isEmpty() &&
                it.returnType == Boolean::class.javaPrimitiveType
        }
        check(getters.isNotEmpty()) { "Missing keyguard showing getter" }
        val callbacks = tile.declaredMethods.filter {
            (it.name == "screenRecordDisabled" && it.parameterTypes.isEmpty()) ||
                (it.name == "handleUpdateState" && it.parameterTypes.size == 2)
        }
        check(callbacks.any { it.name == "screenRecordDisabled" }) { "Missing recorder tile gate" }
        val scope = ThreadLocal<Int>()
        getters.forEach { method -> fast(method) { chain ->
            if ((scope.get() ?: 0) > 0 && recordingEnabled()) false else chain.proceed()
        } }
        callbacks.forEach { method -> fast(method) { chain ->
            if (!recordingEnabled()) return@fast chain.proceed()
            val previous = scope.get() ?: 0
            scope.set(previous + 1)
            try {
                chain.proceed()
            } finally {
                if (previous == 0) scope.remove() else scope.set(previous)
            }
        } }
        // The original screenRecordDisabled() still checks device-management restrictions,
        // and getCddNotShow()/startRecording() retain the stock first-use consent flow.
    }

    private fun installRecorderQuickPanel() {
        val getter = KeyguardManager::class.java.getDeclaredMethod("isKeyguardLocked")
        fast(getter) { chain ->
            val quickPanelQuery = recordingEnabled() && Thread.currentThread().stackTrace.any {
                it.className == QUICK_PANEL || it.className.startsWith("$QUICK_PANEL\$")
            }
            if (quickPanelQuery) false else chain.proceed()
        }
        // Gallery/preview access and every other recorder keyguard query keep their real state.
    }

    private fun installRecorderEvents(loader: ClassLoader) {
        val eventType = Reflect.findClass(EVENT_PREFIX + "RecordEventType", loader)
        check(eventType.isEnum) { "Unknown recorder event contract" }
        // The recorder obfuscates its event bus classes, discover by enum/Map signatures
        // rather than depending on jadx-generated field names or a particular APK's letters.
        val names = eventClassNames(loader).ifEmpty {
            setOf(EVENT_PREFIX + "b", EVENT_PREFIX + "g", EVENT_PREFIX + "j")
        }
        val types = names.mapNotNull { Reflect.findClassIfExists(it, loader) }
        val base = types.single { candidate ->
            candidate.declaredConstructors.any { it.parameterTypes.contentEquals(arrayOf(eventType)) } &&
                candidate.declaredFields.any { it.type == eventType && !Modifier.isStatic(it.modifiers) }
        }
        val typeGetter = base.declaredMethods.single {
            it.parameterTypes.isEmpty() && it.returnType == eventType
        }.apply { isAccessible = true }
        val dispatch = types.flatMap { candidate ->
            if (candidate.declaredFields.none { Map::class.java.isAssignableFrom(it.type) }) emptyList()
            else candidate.declaredMethods.filter {
                !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(arrayOf(base))
            }
        }.single()
        fast(dispatch) { chain ->
            if (!recordingEnabled()) return@fast chain.proceed()
            val event = chain.getArg(0) ?: return@fast chain.proceed()
            val type = (typeGetter.invoke(event) as? Enum<*>)?.name
            val action = if (type == "POWER_OFF" || type == "SCREEN_OFF_OR_SHUT_DOWN") {
                event.javaClass.declaredFields
                    .filter { it.type == String::class.java && !Modifier.isStatic(it.modifiers) }
                    .singleOrNull()?.let { field ->
                        field.isAccessible = true
                        field.get(event) as? String
                    }
            } else null
            if (KeyguardCaptureRules.ignoreRecorderEvent(true, type, action)) null else chain.proceed()
        }
    }

    private fun eventClassNames(loader: ClassLoader): Set<String> {
        val names = linkedSetOf<String>()
        var current: ClassLoader? = loader
        while (current != null) {
            val active = current
            runCatching {
                val pathList = Reflect.getObjectField(active, "pathList") ?: return@runCatching
                val elements = Reflect.getObjectField(pathList, "dexElements") as? Array<*> ?: return@runCatching
                elements.filterNotNull().forEach { element ->
                    val dex = Reflect.getObjectField(element, "dexFile") ?: return@forEach
                    val entries = Reflect.callMethod(dex, "entries") as? Enumeration<*> ?: return@forEach
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement() as? String ?: continue
                        if (name.startsWith(EVENT_PREFIX)) names += name
                    }
                }
            }
            current = active.parent
        }
        return names
    }

    private fun fast(method: Method, callback: (XposedInterface.Chain) -> Any?) {
        ModernHookRegistry.installFast(
            "capture:" + method.toGenericString(), method, XposedInterface.Hooker(callback),
        )
    }
}

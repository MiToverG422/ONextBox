package com.mi.onextbox.lsp

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object PictorialCustomizationHooker {
    private const val TAG = "PictorialCustomization"
    private fun active(suffix: String) = KeyboardPictorialSettings.active("pictorial_$suffix")

    // Runs in SystemUI, so the checked permission belongs to the host rather than this APK.
    @SuppressLint("MissingPermission")
    private fun syncHomeWallpaper(host: Context, image: Bitmap) {
        if (host.checkSelfPermission(Manifest.permission.SET_WALLPAPER) != PackageManager.PERMISSION_GRANTED) return
        WallpaperManager.getInstance(host).setBitmap(image, null, true, WallpaperManager.FLAG_SYSTEM)
    }
    private fun field(type: Class<*>, name: String): Field {
        var current: Class<*>? = type
        while (current != null) {
            val owner = current
            runCatching { owner.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()?.let { return it }
            current = owner.superclass
        }
        error("Missing field $name")
    }

    fun hookApp() {
        WallpaperManager::class.java.declaredMethods.filter { method ->
            method.name in setOf("setBitmap", "setStream") && method.parameterCount >= 4 &&
                method.parameterTypes[1] == android.graphics.Rect::class.java && method.parameterTypes[2] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType
        }.forEach { method ->
            ModernHookRegistry.installFast("$TAG:app:$method", method) { chain ->
                if (active("sync_home") && chain.args[3] == WallpaperManager.FLAG_LOCK) {
                    val args = chain.args.toTypedArray(); args[3] = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    HookLog.i(TAG, "Applying lock-screen image to both wallpaper targets")
                    chain.proceed(args)
                } else chain.proceed()
            }
        }
        HookLog.i(TAG, "Pictorial app wallpaper hooks installed")
    }

    fun hookSystemUi(loader: ClassLoader) {
        val helperName = "com.oplus.systemui.keyguard.wallpaper.pictorial.PictorialWallpaperHelper"
        val stateKey = "$TAG:controllers"
        fun executor() = Executors.newSingleThreadExecutor { action -> Thread(action, "ONextBox-Pictorial").apply { isDaemon = true } }
        var worker = executor()
        val busy = AtomicBoolean(false)
        val pending = AtomicReference<Pair<Context, Bitmap>?>(null)
        var clock = WeakReference<Any>(null)
        val images = PictorialImageRevisions()
        val screenOffRequest = ThreadLocal<Boolean?>()
        fun scheduleSync() {
            if (!busy.compareAndSet(false, true)) return
            worker.execute {
                try {
                    while (true) {
                        val item = pending.getAndSet(null) ?: break
                        try {
                            HotReloadLifecycle.withHook({}) {
                                if (active("sync_home")) syncHomeWallpaper(item.first, item.second)
                            }
                        } catch (error: Throwable) { HookLog.w(TAG, "Home wallpaper synchronization failed", error) }
                        finally { item.second.recycle() }
                    }
                } finally {
                    busy.set(false)
                    if (pending.get() != null && !worker.isShutdown) scheduleSync()
                }
            }
        }
        fun refreshClock() {
            if (!active("clock_color")) return
            Handler(Looper.getMainLooper()).post {
                HotReloadLifecycle.withHook({}) {
                    runCatching {
                        val controller = clock.get() ?: run {
                            val dependency = loader.loadClass("com.android.systemui.DependencyEx").getDeclaredField("sDependency").get(null)
                                ?: return@runCatching
                            val type = loader.loadClass("com.android.keyguard.OplusKeyguardDependencyEx")
                            val owner = dependency.javaClass.methods.first { it.name == "getDependency" && it.parameterTypes.contentEquals(arrayOf(Class::class.java)) }.invoke(dependency, type)
                            val existing = type.getDeclaredMethod("getKeyguardStyleClockController").invoke(owner) ?: return@runCatching
                            clock = WeakReference(existing)
                            existing
                        }
                        controller.javaClass.getDeclaredMethod("updateDynamicMixColorState").invoke(controller)
                        val delivery = field(controller.javaClass, "wallpaperDeliveryController").get(controller)
                        delivery?.javaClass?.getDeclaredMethod("handleWallpaperBitmapRequest")?.invoke(delivery)
                        HookLog.i(TAG, "Requested clock colors for current rotating wallpaper")
                    }.onFailure { HookLog.w(TAG, "Clock color refresh unavailable", it) }
                }
            }
        }
        runCatching {
            val controller = loader.loadClass("com.oplus.systemui.keyguard.clockstyle.KeyguardStyleClockControllerImpl")
            controller.declaredConstructors.forEach { constructor ->
                ModernHookRegistry.installFast("$TAG:clock:$constructor", constructor) { chain ->
                    val result = chain.proceed(); clock = WeakReference(chain.thisObject); result
                }
            }
            controller.declaredMethods.filter { it.name in setOf("updateDynamicMixColorState", "loadClock", "loadConfig", "onInit") }.forEach { method ->
                ModernHookRegistry.installFast("$TAG:clock-owner:$method", method) { chain ->
                    clock = WeakReference(chain.thisObject)
                    chain.proceed()
                }
            }
            val previous = HotReloadLifecycle.restoredState(stateKey) as? Array<*>
            previous?.getOrNull(0)?.let { clock = WeakReference(it) }
            val mediator = loader.loadClass("com.oplus.systemui.keyguard.wallpaper.pictorial.PictorialMediator")
            ModernHookRegistry.installFast("$TAG:keep-clock", mediator.getDeclaredMethod("isAutoPlaySwitchOn")) { chain ->
                if (active("keep_clock") && Thread.currentThread().stackTrace.any { it.className.startsWith(controller.name) }) false else chain.proceed()
            }
            controller.declaredMethods.filter { it.name in setOf("loadDeepSceneryImage", "loadClock", "loadConfig", "onInit", "sendKeyguardStateCommands") }
                .forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
            val plugin = loader.loadClass("com.oplus.keyguard.plugin.ClockPlugin")
            ModernHookRegistry.installFast("$TAG:clock-rotation", plugin.getDeclaredMethod("setPictorialAutoPlayEnable", Boolean::class.javaPrimitiveType)) { chain ->
                if (active("keep_clock") && chain.args[0] == true) chain.proceed(arrayOf(false)) else chain.proceed()
            }
        }.onFailure { HookLog.w(TAG, "Clock customization unavailable", it) }
        runCatching {
            val helper = loader.loadClass(helperName)
            val context = field(helper, "mContext")
            val sourceKey = helper.getDeclaredMethod("getCurrentSourceKey").apply { isAccessible = true }
            val copyBitmap = helper.getDeclaredMethod("copyWallpaperBitmapSafely")
            ModernHookRegistry.installFast("$TAG:bitmap", copyBitmap) { chain ->
                val result = chain.proceed()
                runCatching {
                    val id = sourceKey.invoke(chain.thisObject) as? String ?: return@runCatching
                    val bitmap = result as? Bitmap ?: return@runCatching
                    if (bitmap.isRecycled) return@runCatching
                    val request = images.request(id, active("sync_home"), active("clock_color"))
                    if (request.clock) refreshClock()
                    if (!request.home) return@runCatching
                    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching
                    val owner = context.get(chain.thisObject) as Context
                    pending.getAndSet(owner to copy)?.second?.recycle()
                    scheduleSync()
                    HookLog.i(TAG, "Queued current rotating wallpaper for home synchronization")
                }.onFailure { HookLog.w(TAG, "Wallpaper update skipped", it) }
                result
            }
            val wallpaperChanged = helper.getDeclaredMethod("onWallpaperChange", Int::class.javaPrimitiveType)
            ModernHookRegistry.installFast("$TAG:wallpaper-change", wallpaperChanged) { chain ->
                val result = chain.proceed()
                if (active("sync_home") || active("clock_color")) runCatching {
                    if (helper.getDeclaredMethod("isEnableLockScreen").invoke(chain.thisObject) == true) {
                        val bitmap = copyBitmap.invoke(chain.thisObject) as? Bitmap
                        bitmap?.recycle()
                    }
                }.onFailure { HookLog.w(TAG, "Current rotating wallpaper unavailable", it) }
                result
            }
            runCatching { ModernHookRuntime.requireModule().deoptimize(wallpaperChanged) }
            runCatching {
                ModernHookRuntime.requireModule().deoptimize(loader.loadClass("$helperName\$\$ExternalSyntheticLambda1").getDeclaredMethod("onTypeChange"))
            }
            val observer = loader.loadClass("$helperName\$3")
            val outer = field(observer, "this\$0")
            val update = helper.getDeclaredMethod("updateAndFetchWallpaper", String::class.java, Boolean::class.javaPrimitiveType)
            ModernHookRegistry.installFast("$TAG:off-request", update) { chain ->
                if (chain.args[0] == "screen_off" && active("screen_off")) {
                    if (screenOffRequest.get() != null) screenOffRequest.set(true)
                    val args = chain.args.toTypedArray(); args[1] = true; chain.proceed(args)
                } else chain.proceed()
            }
            ModernHookRegistry.installFast("$TAG:screen-off", observer.getDeclaredMethod("onScreenTurnedOff")) { chain ->
                screenOffRequest.set(false)
                try {
                    val result = chain.proceed()
                    if (active("screen_off") && screenOffRequest.get() != true) runCatching {
                        val owner = outer.get(chain.thisObject)
                        if (helper.getDeclaredMethod("isEnableLockScreen").invoke(owner) == true)
                            update.invoke(owner, "screen_off", true)
                    }.onFailure { HookLog.w(TAG, "Screen-off rotation unavailable", it) }
                    result
                } finally { screenOffRequest.remove() }
            }
            runCatching { ModernHookRuntime.requireModule().deoptimize(observer.getDeclaredMethod("onScreenTurnedOff")) }
            HookLog.i(TAG, "SystemUI wallpaper rotation hooks installed")
        }.onFailure { HookLog.w(TAG, "Wallpaper customization unavailable", it) }
        HotReloadLifecycle.register(stateKey, capture = { arrayOf(clock.get()) },
            canReload = { !busy.get() }, detach = { pending.getAndSet(null)?.second?.recycle(); worker.shutdownNow(); clock.clear() },
            restore = { state -> clock = WeakReference((state as? Array<*>)?.getOrNull(0)); if (worker.isShutdown) worker = executor() })
    }
}

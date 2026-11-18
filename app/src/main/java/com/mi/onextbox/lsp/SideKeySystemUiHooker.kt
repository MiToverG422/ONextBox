package com.mi.onextbox.lsp

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook

internal object SideKeySystemUiHooker {
    private const val TAG = "ONextBox-SideKeyUI"

    fun hook(loader: ClassLoader) {
        if (!SideKeyFeaturePolicy.available) return
        runCatching {
            val owner = Owner(loader)
            val method = HostClassLookup.load("com.android.systemui.SystemUIApplication", false, loader)
                .getDeclaredMethod("onCreate")
            ModernHookRegistry.installCompat("$TAG:application", method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null) (param.thisObject as? Context)?.let { context ->
                        HotReloadLifecycle.afterCommit { owner.attach(context) }
                    }
                }
            })
            val saved = HotReloadLifecycle.restoredState("$TAG:receiver") as? Context
            HotReloadLifecycle.register("$TAG:receiver", capture = { owner.context }, detach = owner::detach,
                restore = { state -> (state as? Context)?.let(owner::attach) })
            if (saved == null) {
                val app = HostClassLookup.load("android.app.ActivityThread", false, loader)
                    .getMethod("currentApplication").invoke(null) as? Context
                if (app?.packageName == "com.android.systemui") HotReloadLifecycle.afterCommit { owner.attach(app) }
            }
        }.onFailure { HookLog.w(TAG, "Side key action receiver unavailable", it) }
    }

    private class Owner(private val loader: ClassLoader) {
        var context: Context? = null
            private set
        private val main = Handler(Looper.getMainLooper())
        private var cameras: CameraManager? = null
        private var registeredTorch = false
        private val torchStates = mutableMapOf<String, Boolean>()
        private val torch = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                HotReloadLifecycle.withHook({}, { torchStates[cameraId] = enabled })
            }
            override fun onTorchModeUnavailable(cameraId: String) {
                HotReloadLifecycle.withHook({}, { torchStates.remove(cameraId) })
            }
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                HotReloadLifecycle.withHook({}, {
                    if (intent.action != SideKeyRules.BROADCAST || !SideKeySettings.enabledXposed()) return@withHook
                    val age = SystemClock.uptimeMillis() - intent.getLongExtra("timestamp", -1)
                    if (age !in 0..2000) return@withHook
                    val action = SideKeyAction.parse(intent.getStringExtra("action"))
                    runCatching { execute(context, SideKeyBinding(action, intent.getStringExtra("package").orEmpty())) }
                        .onFailure {
                            HookLog.w(TAG, "Side key action ${action.id} failed", it)
                            message(context, "side_key_action_failed")
                        }
                })
            }
        }

        fun attach(ctx: Context) {
            if (context != null) return
            runCatching {
                ctx.registerReceiver(receiver, IntentFilter(SideKeyRules.BROADCAST),
                    SideKeyRules.PERMISSION, main, Context.RECEIVER_EXPORTED)
                context = ctx
                cameras = ctx.getSystemService(CameraManager::class.java)
                runCatching {
                    cameras?.registerTorchCallback(torch, main)
                    registeredTorch = cameras != null
                }.onFailure { HookLog.w(TAG, "Flashlight state listener unavailable", it) }
                HookLog.i(TAG, "Side key action receiver ready")
            }.onFailure { HookLog.w(TAG, "Could not register side key receiver", it) }
        }

        fun detach() {
            context?.unregisterReceiver(receiver)
            context = null
            if (registeredTorch) cameras?.unregisterTorchCallback(torch)
            registeredTorch = false
            cameras = null
            torchStates.clear()
        }

        private fun execute(ctx: Context, binding: SideKeyBinding) {
            when (binding.action) {
                SideKeyAction.Stock, SideKeyAction.None -> Unit
                SideKeyAction.Flashlight -> {
                    val manager = checkNotNull(cameras)
                    val candidates = manager.cameraIdList.filter {
                        manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    val id = candidates.firstOrNull {
                        manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_BACK
                    } ?: candidates.firstOrNull() ?: error("No flashlight")
                    check(id in torchStates) { "Flashlight state is unavailable" }
                    manager.setTorchMode(id, torchStates[id] != true)
                }
                SideKeyAction.MediaPlayPause, SideKeyAction.MediaPrevious, SideKeyAction.MediaNext -> {
                    val code = when (binding.action) {
                        SideKeyAction.MediaPrevious -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        SideKeyAction.MediaNext -> KeyEvent.KEYCODE_MEDIA_NEXT
                        else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    }
                    val audio = ctx.getSystemService(AudioManager::class.java)
                    val now = SystemClock.uptimeMillis()
                    audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
                    audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
                }
                SideKeyAction.LockScreen -> {
                    val power = ctx.getSystemService(PowerManager::class.java)
                    PowerManager::class.java.getMethod("goToSleep", Long::class.javaPrimitiveType)
                        .invoke(power, SystemClock.uptimeMillis())
                }
                SideKeyAction.DoNotDisturb -> {
                    val manager = ctx.getSystemService(NotificationManager::class.java)
                    manager.setInterruptionFilter(if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL)
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                }
                SideKeyAction.Notifications, SideKeyAction.ControlCenter -> {
                    val locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    if (locked) { message(ctx, "side_key_unlock_required"); wake(ctx); return }
                    val status = ctx.getSystemService("statusbar") ?: error("No status bar service")
                    if (binding.action == SideKeyAction.Notifications)
                        status.javaClass.getMethod("expandNotificationsPanel").invoke(status)
                    else status.javaClass.getMethod("expandSettingsPanel", String::class.java).invoke(status, null)
                }
                SideKeyAction.Screenshot -> {
                    if (ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                        message(ctx, "side_key_unlock_required"); return
                    }
                    val type = HostClassLookup.load("com.oplus.screenshot.OplusScreenshotManager", false, loader)
                    val manager = type.getMethod("getInstance").invoke(null)
                    type.getMethod("takeScreenshot", Bundle::class.java).invoke(manager,
                        Bundle().apply { putString("screenshot_source", "ONextBox_side_key") })
                }
                else -> launch(ctx, binding)
            }
        }

        private fun launch(ctx: Context, binding: SideKeyBinding) {
            val choices = if (binding.action == SideKeyAction.App) {
                if (!SideKeyRules.validPackage(binding.packageName)) { message(ctx, "side_key_select_app"); return }
                listOfNotNull(ctx.packageManager.getLaunchIntentForPackage(binding.packageName))
            } else SideKeyPresetIntents.candidates(binding.action)
            val intent = choices.firstOrNull { candidate ->
                val activity = ctx.packageManager.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                activity?.enabled == true && activity.exported && activity.packageName == candidate.`package`
            } ?: run { message(ctx, "side_key_app_unavailable"); return }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val starterType = HostClassLookup.load("com.android.systemui.plugins.ActivityStarter", false, loader)
            val dependency = HostClassLookup.load("com.android.systemui.Dependency", false, loader)
            val starter = runCatching {
                dependency.getMethod("get", Class::class.java).invoke(null, starterType)
            }.getOrElse {
                val instance = dependency.getDeclaredField("sDependency").apply { isAccessible = true }.get(null)
                dependency.getDeclaredMethod("getDependencyInner", Any::class.java).apply { isAccessible = true }
                    .invoke(instance, starterType)
            }
            wake(ctx)
            starterType.getMethod("postStartActivityDismissingKeyguard", Intent::class.java, Int::class.javaPrimitiveType)
                .invoke(starter, intent, 0)
        }

        private fun wake(ctx: Context) {
            val power = ctx.getSystemService(PowerManager::class.java)
            if (!power.isInteractive) PowerManager::class.java.getMethod("wakeUp", Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, String::class.java).invoke(power, SystemClock.uptimeMillis(), 2, "ONextBox:SideKey")
        }

        private fun message(ctx: Context, name: String) {
            val text = runCatching {
                val module = ctx.createPackageContext("com.mi.onextbox", 0)
                module.getString(module.resources.getIdentifier(name, "string", "com.mi.onextbox"))
            }.getOrDefault("ONextBox: action unavailable")
            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
        }
    }
}

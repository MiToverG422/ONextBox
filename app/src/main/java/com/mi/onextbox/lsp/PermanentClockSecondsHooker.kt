package com.mi.onextbox.lsp

import android.os.Handler
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier

/** Scoped clock hooks: do not alter global power-save, user, or screen lifecycle behavior. */
internal object PermanentClockSecondsHooker {
    private const val TAG = "ONextBox-ClockSeconds"
    private const val CLOCK = "com.oplus.systemui.statusbar.clock"
    private const val SETTINGS = "com.oplus.settings.feature.notification.controller"
    private fun enabled() = LspConfig.isPermanentClockSecondsEnabledXposed()

    fun hookSystemUi(loader: ClassLoader) {
        val key = "$TAG:SystemUI@${System.identityHashCode(loader)}:"
        runCatching {
            // Resolve all critical contracts before installing any clock-state hooks.
            val controller = Reflect.findClass("$CLOCK.ClockSecondsController", loader)
            val repository = Reflect.findClass("$CLOCK.data.ClockSecondsRepository", loader)
            val combiner = Reflect.findClass("$CLOCK.ClockSecondsController\$showSeconds\$1", loader)
            val getDeadline = repository.getDeclaredMethod("getDeadline", Integer.TYPE)
            val schedule = controller.getDeclaredMethod("scheduleExpiryLocked", Integer.TYPE, java.lang.Long.TYPE)
            val modeChanged = controller.getDeclaredMethod("onUserOrModeChanged", Integer.TYPE, Integer.TYPE, java.lang.Long.TYPE)
            val cancel = controller.getDeclaredMethod("cancelExpiryLocked").apply { isAccessible = true }
            val expiry = controller.getDeclaredMethod("access\$onExpiryLocked", controller)
            val combine = combiner.getDeclaredMethod("invokeSuspend", Any::class.java)
            val pairField = combiner.getDeclaredField("L\$0").apply { isAccessible = true }
            val legacyField = combiner.getDeclaredField("Z\$0").apply { isAccessible = true }
            val pair = Reflect.findClass("kotlin.Pair", loader)
            val first = pair.getDeclaredMethod("getFirst").apply { isAccessible = true }
            val longPress = controller.getDeclaredMethod("toggleByLongPress", String::class.java)
            val workHandler = controller.getDeclaredField("workHandler").apply { isAccessible = true }
            // Reuse the OEM worker-thread toggle, bypassing only its super-save entry gate.
            val toggle = controller.declaredMethods.single {
                Modifier.isStatic(it.modifiers) && it.returnType.name == "kotlin.Unit" &&
                    it.parameterTypes.contentEquals(arrayOf(controller, String::class.java))
            }.apply { isAccessible = true }
            val statClock = Reflect.findClass("com.oplus.systemui.statusbar.widget.StatClock", loader)
            val hideSeconds = statClock.getDeclaredMethod("setHideSecBySeeding", java.lang.Boolean.TYPE)
            val tip = Reflect.findClass("$CLOCK.ClockSecondsTipHelper", loader)
                .getDeclaredMethod("maybeShowFirstTimeDialog", String::class.java)

            ModernHookRegistry.installFast("${key}deadline", getDeadline, XposedInterface.Hooker { chain ->
                val original = chain.proceed() as Long
                PermanentClockSecondsRules.effectiveDeadline(enabled(), original)
            })
            ModernHookRegistry.installFast("${key}schedule", schedule, XposedInterface.Hooker { chain ->
                if (!enabled()) return@Hooker chain.proceed()
                cancel.invoke(chain.thisObject)
                null
            })
            ModernHookRegistry.installFast("${key}mode", modeChanged, XposedInterface.Hooker { chain ->
                if (!enabled()) return@Hooker chain.proceed()
                // The settings-observer Flow reads the persisted deadline directly, not getDeadline.
                // Normalize that path too, including a user switch or SystemUI restart after expiry.
                chain.proceed(arrayOf(chain.getArg(0), chain.getArg(1), 0L))
            })
            ModernHookRegistry.installFast("${key}expiry", expiry, XposedInterface.Hooker { chain ->
                if (!enabled()) return@Hooker chain.proceed()
                cancel.invoke(chain.getArg(0))
                null
            })
            ModernHookRegistry.installFast("${key}show", combine, XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                if (!enabled() || original !is Boolean) return@Hooker original
                runCatching {
                    val mode = first.invoke(pairField.get(chain.thisObject)) as Int
                    PermanentClockSecondsRules.showSeconds(true, mode, legacyField.getBoolean(chain.thisObject), original)
                }.getOrElse { error ->
                    HookLog.w(TAG, "Unable to apply permanent clock state", error)
                    original
                }
            })
            ModernHookRegistry.installFast("${key}longPress", longPress, XposedInterface.Hooker { chain ->
                if (!enabled()) return@Hooker chain.proceed()
                val target = chain.thisObject ?: return@Hooker chain.proceed()
                val handler = workHandler.get(target) as Handler
                val source = chain.getArg(0) as String
                val action = Runnable {
                    runCatching {
                        // A setting change while queued must not bypass the stock power-save gate.
                        if (enabled()) toggle.invoke(null, target, source)
                        else longPress.invoke(target, source)
                    }.onFailure { HookLog.w(TAG, "Native clock toggle failed", it) }
                }
                if (handler.looper.isCurrentThread) action.run()
                else if (!handler.post(action)) return@Hooker chain.proceed()
                null
            })
            ModernHookRegistry.installFast("${key}fluidCloud", hideSeconds, XposedInterface.Hooker { chain ->
                val requested = chain.getArg(0) as Boolean
                val replacement = PermanentClockSecondsRules.hideSeconds(enabled(), requested)
                if (replacement == requested) chain.proceed() else chain.proceed(arrayOf(replacement))
            })
            ModernHookRegistry.installFast("${key}tip", tip, XposedInterface.Hooker { chain ->
                // The stock first-use dialog explicitly promises a five-minute timeout.
                if (enabled()) null else chain.proceed()
            })
            HookLog.i(TAG, "Permanent seconds, native toggle, and Fluid Cloud hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Permanent clock hooks unavailable; stock clock retained", it)
        }
    }

    fun hookSettings(loader: ClassLoader) {
        val key = "$TAG:Settings@${System.identityHashCode(loader)}:"
        runCatching {
            val controller = Reflect.findClass("$SETTINGS.ClockSecondsModePreferenceController", loader)
            val getItems = controller.getDeclaredMethod("getItems")
            val getChecked = controller.getDeclaredMethod("getCheckedItem")
            val availability = controller.getDeclaredMethod("getAvailabilityStatus")
            val resolverField = controller.getDeclaredField("mContentResolver").apply { isAccessible = true }
            val statusIcon = Reflect.findClass("$SETTINGS.StatusIconBottomSheetDialog\$StatusIcon", loader)
            val titleField = statusIcon.getDeclaredField("mTitle").apply { isAccessible = true }
            val itemField = statusIcon.getDeclaredField("mStatusIconDialogItem").apply { isAccessible = true }
            val itemClass = Reflect.findClass("com.oplus.settings.feature.notification.StatusIconDialogItem", loader)
            val value = itemClass.getDeclaredMethod("getSettingsValue").apply { isAccessible = true }
            val constructor = statusIcon.getDeclaredConstructor(String::class.java, itemClass).apply { isAccessible = true }
            val preference = Reflect.findClass("com.coui.appcompat.preference.COUIMenuPreference", loader)
            val selection = controller.getDeclaredMethod("onMenuSelection", preference, Integer.TYPE)
            val refresh = Reflect.findMethodExact(controller, "refreshMenuAssignment", arrayOf(preference))
                .apply { isAccessible = true }

            ModernHookRegistry.installFast("${key}items", getItems, XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                if (!enabled() || original !is List<*>) return@Hooker original
                runCatching {
                    ArrayList(original.map { icon ->
                        val item = itemField.get(icon)
                        if (value.invoke(item) == PermanentClockSecondsRules.MODE_SECONDS) {
                            val title = PermanentClockSecondsRules.optionLabel(titleField.get(icon) as String)
                            constructor.newInstance(title, item)
                        } else icon
                    })
                }.getOrElse { error ->
                    HookLog.w(TAG, "Unable to relabel native seconds option", error)
                    original
                }
            })
            ModernHookRegistry.installFast("${key}checked", getChecked, XposedInterface.Hooker { chain ->
                if (!enabled()) return@Hooker chain.proceed()
                val resolver = resolverField.get(chain.thisObject) as android.content.ContentResolver
                PermanentClockSecondsRules.checkedItem(android.provider.Settings.Secure.getInt(
                    resolver, "oplus_status_bar_clock_seconds_mode", PermanentClockSecondsRules.MODE_OFF,
                ))
            })
            ModernHookRegistry.installFast("${key}availability", availability, XposedInterface.Hooker { chain ->
                if (enabled()) 0 else chain.proceed()
            })
            ModernHookRegistry.installFast("${key}selection", selection, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (enabled()) runCatching { refresh.invoke(chain.thisObject, chain.getArg(0)) }
                    .onFailure { HookLog.w(TAG, "Unable to refresh native clock label", it) }
                result
            })
            HookLog.i(TAG, "Native seconds label, selection, and availability hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Native clock settings hooks unavailable; stock settings retained", it)
        }
    }
}

package com.mi.onextbox.lsp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.telephony.ServiceState
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.SparseArray
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Display-only aliases: no subscription records, telephony properties or service state are changed. */
internal object CarrierNameHooker {
    private const val TAG = "ONextBox-CarrierName"
    private val diagnosed = ConcurrentHashMap.newKeySet<String>()
    private val applied = ConcurrentHashMap.newKeySet<String>()

    fun hook(loader: ClassLoader) {
        val scope = CarrierNameFormatScope()
        runCatching {
            val getter = SubscriptionInfo::class.java.getDeclaredMethod("getCarrierName")
            ModernHookRegistry.installFast("$TAG:displayName", getter, XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                if (!enabled()) return@Hooker original
                val owner = scope.owner() ?: return@Hooker original
                runCatching {
                    val info = chain.thisObject as? SubscriptionInfo ?: return@runCatching null
                    val context = Reflect.getObjectField(owner, "mContext") as? Context ?: return@runCatching null
                    val monitor = Reflect.getObjectField(owner, "mKeyguardUpdateMonitor") ?: return@runCatching null
                    val slot = info.simSlotIndex
                    scope.resolve(slot, info.subscriptionId) { replacement(context, monitor, slot, info.subscriptionId) }
                        ?.also { scope.markApplied(slot, it) }
                }.onFailure { diagnose("Native carrier formatter", it) }.getOrNull() ?: original
            })
            deoptimize(getter)
        }.onFailure { diagnose("Carrier display getter", it) }

        for (name in listOf("com.android.keyguard.CarrierTextManager", "com.oplus.systemui.qs.OplusQSCarrierTextController")) {
            runCatching {
                val owner = Reflect.findClass(name, loader)
                val methods = owner.declaredMethods.filter { method ->
                    (method.name == "updateCarrierText" && method.returnType == Void.TYPE &&
                        (method.parameterCount == 0 || method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)))) ||
                        (Modifier.isStatic(method.modifiers) &&
                        method.name.startsWith("\$r8\$lambda\$") && method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(arrayOf(owner, Boolean::class.javaPrimitiveType)))
                }
                check(methods.isNotEmpty()) { "Native carrier formatter not found" }
                methods.forEach { method ->
                    ModernHookRegistry.installFast("$TAG:scope:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                        val target = if (Modifier.isStatic(method.modifiers)) chain.getArg(0) else chain.thisObject
                        scope.during(if (enabled()) target else null) { chain.proceed() }
                    })
                    deoptimize(method)
                }
                // Older versions call an instance formatter; newer builds use the R8 delegate.
                owner.declaredMethods.filter { it.name == "updateCarrierTextInBgThread" }.forEach(::deoptimize)
                HookLog.i(TAG, "Carrier display formatter installed: $name")
            }.onFailure { diagnose("Carrier formatter: $name", it) }
        }

        // CUSTOM WFC rules can replace the raw subscription name again. At the final
        // merge, this array is indexed by physical slot (including a third virtual slot),
        // not subscription order or the current data SIM. Never mutate its shared array.
        runCatching {
            val controller = Reflect.findClass("com.oplus.systemui.qs.OplusQSCarrierTextController", loader)
            val method = try {
                controller.getDeclaredMethod("joinNotEmpty", CharSequence::class.java,
                    SparseArray::class.java, Int::class.javaPrimitiveType)
            } catch (_: NoSuchMethodException) {
                controller.getDeclaredMethod("joinNotEmpty", CharSequence::class.java,
                    Array<CharSequence>::class.java, Int::class.javaPrimitiveType)
            }
            ModernHookRegistry.installFast("$TAG:finalMerge", method, XposedInterface.Hooker { chain ->
                if (!enabled()) return@Hooker chain.proceed()
                val names = runCatching {
                    val owner = requireNotNull(chain.thisObject)
                    val original = chain.getArg(1)
                    val next = SparseArray<CharSequence?>()
                    when (original) {
                        is SparseArray<*> -> for (i in 0 until original.size()) {
                            val value = original.valueAt(i)
                            if (value != null && value !is CharSequence) return@runCatching null
                            next.append(original.keyAt(i), value as? CharSequence)
                        }
                        is Array<*> -> original.forEachIndexed { slot, value ->
                            if (value != null && value !is CharSequence) return@runCatching null
                            next.append(slot, value as? CharSequence)
                        }
                        else -> return@runCatching null
                    }
                    val context = Reflect.getObjectField(owner, "mContext") as? Context ?: return@runCatching null
                    val monitor = Reflect.getObjectField(owner, "mKeyguardUpdateMonitor") ?: return@runCatching null
                    val subscriptions = Reflect.callMethod(owner, "getSubscriptionInfo", false) as? List<*>
                        ?: return@runCatching null
                    val count = chain.getArg(2) as? Int ?: return@runCatching null
                    val emergencyUpdate = Reflect.getObjectField(owner, "mEmergencyShouldUpdate") as? Boolean
                        ?: return@runCatching null
                    val helperType = Reflect.findClass("com.oplus.systemui.keyguard.telephony.SimStateHelper", loader)
                    val helper = Reflect.callStaticMethod(helperType, "getInstance", context) ?: return@runCatching null
                    var changed = false
                    for (info in subscriptions.filterIsInstance<SubscriptionInfo>()) {
                        val slot = info.simSlotIndex
                        if (slot !in 0..1 || next[slot].isNullOrEmpty()) continue
                        // Preserve the vendor's per-slot emergency warning even if its
                        // service-state broadcast and the live telephony query disagree.
                        val emergency = Reflect.callMethod(helper, "isEmergencyOnlyBySlot", count, slot, emergencyUpdate)
                            as? Boolean ?: continue
                        if (emergency) continue
                        val alias = scope.resolve(slot, info.subscriptionId) { replacement(context, monitor, slot, info.subscriptionId) }
                        val text = CarrierNameRules.finalName(next[slot], alias, scope.applied(slot), emergency)
                        if (text != next[slot]) { next.put(slot, text); changed = true }
                        if (alias != null) logApplied(slot)
                    }
                    when {
                        !changed -> null
                        original is Array<*> -> Array<CharSequence?>(original.size) { next[it] }
                        else -> next
                    }
                }.onFailure { diagnose("Final per-slot carrier merge", it) }.getOrNull()
                if (names == null) chain.proceed() else {
                    val args = chain.args.toTypedArray()
                    args[1] = names
                    chain.proceed(args)
                }
            })
            deoptimize(method)
            HookLog.i(TAG, "Final per-slot carrier merge installed")
        }.onFailure { diagnose("Final carrier merge adapter", it) }

        // C17's home status bar uses a separate per-slot Flow. Transform only its String
        // emissions; null resume arguments and the native coroutine continuation stay intact.
        runCatching {
            val prefix = "com.oplus.systemui.statusbar.pipeline.shared.ui.viewmodel." +
                "OplusHomeStatusBarViewModelImpl\$operatorNameForDisplaySlot\$lambda\$"
            val collector = listOf("7\$lambda\$6", "6\$lambda\$5").firstNotNullOf { suffix ->
                Reflect.findClassIfExists("$prefix$suffix\$\$inlined\$map\$1\$2", loader)?.takeIf { type ->
                    type.declaredFields.any { it.name == "\$subId\$inlined" && it.type == Int::class.javaObjectType } &&
                        type.declaredFields.any { it.name == "\$slot\$inlined" && it.type == Int::class.javaPrimitiveType }
                }
            }
            val method = collector.declaredMethods.single { it.name == "emit" && it.parameterCount == 2 }
            val dependency = Reflect.findClass("com.android.systemui.Dependency", loader)
            val monitorType = Reflect.findClass("com.android.keyguard.KeyguardUpdateMonitor", loader)
            ModernHookRegistry.installFast("$TAG:operatorFlow", method, XposedInterface.Hooker { chain ->
                val text = chain.getArg(0) as? String
                val replacement = if (enabled() && !text.isNullOrEmpty()) runCatching {
                    val context = Reflect.callStaticMethod(Class.forName("android.app.ActivityThread"), "currentApplication") as? Context
                        ?: return@runCatching null
                    val nativeDependency = dependency.getDeclaredField("sDependency").apply { isAccessible = true }.get(null)
                        ?: return@runCatching null
                    val monitor = Reflect.callMethod(nativeDependency, "getDependencyInner", monitorType) ?: return@runCatching null
                    val receiver = requireNotNull(chain.thisObject)
                    val slot = Reflect.getObjectField(receiver, "\$slot\$inlined") as? Int ?: return@runCatching null
                    val subId = Reflect.getObjectField(receiver, "\$subId\$inlined") as? Int ?: return@runCatching null
                    replacement(context, monitor, slot, subId)
                }.onFailure { diagnose("Operator name flow", it) }.getOrNull() else null
                if (replacement == null) chain.proceed() else {
                    val args = chain.args.toTypedArray()
                    args[0] = replacement
                    chain.proceed(args)
                }
            })
            deoptimize(method)
            HookLog.i(TAG, "Per-slot status-bar operator display installed")
        }.onFailure { diagnose("Operator name flow adapter", it) }
    }

    private fun enabled() = !HotReloadLifecycle.isRetiring && CarrierNameSettings.enabledXposed()

    private fun replacement(context: Context, monitor: Any, slot: Int, subId: Int): String? {
        val name = CarrierNameSettings.nameXposed(slot)
        if (name.isEmpty() || !SubscriptionManager.isValidSubscriptionId(subId)) return null
        val state = (Reflect.callMethod(monitor, "getSimStateForSlotId", slot) as? Number)?.toInt() ?: return null
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        if (state != TelephonyManager.SIM_STATE_READY || airplane) return null
        // Query the actual subscription, never TelephonyManager's default-data instance.
        // Keyguard's cache may not yet contain every active subscription.
        val current = currentRegistration(context, slot, subId)
        val cached = if (current == null) runCatching {
            val services = Reflect.getObjectField(monitor, "mServiceStates") as? Map<*, *>
            (services?.get(subId) as? ServiceState)?.let(::registration)
        }.getOrNull() else null
        val registration = CarrierNameRules.registration(cached, current)
        if (registration == null && applied.add("missing-service:$slot")) {
            HookLog.i(TAG, "SIM ${slot + 1} carrier alias skipped: no subscription service state available")
        }
        return CarrierNameRules.replacement(enabled(), name, slot, state, registration?.inService == true, airplane)
    }

    // This query runs only inside SystemUI with its checked phone permission, not
    // the module app. Request registration state without cell/location details.
    @SuppressLint("MissingPermission")
    private fun currentRegistration(context: Context, slot: Int, subId: Int): CarrierNameRules.Registration? {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(subId)
                ?.getServiceState(TelephonyManager.INCLUDE_LOCATION_DATA_NONE)?.let(::registration)
        }.onFailure { diagnose("Subscription service state for SIM ${slot + 1}", it) }.getOrNull()
    }

    private fun registration(service: ServiceState) = CarrierNameRules.Registration(service.state,
        runCatching { (Reflect.callMethod(service, "getDataRegistrationState") as? Number)?.toInt() }.getOrNull(),
        runCatching { Reflect.callMethod(service, "isEmergencyOnly") as? Boolean }.getOrNull() == true)

    private fun logApplied(slot: Int) {
        if (applied.add("applied:$slot")) HookLog.i(TAG, "Native carrier label override applied for SIM ${slot + 1}")
    }

    private fun deoptimize(method: Method) {
        runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
            .onFailure { diagnose("Carrier caller deoptimization: ${method.name}", it) }
    }
    private fun diagnose(message: String, error: Throwable) {
        if (diagnosed.add(message)) HookLog.w(TAG, "$message unavailable; native text retained", error)
    }
}

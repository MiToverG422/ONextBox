package com.mi.onextbox.lsp

import android.content.pm.ApplicationInfo
import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal object CouiDividerHooker {
    private const val TAG = "ONextBox-CouiDividers"
    private const val RECYCLER = "androidx.recyclerview.widget.RecyclerView"
    private const val COUI_RECYCLER = "androidx.recyclerview.widget.COUIRecyclerView"

    fun hook(packageName: String, loader: ClassLoader, apkPath: String) {
        if (packageName == "android" || packageName == "system" || packageName == "com.mi.onextbox") return
        val prefix = "$TAG@$packageName@${System.identityHashCode(loader)}:"
        CouiPreferenceDividerHooker.hook(loader, apkPath, prefix)
        runCatching { CouiDividerViewHooker.hook() }
            .onFailure { HookLog.w(TAG, "COUI divider view hooks unavailable in $packageName", it) }
        ModernReflect.findClassIfExists("com.coui.appcompat.utils.COUIDividerHelper", loader)?.let { helper ->
            runCatching {
                val owner = helper.declaredFields.single { it.type == View::class.java }.apply { isAccessible = true }
                val draw = helper.getDeclaredMethod("draw", Canvas::class.java)
                ModernHookRegistry.installFast(prefix + "helper", draw, XposedInterface.Hooker { chain ->
                    if (shouldHide(runCatching { owner.get(chain.thisObject) as? View }.getOrNull())) null else chain.proceed()
                })
            }.onFailure { HookLog.w(TAG, "COUI divider helper unavailable in $packageName", it) }
        }
        val recycler = ModernReflect.findClassIfExists(RECYCLER, loader) ?: return
        val couiRecycler = ModernReflect.findClassIfExists(COUI_RECYCLER, loader)
        runCatching {
            val targets = mutableListOf<CouiDividerTargets.Target>()
            couiRecycler?.let { targets += CouiDividerTargets.resolve(it, recycler) }
            listOf("androidx.preference.PreferenceFragmentCompat", "com.coui.appcompat.preference.COUIPreferenceFragment").forEach { name ->
                ModernReflect.findClassIfExists(name, loader)?.let { fragment ->
                    targets += CouiDividerTargets.resolvePreference(fragment, recycler)
                    fragment.declaredFields.forEach { field ->
                        if (field.type.name.startsWith("com.coui.") || field.type.name.startsWith("androidx.")) {
                            targets += CouiDividerTargets.resolveDecoration(field.type, recycler)
                        }
                    }
                }
            }
            ModernReflect.findClassIfExists("com.coui.appcompat.preference.COUIPreferenceItemDecoration", loader)
                ?.let { targets += CouiDividerTargets.resolveDecoration(it, recycler) }
            var installed = 0
            fun install(target: CouiDividerTargets.Target) {
                runCatching {
                    ModernHookRegistry.installFast(prefix + target.method.toGenericString(), target.method,
                        XposedInterface.Hooker { chain ->
                            val view = if (CouiDividerSettings.enabledXposed()) {
                                runCatching {
                                    val argument = chain.args[1]
                                    (if (target.itemView == null) argument else target.itemView.get(argument)) as? View
                                }.getOrNull()
                            } else null
                            if (shouldHide(view)) null else chain.proceed()
                        })
                    installed++
                }.onFailure { HookLog.w(TAG, "COUI divider hook unavailable in $packageName", it) }
            }
            targets.distinctBy { it.method }.forEach(::install)

            val inspected = ConcurrentHashMap.newKeySet<Class<*>>()
            fun inspect(decoration: Any?) {
                val type = decoration?.javaClass ?: return
                if (!inspected.add(type)) return
                runCatching {
                    val resolved = CouiDividerTargets.resolveDecoration(type, recycler)
                    if (resolved.none {
                        it.method.declaringClass.name.startsWith("androidx.recyclerview.widget.") ||
                            it.method.declaringClass.name.startsWith("com.coui.appcompat.preference.")
                    }) return
                    resolved.forEach(::install)
                }.onFailure { HookLog.w(TAG, "Divider instance unavailable in $packageName", it) }
            }
            recycler.declaredMethods.filter { method ->
                val args = method.parameterTypes
                method.returnType == Void.TYPE && args.isNotEmpty() && args.size <= 2 &&
                    CouiDividerTargets.isDecoration(args[0], recycler) &&
                    (args.size == 1 || args[1] == Int::class.javaPrimitiveType)
            }.forEach { method ->
                ModernHookRegistry.installFast(prefix + "register:" + method.toGenericString(), method,
                    XposedInterface.Hooker { chain -> inspect(chain.args[0]); chain.proceed() })
            }
            val seenViews = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
            val lists = recycler.declaredFields.filter { List::class.java.isAssignableFrom(it.type) }
                .onEach { it.isAccessible = true }
            recycler.declaredMethods.filter { it.name == "onDraw" && it.parameterTypes.contentEquals(arrayOf(Canvas::class.java)) }
                .forEach { method ->
                    ModernHookRegistry.installFast(prefix + "existing:" + method.toGenericString(), method,
                        XposedInterface.Hooker { chain ->
                            val view = chain.thisObject
                            if (view != null && synchronized(seenViews) { seenViews.add(view) }) {
                                lists.forEach { field -> runCatching { (field.get(view) as? List<*>)?.forEach(::inspect) } }
                            }
                            chain.proceed()
                        })
                }
            // Invalidate callers that may already contain an inlined divider drawing method.
            val callers = listOfNotNull(recycler, couiRecycler) + (couiRecycler?.declaredClasses?.toList() ?: emptyList()) +
                (couiRecycler?.declaredFields?.map { it.type } ?: emptyList()).filter { it.name.startsWith("androidx.recyclerview.widget.") }
            callers.distinct().forEach { type ->
                runCatching {
                    type.declaredMethods.filter { method ->
                        !Modifier.isAbstract(method.modifiers) && method.parameterTypes.contains(Canvas::class.java)
                    }.forEach { method -> runCatching { ModernHookRuntime.requireModule().deoptimize(method) } }
                }
            }
            HookLog.i(TAG, "Installed $installed COUI divider drawing hooks in $packageName")
        }.onFailure { HookLog.w(TAG, "COUI divider structure unavailable in $packageName", it) }
    }

    internal fun shouldHide(view: View?): Boolean {
        if (view == null || !CouiDividerSettings.enabledXposed()) return false
        return isSystemContext(view.context)
    }

    internal fun isSystemContext(context: Context): Boolean = runCatching {
        val app = context.applicationInfo
        app.packageName != "com.mi.onextbox" &&
            app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)
}

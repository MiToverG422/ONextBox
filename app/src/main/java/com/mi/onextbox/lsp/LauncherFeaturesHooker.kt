package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.Editable
import android.text.InputFilter
import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.WeakHashMap

// Recent tasks, icon badges, and folder name input.
internal object LauncherFeaturesHooker {
    private const val TAG = "LauncherFeatures"

    fun hook(classLoader: ClassLoader, apkPath: String) {
        LauncherGridHooker.hook(classLoader, apkPath)
        LauncherRecentInteractionHooker.hook(classLoader)
        LauncherBlurHooker.hook(classLoader, apkPath)
        if (enabled(LauncherFeature.RightmostCategories)) LauncherCategoryPageHooker.hook(classLoader)
        if (enabled(LauncherFeature.OldClearButton) || enabled(LauncherFeature.HideClearButton)) {
            install("clear-button") { hookClearButton(classLoader) }
        }
        if (listOf(LauncherFeature.HideShortcutBadge, LauncherFeature.HideWorkBadge, LauncherFeature.HideCloneBadge)
                .any(::enabled)
        ) install("badges") { hookBadges(classLoader) }
        if (enabled(LauncherFeature.HideUpdateDot)) install("update-dot") {
            val type = ModernReflect.findClass("com.android.launcher3.BubbleTextView", classLoader)
            val method = type.getDeclaredMethod("isShouldShowGreenDot", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            require(method.returnType == Boolean::class.javaObjectType)
            hook(method) { chain -> if (enabled(LauncherFeature.HideUpdateDot)) false else chain.proceed() }
            val draw = type.getDeclaredMethod("drawNewUpdateDotIfNecessary", Canvas::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            hook(draw) { chain -> if (enabled(LauncherFeature.HideUpdateDot)) null else chain.proceed() }
        }
        if (enabled(LauncherFeature.UnlimitedFolderInput)) install("folder-input") { hookFolderInput(classLoader) }
        LauncherDockHooker.hook(classLoader)
        LauncherRecentMemoryHooker.hook(classLoader, apkPath)
    }

    /** C16 groups whose native producers and consumers are present in the C16 launcher. */
    fun hookColorOs16(classLoader: ClassLoader, apkPath: String) {
        LauncherGridHooker.hook(classLoader, apkPath)
        LauncherRecentInteractionHooker.hook(classLoader)
        if (enabled(LauncherFeature.OldClearButton) || enabled(LauncherFeature.HideClearButton)) {
            install("clear-button") { hookClearButton(classLoader) }
        }
        if (listOf(LauncherFeature.HideShortcutBadge, LauncherFeature.HideWorkBadge, LauncherFeature.HideCloneBadge)
                .any(::enabled)) install("badges") { hookBadges(classLoader) }
        if (enabled(LauncherFeature.HideUpdateDot)) install("update-dot") {
            val type = ModernReflect.findClass("com.android.launcher3.BubbleTextView", classLoader)
            hook(type.getDeclaredMethod("isShouldShowGreenDot", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)) {
                if (enabled(LauncherFeature.HideUpdateDot)) false else it.proceed()
            }
            hook(type.getDeclaredMethod("drawNewUpdateDotIfNecessary", Canvas::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)) {
                if (enabled(LauncherFeature.HideUpdateDot)) null else it.proceed()
            }
        }
        if (enabled(LauncherFeature.UnlimitedFolderInput)) install("folder-input") { hookFolderInput(classLoader) }
        LauncherDockHooker.hook(classLoader)
        LauncherRecentMemoryHooker.hook(classLoader, apkPath)
    }

    private fun hookClearButton(loader: ClassLoader) {
        val c16 = SystemAdaptationDetector.current() == SystemAdaptationVersion.ColorOs16
        val panel = ModernReflect.findClass("com.oplus.quickstep.views.OplusClearAllPanelView", loader)
        val buttonClass = ModernReflect.findClass("com.android.launcher.views.PressFeedbackButton", loader)
        val getButton = panel.getDeclaredMethod("getClearPressButton")
        val setSource = buttonClass.getDeclaredMethod("setSrcDrawable", Drawable::class.java)
        val icons = WeakHashMap<TextView, Drawable>()
        fun isClearButton(button: View?): Boolean = button != null && runCatching {
            button.resources.getResourceEntryName(button.id) == "btn_clear" &&
                generateSequence(button.parent) { it.parent }.any(panel::isInstance)
        }.getOrDefault(false)
        fun applyPanel(instance: Any?) {
            val button = getButton.invoke(instance) as? TextView ?: return
            when (LauncherFeatureRules.clearButton(enabled(LauncherFeature.OldClearButton), enabled(LauncherFeature.HideClearButton))) {
                LauncherFeatureRules.ClearButton.Hidden -> {
                    ModernReflect.callMethod(button, "setVisibilityByForce", View.INVISIBLE)
                    button.isClickable = false
                    button.isLongClickable = false
                }
                LauncherFeatureRules.ClearButton.Old -> {
                    val resources = button.resources
                    val iconId = resources.getIdentifier("oplus_recent_clear_all", "drawable", "com.android.launcher")
                    val sizeId = resources.getIdentifier("oplus_recent_clear_all_btn_width", "dimen", "com.android.launcher")
                    if (iconId == 0 || sizeId == 0) return
                    val size = resources.getDimensionPixelSize(sizeId)
                    val cached = icons[button]
                    val icon = cached ?: button.context.getDrawable(iconId)?.mutate()?.also { icons[button] = it }
                        ?: return
                    val bounds = LauncherFeatureRules.clearIconBounds(
                        button.width, button.height, icon.intrinsicWidth, icon.intrinsicHeight, size,
                    )
                    icon.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    if (cached == null || button.text.isNotEmpty()) {
                        // Set icon bounds during binding instead of waiting for layout.
                        setSource.invoke(button, icon)
                        // Preserve native click handling, task clearing, and accessibility labels.
                        button.text = ""
                    }
                    if (button.layoutParams.width != size || button.layoutParams.height != size) {
                        button.layoutParams = button.layoutParams.apply { width = size; height = size }
                    }
                    button.minimumWidth = size
                    button.minimumHeight = size
                }
                LauncherFeatureRules.ClearButton.Default -> Unit
            }
        }
        listOf(
            panel.getDeclaredMethod("onFinishInflate"),
            panel.getDeclaredMethod("setAlpha", Float::class.javaPrimitiveType),
            panel.getDeclaredMethod("onConfigurationChanged", android.content.res.Configuration::class.java),
            panel.getDeclaredMethod(if (c16) "updateClearAllSize" else "y"),
        ).forEach { method ->
            after(method) { chain ->
                if (method.name == "onConfigurationChanged") {
                    (getButton.invoke(chain.thisObject) as? TextView)?.let { icons.remove(it) }
                }
                applyPanel(chain.thisObject)
            }
        }
        for (name in listOf("setVisibility", "setVisibilityByForce")) {
            hook(buttonClass.getDeclaredMethod(name, Int::class.javaPrimitiveType)) { chain ->
                val button = chain.thisObject as? View
                if (isClearButton(button) && enabled(LauncherFeature.HideClearButton)) chain.proceed(arrayOf(View.INVISIBLE))
                else chain.proceed()
            }
        }
        // The host re-enables hit targets during every recents transition. It also
        // invokes performClick from the panel, which ignores View.isClickable.
        val clickable = panel.getDeclaredMethod("setViewClickable", Float::class.javaPrimitiveType)
        hook(clickable) { chain ->
            if (enabled(LauncherFeature.HideClearButton)) chain.proceed(arrayOf(0f)) else chain.proceed()
        }
        val panelClick = panel.getDeclaredMethod(if (c16) "clearAllBtnPerformClick" else "m")
        require(panelClick.returnType == Void.TYPE)
        hook(panelClick) { chain ->
            if (enabled(LauncherFeature.HideClearButton)) null else chain.proceed()
        }
        val listenerSetter = panel.getDeclaredMethod("setOnClearClickListener", View.OnClickListener::class.java)
        after(listenerSetter) { chain ->
            // Leave the host listener untouched: a module-owned wrapper would outlive
            // its hooks during hot reload. Deopt its runtime bridge instead (C17 inlines b).
            runCatching {
                val button = getButton.invoke(chain.thisObject) as? View ?: return@runCatching
                val info = ModernReflect.getObjectField(button, "mListenerInfo") ?: return@runCatching
                val listener = ModernReflect.getObjectField(info, "mOnClickListener") ?: return@runCatching
                ModernHookRuntime.requireModule().deoptimize(listener.javaClass.getDeclaredMethod("onClick", View::class.java))
            }.onFailure {
                HookLog.w(TAG, "Clear click bridge deoptimization unavailable", it)
            }
            applyPanel(chain.thisObject)
        }
        // The verified host click bridge may show a permission dialog before reaching
        // the external listener. Suppress that bridge too, scoped to this panel only.
        val clickBridge = if (c16) {
            panel.getDeclaredMethod("lambda\$setOnClearClickListener\$6", View.OnClickListener::class.java, View::class.java)
        } else panel.getDeclaredMethod("b", panel, View.OnClickListener::class.java, View::class.java)
        hook(clickBridge) { chain ->
            if (enabled(LauncherFeature.HideClearButton)) null else chain.proceed()
        }
        panel.declaredMethods.forEach { method ->
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { HookLog.w(TAG, "Clear button caller deoptimization unavailable: ${method.name}", it) }
        }
        if (enabled(LauncherFeature.OldClearButton)) {
            val iconDrawing = LauncherClearIconDrawing(buttonClass)
            hook(buttonClass.getDeclaredMethod("onDraw", Canvas::class.java)) { chain ->
                val button = chain.thisObject as? View ?: return@hook chain.proceed()
                if (!isClearButton(button) ||
                    LauncherFeatureRules.clearButton(enabled(LauncherFeature.OldClearButton), enabled(LauncherFeature.HideClearButton)) !=
                    LauncherFeatureRules.ClearButton.Old
                ) return@hook chain.proceed()
                iconDrawing.draw(button) { chain.proceed() }
            }
        }
    }

    private fun hookBadges(loader: ClassLoader) {
        val c16 = SystemAdaptationDetector.current() == SystemAdaptationVersion.ColorOs16
        val bitmap = ModernReflect.findClass("com.android.launcher3.icons.BitmapInfo", loader)
        val drawable = ModernReflect.findClass("com.android.launcher3.icons.FastBitmapDrawable", loader)
        val flags = bitmap.getDeclaredField("flags")
        val shortcut = bitmap.getDeclaredField("badgeInfo")
        val clear = drawable.getDeclaredMethod("setBadge", Drawable::class.java)
        val bubble = ModernReflect.findClass("com.android.launcher3.BubbleTextView", loader)
        val oplusBubble = ModernReflect.findClass("com.android.launcher3.OplusBubbleTextView", loader)
        val item = ModernReflect.findClass("com.android.launcher3.model.data.ItemInfoWithIcon", loader)
        val itemBitmap = item.getDeclaredField("bitmap")
        val getIcon = bubble.getDeclaredMethod("getIcon")
        val badges = LauncherBadgeState()
        val fancy = ModernReflect.findClass(if (c16) "com.oplus.fancyicon.fancydrawable.IconFancyDrawable" else "xl.c", loader)
        val setWorkFlag = fancy.getDeclaredMethod("setWorkFlag", Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        fun badgeFor(info: Any) = LauncherFeatureRules.badge(shortcut.get(info) != null, flags.getInt(info))
        fun hidden(badge: LauncherFeatureRules.Badge) = LauncherFeatureRules.hideBadge(
            badge, enabled(LauncherFeature.HideShortcutBadge), enabled(LauncherFeature.HideWorkBadge),
            enabled(LauncherFeature.HideCloneBadge),
        )
        fun hide(badge: LauncherFeatureRules.Badge, icon: Any?) {
            when {
                drawable.isInstance(icon) -> {
                    badges.remember(icon!!, badge)
                    if (hidden(badge)) clear.invoke(icon, null)
                }
                icon is LayerDrawable -> {
                    for (index in 0 until icon.numberOfLayers) hide(badge, icon.getDrawable(index))
                }
                fancy.isInstance(icon) && badge == LauncherFeatureRules.Badge.Work && hidden(badge) -> {
                    setWorkFlag.invoke(icon, false, 0)
                }
            }
        }
        fun hideForView(view: Any?, icon: Any?) {
            val value = (view as? View)?.tag?.takeIf(item::isInstance) ?: return
            val info = itemBitmap.get(value) ?: return
            hide(badgeFor(info), icon)
        }
        // Apply the same badge rules when creating, copying, and restoring icons.
        val applyFlags = if (c16) bitmap.getDeclaredMethod("applyFlags", Context::class.java, drawable,
            Int::class.javaPrimitiveType, android.graphics.Path::class.java)
        else bitmap.getDeclaredMethod("applyFlags", Context::class.java, drawable, Int::class.javaPrimitiveType)
        hook(applyFlags) { chain ->
            val badge = badgeFor(chain.thisObject!!)
            chain.getArg(1)?.let { badges.remember(it, badge) }
            val result = chain.proceed()
            hide(badge, chain.getArg(1))
            result
        }
        hook(clear) { chain ->
            val badge = badges.badge(chain.thisObject)
            if (chain.getArg(0) != null && badge != null && hidden(badge)) chain.proceed(arrayOf(null))
            else chain.proceed()
        }
        hook(drawable.getDeclaredMethod("getConstantState")) { chain ->
            val result = chain.proceed()
            badges.copy(chain.thisObject, result)
            result
        }
        val constantState = ModernReflect.findClass("com.android.launcher3.icons.FastBitmapDrawable\$FastBitmapConstantState", loader)
        hook(constantState.getDeclaredMethod("newDrawable")) { chain ->
            val result = chain.proceed()
            badges.badge(chain.thisObject)?.let { hide(it, result) }
            result
        }
        hook(bubble.getDeclaredMethod("setIcon", Drawable::class.java)) { chain ->
            hideForView(chain.thisObject, chain.getArg(0))
            chain.proceed()
        }
        hook(setWorkFlag) { chain ->
            if (enabled(LauncherFeature.HideWorkBadge)) chain.proceed(arrayOf<Any>(false, 0)) else chain.proceed()
        }
        hook(oplusBubble.getDeclaredMethod("resetBadgeState", Boolean::class.javaPrimitiveType)) { chain ->
            hideForView(chain.thisObject, getIcon.invoke(chain.thisObject))
            chain.proceed()
        }
        oplusBubble.declaredMethods.filter { it.name == "setIconToVisibleForFIView" || it.name == "setIconVisibleForFIView" }
            .forEach { method ->
                after(method) { chain -> hideForView(chain.thisObject, getIcon.invoke(chain.thisObject)) }
            }
        listOf(bubble, oplusBubble).forEach { type ->
            after(type.getDeclaredMethod("applyIconAndLabel", item)) { chain ->
                chain.getArg(0)?.let { value ->
                    itemBitmap.get(value)?.let { hide(badgeFor(it), getIcon.invoke(chain.thisObject)) }
                }
            }
        }
        listOf(bitmap, item).forEach { type ->
            type.declaredMethods.filter { it.name == "newIcon" }.forEach { method ->
                ModernHookRuntime.requireModule().deoptimize(method)
            }
        }
    }

    private fun hookFolderInput(loader: ClassLoader) {
        val folder = ModernReflect.findClass("com.android.launcher3.folder.FolderNameEditText", loader)
        val setFilters = TextView::class.java.getDeclaredMethod("setFilters", Array<InputFilter>::class.java)
        hook(setFilters) { chain ->
            if (!folder.isInstance(chain.thisObject) || !enabled(LauncherFeature.UnlimitedFolderInput)) {
                return@hook chain.proceed()
            }
            val filters = chain.getArg(0) as? Array<*> ?: return@hook chain.proceed()
            val replacement = filters.filterNot {
                LauncherFeatureRules.removeFolderFilter(true, true, it is InputFilter.LengthFilter)
            }
            if (replacement.size == filters.size) chain.proceed()
            else chain.proceed(arrayOf(replacement.filterIsInstance<InputFilter>().toTypedArray()))
        }
        folder.declaredConstructors.forEach { constructor ->
            after(constructor) { chain ->
                val input = chain.thisObject as? TextView
                if (input != null && enabled(LauncherFeature.UnlimitedFolderInput)) {
                    val filters = input.filters.filterNot { it is InputFilter.LengthFilter }.toTypedArray()
                    if (filters.size != input.filters.size) input.filters = filters
                }
            }
        }
        // OPPO also crops folder names to their display width through a text watcher.
        val oplusFolder = ModernReflect.findClass("com.android.launcher3.folder.OplusFolder", loader)
        val nameWatcher = oplusFolder.getDeclaredField("mTextWatcher").apply { isAccessible = true }
        val watchers = java.util.Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
        fun observeWatcher(watcher: Any) {
            if (HotReloadLifecycle.isRetiring) return
            watchers[watcher] = true
            hook(watcher.javaClass.getDeclaredMethod("afterTextChanged", Editable::class.java)) { change ->
                if (LauncherFeatureRules.skipFolderNameCrop(
                        enabled(LauncherFeature.UnlimitedFolderInput), watchers.containsKey(change.thisObject),
                    )
                ) null else change.proceed()
            }
        }
        HotReloadLifecycle.registerReplay("launcher:folder-watchers",
            capture = { synchronized(watchers) { watchers.keys.toTypedArray() } },
            replay = { saved -> (saved as? Array<*>)?.filterNotNull()?.forEach(::observeWatcher) })
        oplusFolder.declaredConstructors.forEach { constructor ->
            after(constructor) { chain ->
                val watcher = nameWatcher.get(chain.thisObject) ?: return@after
                observeWatcher(watcher)
            }
        }
    }

    private fun enabled(feature: LauncherFeature) = LspConfig.isLauncherFeatureEnabledXposed(feature)

    private fun hook(method: Executable, callback: (XposedInterface.Chain) -> Any?) {
        ModernHookRegistry.installFast("$TAG:${method.toGenericString()}", method, XposedInterface.Hooker(callback))
    }

    private fun after(method: Executable, callback: (XposedInterface.Chain) -> Unit) {
        hook(method) { chain ->
            val result = chain.proceed()
            runCatching { callback(chain) }.onFailure {
                HookLog.w(TAG, "Failed to update ${method.name}", it)
            }
            result
        }
    }

    private fun install(name: String, block: () -> Unit) {
        runCatching(block).onSuccess { HookLog.i(TAG, "$name hooks installed") }
            .onFailure { HookLog.w(TAG, "$name hooks unavailable", it) }
    }
}

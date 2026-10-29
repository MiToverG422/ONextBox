package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.inputmethod.EditorInfo
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.AdaptationHookGate
import java.lang.reflect.Field
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.WeakHashMap
import kotlin.math.min

internal object KeyboardAppearanceHooker {
    private const val TAG = "KeyboardAppearance"
    private val frames = ThreadLocal<Frame?>()
    private val edgeCorners = ThreadLocal<Iterator<Float>?>()
    private val drawables = IdentityHashMap<Drawable, MutableMap<String, Drawable>>()
    private val paletteIds = ConcurrentHashMap<String, Int>()
    private val renderedViews = WeakHashMap<View, Int>()
    private val blurOwners = WeakHashMap<Any, Boolean>()
    private val hits = ConcurrentHashMap.newKeySet<String>()
    private data class Frame(val mark: String, val left: Float, val right: Float, val type: String)
    private val application by lazy { Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication") }
    private fun context() = application.invoke(null) as? Context
    private fun hit(name: String) { if (hits.add(name)) HookLog.i(TAG, "Applied $name") }
    private fun enabled(key: String) = KeyboardPictorialSettings.active("keyboard_$key")
    private fun number(key: String) = KeyboardPictorialSettings.number("keyboard_$key")
    private fun styled(original: Drawable, name: String, icon: Boolean): Drawable = synchronized(drawables) {
        if (drawables.size > 256) drawables.clear()
        drawables.getOrPut(original) { mutableMapOf() }.getOrPut("$name:$icon") { StyledDrawable(original, name, icon) }
    }

    fun hook(loader: ClassLoader, apkPath: String) {
        fun install(key: String, block: () -> Unit) = runCatching(block)
            .onFailure { HookLog.w(TAG, "$key unavailable", it) }
        install("theme") {
            // Theme consumers can inline the extension facade, so use its backing theme engine.
            val themes = listOf("com.oplus.keyboard.base.theme.a", "com.oplus.keyboard.base.theme.work.e",
                "com.oplus.keyboard.base.theme.work.pack.d", "com.oplus.keyboard.base.theme.work.marker.b").mapNotNull { runCatching { loader.loadClass(it) }.getOrNull() }
            themes.forEach { theme ->
            theme.declaredMethods.filter { it.returnType in setOf(Integer::class.java, Int::class.javaPrimitiveType) && String::class.java in it.parameterTypes }.forEach { method ->
                ModernHookRegistry.installFast("$TAG:color:$method", method) { chain ->
                    val original = chain.proceed()
                    val name = chain.args.firstOrNull { it is String } as? String
                    if (name == null) original else runCatching {
                        val monet = if (enabled("monet")) KeyboardAppearanceRules.monetRole(name)?.let(::systemColor) else null
                        var color = monet ?: (original as? Int) ?: return@runCatching original
                        if (name == "bgKeyboardBlur" && enabled("blur_opacity") && !enabled("monet"))
                            color = (color and 0xffffff) or ((number("blur_opacity") * 255 / 100) shl 24)
                        if (color != original) hit("theme:$name")
                        color
                    }.getOrDefault(original)
                }
            }
            theme.declaredMethods.filter { it.returnType == ColorStateList::class.java && String::class.java in it.parameterTypes }.forEach { method ->
                ModernHookRegistry.installFast("$TAG:color-list:$method", method) { chain ->
                    val original = chain.proceed() as? ColorStateList ?: return@installFast null
                    if (!enabled("monet")) return@installFast original
                    runCatching {
                        val name = chain.args.first { it is String } as String
                        val target = KeyboardAppearanceRules.monetRole(name)?.let(::systemColor) ?: return@runCatching original
                        hit("color-list:$name")
                        KeyboardMonetColors.recolor(original, target)
                    }.onFailure { if (hits.add("color-list-failure")) HookLog.w(TAG, "State color correction failed", it) }.getOrDefault(original)
                }
            }
            theme.declaredMethods.filter { Drawable::class.java.isAssignableFrom(it.returnType) && String::class.java in it.parameterTypes }.forEach { method ->
                ModernHookRegistry.installFast("$TAG:drawable:$method", method) { chain ->
                    val original = chain.proceed() as? Drawable ?: return@installFast null
                    val name = chain.args.first { it is String } as String
                    when {
                        original is StyledDrawable -> original
                        enabled("monet") && (name.startsWith("bg") || name.startsWith("icon")) -> styled(original, name,
                            name.startsWith("icon") && !name.startsWith("iconHeaderToolItem"))
                        else -> original
                    }
                }
            }
            }
            val cacheKey = "$TAG:theme-consumers-v2"
            val cached = DexTargetCache.restoredState(cacheKey) as? Array<*>
            val consumers = cached?.filterIsInstance<java.lang.reflect.Executable>() ?: ApkDexSession.withSession(apkPath) { open ->
                val dex = open()
                val roots = (themes.map { it.name } + "com.oplus.keyboard.base.theme.work.a" + "com.oplus.keyboard.base.theme.work.b").flatMap { themeName ->
                    dex.findMethod { matcher { declaredClass = themeName } }
                }.filter { "java.lang.String" in it.paramTypeNames && it.returnTypeName in setOf("java.lang.Integer", "int", "android.graphics.drawable.Drawable", "android.content.res.ColorStateList") }
                val direct = roots.flatMap { it.callers }.distinctBy { it.descriptor }
                val second = direct.flatMap { it.callers }.distinctBy { it.descriptor }
                val third = second.flatMap { it.callers }.distinctBy { it.descriptor }
                (roots + direct + second + third).distinctBy { it.descriptor }
                    .filter { it.className.startsWith("com.oplus.keyboard.") && !it.isStaticInitializer }
                    .mapNotNull { method -> runCatching {
                        if (method.isConstructor) method.getConstructorInstance(loader) else method.getMethodInstance(loader)
                    }.getOrNull() }
            }
            consumers.forEach { runCatching { com.mi.onextbox.lsp.compat.ModernHookRuntime.requireModule().deoptimize(it) } }
            HotReloadLifecycle.register(cacheKey, capture = { consumers.toTypedArray() })
            HookLog.i(TAG, "Theme render paths prepared: ${themes.size} theme backends, ${consumers.size} consumers")
        }
        install("blur") {
            val blur = loader.loadClass("com.oplus.keyboard.input.view.blur.b")
            val view = blur.getDeclaredField("a").apply { isAccessible = true }
            val manager = blur.getDeclaredField("b").apply { isAccessible = true }
            blur.declaredMethods.filter { it.name == "b" && it.parameterCount == 0 || it.name == "a" && it.parameterTypes.contentEquals(arrayOf(View::class.java)) }.forEach { method ->
            ModernHookRegistry.installFast("$TAG:blur:$method", method) { chain ->
                val result = chain.proceed()
                runCatching {
                    synchronized(blurOwners) { blurOwners[chain.thisObject!!] = true }
                    val target = manager.get(chain.thisObject) ?: return@runCatching
                    val owner = view.get(chain.thisObject) as? View ?: return@runCatching
                    if (enabled("blur_radius")) {
                        target.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType).invoke(target, number("blur_radius"))
                        hit("blur-radius:${number("blur_radius")}")
                    }
                    if (enabled("blur_corner")) {
                        val radius = min(owner.width, owner.height) * number("blur_corner") / 200f
                        target.javaClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).invoke(target, radius, radius, radius, radius)
                        hit("blur-corner:${number("blur_corner")}")
                    }
                }.onFailure { HookLog.d(TAG, "Material update skipped: ${it.javaClass.simpleName}") }
                result
            }
            }
        }
        install("toolbar") {
            val item = loader.loadClass("com.oplus.keyboard.input.view.head.ToolItemView")
            val icon = item.getDeclaredField("a").apply { isAccessible = true }
            val background = item.getDeclaredField("c").apply { isAccessible = true }
            val interactiveBackground = item.getDeclaredField("l").apply { isAccessible = true }
            val persistentBackground = item.getDeclaredField("m").apply { isAccessible = true }
            val backgroundSize = item.getDeclaredField("d").apply { isAccessible = true }
            val monetBackground = StyledDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT), "bgHeaderItem", false)
            ModernHookRegistry.installFast("$TAG:toolbar", item.getDeclaredMethod("onDraw", Canvas::class.java)) { chain ->
                val owner = chain.thisObject as View
                val oldIcon = icon.get(chain.thisObject) as? Drawable
                val oldBackground = background.get(chain.thisObject) as? StateListDrawable
                val oldInteractive = interactiveBackground.getBoolean(owner)
                val oldPersistent = persistentBackground.getBoolean(owner)
                try {
                    if (oldIcon != null && oldIcon !is StyledDrawable && (enabled("toolbar_icon_alpha") || enabled("monet")))
                        icon.set(chain.thisObject, styled(oldIcon, "iconHeaderItem", true))
                    if (enabled("monet")) {
                        val width = owner.width - owner.paddingLeft - owner.paddingRight
                        val height = owner.height - owner.paddingTop - owner.paddingBottom
                        val requested = backgroundSize.getInt(owner)
                        val size = if (requested > 0) requested else min(width, height)
                        val x = owner.paddingLeft + (width - size) / 2
                        val y = owner.paddingTop + (height - size) / 2
                        if (size > 0) {
                            monetBackground.setBounds(x, y, x + size, y + size)
                            monetBackground.state = owner.drawableState
                            monetBackground.draw(chain.args[0] as Canvas)
                        }
                        interactiveBackground.setBoolean(owner, false)
                        persistentBackground.setBoolean(owner, false)
                        hit("consistent-monet-toolbar")
                    } else if (oldBackground != null && listOf("toolbar_bg_alpha", "toolbar_outline", "toolbar_no_shadow").any(::enabled)) {
                        val state = StateListDrawable()
                        state.addState(intArrayOf(), styled(oldBackground, "bgHeaderItem", false))
                        background.set(chain.thisObject, state)
                    }
                    chain.proceed()
                } finally {
                    icon.set(chain.thisObject, oldIcon)
                    // Let the native first draw initialize its background when it was absent.
                    if (oldBackground != null) background.set(chain.thisObject, oldBackground)
                    interactiveBackground.setBoolean(owner, oldInteractive)
                    persistentBackground.setBoolean(owner, oldPersistent)
                }
            }
        }
        install("keys") {
            val renderer = loader.loadClass("com.oplus.keyboard.input.view.body.r")
            val keyType = loader.loadClass("com.oplus.keyboard.base.entity.e")
            val draws = KeyboardAppearanceRules.drawEntries(renderer, Canvas::class.java, keyType)
            fun field(name: String): Field = keyType.getDeclaredField(name).apply { isAccessible = true }
            val mark = field("t"); val left = field("b"); val right = field("c")
            val code = field("a")
            val top = field("d"); val bottom = field("e"); val eligible = field("E")
            val codes = loader.loadClass("com.oplus.keyboard.base.enums.KeyCode").let { type ->
                val value = type.getDeclaredMethod("getValue")
                type.enumConstants.orEmpty().associate { value.invoke(it) as Int to (it as Enum<*>).name }
            }
            val keyboardType = renderer.declaredFields.single { it.type.name == "com.oplus.keyboard.base.enums.KeyboardType" }.apply { isAccessible = true }
            install("material-key-geometry") {
                val edge = loader.loadClass("com.oplus.keyboard.base.util.t")
                val shadow = loader.loadClass("com.oplus.keyboard.base.util.s")
                val effectField = edge.getDeclaredField("a").apply { isAccessible = true }
                val effectClass = effectField.type
                val setCorner = effectClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType)
                val setEdge = effectClass.getMethod("setEdgeEnable", Boolean::class.javaPrimitiveType)
                val setShadow = effectClass.getMethod("setShadowEnable", Boolean::class.javaPrimitiveType)
                val dp = loader.loadClass("com.oplus.keyboard.utils.f").getDeclaredMethod("a", Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                val signature = arrayOf(Canvas::class.java, List::class.java, Boolean::class.javaPrimitiveType)
                val edges = edge.getDeclaredMethod("b", *signature)
                ModernHookRegistry.installFast("$TAG:material-edges", edges) { chain ->
                    if (listOf("monet", "key_corner", "key_no_outline", "key_no_shadow").none(::enabled)) return@installFast chain.proceed()
                    val nativeRadius = (dp.invoke(null, 8f, false) as Number).toFloat()
                    val radii = (chain.args[1] as List<*>).filterNotNull().mapNotNull { key ->
                        val width = right.getInt(key) - left.getInt(key)
                        val height = bottom.getInt(key) - top.getInt(key)
                        if (!eligible.getBoolean(key) || width <= 0 || height <= 0) null else {
                            val ratio = KeyboardAppearanceRules.keyCornerRatio(codes[code.getInt(key)].orEmpty().contains("ENTER"), enabled("monet"),
                                if (enabled("key_corner")) number("key_corner") else null)
                            if (!enabled("monet") && !enabled("key_corner")) nativeRadius else min(width, height) * ratio / 2f
                        }
                    }
                    val previous = edgeCorners.get()
                    edgeCorners.set(radii.iterator())
                    try { chain.proceed() } finally { edgeCorners.set(previous) }
                }
                ModernHookRegistry.installFast("$TAG:material-edge-shape", effectClass.getDeclaredMethod("draw", Canvas::class.java)) { chain ->
                    val iterator = edgeCorners.get()
                    if (iterator == null || !iterator.hasNext()) return@installFast chain.proceed()
                    val effect = effectField.get(null) as Drawable
                    if (chain.thisObject !== effect) return@installFast chain.proceed()
                    val nativeRadius = (dp.invoke(null, 8f, false) as Number).toFloat()
                    val radius = iterator.next()
                    synchronized(effect) {
                        try {
                            setCorner.invoke(effect, radius)
                            setEdge.invoke(effect, !enabled("key_no_outline"))
                            setShadow.invoke(effect, !enabled("key_no_shadow"))
                            hit("material-key-outline")
                            chain.proceed()
                        } finally {
                            // This dedicated native key effect is initialized with 8dp corners and both effects enabled.
                            setCorner.invoke(effect, nativeRadius)
                            setEdge.invoke(effect, true)
                            setShadow.invoke(effect, true)
                        }
                    }
                }
                val ambient = shadow.getDeclaredMethod("d", *signature)
                ModernHookRegistry.installFast("$TAG:key-ambient-shadow", ambient) { chain ->
                    if (enabled("key_no_shadow") || enabled("monet")) null else chain.proceed()
                }
                renderer.declaredMethods.forEach { runCatching { com.mi.onextbox.lsp.compat.ModernHookRuntime.requireModule().deoptimize(it) } }
            }
            draws.forEach { draw -> ModernHookRegistry.installFast("$TAG:key-draw:$draw", draw) { chain ->
                if (listOf("key_font_scale", "hide_t9_marks", "hide_qwerty_marks", "center_marks").none(::enabled)) return@installFast chain.proceed()
                val old = frames.get()
                val key = chain.args[1]
                frames.set(Frame(mark.get(key)?.toString().orEmpty(), left.getInt(key).toFloat(), right.getInt(key).toFloat(), keyboardType.get(chain.thisObject)?.toString().orEmpty()))
                try { chain.proceed() } finally { frames.set(old) }
            } }
            val background = renderer.declaredMethods.single { Drawable::class.java.isAssignableFrom(it.returnType) &&
                it.parameterTypes.contentEquals(arrayOf(keyType, Boolean::class.javaPrimitiveType)) }
            ModernHookRegistry.installFast("$TAG:key-background", background) { chain ->
                val original = chain.proceed() as? Drawable ?: return@installFast null
                if (listOf("monet", "key_corner", "key_alpha", "key_no_outline", "key_no_shadow").none(::enabled)) original
                else {
                    val name = codes[code.getInt(chain.args[0])].orEmpty()
                    val kind = when {
                        name.contains("ENTER") -> "keyEnter"
                        name in setOf("BACK", "SHIFT", "SPACE", "SYMBOL", "LEFTSYMBOL", "CLEAR") -> "keyFunction"
                        else -> "key"
                    }
                    hit("key-background")
                    styled(original, kind + if (chain.args[1] == true) "Pressed" else "", false)
                }
            }
            val text = Canvas::class.java.getDeclaredMethod("drawText", String::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Paint::class.java)
            ModernHookRegistry.installFast("$TAG:key-text", text) { chain ->
                val frame = frames.get() ?: return@installFast chain.proceed()
                val word = chain.args[0] as String
                val secondary = frame.mark.isNotEmpty() && word == frame.mark
                if (secondary && KeyboardAppearanceRules.hideMark(frame.type, enabled("hide_t9_marks"), enabled("hide_qwerty_marks"))) {
                    hit("hide-secondary:${frame.type}"); return@installFast null
                }
                val paint = chain.args[3] as Paint
                val size = paint.textSize
                val args = chain.args.toTypedArray()
                try {
                    if (enabled("key_font_scale")) { paint.textSize = size * number("key_font_scale") / 100f; hit("key-font-scale:${number("key_font_scale")}") }
                    if (secondary && enabled("center_marks")) { args[1] = (frame.left + frame.right - paint.measureText(word)) / 2f; hit("center-secondary") }
                    chain.proceed(args)
                } finally { paint.textSize = size }
            }
            val dirty = renderer.getDeclaredField("p").apply { isAccessible = true }
            ModernHookRegistry.installFast("$TAG:cached-keys", renderer.getDeclaredMethod("onDraw", Canvas::class.java)) { chain ->
                val owner = chain.thisObject as View
                val token = KeyboardPictorialSettings.keyboard.fold(1) { hash, option ->
                    hash * 31 + if (!KeyboardPictorialSettings.active(option.key)) 0 else if (option.max > option.min) KeyboardPictorialSettings.number(option.key) + 1 else 1
                }
                synchronized(renderedViews) {
                    if (renderedViews.put(owner, token) != token) dirty.setBoolean(owner, true)
                }
                chain.proceed()
            }
            renderer.declaredMethods.forEach { runCatching { com.mi.onextbox.lsp.compat.ModernHookRuntime.requireModule().deoptimize(it) } }
            HookLog.i(TAG, "Key appearance installed: ${draws.size} native draw entries")
        }
        install("input-refresh") {
            val service = loader.loadClass("com.oplus.keyboard.service.ImeService")
            val start = runCatching { service.getDeclaredMethod("onStartInputView", EditorInfo::class.java, Boolean::class.javaPrimitiveType) }
                .getOrElse { android.inputmethodservice.InputMethodService::class.java.getDeclaredMethod("onStartInputView", EditorInfo::class.java, Boolean::class.javaPrimitiveType) }
            ModernHookRegistry.installFast("$TAG:input-refresh", start) { chain ->
                val result = chain.proceed()
                synchronized(renderedViews) { renderedViews.keys.toList() }.forEach { it.invalidate() }
                synchronized(blurOwners) { blurOwners.keys.toList() }.forEach { owner ->
                    runCatching { owner.javaClass.getDeclaredMethod("b").invoke(owner) }
                }
                result
            }
        }
        HotReloadLifecycle.register("$TAG:frame", canReload = { synchronized(drawables) { drawables.isEmpty() } },
            detach = { frames.remove(); edgeCorners.remove(); synchronized(drawables) { drawables.clear() }; synchronized(renderedViews) { renderedViews.clear() }; synchronized(blurOwners) { blurOwners.clear() } })
    }

    private fun systemColor(role: String): Int? = runCatching {
        val res = context()?.resources ?: return@runCatching null
        val night = res.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val name = "system_${role}_${if (night) "dark" else "light"}"
        val id = paletteIds.getOrPut(name) { res.getIdentifier(name, "color", "android") }
        if (id == 0) null else res.getColor(id, null)
    }.getOrNull()

    private class StyledDrawable(private val original: Drawable, private val name: String, private val icon: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var extraAlpha = 255
        private var filter: ColorFilter? = null
        private val sampled by lazy {
            runCatching {
                val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                val previous = Rect(original.bounds)
                try { original.setBounds(0, 0, 24, 24); original.draw(Canvas(bitmap)); bitmap.getPixel(12, 12) }
                finally { original.bounds = previous; bitmap.recycle() }
            }.getOrDefault(Color.TRANSPARENT)
        }
        override fun draw(canvas: Canvas) {
            if (enabled("monet") || icon && enabled("toolbar_icon_alpha") || name.startsWith("bgHeader") && listOf("toolbar_bg_alpha", "toolbar_outline", "toolbar_no_shadow").any(::enabled)) hit("drawable:$name")
            val key = name.startsWith("key")
            val toolbar = name == "bgHeaderItem"
            val themedSurface = !icon && !key && !toolbar
            val controls = when {
                icon -> listOf("monet", "toolbar_icon_alpha")
                key -> listOf("monet", "key_corner", "key_alpha", "key_no_outline", "key_no_shadow")
                toolbar -> listOf("monet", "toolbar_bg_alpha", "toolbar_outline", "toolbar_no_shadow")
                else -> listOf("monet")
            }
            if (!AdaptationHookGate.isBoundProfileActive() || HotReloadLifecycle.isRetiring || controls.none(::enabled)) {
                val previous = Rect(original.bounds)
                try { original.bounds = bounds; original.draw(canvas) } finally { original.bounds = previous }
                return
            }
            val monet = enabled("monet")
            val alpha = when {
                icon && name == "iconHeaderItem" && enabled("toolbar_icon_alpha") -> number("toolbar_icon_alpha")
                toolbar && enabled("toolbar_bg_alpha") -> number("toolbar_bg_alpha")
                key && enabled("key_alpha") -> number("key_alpha")
                else -> 100
            } * extraAlpha / 100
            val flat = !icon && (monet && (!themedSurface || KeyboardAppearanceRules.rectangularKeyboardBackground(name)) || key && (enabled("key_corner") || enabled("key_no_outline") || enabled("key_no_shadow")) || toolbar && enabled("toolbar_no_shadow"))
            val saved = canvas.saveLayerAlpha(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), alpha)
            try {
                if (flat) {
                    val selected = state.any { it == android.R.attr.state_pressed || it == android.R.attr.state_selected || it == android.R.attr.state_checked }
                    val role = when {
                        name.contains("Enter") || name.contains("Pressed") || toolbar && selected -> "primary_container"
                        name.contains("Function") -> "secondary_container"
                        toolbar -> "secondary_container"
                        key -> "surface_container_highest"
                        else -> "surface_container_low"
                    }
                    paint.color = if (monet) systemColor(role) ?: sampled else sampled
                    paint.colorFilter = filter
                    val ratio = if (themedSurface) 0f else if (toolbar) 1f else KeyboardAppearanceRules.keyCornerRatio(name.contains("Enter"), monet,
                        if (key && enabled("key_corner")) number("key_corner") else null)
                    val radius = min(bounds.width(), bounds.height()) * ratio / 2f
                    val shape = RectF(bounds)
                    val density = context()?.resources?.displayMetrics?.density ?: 1f
                    if (key && !monet && !enabled("key_no_shadow")) {
                        shape.inset(density, density)
                        paint.setShadowLayer(density * .75f, 0f, density * .5f, 0x26000000)
                    }
                    canvas.drawRoundRect(shape, radius, radius, paint)
                    paint.clearShadowLayer()
                    if (key && !monet && !enabled("key_no_outline")) {
                        paint.color = systemColor("outline_variant") ?: 0x40808080
                        paint.style = Paint.Style.STROKE; paint.strokeWidth = density * .33f
                        canvas.drawRoundRect(shape, radius, radius, paint)
                        paint.style = Paint.Style.FILL
                    }
                } else {
                    val previous = Rect(original.bounds)
                    val oldFilter = original.colorFilter
                    try {
                        original.bounds = bounds
                        original.colorFilter = filter ?: if (monet) KeyboardAppearanceRules.monetRole(name)?.let(::systemColor)?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) } ?: oldFilter else oldFilter
                        original.draw(canvas)
                    } finally { original.bounds = previous; original.colorFilter = oldFilter }
                }
                if (toolbar && enabled("toolbar_outline")) {
                    paint.color = systemColor("outline_variant") ?: Color.GRAY
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = context()?.resources?.displayMetrics?.density ?: 1f
                    val r = min(bounds.width(), bounds.height()) * .15f
                    canvas.drawRoundRect(RectF(bounds).apply { inset(paint.strokeWidth, paint.strokeWidth) }, r, r, paint)
                    paint.style = Paint.Style.FILL
                }
            } finally { canvas.restoreToCount(saved) }
        }
        override fun setAlpha(alpha: Int) { extraAlpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = original.intrinsicWidth
        override fun getIntrinsicHeight() = original.intrinsicHeight
        override fun getPadding(padding: Rect) = original.getPadding(padding)
        override fun onStateChange(state: IntArray): Boolean = original.setState(state)
        override fun isStateful() = original.isStateful
        override fun getConstantState(): ConstantState? = original.constantState?.let { native ->
            object : ConstantState() {
                override fun newDrawable(): Drawable = StyledDrawable(native.newDrawable().mutate(), name, icon)
                override fun newDrawable(resources: android.content.res.Resources?): Drawable = StyledDrawable(native.newDrawable(resources).mutate(), name, icon)
                override fun getChangingConfigurations() = native.changingConfigurations
            }
        }
    }
}

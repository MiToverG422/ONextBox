package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/** Restores the ColorOS 15 About device OTA card composition inside ColorOS 17 Settings. */
object C15AboutOtaHooker {
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val MODULE_PACKAGE = "com.mi.onextbox"
    private const val PREFERENCE_CLASS =
        "com.oplus.settings.widget.preference.AboutDeviceOtaUpdatePreference"
    private const val DEVICE_INFO_UTILS = "com.oplus.settings.utils.OplusDeviceInfoUtils"
    private const val LIGHT_BACKGROUND = "settings_c15/about_ota_light.webp"
    private const val DARK_BACKGROUND = "settings_c15/about_ota_dark.png"

    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val backgroundBitmaps = ConcurrentHashMap<String, Bitmap>()

    fun hook(classLoader: ClassLoader) {
        val loaderKey = System.identityHashCode(classLoader)
        synchronized(hookedLoaders) {
            if (loaderKey in hookedLoaders) return
            val preferenceClass = XposedHelpers.findClassIfExists(PREFERENCE_CLASS, classLoader)
            if (preferenceClass == null) {
                HookLog.w("C15AboutOta", "OTA preference class unavailable")
                return
            }
            runCatching {
                val bindHooks = XposedBridge.hookAllMethods(
                    preferenceClass,
                    "onBindViewHolder",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val root = rootFromHolder(param.args.firstOrNull()) ?: return
                            runCatching { clearOverlay(root) }
                                .onFailure { HookLog.w("C15AboutOta", "restore before OEM bind failed", it) }
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable != null) return
                            val root = rootFromHolder(param.args.firstOrNull()) ?: return
                            runCatching {
                                bindCard(
                                    root = root,
                                    classLoader = classLoader,
                                    enabled = LspConfig.isSettingsC15AboutLayoutEnabledXposed(),
                                )
                            }.onFailure { HookLog.w("C15AboutOta", "OTA card bind failed", it) }
                        }
                    },
                )
                if (bindHooks.isEmpty()) {
                    HookLog.w("C15AboutOta", "OTA onBindViewHolder method unavailable")
                    return
                }
                listOf("isSupportTopVideo", "isSupportEasterEggVideo").forEach { methodName ->
                    runCatching {
                        val mediaHooks = XposedBridge.hookAllMethods(
                            preferenceClass,
                            methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (LspConfig.isSettingsC15AboutLayoutEnabledXposed()) {
                                        param.result = false
                                    }
                                }
                            },
                        )
                        if (mediaHooks.isEmpty()) {
                            HookLog.w("C15AboutOta", "$methodName unavailable; static overlay remains active")
                        }
                    }.onFailure { HookLog.w("C15AboutOta", "$methodName hook failed", it) }
                }
                runCatching {
                    val updateHooks = XposedBridge.hookAllMethods(
                        preferenceClass,
                        "updateFindNewer",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (param.throwable != null ||
                                    !LspConfig.isSettingsC15AboutLayoutEnabledXposed()
                                ) return
                                runCatching {
                                    refreshOtaIndicator(param.thisObject, classLoader)
                                }.onFailure {
                                    HookLog.w("C15AboutOta", "OTA indicator refresh failed", it)
                                }
                            }
                        },
                    )
                    if (updateHooks.isEmpty()) {
                        HookLog.w("C15AboutOta", "updateFindNewer unavailable; bind state remains active")
                    }
                }.onFailure { HookLog.w("C15AboutOta", "updateFindNewer hook failed", it) }
                hookedLoaders.add(loaderKey)
                HookLog.i("C15AboutOta", "OTA card layout hook installed")
            }.onFailure { HookLog.w("C15AboutOta", "OTA card hook installation failed", it) }
        }
    }

    private data class CardState(
        val originalHeight: Int,
        val originalMargins: IntArray?,
        val rootVisibility: IntArray,
        val cardVisibility: IntArray,
        val originalClipToOutline: Boolean,
    )

    private fun rootFromHolder(holder: Any?): ViewGroup? = holder?.let {
        runCatching { XposedHelpers.getObjectField(it, "itemView") as? ViewGroup }.getOrNull()
    }

    private fun findCard(root: ViewGroup): ViewGroup? = (0 until root.childCount)
        .map(root::getChildAt)
        .filterIsInstance<ViewGroup>()
        .firstOrNull { it.javaClass.name.contains("COUICardView") }

    private fun clearOverlay(root: ViewGroup) {
        val card = findCard(root) ?: return
        val overlay = findOverlay(card) ?: return
        val state = overlay.tag as CardState
        card.removeView(overlay)
        restoreNativeViews(root, card, state)
    }

    private fun findOverlay(card: ViewGroup): FrameLayout? = (0 until card.childCount)
        .map(card::getChildAt)
        .firstOrNull { it.tag is CardState } as? FrameLayout

    private fun refreshOtaIndicator(preference: Any?, classLoader: ClassLoader) {
        val target = preference ?: return
        val card = runCatching {
            XposedHelpers.getObjectField(target, "mTopVideoCard") as? ViewGroup
        }.getOrNull() ?: return
        val overlay = findOverlay(card) ?: return
        val root = card.parent as? ViewGroup ?: return
        val updateAvailable = findView(root, "update_find")?.visibility == View.VISIBLE
        val version = (findView(root, "model_build_number") as? TextView)
            ?.text?.toString()?.trim()?.takeIf(String::isNotEmpty)
            ?: readStaticString(classLoader, "getOplusOSVersion")
            ?: ""
        val (modelName, description) = readModelName(classLoader)
        val replacement = FrameLayout(root.context).apply {
            tag = overlay.tag
            isClickable = false
            isFocusable = false
        }
        renderOverlay(replacement, modelName, description, version, updateAvailable)
        card.addView(replacement, ViewGroup.LayoutParams(-1, -1))
        card.removeView(overlay)
        findView(root, "update_find")?.visibility = View.GONE
        findView(root, "update_text")?.visibility = View.GONE
    }

    private fun bindCard(root: ViewGroup, classLoader: ClassLoader, enabled: Boolean) {
        if (!enabled) return
        val card = findCard(root) ?: return
        clearOverlay(root)

        val context = root.context
        val updateAvailable = findView(root, "update_find")?.visibility == View.VISIBLE
        val version = (findView(root, "model_build_number") as? TextView)
            ?.text?.toString()?.trim()?.takeIf(String::isNotEmpty)
            ?: readStaticString(classLoader, "getOplusOSVersion")
            ?: ""
        val (modelName, description) = readModelName(classLoader)

        val state = CardState(
            originalHeight = root.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            originalMargins = (root.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                intArrayOf(it.marginStart, it.topMargin, it.marginEnd, it.bottomMargin)
            },
            rootVisibility = IntArray(root.childCount) { root.getChildAt(it).visibility },
            cardVisibility = IntArray(card.childCount) { card.getChildAt(it).visibility },
            originalClipToOutline = card.clipToOutline,
        )
        val overlay = FrameLayout(context).also {
            it.tag = state
            it.isClickable = false
            it.isFocusable = false
        }
        // Build offscreen first. A missing resource or malformed OEM view must leave native UI intact.
        renderOverlay(overlay, modelName, description, version, updateAvailable)
        try {
            card.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            root.layoutParams?.let { params ->
                params.height = context.dp(234)
                if (params is ViewGroup.MarginLayoutParams) {
                    params.marginStart = context.dp(16)
                    params.marginEnd = context.dp(16)
                    params.topMargin = context.dp(23)
                    params.bottomMargin = context.dp(16)
                }
                root.layoutParams = params
            }
            for (index in 0 until root.childCount) {
                root.getChildAt(index).takeIf { it !== card }?.visibility = View.GONE
            }
            for (index in 0 until card.childCount) {
                card.getChildAt(index).takeIf { it !== overlay }?.visibility = View.GONE
            }
            // COUICardView supplies the existing ColorOS 17 outline and corner radius.
            card.clipToOutline = true
        } catch (error: Throwable) {
            card.removeView(overlay)
            restoreNativeViews(root, card, state)
            throw error
        }
    }

    private fun restoreNativeViews(root: ViewGroup, card: ViewGroup, state: CardState) {
        root.layoutParams?.let { params ->
            params.height = state.originalHeight
            if (params is ViewGroup.MarginLayoutParams) {
                state.originalMargins?.let { margins ->
                    params.marginStart = margins[0]
                    params.topMargin = margins[1]
                    params.marginEnd = margins[2]
                    params.bottomMargin = margins[3]
                }
            }
            root.layoutParams = params
        }
        for (index in 0 until minOf(root.childCount, state.rootVisibility.size)) {
            root.getChildAt(index).visibility = state.rootVisibility[index]
        }
        for (index in 0 until minOf(card.childCount, state.cardVisibility.size)) {
            card.getChildAt(index).visibility = state.cardVisibility[index]
        }
        card.clipToOutline = state.originalClipToOutline
    }

    private fun renderOverlay(
        overlay: FrameLayout,
        modelName: String,
        description: String?,
        version: String,
        updateAvailable: Boolean,
    ) {
        val context = overlay.context
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val textColor = if (night) Color.rgb(240, 242, 244) else Color.rgb(46, 50, 53)
        overlay.removeAllViews()

        val background = loadBackground(context, night)
        if (background != null) {
            overlay.addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(background)
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            overlay.setBackground(GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (night) intArrayOf(0xff4a3427.toInt(), 0xff252b2e.toInt())
                else intArrayOf(0xffefbe9e.toInt(), 0xffc3d4dc.toInt()),
            ))
        }

        val topLogo = logoImage(context, "logo_view", 62, 14, textColor)
        if (topLogo != null) {
            overlay.addView(topLogo, FrameLayout.LayoutParams(
                context.dp(62), context.dp(14), Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = context.dp(35) })
        } else {
            overlay.addView(textView(context, "OPPO", 14f, textColor, true),
                FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = context.dp(35)
                })
        }

        val nameColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        nameColumn.addView(textView(context, modelName, 34f, textColor, true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(context.dp(30), 0, context.dp(30), 0)
        }, LinearLayout.LayoutParams(-1, -2))
        if (!description.isNullOrBlank()) {
            nameColumn.addView(textView(context, description, 16f, textColor, false).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(4) })
        }
        overlay.addView(nameColumn, FrameLayout.LayoutParams(
            -1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply { topMargin = context.dp(69) })

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(context.dp(14), 0, context.dp(14), 0)
        }
        val colorOsLogo = logoImage(context, "brand_logo", 54, 15, textColor)
        if (colorOsLogo != null) {
            footer.addView(colorOsLogo, LinearLayout.LayoutParams(context.dp(54), context.dp(15)))
        } else {
            footer.addView(textView(context, "ColorOS", 14f, textColor, true))
        }
        footer.addView(textView(context, version, 14f, textColor, true).apply {
            // Match the C15 model_number TextView's font metrics and optical centering.
            includeFontPadding = true
            setPadding(0, 0, 0, context.dp(1.5f))
        }, LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = context.dp(3)
            marginEnd = context.dp(4)
        })
        if (updateAvailable) {
            footer.addView(textView(context, newVersionText(context), 8f, textColor, true),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
            footer.addView(View(context).apply {
                setBackground(GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xffff5148.toInt())
                })
            }, LinearLayout.LayoutParams(context.dp(6), context.dp(6)).apply {
                marginStart = context.dp(3)
            })
        } else {
            val arrow = logoImage(context, "device_intent_ota", 15, 15, textColor)
            if (arrow != null) {
                footer.addView(arrow, LinearLayout.LayoutParams(context.dp(15), context.dp(15)))
            } else {
                footer.addView(textView(context, "›", 18f, textColor, false),
                    LinearLayout.LayoutParams(-2, -2))
            }
        }
        overlay.addView(footer, FrameLayout.LayoutParams(
            -2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = context.dp(30) })
    }

    private fun textView(
        context: Context,
        text: String,
        size: Float,
        color: Int,
        medium: Boolean,
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        isClickable = false
        isFocusable = false
    }

    private fun logoImage(
        context: Context,
        drawableName: String,
        widthDp: Int,
        heightDp: Int,
        color: Int,
    ): ImageView? {
        val id = context.resources.getIdentifier(drawableName, "drawable", SETTINGS_PACKAGE)
        if (id == 0) return null
        return ImageView(context).apply {
            setImageResource(id)
            imageTintList = ColorStateList.valueOf(color)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = ViewGroup.LayoutParams(context.dp(widthDp), context.dp(heightDp))
            isClickable = false
            isFocusable = false
        }
    }

    private fun loadBackground(context: Context, night: Boolean): Bitmap? {
        val path = if (night) DARK_BACKGROUND else LIGHT_BACKGROUND
        backgroundBitmaps[path]?.let { return it }
        val bitmap = runCatching {
            context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
                .assets.open(path).use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return null
        return backgroundBitmaps.putIfAbsent(path, bitmap) ?: bitmap
    }

    private fun readModelName(classLoader: ClassLoader): Pair<String, String?> {
        val marketName = readStaticString(classLoader, "getOplusMarketName")
            ?: readStaticString(classLoader, "getDeviceModel")
            ?: Build.MODEL
        val unbranded = marketName
            .replace(Regex("^(OPPO|OnePlus|realme|一加)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { marketName }
        val chineseSuffix = runCatching {
            val cls = XposedHelpers.findClassIfExists(DEVICE_INFO_UTILS, classLoader)
                ?: return@runCatching null
            XposedHelpers.callStaticMethod(cls, "extractLastChineseSubstring", unbranded) as? String
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != unbranded }
        val model = chineseSuffix?.let { unbranded.removeSuffix(it).trim() }
            ?.takeIf(String::isNotEmpty) ?: unbranded
        return model to chineseSuffix
    }

    private fun readStaticString(classLoader: ClassLoader, methodName: String): String? =
        runCatching {
            val cls = XposedHelpers.findClassIfExists(DEVICE_INFO_UTILS, classLoader)
                ?: return@runCatching null
            (XposedHelpers.callStaticMethod(cls, methodName) as? String)
                ?.trim()?.takeIf(String::isNotEmpty)
        }.getOrNull()

    private fun findView(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", SETTINGS_PACKAGE)
        return if (id != 0) root.findViewById(id) else null
    }

    private fun newVersionText(context: Context): String {
        val id = context.resources.getIdentifier("new_version_text", "string", SETTINGS_PACKAGE)
        return if (id != 0) context.getString(id) else "新版本"
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun Context.dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}

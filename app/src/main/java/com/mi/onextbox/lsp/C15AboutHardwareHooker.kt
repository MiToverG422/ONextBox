package com.mi.onextbox.lsp

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Presents the C17 About device hardware data in C15's compact list arrangement. */
internal object C15AboutHardwareHooker {
    private const val FRAGMENT_CLASS =
        "com.oplus.settings.feature.deviceinfo.aboutphone.DeviceInfoFragment"
    private const val SCREEN_KEY = "my_device_info_pref_screen"
    private const val CARD_CLASS = "com.coui.appcompat.cardlist.COUICardListSelectedItemLayout"
    private const val CARD_HELPER_CLASS = "com.coui.appcompat.cardlist.COUICardListHelper"
    private const val NATIVE_ROW_LAYOUT = "oplus_device_info_item_preference"
    private const val ROW_TAG = "onextbox_c15_about_hardware_row"
    private const val DIVIDER_TAG = "onextbox_c15_about_hardware_divider"

    private val hardwareKeys = setOf(
        "cpu_info", "battery_and_charge_info", "ram_info", "camera_info",
        "processor_detail", "tidal_architecture", "game_architecture",
        "security_chip_info", "charge_info", "screen_physics_size",
        "device_market_name_header",
    )
    private val hookedLoaders = ConcurrentHashMap.newKeySet<ClassLoader>()
    private val hookedAdapterClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val originalCardStates = Collections.synchronizedMap(WeakHashMap<LinearLayout, CardState>())

    private data class CardState(
        val childVisibilities: IntArray,
        val minHeight: Int,
        val padding: IntArray,
        val orientation: Int,
        val gravity: Int,
        val topMargin: Int,
        val bottomMargin: Int,
    )

    fun hook(classLoader: ClassLoader) {
        val fragmentClass = XposedHelpers.findClassIfExists(FRAGMENT_CLASS, classLoader) ?: return
        if (!hookedLoaders.add(classLoader)) return
        runCatching {
            val handles = XposedBridge.hookAllMethods(
                fragmentClass,
                "onCreateAdapter",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val adapter = param.result ?: return
                        runCatching { hookConcreteAdapter(adapter.javaClass) }
                            .onFailure { log("hook concrete About device adapter failed", it) }
                    }
                },
            )
            if (handles.isEmpty()) {
                hookedLoaders.remove(classLoader)
                log("DeviceInfoFragment has no onCreateAdapter method")
            } else {
                log("DeviceInfoFragment adapter factory hook installed")
            }
        }.onFailure {
            hookedLoaders.remove(classLoader)
            log("hook DeviceInfoFragment.onCreateAdapter failed", it)
        }
    }

    private fun hookConcreteAdapter(adapterClass: Class<*>) {
        var declaredBy: Class<*>? = adapterClass
        while (declaredBy != null) {
            val methods = declaredBy.declaredMethods.filter {
                it.name == "onBindViewHolder" && it.parameterCount == 2 && !it.isBridge
            }
            if (methods.isNotEmpty()) {
                if (!hookedAdapterClasses.add(declaredBy)) return
                methods.forEach { method -> XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            restoreCardFromHolder(param.args.firstOrNull())
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isSettingsC15AboutLayoutEnabledXposed()) return
                            val adapter = param.thisObject ?: return
                            val position = param.args.getOrNull(1) as? Int ?: return
                            val holder = param.args.firstOrNull() ?: return
                            runCatching { adaptBoundHardwareRow(adapter, holder, position) }
                                .onFailure { log("adapt About device hardware row failed", it) }
                        }
                    },
                ) }
                log("concrete adapter hook installed: ${declaredBy.name}")
                return
            }
            declaredBy = declaredBy.superclass
        }
        log("About device adapter has no onBindViewHolder: ${adapterClass.name}")
    }

    private fun adaptBoundHardwareRow(adapter: Any, holder: Any, position: Int) {
        val screen = XposedHelpers.getObjectField(adapter, "mPreferenceGroup") ?: return
        if (XposedHelpers.callMethod(screen, "getKey") != SCREEN_KEY) return
        val preference = XposedHelpers.callMethod(adapter, "getItem", position) ?: return
        val key = XposedHelpers.callMethod(preference, "getKey") as? String ?: return
        if (key !in hardwareKeys) return
        val itemView = XposedHelpers.getObjectField(holder, "itemView") as? View ?: return
        val card = itemView as? LinearLayout ?: return
        if (!inheritsCardLayout(card.javaClass)) return
        val margins = card.layoutParams as? ViewGroup.MarginLayoutParams
        originalCardStates[card] = CardState(
            childVisibilities = IntArray(card.childCount) { index ->
                card.getChildAt(index).visibility
            },
            minHeight = card.minimumHeight,
            padding = intArrayOf(card.paddingStart, card.paddingTop,
                card.paddingEnd, card.paddingBottom),
            orientation = card.orientation,
            gravity = card.gravity,
            topMargin = margins?.topMargin ?: 0,
            bottomMargin = margins?.bottomMargin ?: 0,
        )

        // C17's spec cards all carry an 8dp (model: 16dp) independent-card gap.
        // Keep one section gap above the first row, then join adjacent rows.
        val positionInGroup = nativeGroupPosition(preference, card)
        XposedHelpers.callMethod(card, "setPositionInGroup", positionInGroup)
        (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            val top = if (positionInGroup == 1 || positionInGroup == 4) dp(card, 16) else 0
            if (it.topMargin != top || it.bottomMargin != 0) {
                it.topMargin = top
                it.bottomMargin = 0
                card.layoutParams = it
            }
        }
        runCatching {
            XposedHelpers.callMethod(holder, "setDividerAllowedAbove",
                positionInGroup != 1 && positionInGroup != 4)
            XposedHelpers.callMethod(holder, "setDividerAllowedBelow",
                positionInGroup != 3 && positionInGroup != 4)
        }

        // The C17 charge preference still uses the original compact two-value
        // layout. It only needs the native group background above.
        if (key == "charge_info") return

        val title = displayTitle(preference, key, card) ?: run {
            restoreCard(card)
            return
        }
        val values = boundValues(key, card, title)
        if (values.isEmpty() && key != "tidal_architecture") {
            restoreCard(card)
            return
        }
        val showArrow = hasVisibleOriginalArrow(card)
        val row = findOrCreateNativeRow(card) ?: run {
            restoreCard(card)
            return
        }
        row.title.text = title
        row.value.text = values.joinToString("\n")
        row.value.maxLines = if (key == "camera_info") Int.MAX_VALUE
            else if (values.size > 1) values.size.coerceAtMost(3) else 2
        row.value.ellipsize = if (key == "camera_info") null else TextUtils.TruncateAt.END
        row.value.setLineSpacing(if (key == "camera_info") dp(card, 4).toFloat() else 0f, 1f)
        row.arrow.visibility = if (showArrow) View.VISIBLE else View.GONE
        if (key == "camera_info") {
            (row.title.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.gravity = Gravity.TOP or Gravity.START
                row.title.layoutParams = it
            }
        }
        for (index in 0 until card.childCount) {
            val child = card.getChildAt(index)
            if (child !== row.container) child.visibility = View.GONE
        }
        row.container.visibility = View.VISIBLE
        val customRowPadding = if (key == "camera_info" || key == "ram_info" ||
            key == "screen_physics_size" || key == "security_chip_info"
        ) dp(card, 12) else 0
        row.container.setPaddingRelative(0, customRowPadding, 0, customRowPadding)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_VERTICAL
        // AboutPhoneSpecCardListSelectedItemLayout resets these in
        // setPositionInGroup, so the compact dimensions are applied last.
        card.minimumHeight = dp(card, 48)
        card.setPaddingRelative(dp(card, 32), 0, dp(card, 32), 0)
        // These C17 spec preferences suppress most item decorations even after
        // being moved into one category. Draw the missing C15 inset dividers in
        // the joined card; camera and charging already receive the native line.
        if (key != "camera_info" && positionInGroup != 3 && positionInGroup != 4) {
            addInsetDivider(card)
        }
    }

    private fun restoreCardFromHolder(holder: Any?) {
        val card = holder?.let {
            runCatching { XposedHelpers.getObjectField(it, "itemView") as? LinearLayout }
                .getOrNull()
        } ?: return
        restoreCard(card)
    }

    private fun restoreCard(card: LinearLayout) {
        val state = originalCardStates.remove(card) ?: return
        for (index in card.childCount - 1 downTo 0) {
            if (card.getChildAt(index).tag == ROW_TAG ||
                card.getChildAt(index).tag == DIVIDER_TAG
            ) card.removeViewAt(index)
        }
        state.childVisibilities.forEachIndexed { index, visibility ->
            if (index < card.childCount) card.getChildAt(index).visibility = visibility
        }
        card.orientation = state.orientation
        card.gravity = state.gravity
        card.minimumHeight = state.minHeight
        card.setPaddingRelative(state.padding[0], state.padding[1],
            state.padding[2], state.padding[3])
        (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.topMargin = state.topMargin
            it.bottomMargin = state.bottomMargin
            card.layoutParams = it
        }
    }

    private fun inheritsCardLayout(type: Class<*>): Boolean {
        var current: Class<*>? = type
        while (current != null) {
            if (current.name == CARD_CLASS) return true
            current = current.superclass
        }
        return false
    }

    private fun nativeGroupPosition(preference: Any, card: View): Int {
        val helper = XposedHelpers.findClassIfExists(CARD_HELPER_CLASS, card.javaClass.classLoader)
            ?: return 4
        return (runCatching {
            XposedHelpers.callStaticMethod(helper, "getPositionInGroup", preference)
        }.getOrNull() as? Int)?.takeIf { it in 1..4 } ?: 4
    }

    private fun displayTitle(preference: Any, key: String, card: View): CharSequence? {
        val resourceName = when (key) {
            "battery_and_charge_info" -> "battery_capacity_equivalence_new"
            "device_market_name_header" -> "device_info_show_model_num"
            else -> null
        }
        if (resourceName != null) {
            val id = card.resources.getIdentifier(resourceName, "string", "com.android.settings")
            if (id != 0) return card.context.getString(id)
        }
        return XposedHelpers.callMethod(preference, "getTitle") as? CharSequence
    }

    private fun boundValues(key: String, card: View, title: CharSequence): List<String> {
        val values = when (key) {
            "cpu_info", "processor_detail" -> listOfNotNull(text(card, "assignment"))
            "battery_and_charge_info" -> listOfNotNull(
                joinNonBlank(text(card, "battery_capacity_number"),
                    text(card, "battery_capacity_unit"), separator = " "),
            )
            "ram_info" -> listOfNotNull(
                joinRam(text(card, "coui_statusText1"), text(card, "expand_ram")),
            )
            "camera_info" -> listOfNotNull(
                text(card, "device_camera_info_front_content"),
                text(card, "device_camera_info_back_content"),
            )
            "security_chip_info" -> listOfNotNull(
                text(card, "device_independent_chip_info_content"),
                text(card, "device_nfc_chip_info_content"),
            )
            "screen_physics_size" -> {
                val size = listOfNotNull(
                    text(card, "text_screen_size_number_int"),
                    text(card, "text_screen_size_number_dot"),
                    text(card, "text_screen_size_number_decimal"),
                    text(card, "text_screen_size_unit"),
                ).joinToString("").trim().ifEmpty { null }
                val details = listOfNotNull(
                    text(card, "text_screen_description_part_1"),
                    text(card, "text_screen_description_part_2"),
                    text(card, "text_screen_description_part_3"),
                ).joinToString(" ").trim().ifEmpty {
                    text(card, "text_screen_description")
                }
                listOfNotNull(size, details)
            }
            "tidal_architecture" -> listOfNotNull(text(card, "tidal_architecture_headline"))
                .filterNot { it == title.toString() }
            "game_architecture" -> listOfNotNull(
                text(card, "assignment_fengchi"),
                text(card, "assignment_gameing_three_cores"),
                text(card, "assignment_high_brush"),
            )
            "device_market_name_header" -> listOfNotNull(text(card, "device_model_badge"))
            else -> emptyList()
        }
        return values.filter { it.isNotBlank() }
    }

    private fun text(card: View, resourceName: String): String? {
        val id = card.resources.getIdentifier(resourceName, "id", "com.android.settings")
        if (id == 0) return null
        val view = card.findViewById<TextView>(id) ?: return null
        if (view.visibility != View.VISIBLE) return null
        return view.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun joinNonBlank(first: String?, second: String?, separator: String): String? =
        listOfNotNull(first, second).joinToString(separator).takeIf { it.isNotBlank() }

    private fun joinRam(base: String?, expansion: String?): String? = when {
        base == null -> expansion
        expansion == null -> base
        base.trimEnd().endsWith("+") -> "$base $expansion"
        else -> "$base + $expansion"
    }

    private fun hasVisibleOriginalArrow(card: View): Boolean =
        listOf("img_jump", "jump_mark").any { name ->
            val id = card.resources.getIdentifier(name, "id", "com.android.settings")
            id != 0 && card.findViewById<View>(id)?.visibility == View.VISIBLE
        }

    private data class NativeRow(
        val container: LinearLayout,
        val title: TextView,
        val value: TextView,
        val arrow: ImageView,
    )

    private fun findOrCreateNativeRow(card: LinearLayout): NativeRow? {
        for (index in 0 until card.childCount) {
            val child = card.getChildAt(index)
            if (child.tag == ROW_TAG && child is LinearLayout && child.childCount >= 3) {
                return NativeRow(
                    child,
                    child.getChildAt(0) as? TextView ?: return null,
                    child.getChildAt(1) as? TextView ?: return null,
                    child.getChildAt(2) as? ImageView ?: return null,
                )
            }
        }
        val layoutId = card.resources.getIdentifier(
            NATIVE_ROW_LAYOUT, "layout", "com.android.settings",
        )
        if (layoutId == 0) return null
        val template = LayoutInflater.from(card.context).inflate(layoutId, null, false)
            as? ViewGroup ?: return null
        val row = template.getChildAt(0) as? LinearLayout ?: return null
        val title = row.findViewById<TextView>(android.R.id.title) ?: return null
        val assignmentId = card.resources.getIdentifier("assignment", "id", "com.android.settings")
        val arrowId = card.resources.getIdentifier("img_jump", "id", "com.android.settings")
        if (assignmentId == 0 || arrowId == 0) return null
        val value = row.findViewById<TextView>(assignmentId) ?: return null
        val arrow = row.findViewById<ImageView>(arrowId) ?: return null
        template.removeView(row)
        row.tag = ROW_TAG
        row.id = View.NO_ID
        title.id = View.NO_ID
        value.id = View.NO_ID
        arrow.id = View.NO_ID
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        row.minimumHeight = dp(card, 48)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPaddingRelative(0, 0, 0, 0)
        card.addView(row)
        return NativeRow(row, title, value, arrow)
    }

    private fun addInsetDivider(card: LinearLayout) {
        val colorId = card.resources.getIdentifier(
            "coui_color_divider", "color", "com.android.settings",
        )
        val heightId = card.resources.getIdentifier(
            "coui_list_divider_height", "dimen", "com.android.settings",
        )
        if (colorId == 0 || heightId == 0) return
        val divider = View(card.context).apply {
            tag = DIVIDER_TAG
            setBackgroundColor(card.context.getColor(colorId))
        }
        card.addView(divider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            card.resources.getDimensionPixelSize(heightId).coerceAtLeast(1),
        ))
    }

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density + 0.5f).toInt()

    private fun log(message: String, throwable: Throwable? = null) {
        HookLog.i("C15AboutHardwareHooker", message, throwable)
    }
}

package com.mi.onextbox.lsp

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Restores the ColorOS 15 About device arrangement using ColorOS 17's own preferences. */
internal object C15AboutGridHooker {
    private const val FRAGMENT_CLASS =
        "com.oplus.settings.feature.deviceinfo.aboutphone.DeviceInfoFragment"
    private const val NAME_STORAGE_CATEGORY = "about_device_name_storage_category"
    private const val DEVICE_NAME = "device_name"
    private const val DEVICE_STORAGE = "device_storage"
    private const val CAMERA_INFO = "camera_info"
    private const val CHARGE_INFO = "charge_info"
    private const val CHARGE_PREFERENCE =
        "com.oplus.settings.feature.deviceinfo.DeviceChargeInfoItemPreference"
    private const val DEVICE_NAME_PREFERENCE =
        "com.oplus.settings.widget.preference.DeviceNameSquarePreference"
    private const val DEVICE_STORAGE_PREFERENCE =
        "com.oplus.settings.widget.preference.DeviceInfoSquarePreference"
    private const val COUI_CARD_LAYOUT =
        "com.coui.appcompat.cardlist.COUICardListSelectedItemLayout"

    private val halfWidthKeys = setOf(DEVICE_NAME, DEVICE_STORAGE)
    private val hardwareItemsInC15Order = linkedMapOf(
        "cpu_info" to 350,
        // ColorOS 17 combines the ColorOS 15 battery and charging rows.
        "battery_and_charge_info" to 360,
        "ram_info" to 370,
        CAMERA_INFO to 380,
        "processor_detail" to 390,
        "tidal_architecture" to 400,
        "game_architecture" to 401,
        "security_chip_info" to 410,
        "screen_physics_size" to 430,
        // The ColorOS 17 model card fills the ColorOS 15 device_model position.
        "device_market_name_header" to 440,
    )

    private val hookedLoaders = ConcurrentHashMap.newKeySet<ClassLoader>()
    private val hookedSpanClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val fragmentBySpanLookup =
        Collections.synchronizedMap(WeakHashMap<Any, WeakReference<Any>>())

    fun hook(classLoader: ClassLoader) {
        val fragmentClass = XposedHelpers.findClassIfExists(FRAGMENT_CLASS, classLoader)
            ?: return
        if (!hookedLoaders.add(classLoader)) return

        runCatching {
            val handles = XposedBridge.hookAllMethods(
                fragmentClass,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsC15AboutLayoutEnabledXposed()) return
                        val fragment = param.thisObject ?: return
                        runCatching { arrangePreferences(fragment) }
                            .onFailure { log("arrange About device preferences failed", it) }
                    }
                },
            )
            if (handles.isEmpty()) log("DeviceInfoFragment has no onCreate method")
        }.onFailure { log("hook DeviceInfoFragment.onCreate failed", it) }

        runCatching {
            val handles = XposedBridge.hookAllMethods(
                fragmentClass,
                "onCreateLayoutManager",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsC15AboutLayoutEnabledXposed()) return
                        val fragment = param.thisObject ?: return
                        val layoutManager = param.result ?: return
                        runCatching {
                            val lookup = XposedHelpers.callMethod(
                                layoutManager,
                                "getSpanSizeLookup",
                            ) ?: return@runCatching
                            fragmentBySpanLookup[lookup] = WeakReference(fragment)
                            hookSpanLookup(lookup.javaClass)
                        }.onFailure { log("hook About device grid spans failed", it) }
                    }
                },
            )
            if (handles.isEmpty()) log("DeviceInfoFragment has no onCreateLayoutManager method")
        }.onFailure { log("hook DeviceInfoFragment.onCreateLayoutManager failed", it) }

        hookSquareCard(classLoader, DEVICE_NAME_PREFERENCE, DEVICE_NAME)
        hookSquareCard(classLoader, DEVICE_STORAGE_PREFERENCE, DEVICE_STORAGE)
    }

    private fun hookSquareCard(classLoader: ClassLoader, className: String, key: String) {
        val preferenceClass = XposedHelpers.findClassIfExists(className, classLoader) ?: return
        runCatching {
            val handles = XposedBridge.hookAllMethods(
                preferenceClass,
                "onBindViewHolder",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsC15AboutLayoutEnabledXposed()) return
                        val holder = param.args.firstOrNull() ?: return
                        val itemView = runCatching {
                            XposedHelpers.getObjectField(holder, "itemView") as? View
                        }.getOrNull() ?: return
                        runCatching {
                            if (key == DEVICE_NAME) adaptNameCard(itemView)
                            else adaptStorageCard(itemView)
                        }.onFailure { log("adapt About device $key card failed", it) }
                    }
                },
            )
            if (handles.isEmpty()) log("$className has no onBindViewHolder method")
        }.onFailure { log("hook $className.onBindViewHolder failed", it) }
    }

    private fun adaptNameCard(itemView: View) {
        val card = itemView as? LinearLayout ?: return
        if (card.layoutParams !is ViewGroup.MarginLayoutParams) return
        val title = card.findViewById<TextView>(android.R.id.title) ?: return
        val summary = card.findViewById<TextView>(android.R.id.summary) ?: return
        val content = title.parent as? LinearLayout ?: return
        if (summary.parent !== content || content.parent !== card) return
        val editIcon = card.findViewById<ImageView>(android.R.id.icon)
            ?.takeIf { it.parent === content }

        if (!applyHalfCardSpacing(card, isLeftCard = true)) return
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.START
        title.layoutParams = verticalTextParams()
        summary.layoutParams = verticalTextParams(topMargin = dp(card, 4))
        summary.gravity = Gravity.START
        summary.textAlignment = View.TEXT_ALIGNMENT_VIEW_START

        if (editIcon != null) {
            // Reuse ColorOS 17's own phone outline from the original model card.
            val phoneIcon = card.resources.getIdentifier(
                "device_market_name_phone_icon", "drawable", "com.android.settings",
            )
            if (phoneIcon != 0) editIcon.setImageResource(phoneIcon)
            if (content.indexOfChild(editIcon) != 0) {
                content.removeView(editIcon)
                content.addView(editIcon, 0)
            }
            editIcon.layoutParams = LinearLayout.LayoutParams(dp(card, 20), dp(card, 20)).apply {
                topMargin = dp(card, 6)
                bottomMargin = dp(card, 6)
            }
        }
    }

    private fun adaptStorageCard(itemView: View) {
        val card = itemView as? LinearLayout ?: return
        if (card.layoutParams !is ViewGroup.MarginLayoutParams) return
        val title = card.findViewById<TextView>(android.R.id.title) ?: return
        val summary = card.findViewById<TextView>(android.R.id.summary) ?: return
        val content = title.parent as? LinearLayout ?: return
        if (summary.parent !== content || content.parent !== card) return
        val barId = card.resources.getIdentifier("card_view", "id", "com.android.settings")
        if (barId == 0) return
        val capacityBar = card.findViewById<View>(barId) ?: return
        if (capacityBar.parent !== card) return

        if (!applyHalfCardSpacing(card, isLeftCard = false)) return
        if (card.indexOfChild(capacityBar) != 0) {
            card.removeView(capacityBar)
            card.addView(capacityBar, 0)
        }
        capacityBar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(card, 10),
        ).apply {
            topMargin = dp(card, 10)
            bottomMargin = dp(card, 12)
        }
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.START
        content.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        title.layoutParams = verticalTextParams()
        summary.layoutParams = verticalTextParams(topMargin = dp(card, 4))
        summary.gravity = Gravity.START
        summary.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        summary.maxLines = 2
    }

    private fun applyHalfCardSpacing(card: LinearLayout, isLeftCard: Boolean): Boolean {
        if (card.javaClass.name != COUI_CARD_LAYOUT) return false
        val params = card.layoutParams as? ViewGroup.MarginLayoutParams ?: return false
        // The C17 card draws its background 16dp inside its View by default.
        // Our grid cell margins already provide the outer 16dp and inner 5dp.
        XposedHelpers.callMethod(card, "setMarginHorizontal", 0)
        // These were vertically grouped as HEAD and TAIL in C17. Each is now
        // an independent half-width card and needs all four native C17 corners.
        XposedHelpers.callMethod(card, "setPositionInGroup", 4)
        params.marginStart = dp(card, if (isLeftCard) 16 else 5)
        params.marginEnd = dp(card, if (isLeftCard) 5 else 16)
        card.layoutParams = params
        card.minimumHeight = dp(card, 112)
        // The name layout was vertically centered while storage started at the
        // top. Anchor both to the same row so their icons, titles and values align.
        card.gravity = Gravity.TOP or Gravity.START
        card.setPaddingRelative(dp(card, 16), dp(card, 12), dp(card, 16), dp(card, 16))
        return true
    }

    private fun verticalTextParams(topMargin: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { this.topMargin = topMargin }

    private fun dp(view: View, amount: Int): Int =
        (amount * view.resources.displayMetrics.density + 0.5f).toInt()

    private fun arrangePreferences(fragment: Any) {
        val screen = XposedHelpers.callMethod(fragment, "getPreferenceScreen") ?: return
        val nameStorageCategory = findPreference(fragment, NAME_STORAGE_CATEGORY)
        val deviceName = findPreference(fragment, DEVICE_NAME)
        val deviceStorage = findPreference(fragment, DEVICE_STORAGE)

        // C15 has two sibling half-width cards immediately below the OTA preference.
        if (nameStorageCategory != null && deviceName != null && deviceStorage != null) {
            val nameOriginalParent = XposedHelpers.callMethod(deviceName, "getParent")
            val storageOriginalParent = XposedHelpers.callMethod(deviceStorage, "getParent")
            val nameMoved = movePreference(deviceName, screen)
            val storageMoved = movePreference(deviceStorage, screen)
            if (nameMoved && storageMoved &&
                XposedHelpers.callMethod(deviceName, "getParent") === screen &&
                XposedHelpers.callMethod(deviceStorage, "getParent") === screen
            ) {
                XposedHelpers.callMethod(deviceName, "setOrder", 200)
                XposedHelpers.callMethod(deviceStorage, "setOrder", 300)
                XposedHelpers.callMethod(nameStorageCategory, "setVisible", false)
            } else {
                if (nameOriginalParent != null) movePreference(deviceName, nameOriginalParent)
                if (storageOriginalParent != null) {
                    movePreference(deviceStorage, storageOriginalParent)
                }
                log("keeping original About device name and storage category")
            }
        }

        // C15 puts hardware details in one section. Keep C17's current preference
        // implementations and new information while restoring that section's order.
        val camera = findPreference(fragment, CAMERA_INFO) ?: return
        val hardwareCategory = XposedHelpers.callMethod(camera, "getParent") ?: return
        if (hardwareCategory === screen) return
        for ((key, order) in hardwareItemsInC15Order) {
            val preference = findPreference(fragment, key) ?: continue
            runCatching {
                val previousParent = XposedHelpers.callMethod(preference, "getParent")
                if (movePreference(preference, hardwareCategory)) {
                    XposedHelpers.callMethod(preference, "setOrder", order)
                    if (previousParent != null && previousParent !== hardwareCategory) {
                        hideIfEmptyGroup(previousParent, screen)
                    }
                }
            }.onFailure { log("arrange About device item $key failed", it) }
        }
        addChargeInfoPreference(fragment, hardwareCategory)
        XposedHelpers.callMethod(hardwareCategory, "setOrder", 400)
    }

    private fun addChargeInfoPreference(fragment: Any, hardwareCategory: Any) {
        if (findPreference(fragment, CHARGE_INFO) != null) return
        runCatching {
            val context = XposedHelpers.callMethod(fragment, "getContext") as? Context
                ?: return@runCatching
            val preferenceClass = XposedHelpers.findClassIfExists(
                CHARGE_PREFERENCE,
                fragment.javaClass.classLoader,
            ) ?: return@runCatching
            val titleId = context.resources.getIdentifier(
                "device_charging",
                "string",
                context.packageName,
            )
            if (titleId == 0) return@runCatching
            val chargePreference = XposedHelpers.newInstance(preferenceClass, context)
            XposedHelpers.callMethod(chargePreference, "setKey", CHARGE_INFO)
            XposedHelpers.callMethod(chargePreference, "setTitle", context.getString(titleId))
            XposedHelpers.callMethod(chargePreference, "setSelectable", false)
            XposedHelpers.callMethod(chargePreference, "setOrder", 420)
            val added = XposedHelpers.callMethod(
                hardwareCategory,
                "addPreference",
                chargePreference,
            ) as? Boolean
            if (added != true) log("could not add ColorOS 15 charging row")
        }.onFailure { log("add About device charging row failed", it) }
    }

    private fun hideIfEmptyGroup(group: Any, screen: Any) {
        if (group === screen) return
        val count = XposedHelpers.callMethod(group, "getPreferenceCount") as? Int ?: return
        if (count == 0) XposedHelpers.callMethod(group, "setVisible", false)
    }

    private fun movePreference(preference: Any, destination: Any): Boolean {
        val parent = XposedHelpers.callMethod(preference, "getParent") ?: return false
        if (parent === destination) return true
        val removed = XposedHelpers.callMethod(parent, "removePreference", preference) as? Boolean
        if (removed != true) return false
        val added = runCatching {
            XposedHelpers.callMethod(destination, "addPreference", preference) as? Boolean
        }.getOrNull()
        if (added == true) return true
        runCatching { XposedHelpers.callMethod(parent, "addPreference", preference) }
        log("could not move About device preference ${preferenceKey(preference)}")
        return false
    }

    private fun hookSpanLookup(lookupClass: Class<*>) {
        if (!hookedSpanClasses.add(lookupClass)) return
        runCatching {
            val handles = XposedBridge.hookAllMethods(
                lookupClass,
                "getSpanSize",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsC15AboutLayoutEnabledXposed()) return
                        val lookup = param.thisObject ?: return
                        val fragment = fragmentBySpanLookup[lookup]?.get() ?: return
                        val position = param.args.firstOrNull() as? Int ?: return
                        if (position < 0) return
                        runCatching {
                            val list = XposedHelpers.callMethod(fragment, "getListView")
                                ?: return@runCatching
                            val adapter = XposedHelpers.callMethod(list, "getAdapter")
                                ?: return@runCatching
                            val item = XposedHelpers.callMethod(adapter, "getItem", position)
                                ?: return@runCatching
                            val key = preferenceKey(item)
                            if (key in halfWidthKeys) {
                                param.result = 1
                            }
                        }.onFailure { log("resolve About device grid item failed", it) }
                    }
                },
            )
            if (handles.isEmpty()) {
                hookedSpanClasses.remove(lookupClass)
                log("About device span lookup has no getSpanSize method")
            }
        }.onFailure {
            hookedSpanClasses.remove(lookupClass)
            log("hook About device span lookup failed", it)
        }
    }

    private fun findPreference(fragment: Any, key: String): Any? =
        XposedHelpers.callMethod(fragment, "findPreference", key)

    private fun preferenceKey(preference: Any): String? =
        XposedHelpers.callMethod(preference, "getKey") as? String

    private fun log(message: String, throwable: Throwable? = null) {
        HookLog.i("C15AboutGridHooker", message, throwable)
    }
}

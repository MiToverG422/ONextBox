package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import com.mi.onextbox.lsp.compat.ModernReflect
import org.xmlpull.v1.XmlPullParser

internal object SettingsHomeEntryAppearance {
    private const val SETTINGS = "com.android.settings"
    private const val MODULE = "com.mi.onextbox"
    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    @SuppressLint("DiscouragedApi")
    fun createRow(context: Context, rowClass: Class<*>, style: SettingsEntryIconStyle): Any {
        val resources = context.resources
        val xml = resources.getIdentifier("top_level_settings_oplus", "xml", SETTINGS)
        require(xml != 0) { "Native Settings home style unavailable" }
        resources.getXml(xml).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.getAttributeValue(ANDROID, "key") != "wifi_settings") continue
                // Let the host consume its own layout, typography, tint and two-tone attributes.
                val row = ModernReflect.newInstance(rowClass, context, parser)
                val template = notificationIcon(context)
                val icon = when (style) {
                    SettingsEntryIconStyle.System -> template.also { applyBaseTint(context, parser, rowClass.classLoader, it) }
                    SettingsEntryIconStyle.App -> {
                        ModernReflect.callMethod(row, "setShowColorTintIcon", 0)
                        LayerDrawable(arrayOf(context.packageManager.getApplicationIcon(MODULE).mutate())).apply {
                            setLayerSize(0, template.intrinsicWidth, template.intrinsicHeight)
                        }
                    }
                }
                ModernReflect.callMethod(row, "setIcon", icon)
                return row
            }
        }
        error("Native Wi-Fi preference style unavailable")
    }

    @SuppressLint("DiscouragedApi")
    private fun notificationIcon(context: Context): Drawable {
        val module = context.createPackageContext(MODULE, 0)
            .createConfigurationContext(context.resources.configuration)
        val id = module.resources.getIdentifier("ic_update_notification", "drawable", MODULE)
        require(id != 0) { "Module notification icon unavailable" }
        return requireNotNull(module.getDrawable(id)).mutate()
    }

    @SuppressLint("DiscouragedApi")
    private fun applyBaseTint(context: Context, attributes: AttributeSet, loader: ClassLoader?, icon: Drawable) {
        val resources = context.resources
        val tintAttr = resources.getIdentifier("tintType", "attr", SETTINGS)
        val colorAttr = resources.getIdentifier("couiColorContainerTheme", "attr", SETTINGS)
        require(tintAttr != 0 && colorAttr != 0) { "Native icon theme attributes unavailable" }
        val types = context.obtainStyledAttributes(attributes, intArrayOf(tintAttr))
        val tintType = try { types.getInt(0, 0) } finally { types.recycle() }
        val themeUtils = ModernReflect.findClass("com.oplus.settings.utils.ThemeUtils", loader)
        val themed = ModernReflect.callStaticMethod(themeUtils, "getWrapperOrPrimaryContext", context, tintType) as Context
        val colors = themed.obtainStyledAttributes(intArrayOf(colorAttr))
        try {
            colors.getColorStateList(0)?.let(icon::setTintList)
        } finally {
            colors.recycle()
        }
        // The native preference reapplies its regular tint policy whenever the row is bound.
    }
}

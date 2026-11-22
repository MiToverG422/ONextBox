package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.util.WeakHashMap
import java.lang.ref.WeakReference

/** Keep PathCornerDrawable visible to the native launch controller; change only color inputs. */
internal class FluidCloudFlashlightMaterial(private val enabled: () -> Boolean, private val mix: (Context) -> Any?) {
    private val states = WeakHashMap<Drawable, Any>()
    private val masks = WeakHashMap<GradientDrawable, ColorStateList>()
    private val backgrounds = WeakHashMap<Drawable, WeakReference<Context>>()

    init {
        HotReloadLifecycle.register("fluid-material:flashlight-native",
            capture = { backgrounds.mapNotNull { (bg, ref) -> ref.get()?.let { arrayOf(bg, it) } }.toTypedArray() },
            detach = ::releaseAll,
            restore = { saved ->
                if (enabled() && saved is Array<*>) saved.filterIsInstance<Array<*>>().forEach { row ->
                    val bg = row.getOrNull(0) as? Drawable ?: return@forEach
                    val context = row.getOrNull(1) as? Context ?: return@forEach
                    apply(bg, context)
                }
            })
    }

    fun apply(background: Drawable, context: Context): Boolean {
        val wrapper = background as? DrawableWrapper ?: return false
        if (background.javaClass.name != "com.oplus.systemui.qs.base.res.drawable.TileDrawableWrapper") return false
        val layers = wrapper.drawable as? LayerDrawable ?: return false
        if (layers.numberOfLayers != 2) return false
        val tile = layers.getDrawable(0)
        if (tile.javaClass.name != "com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable") return false
        val mask = (layers.getDrawable(1) as? DrawableWrapper)?.drawable as? GradientDrawable ?: return false
        val original = Reflect.getObjectField(tile, "stateListConfig") as? Map<*, *> ?: return false
        val savedMask = mask.color ?: return false
        val replacement = LinkedHashMap<Any, Any>()
        // Allocate a recipe per state; never mutate a resource-pool/shared shader.
        for ((state, value) in original) {
            if (state == null || value == null) return false
            val shader = mix(context) ?: return false
            replacement[state] = Reflect.newInstance(value.javaClass, shader, 0, Reflect.callMethod(value, "getThird"))
        }
        if (replacement.isEmpty()) return false
        states.putIfAbsent(tile, original)
        masks.putIfAbsent(mask, savedMask)
        try {
            Reflect.callMethod(tile, "updateStateList", replacement)
            tile.jumpToCurrentState()
            // Alpha is controlled by native launch animation; a transparent color
            // remains transparent even when the LayerDrawable forwards setAlpha(255).
            mask.setColor(Color.TRANSPARENT)
            backgrounds[background] = WeakReference(context)
        } catch (failure: Throwable) {
            Reflect.callMethod(tile, "updateStateList", original)
            tile.jumpToCurrentState()
            mask.setColor(savedMask)
            throw failure
        }
        return true
    }

    private fun releaseAll() {
        states.entries.toList().forEach { (tile, state) ->
            Reflect.callMethod(tile, "updateStateList", state)
            tile.jumpToCurrentState()
        }
        masks.entries.toList().forEach { (mask, color) -> mask.setColor(color) }
        states.clear()
        masks.clear()
        backgrounds.clear()
    }
}

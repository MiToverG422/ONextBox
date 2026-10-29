package com.mi.onextbox.lsp

internal object NativeInstallerThemeRules {
    data class ColorValue(val type: Int, val data: Int, val cookie: Int, val id: Int, val configurations: Int, val density: Int)
    data class Slots(val stride: Int, val type: Int, val data: Int, val cookie: Int, val id: Int, val configurations: Int, val density: Int) {
        fun valid(): Boolean {
            val fields = listOf(type, data, cookie, id, configurations, density)
            return stride in 6..16 && fields.distinct().size == fields.size && fields.all { it in 0 until stride }
        }
    }
    const val PACKAGE = "com.android.packageinstaller"
    val colors = mapOf(
        "primaryColor" to "primary",
        "onPrimaryColor" to "on_primary",
        "onSurfaceColor" to "on_surface",
        "onSurfaceVariantColor" to "on_surface_variant",
        "outlineVariantColor" to "outline_variant",
        "surfaceContainerHighColor" to "surface_container_high",
    )

    fun replacement(packageName: String, name: String, night: Boolean, enabled: Boolean): String? {
        if (!enabled || !night) return null
        val role = when (packageName) {
            PACKAGE -> colors[name]
            "android" -> colors.values.firstOrNull { name == "system_${it}_light" }
            else -> null
        } ?: return null
        return "system_${role}_dark"
    }

    fun rewriteColors(data: IntArray, length: Int, slots: Slots, resolve: (Int) -> ColorValue?): Int {
        if (!slots.valid() || length < 0 || length > data.size / slots.stride) return 0
        var changed = 0
        repeat(length) { index ->
            val offset = index * slots.stride
            // XML drawables and state lists must retain their own inflation and state handling.
            if (data[offset + slots.type] !in 28..31) return@repeat
            val source = data[offset + slots.id]
            if (source == 0) return@repeat
            val color = resolve(source) ?: return@repeat
            if (color.type !in 28..31 || color.id == 0 || color.id == source) return@repeat
            data[offset + slots.type] = color.type
            data[offset + slots.data] = color.data
            data[offset + slots.cookie] = color.cookie
            data[offset + slots.id] = color.id
            data[offset + slots.configurations] = data[offset + slots.configurations] or color.configurations
            data[offset + slots.density] = color.density
            changed++
        }
        return changed
    }
}

package com.mi.onextbox.lsp

import java.io.File

internal object CameraPaletteSupport {
    const val OPTION = "camera_unlock_color_palette"
    const val GATE = "com.oplus.feature.color.palette.support"
    private const val ENGINE = "65f19d10c926d86a8fdf871ce4bd4bb67125f6086957481684a92788a44fa7dc"
    private val ready by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val directory = File("/odm/etc/camera/basictone/palette")
        runCatching {
            File(directory, "onextbox-ready-v1").readText().trim() == ENGINE &&
                listOf("HDR", "SDR").all { range -> (0..8).all { index ->
                    File(directory, "$range/SC16Lut$index").let { it.canRead() && it.length() == 49152L }
                } } && listOf("libONextBoxToneOriginal.so", "libONextBoxTonePalette.so").all {
                    File("/odm/lib64/hw", it).let { file -> file.canRead() && file.length() > 500000L }
                }
        }.getOrDefault(false)
    }
    val options = setOf(OPTION, "gallery_unlock_palette", "gallery_unlock_palette_ipu")
    fun available() = ready
}

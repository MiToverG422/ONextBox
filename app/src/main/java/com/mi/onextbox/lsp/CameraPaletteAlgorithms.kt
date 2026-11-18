package com.mi.onextbox.lsp

internal object CameraPaletteAlgorithms {
    const val ID = "aps_algo_color_palette"
    fun include(original: Array<String>): Array<String> {
        if (ID in original) return original
        val position = original.indexOfFirst { it == "aps_algo_face_info" || it == "aps_algo_post_proc" }
            .let { if (it < 0) original.size else it }
        return (original.take(position) + ID + original.drop(position)).toTypedArray()
    }
}

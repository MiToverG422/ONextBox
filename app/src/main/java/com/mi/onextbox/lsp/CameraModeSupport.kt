package com.mi.onextbox.lsp

internal class CameraModeSupport(loader: ClassLoader) {
    private val modesField = runCatching {
        Class.forName("com.oplus.ocs.camera.producer.info.CameraConfigHelper", false, loader)
            .getDeclaredField("sSupportCameraModeMap").apply { isAccessible = true }
    }.getOrNull()

    fun allowOverride(gate: String): Boolean {
        val mode = gates[gate] ?: return true
        // The SDK getter waits for MODE_CONDITION, including on its own initialization thread.
        return runCatching { mayExpose(mode, modesField?.get(null) as? Map<*, *>) }.getOrDefault(false)
    }

    companion object {
        internal fun mayExpose(mode: String, table: Map<*, *>?): Boolean =
            table?.values?.any { it is Collection<*> && mode in it } == true

        private val gates = mapOf(
            "com.oplus.feature.xpan.mode.support" to "xpan_mode",
            "com.oplus.gr.mode.support" to "gr_mode",
            "com.oplus.vibe.mode.support" to "vibe_mode",
            "com.oplus.feature.street.mode.support" to "street_mode",
            "com.oplus.feature.professional.mode.support" to "professional_mode",
            "com.oplus.feature.movie.mode.support" to "movie_mode",
            "com.oplus.feature.tilt.shift.photo.support" to "tilt_shift_mode",
        )
    }
}

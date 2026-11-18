package com.mi.onextbox.lsp

internal object CameraFilterRecommendationRules {
    const val OWNER = "com.oplus.camera.filter.FilterHelper"
    val keys = listOf("camera_unlock_filter_grand_tour", "camera_unlock_filter_street_tour")

    /** Native indices belong to different photo/street lists; the LUT ID is shared. */
    fun recommended(type: String?, active: Boolean, city: String?, registered: Map<*, *>?, cities: Map<*, *>?): Boolean =
        active && !city.isNullOrBlank() && !city.equals("None", ignoreCase = true) && type != null &&
            registered?.containsKey(type) == true && (cities?.get(city) as? Collection<*>)?.contains(type) == true

    fun validIndex(index: Int, count: Int): Boolean = index >= 0 && index < count
}

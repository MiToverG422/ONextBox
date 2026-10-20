package com.oplus.gallery.framework.abilities.config.storage

// Signature fixtures only; no gallery implementation or third-party code.
class OtherSystemStorage {
    fun one(node: Any): Boolean = node is String
    fun two(node: Any, default: Boolean): Boolean? = if (node is String) true else default
    fun objectResult(node: Any): Any = node
    fun wrongDefault(node: Any, default: Int): Boolean = node.hashCode() == default
    fun primitiveNode(node: Int): Boolean = node != 0
    fun tooMany(node: Any, default: Boolean, extra: Boolean): Boolean = node is String || default || extra
    fun wrongResult(node: Any): String = node.toString()

    companion object {
        @JvmStatic fun staticConfig(node: Any): Boolean = node is String
    }
}

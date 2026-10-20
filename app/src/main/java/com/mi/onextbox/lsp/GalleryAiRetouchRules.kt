package com.mi.onextbox.lsp

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Behavioral reference: the user-supplied AiRetouchUnlock 1.0 APK. No APK code is bundled. */
internal object GalleryAiRetouchRules {
    const val STORAGE = "com.oplus.gallery.framework.abilities.config.storage.OtherSystemStorage"
    const val CONFIG_KEY = "feature_is_support_ai_retouch"
    const val SYSTEM_FEATURE = "os.graphic.gallery.photoeditor.ai_retoucher"
    const val UNSUPPORTED_LOG = "[isSupportAiRetouch] unsupported, osVersion:"

    fun shouldOverride(enabled: Boolean, key: String?): Boolean = enabled && key == CONFIG_KEY

    private fun booleanResult(method: Method): Boolean = method.returnType in
        listOf(Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType, Any::class.java)

    private fun concrete(method: Method): Boolean =
        !Modifier.isAbstract(method.modifiers) && !Modifier.isNative(method.modifiers)

    fun supportMethod(method: Method): Boolean =
        concrete(method) && method.parameterCount == 0 && booleanResult(method)

    fun configMethod(method: Method): Boolean = concrete(method) && !Modifier.isStatic(method.modifiers) &&
        method.declaringClass.name == STORAGE && booleanResult(method) &&
        method.parameterCount in 1..2 && !method.parameterTypes[0].isPrimitive &&
        (method.parameterCount == 1 || method.parameterTypes[1] == Boolean::class.javaPrimitiveType)

    fun keyGetter(method: Method, nodeType: Class<*>): Boolean =
        concrete(method) && !Modifier.isStatic(method.modifiers) && method.parameterCount == 0 &&
        method.returnType == String::class.java && method.name != "toString" && method.declaringClass != Any::class.java &&
        method.declaringClass.isAssignableFrom(nodeType)
}

package com.mi.onextbox.lsp

import com.oplus.gallery.framework.abilities.config.storage.OtherSystemStorage
import org.junit.Assert.*
import org.junit.Test

class GalleryAiRetouchRulesTest {
    class Methods {
        fun support(): Boolean = false
        fun boxed(): Boolean? = false
        fun lambda(): Any = false
        fun withArgument(value: Boolean): Boolean = value
        fun text(): String = "not an eligibility check"
        fun key(): String = GalleryAiRetouchRules.CONFIG_KEY
        override fun toString(): String = GalleryAiRetouchRules.CONFIG_KEY
    }
    open class BaseNode { fun key(): String = GalleryAiRetouchRules.CONFIG_KEY }
    class Node : BaseNode()

    @Test fun onlyTheExactAiRetouchKeyIsOverriddenWhenEnabled() {
        assertTrue(GalleryAiRetouchRules.shouldOverride(true, GalleryAiRetouchRules.CONFIG_KEY))
        assertFalse(GalleryAiRetouchRules.shouldOverride(false, GalleryAiRetouchRules.CONFIG_KEY))
        listOf(null, "", "feature_is_support_ai_retouch_other", "feature_is_support_ai_eliminate",
            GalleryAiRetouchRules.SYSTEM_FEATURE).forEach {
            assertFalse(GalleryAiRetouchRules.shouldOverride(true, it))
        }
    }

    @Test fun supportSignaturesCannotReplaceUnrelatedReturnTypesOrArgumentMethods() {
        val methods = Methods::class.java.declaredMethods.associateBy { it.name }
        listOf("support", "boxed", "lambda").forEach { assertTrue(GalleryAiRetouchRules.supportMethod(methods.getValue(it))) }
        listOf("text", "withArgument").forEach { assertFalse(GalleryAiRetouchRules.supportMethod(methods.getValue(it))) }
    }

    @Test fun dispatchSupportsBothGalleryVersionsButRejectsIncompatibleSignatures() {
        val methods = OtherSystemStorage::class.java.declaredMethods.associateBy { it.name }
        listOf("one", "two", "objectResult").forEach { assertTrue(GalleryAiRetouchRules.configMethod(methods.getValue(it))) }
        listOf("wrongDefault", "primitiveNode", "tooMany", "wrongResult", "staticConfig").forEach {
            assertFalse(it, GalleryAiRetouchRules.configMethod(methods.getValue(it)))
        }
        assertFalse(GalleryAiRetouchRules.configMethod(Methods::class.java.getDeclaredMethod("withArgument", Boolean::class.javaPrimitiveType)))
    }

    @Test fun configGetterMustBelongToTheActualNodeType() {
        assertTrue(GalleryAiRetouchRules.keyGetter(BaseNode::class.java.getDeclaredMethod("key"), Node::class.java))
        assertFalse(GalleryAiRetouchRules.keyGetter(Methods::class.java.getDeclaredMethod("key"), Node::class.java))
        assertFalse(GalleryAiRetouchRules.keyGetter(Any::class.java.getDeclaredMethod("toString"), Node::class.java))
        assertFalse(GalleryAiRetouchRules.keyGetter(Methods::class.java.getDeclaredMethod("toString"), Methods::class.java))
    }

    @Test fun onlyDexFilesContainingTheActualGatesAreIndexed() {
        fun bytes(vararg strings: String) = strings.joinToString("\u0000").toByteArray(Charsets.US_ASCII)
        assertTrue(GalleryAiRetouchDex.containsTarget(bytes(GalleryAiRetouchRules.SYSTEM_FEATURE)))
        assertTrue(GalleryAiRetouchDex.containsTarget(bytes(GalleryAiRetouchRules.UNSUPPORTED_LOG)))
        val storage = "L" + GalleryAiRetouchRules.STORAGE.replace('.', '/') + ";"
        assertTrue(GalleryAiRetouchDex.containsTarget(bytes(storage, GalleryAiRetouchRules.CONFIG_KEY)))
        assertFalse(GalleryAiRetouchDex.containsTarget(bytes(GalleryAiRetouchRules.CONFIG_KEY)))
        assertFalse(GalleryAiRetouchDex.containsTarget(bytes(storage)))
        assertFalse(GalleryAiRetouchDex.containsTarget(byteArrayOf()))
        assertFalse(GalleryAiRetouchDex.containsTarget(bytes("os.graphic.gallery.photoeditor.other")))
    }
}

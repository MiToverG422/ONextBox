package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object CameraGalleryTargets {
    const val SCHEMA = "camera-preview:2"

    fun resolveAll(dex: DexKitBridge, loader: ClassLoader): Map<String, Array<Method>> {
        val ordinary = resolve(dex, loader)
        val result = linkedMapOf(SCHEMA to emptyArray<Method>(), "ordinary" to ordinary)
        runCatching { resolveEmbedded(dex, loader, ordinary[2].declaringClass.name) }
            .onSuccess { result["embedded"] = it }
            .onFailure { HookLog.w("ONextBox-Camera", "Embedded preview targets unavailable; ordinary preview retained", it) }
        return result
    }

    fun instanceField(owner: Class<*>, type: Class<*>): Field {
        val fields = generateSequence(owner) { it.superclass }.flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) }.toList()
        val exact = fields.filter { it.type == type }
        return (exact.ifEmpty { fields.filter { type.isAssignableFrom(it.type) } }).single().apply { isAccessible = true }
    }

    private fun resolveEmbedded(dex: DexKitBridge, loader: ClassLoader, entryClass: String): Array<Method> {
        val embedded = dex.findMethod {
            matcher {
                usingStrings("handleThumbnailClickInternal, thumbnail click sample start", "com.android.camera.action.REVIEW")
                returnType = "void"; paramCount = 7
            }
        }.single()
        require(embedded.paramTypeNames.take(6) == listOf("android.view.View", "android.net.Uri", "java.lang.String",
            "android.graphics.Bitmap", "int", "int"))
        val entry = embedded.invokes.distinctBy { it.descriptor }.single {
            it.returnTypeName == entryClass && it.paramCount == 0
        }
        val direct = embedded.callers
        val callers = (direct + direct.flatMap { it.callers }).filter { it.isMethod }.distinctBy { it.descriptor }
            .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
        return (listOf(embedded, entry).map { it.getMethodInstance(loader) } + callers).distinct().toTypedArray()
    }

    fun resolve(dex: DexKitBridge, loader: ClassLoader): Array<Method> {
        val preview = dex.findMethod {
            matcher {
                declaredClass = "com.oplus.camera.helper.GalleryHelper"
                usingStrings("com.android.camera.action.REVIEW", "IsSecureCamera")
                returnType = "boolean"
                paramCount = 3
            }
        }.single()
        val secure = preview.invokes.distinctBy { it.descriptor }.single {
            it.className == "com.oplus.camera.entry.CameraEntry" && it.returnTypeName == "boolean" && it.paramCount == 0
        }
        val leaving = preview.invokes.distinctBy { it.descriptor }.single {
            it.className == secure.className && it.returnTypeName == "void" && it.paramTypeNames == listOf("boolean")
        }
        val launch = preview.invokes.distinctBy { it.descriptor }.single {
            it.className == preview.className && it.returnTypeName == "void" &&
                it.paramTypeNames == listOf("android.content.Intent", "boolean", "android.net.Uri")
        }
        require(launch.invokes.any { it.className == "android.app.Activity" && it.methodName == "startActivity" })
        val reset = dex.findMethod {
            matcher { declaredClass = preview.className; usingStrings("resetTransitionView"); returnType = "void"; paramTypes() }
        }.single()
        val alternate = dex.findMethod {
            matcher {
                usingStrings("com.android.camera.action.REVIEW", "IsSecureCamera")
                returnType = "void"; paramCount = 3
            }
        }.single { it.className.startsWith("com.oplus.camera.CameraManager$") &&
            it.paramTypeNames.first() == "android.view.View" && it.paramTypeNames.last() == "android.graphics.Bitmap" }
        require(alternate.invokes.any { it.descriptor == launch.descriptor })
        val direct = preview.callers + launch.callers + alternate.callers
        val callers = (direct + direct.flatMap { it.callers }).filter { it.isMethod }.distinctBy { it.descriptor }
            .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
        return (listOf(preview, launch, secure, leaving, reset, alternate).map { it.getMethodInstance(loader) } + callers).distinct().toTypedArray()
    }
}

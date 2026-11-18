package com.mi.onextbox.lsp

import android.graphics.Matrix
import android.app.ActivityManager
import android.graphics.Rect
import android.graphics.RectF
import android.view.SurfaceControl
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class LauncherIosMotionTargets(val loader: ClassLoader) {
    val helper: Class<*> = HostClassLookup.load("com.android.launcher3.anim.OplusLauncherAppTransitionHelper", loader)
    val spring: Class<*> = helper.getDeclaredField("mAppTransitionAnim").type
    private val animationType = HostClassLookup.load("com.oplus.animation.core.controller.AnimationType", loader)
    val springKind: Field = spring.declaredFields.single { it.type == animationType }.apply { isAccessible = true }
    val kindClass: Class<*> get() = animationType
    val setupMethods: List<Method> = listOf("initAllAnimations #", "updateSpringParam").mapIndexed { index, fragment ->
        LauncherDexResolver.method(loader, "ios-cherry-spring-setup-$index-v1") { dex ->
            dex.findMethod { matcher { declaredClass = spring.name; paramCount = 0; returnType = "void"; usingStrings(fragment) } }.single()
        }
    }
    private val forceClass: Class<*> = spring.declaredFields.groupBy { it.type }.entries.single { (type, fields) ->
        fields.size == 7 && type.declaredMethods.count { method ->
            method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(Float::class.javaPrimitiveType))
        } == 2
    }.key
    val forceSetters: List<Method> = listOf("Damping ratio must be non-negative", "Spring stiffness constant must be positive.").mapIndexed { index, fragment ->
        LauncherDexResolver.method(loader, "ios-cherry-force-setter-$index-v1") { dex ->
            dex.findMethod { matcher {
                declaredClass = forceClass.name; paramTypes("float"); returnType = "void"; usingStrings(fragment)
            } }.single()
        }
    }
}

internal class LauncherIosMorphTargets(val base: LauncherIosMotionTargets) {
    val loader: ClassLoader = base.loader

    data class Route(
        val method: Method,
        val transformField: Field,
        val screenRectField: Field,
        val iconRectFields: List<Field>,
        val isWidgetField: Field?,
    )

    val routes: List<Route> = listOf(
        resolveRoute(
            "open",
            "OplusLauncherAppTransitionHelper\$startAppLaunchWindowAnim\$",
            listOf("\$iconRect", "\$startRect"),
        ),
        resolveRoute(
            "close",
            "OplusLauncherAppTransitionHelper\$startAppCloseWindowAnim\$",
            listOf("\$targetRect", "\$iconRectF"),
        ),
        resolveRoute(
            "swipe",
            "SwipeToHomeAnimCreator\$Companion\$buildSpringAnimation\$",
            listOf("\$targetRect", "\$iconRect"),
        ),
        resolveRoute(
            "swipe-break",
            "SwipeBreakLaunchAnimCreator\$Companion\$buildBreakOpenAnimation\$",
            emptyList(),
            "\$transformHelper", "\$curDisplayWindowRectF",
        ),
        resolveRoute(
            "swipe-client",
            "SwipeToClientHomeAnimCreator\$Companion\$buildSpringAnimation\$",
            listOf("\$targetRectF"),
        ),
    )

    val updateListenerInterface: Class<*> = routes.first().method.declaringClass.interfaces.single { iface ->
        iface.declaredMethods.any { method ->
            method.name == "onUpdate" &&
                method.parameterTypes.contentEquals(routes.first().method.parameterTypes)
        }
    }

    val registerUpdateMethod: Method = base.spring.declaredMethods.single { method ->
        method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(updateListenerInterface))
    }.apply { isAccessible = true }

    val transformClass: Class<*> = routes.first().transformField.type
    val transformKindField: Field = transformClass.declaredFields.single {
        it.type == base.kindClass
    }.apply { isAccessible = true }

    val frameMethod: Method = transformClass.declaredMethods.single { method ->
        val params = method.parameterTypes
        !Modifier.isStatic(method.modifiers) &&
            params.size == 9 &&
            params[1].isArray &&
            params[2] == Int::class.javaPrimitiveType &&
            params[5] == RectF::class.java &&
            params[6] == Boolean::class.javaPrimitiveType &&
            params[7] == RectF::class.java &&
            params[8] == params[1].componentType && method.returnType == Void.TYPE
    }.apply { isAccessible = true }

    val transformMatrixField: Field = transformClass.declaredFields.single { it.type == Matrix::class.java }
        .apply { isAccessible = true }
    val transformCropField: Field = transformClass.getDeclaredField("c").apply {
        isAccessible = true
        require(type == Rect::class.java)
    }

    val transformParamsClass: Class<*> = frameMethod.parameterTypes[0]
    val needsRotationField: Field = transformParamsClass.getDeclaredField("J").apply {
        isAccessible = true
        require(type == Boolean::class.javaPrimitiveType)
    }
    val originHeightField = transformParamsClass.getDeclaredField("d").apply {
        isAccessible = true; require(type == Float::class.javaPrimitiveType)
    }
    val cornerRadiusField = transformParamsClass.getDeclaredField("j").apply {
        isAccessible = true; require(type == Float::class.javaPrimitiveType)
    }
    val fullWindowMethod = transformClass.declaredMethods.single {
        it.parameterCount == 0 && it.returnType == Rect::class.java && !Modifier.isStatic(it.modifiers)
    }.apply { isAccessible = true }
    val sourceMethods: List<Method> = listOfNotNull(base.helper, runCatching {
        HostClassLookup.load("com.android.launcher3.anim.OplusLauncherAppTransitionHelperV2", loader)
    }.getOrNull()).flatMap { owner -> owner.declaredMethods.filter { method ->
        method.name in setOf("startAppLaunchWindowAnim", "startAppCloseWindowAnim") &&
            method.parameterTypes.any { View::class.java.isAssignableFrom(it) }
    } }.onEach { it.isAccessible = true }

    val targetClass: Class<*> = frameMethod.parameterTypes[1].componentType!!
    val matchesTargetMethod: Method = transformClass.declaredMethods.single { method ->
        method.returnType == Boolean::class.javaPrimitiveType &&
            method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, targetClass))
    }.apply { isAccessible = true }

    val leashField: Field = listOf("leash", "d").firstNotNullOf { name ->
        runCatching {
            targetClass.getDeclaredField(name).apply { isAccessible = true }
                .takeIf { it.type == SurfaceControl::class.java }
        }.getOrNull()
    }

    val windowConfigurationField: Field = targetClass.declaredFields.single { field ->
        field.type.name == "android.app.WindowConfiguration"
    }.apply { isAccessible = true }
    val getWindowingModeMethod: Method = windowConfigurationField.type.getMethod("getWindowingMode")
    val taskInfoField: Field = targetClass.declaredFields.single { it.type == ActivityManager.RunningTaskInfo::class.java }
        .apply { isAccessible = true }

    val transactionField: Field = frameMethod.parameterTypes[3].declaredFields.single { field ->
        field.type == SurfaceControl.Transaction::class.java
    }.apply { isAccessible = true }

    private val transactionClass = frameMethod.parameterTypes[3]
    val propertiesClass: Class<*> = transactionClass.declaredMethods.single { method ->
        method.parameterTypes.contentEquals(arrayOf(SurfaceControl::class.java)) &&
            method.returnType != Void.TYPE && !Modifier.isStatic(method.modifiers)
    }.returnType
    val propertiesSurfaceField: Field = propertiesClass.declaredFields.single { it.type == SurfaceControl::class.java }
        .apply { isAccessible = true }
    val propertiesTransactionField: Field = propertiesClass.declaredFields.single { it.type == transactionClass }
        .apply { isAccessible = true }
    val setMatrixMethod: Method = propertiesClass.declaredMethods.single { method ->
        method.parameterTypes.contentEquals(arrayOf(Matrix::class.java)) && method.returnType == propertiesClass
    }.apply { isAccessible = true }
    val setAlphaMethod: Method = propertiesClass.getDeclaredMethod("b", Float::class.javaPrimitiveType).apply {
        isAccessible = true
        require(returnType == propertiesClass)
    }
    val radiusMethods: List<Method> = propertiesClass.declaredMethods.filter { method ->
        (method.parameterTypes.contentEquals(arrayOf(Float::class.javaPrimitiveType)) && method.returnType == propertiesClass &&
            method.name == "d") || (method.returnType == Void.TYPE && method.parameterTypes.contentEquals(
            arrayOf(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Int::class.javaPrimitiveType),
        ))
    }.onEach { it.isAccessible = true }

    val iconUpdateMethod: Method = LauncherDexResolver.method(loader, "ios-paired-icon-update-v1") { dex ->
        dex.findMethod { matcher { paramCount = 8; returnType = "void"; usingStrings("Icon params: x: ") } }
            .single { method -> method.paramTypeNames.take(5) == listOf(
                transformParamsClass.name, "boolean", RectF::class.java.name, SurfaceControl::class.java.name, transactionClass.name,
            ) }
    }.also { method ->
        require(method.parameterTypes[6] == Matrix::class.java)
    }
    val iconOpeningField: Field = iconUpdateMethod.declaringClass.getDeclaredField("a").apply {
        isAccessible = true; require(type == Boolean::class.javaPrimitiveType)
    }
    val iconMatrixField: Field = iconUpdateMethod.declaringClass.declaredFields.single { it.type == Matrix::class.java }
        .apply { isAccessible = true }
    val iconCropField: Field = iconUpdateMethod.declaringClass.declaredFields.single { it.type == Rect::class.java }
        .apply { isAccessible = true }
    val dispatchMethod: Method = LauncherDexResolver.method(loader, "ios-paired-dispatch-v1") { dex ->
        dex.findMethod { matcher { paramCount = 6; returnType = "void" } }.single { method ->
            method.paramTypeNames.drop(2) == listOf(transformParamsClass.name, RectF::class.java.name,
                Runnable::class.java.name, "kotlin.jvm.functions.Function2")
        }
    }
    val appAlphaField: Field = transformParamsClass.getDeclaredField("b").apply {
        isAccessible = true
        require(type == Float::class.javaPrimitiveType)
    }
    val surfaceWidthMethod: Method = SurfaceControl::class.java.getMethod("getWidth")
    val surfaceHeightMethod: Method = SurfaceControl::class.java.getMethod("getHeight")

    data class FloatingIcon(val update: Method, val run: Method, val alpha: Field)
    val floatingIcon: FloatingIcon? = runCatching {
        val update = HostClassLookup.load("com.android.launcher3.views.FloatingIconView", loader)
            .getDeclaredMethod("update", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, RectF::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val run = LauncherDexResolver.method(loader, "ios-floating-icon-run-v2") { dex ->
            requireNotNull(dex.getMethodData(update)).callers.single { method ->
                method.name == "run" && method.paramCount == 0 && method.returnTypeName == "void" &&
                    method.invokes.any { it.name == "getMAppOpenAnimRecord" && it.paramCount == 0 &&
                        it.className.startsWith("com.android.launcher3.anim.") }
            }
        }
        val alpha = run.declaringClass.getDeclaredField("s").apply {
            isAccessible = true; require(type == Float::class.javaPrimitiveType)
        }
        FloatingIcon(update, run, alpha)
    }.onFailure { HookLog.w("LauncherIosMotion", "FloatingIconView fallback unavailable; surface handoff retained", it) }.getOrNull()

    val frameRecordClass: Class<*> = frameMethod.parameterTypes[4]
    val recordStoreMethod: Method = frameRecordClass.declaredMethods.single { method ->
        method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Rect::class.java, Float::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        ))
    }.apply { isAccessible = true }
    val recordReplayMethod: Method = frameRecordClass.declaredMethods.single { method ->
        !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(SurfaceControl.Transaction::class.java, SurfaceControl::class.java))
    }.apply { isAccessible = true }
    val recordClearMethod: Method = LauncherDexResolver.method(loader, "ios-paired-record-clear-v1") { dex ->
        dex.findMethod { matcher {
            declaredClass = frameRecordClass.name; paramCount = 0; returnType = "void"
            usingStrings("Clear the break param")
        } }.single()
    }
    val recordValidField: Field = frameRecordClass.getDeclaredField("a").apply {
        isAccessible = true
        require(type == Boolean::class.javaPrimitiveType)
    }
    val recordCropField: Field = frameRecordClass.declaredFields.single { it.type == Rect::class.java }
        .apply { isAccessible = true }
    val recordAffineFields: List<Field> = listOf("b", "c", "d", "g", "q", "r").map { name ->
        frameRecordClass.getDeclaredField(name).apply { isAccessible = true; require(type == Float::class.javaPrimitiveType) }
    }
    val recordRadiusField: Field = frameRecordClass.getDeclaredField("f").apply {
        isAccessible = true
        require(type == Float::class.javaPrimitiveType)
    }
    private val recordRadiusWeightField = frameRecordClass.getDeclaredField("h").apply {
        isAccessible = true; require(type == Float::class.javaPrimitiveType)
    }
    private val recordCornerVersionField = frameRecordClass.getDeclaredField("i").apply {
        isAccessible = true; require(type == Int::class.javaPrimitiveType)
    }
    private val recordRadiusMethod: Method = frameRecordClass.declaredMethods.single { method ->
        Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            SurfaceControl.Transaction::class.java, SurfaceControl::class.java,
        ))
    }.apply { isAccessible = true }

    fun applyRecordRadius(transaction: SurfaceControl.Transaction, surface: SurfaceControl, record: Any, radius: Float) {
        recordRadiusMethod.invoke(null, radius, recordRadiusWeightField.getFloat(record), recordCornerVersionField.getInt(record), transaction, surface)
    }

    val cancelMethod: Method = resolveSpringLifecycle("cancel", " request cancel")
    val springCancelledField: Field = LauncherDexResolver.field(loader, "ios-morph-cancelled-v1") { dex ->
        requireNotNull(dex.getMethodData(cancelMethod)).usingFields.map { it.field }.distinctBy { it.descriptor }
            .single { it.className == base.spring.name && it.typeName == "boolean" }
    }
    private val maybeEndMethod: Method = resolveSpringLifecycle("end", " maybeEnd, animRunning: ")
    val endMethods: List<Method> = listOfNotNull(
        maybeEndMethod,
        runCatching { resolveSpringLifecycle("ended", " onAnimationEnded") }.getOrNull(),
    ).distinct()

    val springRunningMethod: Method = LauncherDexResolver.method(loader, "ios-morph-running-v3") { dex ->
        dex.findMethod {
            matcher {
                declaredClass = base.spring.name
                name = maybeEndMethod.name
                paramCount = 0
            }
        }.single()
            .invokes.distinctBy { it.descriptor }
            .single {
                it.className == base.spring.name &&
                    it.returnTypeName == "boolean" &&
                    it.paramCount == 0
            }
    }

    private val oplusTransactionClass: Class<*> =
        HostClassLookup.load("com.oplus.wrapper.view.SurfaceControl\$Transaction", loader)
    private val perspectiveMethod = oplusTransactionClass.getDeclaredMethod(
        "setTransformationMatrix4x4", SurfaceControl.Transaction::class.java, SurfaceControl::class.java, FloatArray::class.java,
    ).apply { isAccessible = true }
    private val isSameSurfaceMethod: Method =
        SurfaceControl::class.java.getMethod("isSameSurface", SurfaceControl::class.java)

    fun sameSurface(first: SurfaceControl, second: SurfaceControl): Boolean =
        first === second || (first.isValid && second.isValid &&
            runCatching { isSameSurfaceMethod.invoke(first, second) == true }.getOrDefault(false))

    fun applyPerspective(transaction: SurfaceControl.Transaction, surface: SurfaceControl, matrix: FloatArray) {
        if (surface.isValid) perspectiveMethod.invoke(null, transaction, surface, matrix)
    }

    private fun resolveSpringLifecycle(key: String, logFragment: String): Method =
        LauncherDexResolver.method(loader, "ios-morph-$key-v3") { dex ->
            dex.findMethod {
                matcher {
                    declaredClass = base.spring.name
                    paramCount = 0
                    returnType = "void"
                    usingStrings(logFragment)
                }
            }.single()
        }

    private fun resolveRoute(key: String, classFragment: String, iconFieldNames: List<String>,
        transformFieldName: String = "\$rectTransformHelper", screenFieldName: String = "\$screenRectF"): Route {
        val method = LauncherDexResolver.method(loader, "ios-morph-$key-v3") { dex ->
            dex.findMethod {
                matcher {
                    name = "onUpdate"
                    paramTypes(RectF::class.java.name, "float", "float", "float", "float", "float")
                }
            }.single { it.className.contains(classFragment) }
        }
        val ownerClass = method.declaringClass
        val transformField = ownerClass.getDeclaredField(transformFieldName).apply { isAccessible = true }
        val screenRectField = ownerClass.getDeclaredField(screenFieldName).apply { isAccessible = true }
        val iconRectFields = iconFieldNames.mapNotNull { fieldName ->
            runCatching {
                ownerClass.getDeclaredField(fieldName).apply {
                    isAccessible = true
                    require(type == RectF::class.java)
                }
            }.getOrNull()
        }
        val isWidgetField = runCatching {
            ownerClass.getDeclaredField("\$isWidget").apply { isAccessible = true }
        }.getOrNull()
        return Route(method, transformField, screenRectField, iconRectFields, isWidgetField)
    }
}

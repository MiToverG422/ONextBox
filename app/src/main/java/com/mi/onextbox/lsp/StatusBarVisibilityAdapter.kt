package com.mi.onextbox.lsp

import java.lang.reflect.Method

/** Copies native inputs so display restrictions, chips and animation decisions stay native. */
internal class StatusBarVisibilityAdapter(
    val visibilityType: Class<*>,
    private val flagsType: Class<*>,
) {
    private val visibility = visibilityType.getDeclaredField("visibility").apply { isAccessible = true }
    private val animate = visibilityType.getDeclaredField("shouldAnimateChange").apply { isAccessible = true }
    private val visibilityConstructor = visibilityType.getConstructor(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
    private val flagNames = listOf("isClockAllowed", "areNotificationIconsAllowed", "isSystemInfoAllowed", "animate")
    private val fields = flagNames.map { name -> flagsType.getDeclaredField(name).apply { isAccessible = true } }
    private val flagsConstructor = flagsType.getConstructor(*Array(4) { Boolean::class.javaPrimitiveType })

    init {
        require(visibility.type == Int::class.javaPrimitiveType)
        require(animate.type == Boolean::class.javaPrimitiveType)
        require(fields.all { it.type == Boolean::class.javaPrimitiveType })
    }

    fun visibility(model: Any): Int {
        require(visibilityType.isInstance(model))
        return visibility.getInt(model)
    }

    fun notificationIconsAllowed(flags: Any): Boolean {
        require(flagsType.isInstance(flags))
        return fields[1].getBoolean(flags)
    }

    fun withVisibility(model: Any, desired: Int): Any =
        if (visibility(model) == desired) model
        else visibilityConstructor.newInstance(desired, animate.getBoolean(model))

    fun allow(flags: Any, field: String): Any {
        require(flagsType.isInstance(flags))
        val index = flagNames.indexOf(field)
        require(index in 0..2)
        if (fields[index].getBoolean(flags)) return flags
        val values = fields.mapIndexed { i, native -> if (i == index) true else native.getBoolean(flags) }
        return flagsConstructor.newInstance(*values.toTypedArray())
    }

    companion object {
        fun load(loader: ClassLoader) = StatusBarVisibilityAdapter(
            loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel"),
            loader.loadClass("com.android.systemui.statusbar.pipeline.shared.domain.model.StatusBarDisableFlagsVisibilityModel"),
        )

        fun combiner(loader: ClassLoader, name: String, count: Int): Method =
            loader.loadClass(name).declaredMethods.single {
                it.name == "invoke" && it.parameterCount == count && !it.isBridge && it.returnType == Any::class.java
            }.apply { isAccessible = true }
    }
}

package com.mi.onextbox.lsp

// Clear button icon drawing during entry animations.
internal class LauncherClearIconDrawing(buttonClass: Class<*>) {
    private val getBlocked = buttonClass.getDeclaredMethod("getForceBlockDraw")
    private val setBlocked = buttonClass.getDeclaredMethod("setForceBlockDraw", Boolean::class.javaPrimitiveType)

    init {
        require(getBlocked.returnType == Boolean::class.javaPrimitiveType && setBlocked.returnType == Void.TYPE)
    }

    fun <T> draw(button: Any, draw: () -> T): T {
        val wasBlocked = getBlocked.invoke(button) as Boolean
        if (!wasBlocked) return draw()
        // Allow the icon for this draw without changing the system animation state.
        setBlocked.invoke(button, false)
        return try {
            draw()
        } finally {
            setBlocked.invoke(button, true)
        }
    }
}

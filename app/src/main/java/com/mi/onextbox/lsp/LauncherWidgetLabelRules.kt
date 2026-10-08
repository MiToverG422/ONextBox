package com.mi.onextbox.lsp

internal object LauncherWidgetLabelRules {
    const val BASE_WIDGET_HOST = "com.android.launcher3.widget.BaseLauncherAppWidgetHostView"

    fun isTitleOwner(classNames: Sequence<String>): Boolean = classNames.any {
        it.startsWith("com.android.launcher3.card.") || it == BASE_WIDGET_HOST
    }
}

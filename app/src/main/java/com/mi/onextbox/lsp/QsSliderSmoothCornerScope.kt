package com.mi.onextbox.lsp

/** Enable native G2 rendering only during custom painting/material setup. */
internal class QsSliderSmoothCornerScope(
    private val read: (String) -> Any,
    private val write: (String, Any) -> Unit,
) {
    fun <T> paint(block: () -> T): T {
        val support = read(SUPPORT)
        val background = read(BACKGROUND)
        val progress = read(PROGRESS)
        try {
            write(SUPPORT, true)
            write(BACKGROUND, QsCornerSmoothness.weight((background as Number).toFloat()))
            write(PROGRESS, QsCornerSmoothness.weight((progress as Number).toFloat()))
            return block()
        } finally {
            try { write(PROGRESS, progress) } finally {
                try { write(BACKGROUND, background) } finally { write(SUPPORT, support) }
            }
        }
    }

    companion object {
        const val SUPPORT = "mIsSupportSmoothRoundCorner"
        const val BACKGROUND = "mBackgroundRoundCornerWeight"
        const val PROGRESS = "mProgressRoundCornerWeight"
    }
}

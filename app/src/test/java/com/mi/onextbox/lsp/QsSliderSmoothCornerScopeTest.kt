package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class QsSliderSmoothCornerScopeTest {
    private val support = QsSliderSmoothCornerScope.SUPPORT
    private val background = QsSliderSmoothCornerScope.BACKGROUND
    private val progress = QsSliderSmoothCornerScope.PROGRESS
    private fun nativeFields(): MutableMap<String, Any> =
        mutableMapOf(support to false, background to 1f, progress to 0f, "mProgressRadius" to 32f)

    @Test fun backgroundAndFillUseSmoothRenderingWithoutChangingGeometry() {
        val fields = nativeFields()
        val original = fields.toMap()
        QsSliderSmoothCornerScope(fields::getValue) { key, value -> fields[key] = value }.paint {
            assertEquals(true, fields[support])
            assertEquals(3f, fields[background])
            assertEquals(3f, fields[progress])
            assertEquals(32f, fields["mProgressRadius"])
        }
        assertEquals(original, fields)
    }

    @Test fun drawingFailureRestoresNativeSupportAndBothWeights() {
        val fields = nativeFields()
        val original = fields.toMap()
        val failure = runCatching {
            QsSliderSmoothCornerScope(fields::getValue) { key, value -> fields[key] = value }.paint {
                error("native draw failed")
            }
        }
        assertTrue(failure.isFailure)
        assertEquals(original, fields)
    }

    @Test fun partialPreparationFailureRestoresEveryFieldBeforeNativeFallback() {
        val fields = nativeFields()
        val original = fields.toMap()
        val failure = runCatching {
            QsSliderSmoothCornerScope(fields::getValue) { key, value ->
                if (key == progress && value == 3f) error("preparation failed")
                fields[key] = value
            }.paint { fail("native custom drawing must not start") }
        }
        assertTrue(failure.isFailure)
        assertEquals(original, fields)
    }

    @Test fun nestedScopesRetainExistingSmoothWeightsAndRestoreTheirCaller() {
        val fields = nativeFields().apply { this[background] = 4f }
        val original = fields.toMap()
        val scope = QsSliderSmoothCornerScope(fields::getValue) { key, value -> fields[key] = value }
        scope.paint {
            scope.paint {
                assertEquals(4f, fields[background])
                assertEquals(3f, fields[progress])
            }
            assertEquals(true, fields[support])
            assertEquals(3f, fields[progress])
        }
        assertEquals(original, fields)
    }
}

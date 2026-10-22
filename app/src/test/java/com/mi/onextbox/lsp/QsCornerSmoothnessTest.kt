package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class QsCornerSmoothnessTest {
    @Test fun nativeFullAndCircularParametersBecomeSmoothForCustomCorners() {
        listOf(null, 0f, 1f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(3f, QsCornerSmoothness.weight(it), 0f)
        }
    }

    @Test fun existingSmoothParametersRemainUnchanged() {
        listOf(2f, 3f, 4f).forEach {
            assertEquals(it, QsCornerSmoothness.weight(it), 0f)
        }
    }
}

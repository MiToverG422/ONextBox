package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFolderSizingTest {
    @Test fun fiveColumnsKeepTheNativeIconWhenTheCellHasEnoughSpace() {
        assertEquals(168, LauncherFolderSizing.fitIcon(168, 180, 315, 45, 18, 12))
    }

    @Test fun sixColumnsFitTheIconWithoutScalingItByTheWholeGridRatio() {
        assertEquals(138, LauncherFolderSizing.fitIcon(168, 150, 210, 45, 18, 12))
    }

    @Test fun fewerColumnsDoNotEnlargeTheNativeIcon() {
        assertEquals(168, LauncherFolderSizing.fitIcon(168, 450, 420, 45, 18, 12))
    }

    @Test fun fittingReservesSpaceForTheActualLabelAndDrawablePadding() {
        assertEquals(112, LauncherFolderSizing.fitIcon(168, 180, 210, 70, 28, 12))
        assertTrue(LauncherFolderSizing.fitIcon(168, 12, 20, 70, 28, 12) > 0)
        assertEquals(168, LauncherFolderSizing.fitIcon(168, 0, 210, 45, 18, 12))
    }

    @Test fun closingPreviewKeepsItsNativePixelSizeAcrossExpandedGridSizes() {
        for (rendered in listOf(84, 112, 138, 168)) {
            val source = .45f
            val adjusted = LauncherFolderSizing.previewScale(168, rendered, source)
            assertEquals(168 * source, rendered * adjusted, .001f)
        }
    }
}

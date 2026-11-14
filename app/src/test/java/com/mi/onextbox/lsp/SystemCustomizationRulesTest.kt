package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SystemCustomizationRulesTest {
    @Test fun sizingKeepsNativeLayoutSentinelsAndScalesBothEdges() {
        assertEquals(-1, SystemCustomizationRules.scaled(-1, 150))
        assertEquals(-2, SystemCustomizationRules.scaled(-2, 50))
        assertEquals(0, SystemCustomizationRules.scaled(0, 150))
        assertEquals(96, SystemCustomizationRules.scaled(96, 100))
        assertEquals(48, SystemCustomizationRules.scaled(96, 50))
        assertEquals(144, SystemCustomizationRules.scaled(96, 150))
    }

    @Test fun importedFolderAndSizeValuesCannotCreateAnEmptyOrUnboundedGrid() {
        assertEquals(2 to 6, SystemCustomizationRules.folderGrid(-1, 100))
        assertEquals(6 to 2, SystemCustomizationRules.folderGrid(99, 0))
        assertEquals(50, SystemCustomizationSettings.iconPercent.normalize(-50))
        assertEquals(150, SystemCustomizationSettings.clearPercent.normalize(1000))
    }

    @Test fun zeroVolumeStepsFollowTheSystemAndActiveRangesRespectTheNativeMinimum() {
        assertEquals(160, SystemCustomizationRules.volumeMaximum(0, 0, 160))
        assertEquals(2, SystemCustomizationRules.volumeMaximum(1, 1, 5))
        assertEquals(200, SystemCustomizationRules.volumeMaximum(1000, 0, 15))
        assertEquals(80, SystemCustomizationRules.volumeMaximum(80, 0, 160))
    }

    @Test fun nativeDefaultVolumesRemainProportionalAndInsideTheNewRange() {
        assertEquals(40, SystemCustomizationRules.rescaleVolume(80, 160, 80))
        assertEquals(100, SystemCustomizationRules.rescaleVolume(80, 160, 200))
        assertEquals(200, SystemCustomizationRules.rescaleVolume(300, 160, 200))
        assertEquals(-1, SystemCustomizationRules.rescaleVolume(-1, 160, 200))
        assertEquals(0, SystemCustomizationRules.rescaleVolume(0, 160, 200))
    }
}

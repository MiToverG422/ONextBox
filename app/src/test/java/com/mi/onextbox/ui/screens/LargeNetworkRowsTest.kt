package com.mi.onextbox.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class LargeNetworkRowsTest {
    @Test fun disabledParentIsOneStandaloneCard() {
        for (artwork in listOf(false, true)) for (font in listOf(false, true)) {
            assertEquals(listOf(LargeNetworkRow.Main), largeNetworkRows(false, artwork, font))
        }
    }
    @Test fun artworkBranchEndsWithLteRegardlessOfSavedFontChoice() {
        for (font in listOf(false, true)) {
            assertEquals(listOf(LargeNetworkRow.Main, LargeNetworkRow.Position, LargeNetworkRow.Artwork, LargeNetworkRow.Lte),
                largeNetworkRows(true, true, font))
        }
    }
    @Test fun fontBranchHasExactlyOneLastRow() {
        val base = listOf(LargeNetworkRow.Main, LargeNetworkRow.Position, LargeNetworkRow.Artwork, LargeNetworkRow.Font)
        assertEquals(base, largeNetworkRows(true, false, false))
        assertEquals(base + LargeNetworkRow.Bold, largeNetworkRows(true, false, true))
    }
}

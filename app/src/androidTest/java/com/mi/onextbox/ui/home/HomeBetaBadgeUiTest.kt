package com.mi.onextbox.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.ui.common.AppUiTokens
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeBetaBadgeUiTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Hero(version: String, width: Dp = 360.dp) {
        COUITheme {
            Box(Modifier.width(width).aspectRatio(1128f / 430f)
                .squircleSurface(color = Color(0xFF147DF5), cornerRadius = AppUiTokens.CardCornerRadius)
                .testTag("hero")) {
                HomeHeroCardContent(version, "OPPO Find X9 Ultra")
            }
        }
    }

    @Test fun diagonalRibbonMeetsBothCardEdgesWithoutMovingTheHeroText() {
        val version = mutableStateOf("17.0")
        compose.setContent { Hero(version.value) }
        compose.onNodeWithTag("homeBetaBadge").assertDoesNotExist()
        val before = compose.onNodeWithTag("homeHeroTitle").getUnclippedBoundsInRoot()
        val beforeDetails = compose.onNodeWithTag("homeHeroDetails").getUnclippedBoundsInRoot()
        compose.runOnIdle { version.value = "17.0_Beta2" }
        val title = compose.onNodeWithTag("homeHeroTitle").getUnclippedBoundsInRoot()
        val details = compose.onNodeWithTag("homeHeroDetails").getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithTag("homeBetaBadge").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("hero").getUnclippedBoundsInRoot()
        assertEquals(before.top.value, title.top.value, .5f)
        assertEquals(before.bottom.value, title.bottom.value, .5f)
        assertEquals(beforeDetails.top.value, details.top.value, .5f)
        assertEquals(card.top.value, badge.top.value, .5f)
        assertEquals(card.right.value, badge.right.value, .5f)
        assertTrue(title.right + 10.dp <= badge.left)
        compose.onNodeWithText("BETA").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithTag("hero").captureToImage().asAndroidBitmap().let { image ->
            fun gold(x: Int, y: Int): Boolean {
                val pixel = image.getPixel(x, y)
                val red = (pixel shr 16) and 255
                val green = (pixel shr 8) and 255
                val blue = pixel and 255
                return red > 150 && green > 90 && blue < 170 && red >= green
            }
            assertTrue("Ribbon must meet the top edge", (image.width * 2 / 3 until image.width - 4).any { gold(it, 2) })
            assertTrue("Ribbon must meet the right edge", (4 until image.height * 3 / 4).any { gold(image.width - 3, it) })
            File(context.cacheDir, "home-beta-gold.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun theVersionMarkerControlsVisibilityIndependentlyOfBuildType() {
        val version = mutableStateOf("17.0")
        compose.setContent { Hero(version.value) }
        listOf("17.0_Beta", "17.0-beta.2", "17.0bEtA3", "Beta17.0").forEach {
            compose.runOnIdle { version.value = it }
            compose.onNodeWithTag("homeBetaBadge").assertExists()
        }
        listOf("17.0", "17.0-Release", "17.0-Debug", "17.0-CI-abcd", "17.0-rc1").forEach {
            compose.runOnIdle { version.value = it }
            compose.onNodeWithTag("homeBetaBadge").assertDoesNotExist()
        }
    }

    @Test fun aNarrowCardAndLargeTextKeepThePlateClearOfTheTitle() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                Hero("17.0_BETA10", 300.dp)
            }
        }
        val badge = compose.onNodeWithTag("homeBetaBadge").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("homeHeroTitle").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("hero").getUnclippedBoundsInRoot()
        assertTrue(badge.top >= card.top && badge.bottom <= card.bottom)
        assertTrue(badge.right <= card.right && badge.left >= card.left)
        assertTrue(title.right + 10.dp <= badge.left)
        compose.onNodeWithText("BETA").assertExists()
    }
}

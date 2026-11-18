package com.mi.onextbox.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.rememberCountryFlagGlyph
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max

class AndroidEasterEggArtworkUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachVersionShowsItsOriginalColorArtworkAlignedWithTheText() {
        val api = mutableIntStateOf(35)
        var label = ""
        var value = ""
        compose.setContent {
            label = stringResource(R.string.home_info_android_api)
            value = stringResource(R.string.home_dash_android_api_format, (api.intValue - 20).toString(), api.intValue)
            COUITheme {
                Column(Modifier.width(360.dp).testTag("androidApiCard")) {
                    HomeAndroidApiRow((api.intValue - 20).toString(), api.intValue)
                }
            }
        }
        for (sdk in 35..37) {
            compose.runOnIdle { api.intValue = sdk }
            val card = compose.onNodeWithTag("androidApiCard").getUnclippedBoundsInRoot()
            val logo = compose.onNodeWithTag("homeAndroidEasterLogo", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val title = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val summary = compose.onNodeWithText(value, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals((logo.bottom - logo.top).value, (logo.right - logo.left).value, .5f)
            assertEquals(summary.bottom.value - title.top.value, (logo.bottom - logo.top).value, .5f)
            assertEquals(card.right.value - 16f, logo.right.value, .5f)
            assertTrue(title.right + 16.dp <= logo.left)
            val image = compose.onNodeWithTag("homeAndroidEasterLogo", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            val expected = if (sdk == 35) 0xC6FF00 else 0x34A853
            assertTrue("Original color missing for Android ${sdk - 20}", pixels.any { (it and 0xFFFFFF) == expected })
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.cacheDir, "android-artwork-${sdk - 20}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (sdk in listOf(34, 38)) {
            compose.runOnIdle { api.intValue = sdk }
            compose.onNodeWithTag("homeAndroidEasterLogo", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test fun largeTextKeepsTheLogoInsideTheRowWithoutOverlappingText() {
        compose.setContent {
            val density = LocalDensity.current
            COUITheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                    Column(Modifier.width(320.dp).testTag("androidApiCard")) { HomeAndroidApiRow("17", 37) }
                }
            }
        }
        val card = compose.onNodeWithTag("androidApiCard").getUnclippedBoundsInRoot()
        val logo = compose.onNodeWithTag("homeAndroidEasterLogo", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(logo.top >= card.top && logo.bottom <= card.bottom)
        assertEquals(card.right.value - 16f, logo.right.value, .5f)
        assertEquals((logo.right - logo.left).value, (logo.bottom - logo.top).value, .5f)
    }

    @Test fun flagsFitTheAndroidLogoSizeKeepTheirShapeAndStayRightAligned() {
        val selected = mutableIntStateOf(0)
        val flags = listOf("🇹🇼", "🇶🇦", "🇳🇵")
        var aspectRatio = 1f
        compose.setContent {
            val flag = flags[selected.intValue]
            aspectRatio = rememberCountryFlagGlyph(flag).aspectRatio
            COUITheme {
                Column(Modifier.width(360.dp)) {
                    HomeRegionRow("Country / Region", "Region $flag")
                    HomeAndroidApiRow("17", 37)
                }
            }
        }
        for (index in flags.indices) {
            compose.runOnIdle { selected.intValue = index }
            val flag = compose.onNodeWithText(flags[index], useUnmergedTree = true).getUnclippedBoundsInRoot()
            val logo = compose.onNodeWithTag("homeAndroidEasterLogo", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val width = (flag.right - flag.left).value
            val height = (flag.bottom - flag.top).value
            val edge = (logo.right - logo.left).value
            assertTrue(width <= edge + .5f && height <= edge + .5f)
            assertEquals(edge, max(width, height), .5f)
            assertEquals(logo.right.value, flag.right.value, .5f)
            assertEquals(aspectRatio, width / height, .03f)
        }
    }
}

package com.mi.onextbox.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.saveable.SaverScope
import io.github.suqi8.coui.kmp.basic.TopAppBarState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class MainPageScrollStateTest {
    private val saveScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = value is Int || value is Float
    }

    private fun state(scroll: Int, offset: Float, contentOffset: Float = -scroll.toFloat()) =
        MainPageScrollState(ScrollState(scroll), TopAppBarState(-160f, offset, contentOffset))

    private fun restore(original: MainPageScrollState): MainPageScrollState {
        val saved = with(MainPageScrollState.Saver) { saveScope.save(original) }
        return requireNotNull(MainPageScrollState.Saver.restore(requireNotNull(saved)))
    }

    @Test fun scrolledPageRestoresItsCollapsedTitleWithTheContent() {
        val original = state(2800, -160f)
        val restored = restore(original)
        assertNotSame(original, restored)
        assertEquals(2800, restored.content.value)
        assertEquals(-160f, restored.appBar.heightOffset, 0f)
        assertEquals(-2800f, restored.appBar.contentOffset, 0f)
        assertEquals(1f, restored.appBar.collapsedFraction, 0f)
    }

    @Test fun partiallyCollapsedHeaderIsNotGuessedFromAZeroListOffset() {
        val restored = restore(state(0, -60f, -15f))
        assertEquals(0, restored.content.value)
        assertEquals(-60f, restored.appBar.heightOffset, 0f)
        assertEquals(-15f, restored.appBar.contentOffset, 0f)
        assertEquals(.375f, restored.appBar.collapsedFraction, 0f)
    }

    @Test fun fullyCollapsedHeaderCanHaveNoContentScrollYet() {
        val restored = restore(state(0, -160f))
        assertEquals(0, restored.content.value)
        assertEquals(1f, restored.appBar.collapsedFraction, 0f)
    }

    @Test fun eachMainPageKeepsIndependentOffsets() {
        val home = restore(state(30, -160f))
        val features = restore(state(2800, -160f))
        val about = restore(state(0, -40f))
        home.appBar.heightOffset = 0f
        assertEquals(2800, features.content.value)
        assertEquals(-160f, features.appBar.heightOffset, 0f)
        assertEquals(-40f, about.appBar.heightOffset, 0f)
    }

    @Test fun explicitResetClearsContentAndTitleTogether() = runBlocking {
        val page = restore(state(2800, -160f))
        page.reset()
        assertEquals(0, page.content.value)
        assertEquals(0f, page.appBar.heightOffset, 0f)
        assertEquals(0f, page.appBar.contentOffset, 0f)
        assertEquals(-160f, page.appBar.heightOffsetLimit, 0f)
    }
}

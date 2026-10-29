package com.mi.onextbox.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.suqi8.coui.kmp.basic.TopAppBarState

@Stable
internal class MainPageScrollState(
    val content: ScrollState = ScrollState(0),
    val appBar: TopAppBarState = TopAppBarState(-Float.MAX_VALUE, 0f, 0f),
) {
    suspend fun reset() {
        content.scrollTo(0)
        appBar.heightOffset = 0f
        appBar.contentOffset = 0f
    }

    companion object {
        val Saver = listSaver<MainPageScrollState, Any>(
            save = { listOf(it.content.value, it.appBar.heightOffsetLimit, it.appBar.heightOffset, it.appBar.contentOffset) },
            restore = {
                MainPageScrollState(
                    ScrollState(it[0] as Int),
                    TopAppBarState(it[1] as Float, it[2] as Float, it[3] as Float),
                )
            },
        )
    }
}

@Composable
internal fun rememberMainPageScrollState(): MainPageScrollState =
    rememberSaveable(saver = MainPageScrollState.Saver) { MainPageScrollState() }

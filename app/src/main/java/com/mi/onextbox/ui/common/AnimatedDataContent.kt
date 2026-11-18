package com.mi.onextbox.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

@Composable
internal fun <T> AnimatedDataContent(
    value: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                .using(SizeTransform { _, _ -> tween(280, easing = FastOutSlowInEasing) })
        },
        label = "dataContent",
    ) { displayed ->
        Box(if (displayed == value) Modifier else Modifier.clearAndSetSemantics {}) { content(displayed) }
    }
}

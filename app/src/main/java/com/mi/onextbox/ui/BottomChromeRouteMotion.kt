package com.mi.onextbox.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

private val BottomChromeRouteCurve = CubicBezierEasing(.4f, 0f, .6f, 1f)

@Composable
internal fun rememberBottomChromeRouteVisibility(visible: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(360, easing = BottomChromeRouteCurve),
    label = "Bottom chrome route visibility",
)

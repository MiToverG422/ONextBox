package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup

internal object FeatureExpansionMotion {
    const val SizeDurationMillis = 280
    const val FadeDurationMillis = 180

    val Enter = expandVertically(
        tween(SizeDurationMillis, easing = FastOutSlowInEasing),
        expandFrom = Alignment.Top,
    ) + fadeIn(tween(FadeDurationMillis))

    val Exit = shrinkVertically(
        tween(SizeDurationMillis, easing = FastOutSlowInEasing),
        shrinkTowards = Alignment.Top,
    ) + fadeOut(tween(FadeDurationMillis))
}

internal class FeatureAnimatedGroupScope(private val retainHiddenRows: Boolean = false) {
    internal val rows = mutableListOf<Pair<Any, @Composable (Boolean, Boolean) -> Unit>>()
    internal val visibility = mutableMapOf<Any, Boolean>()

    fun row(key: Any, visible: Boolean = true, content: @Composable (above: Boolean, below: Boolean) -> Unit) {
        if (visible || retainHiddenRows) {
            rows += key to content
            visibility[key] = visible
        }
    }
}

/** One flat visible list without empty animation slots or gaps between joined rows. */
@Composable
internal fun FeatureAnimatedGroup(animateChanges: Boolean = true, content: FeatureAnimatedGroupScope.() -> Unit) {
    SettingsGroup { FeatureAnimatedRows(animateChanges = animateChanges, content = content) }
}

@Composable
internal fun FeatureAnimatedRows(
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
    animateChanges: Boolean = true,
    content: FeatureAnimatedGroupScope.() -> Unit,
) {
    val scope = FeatureAnimatedGroupScope(retainHiddenRows = animateChanges).apply(content)
    if (animateChanges) {
        FeatureTransitionRows(scope, hasDividerAbove, hasDividerBelow)
        return
    }
    val rows = scope.rows

    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        rows.forEachIndexed { index, (identity, row) ->
            key(identity) {
                val above = index > 0 || hasDividerAbove
                val below = index < rows.lastIndex || hasDividerBelow
                run {
                    row(above, below)
                    if (below) SettingsDivider()
                }
            }
        }
    }
}

/** Shared entry/exit and keyed placement animations without empty hidden slots. */
@Composable
private fun FeatureTransitionRows(scope: FeatureAnimatedGroupScope, hasDividerAbove: Boolean, hasDividerBelow: Boolean) {

    val transitions = scope.rows.map { (identity, _) ->
        key(identity) {
            remember { MutableTransitionState(scope.visibility.getValue(identity)) }.apply {
                targetState = scope.visibility.getValue(identity)
            }
        }
    }
    // Exiting content stays composed through the entire animation. Finished hidden
    // rows have no placement, segment, divider or gap in the joined card.
    val rendered = scope.rows.indices.filter { index ->
        val transition = transitions[index]
        transition.currentState || transition.targetState || !transition.isIdle
    }
    LookaheadScope {
        val lookahead = this
        Column {
            rendered.forEachIndexed { position, rowIndex ->
                val (identity, row) = scope.rows[rowIndex]
                key(identity) {
                    val above = position > 0 || hasDividerAbove
                    val below = position < rendered.lastIndex || hasDividerBelow
                    // Keep visibility outside animateBounds: its outer height must
                    // be free to collapse, not fixed by bounds-animation constraints.
                    androidx.compose.animation.AnimatedVisibility(
                        visibleState = transitions[rowIndex],
                        enter = FeatureExpansionMotion.Enter,
                        exit = FeatureExpansionMotion.Exit,
                    ) {
                        Box(Modifier.animateBounds(lookahead, boundsTransform = { _, _ -> tween(FeatureExpansionMotion.SizeDurationMillis, easing = FastOutSlowInEasing) })) {
                            Column {
                                run {
                                    row(above, below)
                                    if (below) SettingsDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

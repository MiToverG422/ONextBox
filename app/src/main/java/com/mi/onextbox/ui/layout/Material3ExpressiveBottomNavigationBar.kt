package com.mi.onextbox.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.common.BottomTab

/** The Material 3 Expressive compact navigation adapted to ONextBox's four tabs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Material3ExpressiveBottomNavigationBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
) {
    if (floating) {
        Material3ExpressiveFloatingBottomNavigationBar(tabs, selectedIndex, onTabSelected, modifier)
        return
    }
    ShortNavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) {
        tabs.forEachIndexed { index, tab ->
            ShortNavigationBarItem(
                selected = index == selectedIndex,
                onClick = { if (index != selectedIndex) onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (index == selectedIndex) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.titleRes),
                    )
                },
                label = { Text(stringResource(tab.titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun Material3ExpressiveFloatingBottomNavigationBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        .let { inset -> if (inset > 0.dp) inset + 8.dp else 16.dp }
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal),
            )
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val active = selectedIndex == index
                    val background by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        label = "floatingBottomBarBackground",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        label = "floatingBottomBarContent",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .defaultMinSize(minWidth = 48.dp)
                            .clip(CircleShape)
                            .background(background)
                            .selectable(selected = active, role = Role.Tab) {
                                if (!active) onTabSelected(index)
                            }
                            .padding(horizontal = if (active) 14.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val title = stringResource(tab.titleRes)
                        Icon(
                            imageVector = if (active) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = title,
                            tint = contentColor,
                        )
                        AnimatedVisibility(
                            visible = active,
                            enter = expandHorizontally(
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                expandFrom = Alignment.Start,
                            ) + fadeIn(tween(250)),
                            exit = shrinkHorizontally(
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Start,
                            ) + fadeOut(tween(250)),
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(start = 8.dp),
                                color = contentColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        }
    }
}

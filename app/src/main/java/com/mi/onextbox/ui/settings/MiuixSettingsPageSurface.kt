package com.mi.onextbox.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.ScrollResetEffect
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState

@Composable
internal fun MiuixSettingsPageSurface(
    title: String,
    modifier: Modifier,
    showBack: Boolean,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    bottomContentPadding: Dp,
    bottomChrome: (@Composable () -> Unit)?,
    contentScrollable: Boolean,
    edgeToEdgeBottom: Boolean,
    externalScrollState: ScrollState?,
    scrollResetKey: Int?,
    backgroundContent: (@Composable BoxScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = externalScrollState ?: rememberScrollState()
    val barState = rememberTopAppBarState()
    val behavior = MiuixScrollBehavior(state = barState)
    ScrollResetEffect(scrollResetKey) {
        scroll.scrollTo(0)
        barState.heightOffset = 0f
        barState.contentOffset = 0f
    }
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            if (showBack) {
                SmallTopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = { onBack?.invoke() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.onboarding_back), modifier = Modifier.size(24.dp))
                        }
                    },
                    actions = actions,
                    scrollBehavior = behavior,
                )
            } else TopAppBar(title = title, actions = actions, scrollBehavior = behavior)
        },
        bottomBar = { bottomChrome?.invoke() },
    ) { inner ->
        val direction = LocalLayoutDirection.current
        Box(
            Modifier.fillMaxSize()
                .padding(start = inner.calculateStartPadding(direction), end = inner.calculateEndPadding(direction))
                .consumeWindowInsets(if (edgeToEdgeBottom) PaddingValues(top = inner.calculateTopPadding()) else inner),
            contentAlignment = Alignment.TopCenter,
        ) {
            backgroundContent?.invoke(this)
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxSize()
                    .then(if (contentScrollable) Modifier.verticalScroll(scroll) else Modifier)
                    .imePadding()
                    .padding(
                        top = inner.calculateTopPadding() + 12.dp,
                        bottom = if (edgeToEdgeBottom) bottomContentPadding else maxOf(inner.calculateBottomPadding(), bottomContentPadding) + 12.dp,
                    ),
                content = content,
            )
        }
    }
}

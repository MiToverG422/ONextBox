package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.key as contentKey
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.colorOsScrollEntrance
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.LocalContentColor

/** Joined list cards with independent scroll entry motion. */
@Composable
internal fun <T> SettingsListGroup(
    items: List<T>,
    key: (T) -> Any,
    bottomPadding: Dp = 16.dp,
    content: @Composable ColumnScope.(index: Int, item: T) -> Unit,
) {
    if (items.isEmpty()) return
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        SettingsGroup(bottomPadding = bottomPadding) {
            items.forEachIndexed { index, item ->
                contentKey(key(item)) { content(index, item) }
            }
        }
        return
    }

    val groupIdentity = remember { Any() }
    val colors = CardDefaults.defaultColors()
    CompositionLocalProvider(LocalContentColor provides colors.contentColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomPadding)
                .semantics { isTraversalGroup = true },
        ) {
            items.forEachIndexed { index, item ->
                val itemKey = key(item)
                contentKey(itemKey) {
                    val entranceKey = remember(groupIdentity, itemKey) { groupIdentity to itemKey }
                    val top = if (index == 0) AppUiTokens.CardCornerRadius else 0.dp
                    val bottom = if (index == items.lastIndex) AppUiTokens.CardCornerRadius else 0.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .colorOsScrollEntrance(key = entranceKey)
                            .squircleSurface(colors.color, top, top, bottom, bottom),
                    ) {
                        content(index, item)
                        if (index < items.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}

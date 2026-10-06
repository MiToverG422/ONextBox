package com.mi.onextbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Consume each explicit reset once, including when a NavEntry/pager page is restored. */
@Composable
internal fun ScrollResetEffect(resetKey: Int?, resetScroll: suspend () -> Unit) {
    // Do not key rememberSaveable by resetKey: that would discard the last consumed
    // request. Save it beside ScrollState and the app-bar state in the same entry.
    var appliedResetKey by rememberSaveable { mutableStateOf(resetKey) }
    LaunchedEffect(resetKey) {
        if (shouldResetScroll(appliedResetKey, resetKey)) resetScroll()
        appliedResetKey = resetKey
    }
}

internal fun shouldResetScroll(appliedResetKey: Int?, requestedResetKey: Int?): Boolean =
    requestedResetKey != null && requestedResetKey != appliedResetKey

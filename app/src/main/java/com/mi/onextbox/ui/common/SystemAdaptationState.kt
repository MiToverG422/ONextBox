package com.mi.onextbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mi.onextbox.lsp.SystemAdaptationDetector
import com.mi.onextbox.lsp.SystemAdaptationVersion

@Composable
internal fun rememberSystemAdaptationVersion(): SystemAdaptationVersion {
    return remember { SystemAdaptationDetector.current() }
}

package com.mi.onextbox.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AppLaunchRevealState(restored: Boolean) {
    var ready by mutableStateOf(restored)
        private set
    var animate by mutableStateOf(!restored)
        private set
    private var exitStarted = false
    private var finished = false

    fun onExitStarted() {
        if (finished) return
        exitStarted = true
        ready = true
    }

    fun onFinished() {
        if (finished) return
        finished = true
        if (!exitStarted) animate = false
        ready = true
    }
}

package com.mi.onextbox.touch

data class TouchRatePreset(
    val index: Int,
    val hz: Int?,
    val chipValue: Int,
    val isIstMode: Boolean,
)

data class TouchSamplingState(
    val available: Boolean = false,
    val canWrite: Boolean = false,
    val presets: List<TouchRatePreset> = emptyList(),
    val defaultChipValue: Int? = null,
    val currentIndex: Int? = null,
    val currentChipValue: Int? = null,
    val overrideEnabled: Boolean = false,
    val error: String? = null,
    val diagnostic: String? = null,
    val backendId: String? = null,
    val backendLabel: String? = null,
    val configSource: String? = null,
)

data class TouchRateApplyResult(
    val accepted: Boolean,
    val selected: TouchRatePreset?,
    val state: TouchSamplingState,
)

data class TouchRateToggleResult(
    val accepted: Boolean,
    val needsPreset: Boolean,
    val state: TouchSamplingState,
)

package com.mi.onextbox.lsp

/** C16 16.99.12 / QS 16.001.002 adapters, independently isolated by feature group. */
internal object ColorOs16SystemUiFeatureHooks {
    private const val TAG = "ONextBox-C16SystemUI"

    fun hook(loader: ClassLoader) {
        install("large-network") { LargeNetworkTypeHooker.hookColorOs16(loader) }
        install("data-sim") { DataSimSignalHooker.hook(loader) }
        install("statusbar-layout") { StatusBarLayoutHooker.hookColorOs16(loader) }
        install("power-indicator") { PowerIndicatorHooker.hook(loader) }
        install("clock-period") { ClockPeriodHooker.hook(loader) }
        install("clock-spacing") { ClockSpacingHooker.hook(loader) }
        install("network-speed") { NetworkSpeedHooker.hook(loader) }
        install("carrier-name") { CarrierNameHooker.hook(loader) }
        install("qs-corners") { QsTileCornerHooker.hook(loader) }
        install("qs-percentage") { QsSliderPercentageHooker.hook(loader) }
        install("volume-percentage") { SideVolumePercentageHooker.hookColorOs16(loader) }
        install("volume-appearance") { SideVolumeAppearanceHooker.hook(loader) }
        install("volume-safety") { VolumeSafetyWarningHooker.hook(loader) }
        install("navigation") { ImmersiveNavigationHooker.hook(loader) }
        install("assistant-power") { AssistantHooker.hook("com.android.systemui", loader) }
        install("presentation") { SystemUiHooker.hookColorOs16(loader) }
        install("fluid-battery") { FluidCloudBatteryHooker.hook(loader) }
        install("fluid-media") { FluidCloudMediaBlacklistHooker.hook(loader) }
        install("notification-removal") { NotificationRemovalHooker.hookSystemUi(loader) }
        install("small-window") { SmallWindowHooker.hookSystemUi(loader) }
        install("face-tap") { FaceTapUnlockHooker.hook(loader) }
        install("recorder-tile") { KeyguardCaptureHooker.hookSystemUi(loader) }
    }

    fun hookScreenshot(loader: ClassLoader) {
        install("aod-screenshot") { AodScreenshotHooker.hookColorOs16(loader) }
        install("exclude-floating") { HideScreenshotFloatingHooker.hookColorOs16(loader) }
    }

    fun hookRecorder(loader: ClassLoader) {
        install("screen-off-recording") { KeyguardCaptureHooker.hookColorOs16Recorder(loader) }
    }

    private inline fun install(group: String, action: () -> Unit) {
        runCatching(action).onFailure { HookLog.w(TAG, "$group unavailable; stock handling retained", it) }
    }
}

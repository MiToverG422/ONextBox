package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QsControlCenterCornerContractTest {
    private fun source(name: String) = listOf(File("src/main/java/com/mi/onextbox/lsp/$name.kt"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name.kt")).first { it.isFile }.readText()

    @Test fun mediaBackgroundAndItsIndependentLightBorderCacheFollowTheSamePath() {
        val hook = source("QsTileCornerHooker")
        assertTrue(hook.contains("com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView"))
        assertTrue(hook.contains("com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable"))
        assertTrue(hook.contains("\"getViewRadius\", \"getContainerCornerRadius\""))
        assertTrue(hook.contains("\"\$TAG:media-draw\""))
        assertTrue(hook.contains("syncMediaLight(drawable, accepted)"))
        assertTrue(hook.contains("syncMediaLight(drawable, state.original)"))
        assertTrue(hook.contains("val effective = Reflect.callMethod(drawable, \"getPathProvider\") ?: provider"))
        assertTrue(hook.contains("\"setCornerParams\", radius, weight"))
        assertFalse(hook.contains("setBackgroundColor"))
    }
    @Test fun sliderUpdatesUseNativeSetterAndNeverChangeSystemValuesOrGlobalResources() {
        val slider = source("QsControlCenterSliderCorners")
        assertTrue(source("QsTileCornerHooker").contains("QsControlCenterSliderCorners.hook(loader)"))
        assertTrue(slider.contains("QsControlCenterCornerTargets.isSlider(view.javaClass.name)"))
        assertTrue(slider.contains("\"setCornerRadius\", radius"))
        assertTrue(slider.contains("scope.paint(paintedProgress, paintedBackground)"))
        assertTrue(slider.contains("QsControlCenterCornerTargets.SEEK_BAR"))
        assertTrue(slider.contains("ModernHookRegistry.installFast(\"\$TAG:seek-draw\""))
        assertTrue(slider.contains("radius == previous"))
        for (unsafe in listOf("setProgress", "setVolume", "AudioManager", "Settings.System.put", "Resources::class", "View::class.java", "setStatic", "getStatic")) {
            assertFalse(unsafe, slider.contains(unsafe))
        }
    }
    @Test fun sharedSmoothRoundParametersAreCopiedNotModified() {
        val slider = source("QsControlCenterSliderCorners")
        val round = slider.substringAfter("private fun applyVisualRadius").substringBefore("private fun restore")
        assertTrue(round.contains("Reflect.callMethod(original, \"copy\", radiusDp, radius, weight, false)"))
        assertFalse(round.contains("\"setRadius\""))
        assertFalse(round.contains("\"setWeight\""))
        assertFalse(round.contains("setObjectField"))
        assertTrue(round.contains("finally"))
    }
    @Test fun constructionKeepsNativeCachedValuesAndRetirementRestoresCurrentTheme() {
        val slider = source("QsControlCenterSliderCorners")
        assertTrue(slider.contains("if (view.width <= 0 || view.height <= 0) return null"))
        assertTrue(slider.contains("WeakHashMap<View, Float>()"))
        assertTrue(slider.contains("HotReloadLifecycle.register(\"qs:slider-corners\", detach ="))
        assertTrue(slider.contains("nativeRadius.set(true)"))
        assertTrue(slider.contains("Reflect.callMethod(view, \"getRadius\")"))
        assertTrue(slider.contains("if (previous == null) nativeRadius.remove() else nativeRadius.set(previous)"))
        assertTrue(slider.contains("applied.remove(view)"))
        assertTrue(slider.contains("Reflect.setObjectField(seek, name, value)"))
    }
    @Test fun percentageSettingsKeepTheToggleAndUseNewKeysWithSystemDefaults() {
        val settings = source("QsTileCornerSettings")
        assertTrue(settings.contains("const val ENABLED = \"systemui_qs_tile_corner_enabled\""))
        assertFalse(settings.contains("const val RADIUS = \"systemui_qs_tile_corner_radius\""))
        assertTrue(settings.contains("fun radiusXposed(size: Size)"))
        assertTrue(settings.contains("const val SYSTEM_DEFAULT = -1"))
        assertTrue(settings.contains("const val MAX_PERCENT = 100"))
        assertTrue(settings.contains("systemui_qs_tile_corner_radius_percent_"))
        assertTrue(settings.contains("QsTileCornerRules.customPercent("))
    }
    @Test fun sliderVisualChangesCannotMoveTheNativeTrackOrChangeTouchPadding() {
        val slider = source("QsControlCenterSliderCorners")
        val visual = slider.substringAfter("private fun applyVisualRadius").substringBefore("private fun restore")
        assertFalse(visual.contains("\"setCornerRadius\""))
        assertFalse(slider.contains("\"setPaddingVertical\""))
        assertFalse(slider.contains("\"setTransitionRadius\""))
        assertFalse(visual.contains("setObjectField"))
        assertFalse(visual.contains("\"setBackgroundRadius\""))
        assertFalse(slider.contains("it.name in setOf(\"getRadius\""))
        assertTrue(slider.contains("it.name == \"getViewRadius\""))
        assertTrue(visual.contains("\"getOnRadiusUpdateListener\""))
        assertTrue(visual.contains("\"updateBaseMixColorDrawableRadius\", radius"))
    }
    @Test fun detailSliderUsesVisualTransitionsWithoutOverridingNativeAnimationEndpoints() {
        val slider = source("QsControlCenterSliderCorners")
        assertTrue(slider.contains("QsControlCenterCornerTargets.sliders.forEach"))
        assertTrue(slider.contains("owner?.let(::paintRadius)"))
        assertTrue(slider.contains("it.name == \"beginTransition\""))
        assertTrue(slider.contains("QsSliderDetailCornerTransition.create"))
        assertTrue(slider.contains("detailTransitions.remove(view)"))
        val transition = slider.substringAfter("private fun hookDetailTransitions")
            .substringBefore("private fun detailScale")
        assertFalse(transition.contains("param.result ="))
        assertFalse(transition.contains("param.args[1] ="))
        assertFalse(transition.contains("param.args[2] ="))
        assertFalse(transition.contains("setObjectField"))
    }
    @Test fun scenarioTilesAreAdaptedWithoutChangingDetailPopupTilesOrTheirInnerIcons() {
        val hook = source("QsTileCornerHooker")
        assertTrue(hook.contains("com.oplus.systemui.qs.scenario.ui.template.ScenarioTileViewBase"))
        assertTrue(hook.contains("ScenarioTileViewOneXOne"))
        assertTrue(hook.contains("ScenarioTileViewTwoXOne"))
        assertTrue(hook.contains("ScenarioTileViewTwoXTwo"))
        assertTrue(hook.contains("ScenarioTileViewFourXOne"))
        assertTrue(hook.contains("scenarioWide -> Reflect.callMethod(view, \"getHighLightDrawableBg\")"))
        assertTrue(hook.contains("scenarioWide -> Reflect.callMethod(view, \"getTileBg\")"))
        assertFalse(hook.contains("findClassIfExists(\"com.oplus.systemui.qs.slider.detail.widget.QsDetailTileBaseView\""))
    }
    @Test fun mediaRetainsNativeClipFlagsAndUsesTheDrawableOutline() {
        val hook = source("QsTileCornerHooker")
        assertTrue(hook.contains("view.outlineProvider = custom"))
        assertFalse(hook.contains("view.clipToOutline = true"))
        assertTrue(hook.contains("if (!view.clipToOutline) return"))
        assertTrue(hook.contains("if (view.outlineProvider === previous.custom)"))
        assertTrue(hook.contains("view.outlineProvider = previous.original"))
        assertTrue(hook.contains("view.clipToOutline = previous.clipped"))
        assertTrue(hook.contains("updateMediaClip(view, drawable)"))
        assertTrue(hook.contains("QsCornerDrawableOutline(drawable)"))
        val clip = source("QsCornerDrawableOutline")
        assertTrue(clip.contains("\"getPathProvider\""))
        assertTrue(clip.contains("provider.getOutline(view, outline)"))
        assertFalse(clip.contains("addRoundRect"))
    }
    @Test fun liveViewCallbacksAreWeakOwnedAndDetachedBeforeModuleRetirement() {
        val observers = source("QsCornerViewObservers")
        assertTrue(observers.contains("WeakHashMap<View, Guard>()"))
        assertTrue(observers.contains("private val target = WeakReference(view)"))
        assertTrue(observers.contains("WeakReference<ViewTreeObserver>"))
        assertTrue(observers.contains("removeOnPreDrawListener(this)"))
        assertTrue(observers.contains("removeOnAttachStateChangeListener(this)"))
        assertTrue(observers.contains("HotReloadLifecycle.withHook(fallback = { true })"))
        assertTrue(observers.contains("HotReloadLifecycle.registerReplay"))
        assertTrue(observers.contains("guards.keys.toTypedArray()"))
        assertTrue(source("QsTileCornerHooker").contains("observers::watch"))
        assertTrue(source("QsControlCenterSliderCorners").contains("observers::watch"))
    }
    @Test fun initialAndRebuiltLayoutsRepropagateCornersWithoutAFrameByFramePathReset() {
        val observers = source("QsCornerViewObservers")
        assertTrue(observers.contains("View.OnLayoutChangeListener"))
        assertFalse(observers.contains("OnGlobalLayoutListener"))
        assertTrue(observers.contains("removeOnLayoutChangeListener(this)"))
        assertTrue(observers.contains("gate.changed(state)"))
        assertTrue(source("QsTileCornerHooker").contains("onLayout = { updateSafely(it, provider, refreshChildren = true) }"))
    }
    @Test fun actualMediaSubclassesAreObservedEvenWhenNativeFactoriesInlineConstructors() {
        val hook = source("QsTileCornerHooker")
        assertTrue(hook.contains("QsCornerHierarchyObservers(\"qs:tile-discovery\")"))
        assertTrue(hook.contains(".any { it.name in types }"))
        assertTrue(hook.contains("observers.watch(view)"))
        assertTrue(hook.contains("com.oplus.systemui.plugins.qs.OplusQSRootView"))
    }

    @Test fun observersCommitOnlySuccessfulUpdatesAndRetryIdleInitialLayouts() {
        val observer = source("QsCornerViewObservers")
        assertTrue(observer.contains("if (success)"))
        assertTrue(observer.contains("gate.applied(state.copy(content = after))"))
        assertTrue(observer.contains("view.postDelayed(retry, 250L)"))
        assertTrue(observer.contains("if (retries < 8)"))
        assertTrue(observer.contains("view.removeCallbacks(retry); disarm()"))
        for (name in listOf("QsTileCornerHooker", "QsDeviceCardCorners", "QsControlCenterSliderCorners")) {
            assertTrue(name, source(name).contains("content = ::contentState"))
            assertTrue(name, source(name).contains(".getOrDefault(false)"))
        }
        val tile = source("QsTileCornerHooker")
        assertTrue(tile.contains("System.identityHashCode(target.drawable)"))
        assertTrue(tile.contains("bg.width, bg.height, System.identityHashCode(path)"))
        assertTrue(tile.contains("if (bg.width <= 0 || bg.height <= 0) return null"))
    }

    @Test fun cheapScheduleRunsBeforeAnyDeepInspectionAndNativeEventsMarkDirty() {
        val observer = source("QsCornerViewObservers")
        assertTrue(observer.indexOf("schedule.shouldProbe") < observer.indexOf("val snapshot ="))
        assertTrue(observer.contains("schedule.invalidate()"))
        for (name in listOf("QsTileCornerHooker", "QsDeviceCardCorners", "QsControlCenterSliderCorners"))
            assertTrue(name, source(name).contains("observers::invalidate") || source(name).contains("observers.invalidate(view)"))
        val slider = source("QsControlCenterSliderCorners")
        assertTrue(slider.contains("state.sameMaterial(last)"))
        val cornerOnly = slider.substringAfter("private fun updateMaterialCorner").substringBefore("private fun configureMaterial")
        assertFalse(cornerOnly.contains("updateBaseMixColorDrawableRadius"))
        assertTrue(cornerOnly.contains("getGradientStrokeCornerParam"))
        assertTrue(cornerOnly.contains("applyBlurConfig"))
    }
}

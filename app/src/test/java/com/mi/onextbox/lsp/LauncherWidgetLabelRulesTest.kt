package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherWidgetLabelRulesTest {
    @Test
    fun oplusCardTitlesRemainSupported() {
        for (name in listOf(
            "com.android.launcher3.card.LauncherCardView",
            "com.android.launcher3.card.TitleCardView",
            "com.android.launcher3.card.groupcard.GroupCardView",
        )) {
            assertTrue(LauncherWidgetLabelRules.isTitleOwner(sequenceOf(name)))
        }
    }

    @Test
    fun nativeAppWidgetHostsAreRecognizedThroughTheirBaseClass() {
        for (name in listOf(
            "com.android.launcher3.widget.LauncherAppWidgetHostView",
            "com.android.launcher3.widget.CustomLauncherAppWidgetHostView",
            "com.android.launcher3.widget.PendingAppWidgetHostView",
        )) {
            assertTrue(LauncherWidgetLabelRules.isTitleOwner(sequenceOf(
                name, LauncherWidgetLabelRules.BASE_WIDGET_HOST, "android.appwidget.AppWidgetHostView",
            )))
        }
    }

    @Test
    fun widgetHostSubclassesDoNotDependOnTheirPackageName() {
        assertTrue(LauncherWidgetLabelRules.isTitleOwner(sequenceOf(
            "com.android.launcher.widget.OplusUserLockedAppWidgetHostView",
            LauncherWidgetLabelRules.BASE_WIDGET_HOST,
        )))
    }

    @Test
    fun ordinaryAppIconsAndTextViewsAreNotTitleOwners() {
        for (name in listOf(
            "com.android.launcher3.BubbleTextView",
            "com.android.launcher3.OplusBubbleTextView",
            "com.android.launcher3.ShortcutContainer",
            "com.android.launcher3.folder.FolderIcon",
            "android.widget.TextView",
            "android.widget.FrameLayout",
        )) {
            assertFalse(LauncherWidgetLabelRules.isTitleOwner(sequenceOf(name)))
        }
    }

    @Test
    fun widgetPickerAndRemoteViewsAreNotMatchedByPackageAlone() {
        for (name in listOf(
            "com.android.launcher3.widget.WidgetCell",
            "com.android.launcher3.widget.picker.WidgetsListHeader",
            "com.android.launcher3.widget.utils.WidgetNameHelper",
            "com.android.launcher3.widget.BaseLauncherAppWidgetHostViewPreview",
            "com.example.widget.ClockView",
        )) {
            assertFalse(LauncherWidgetLabelRules.isTitleOwner(sequenceOf(name, "android.view.View")))
        }
        assertFalse(LauncherWidgetLabelRules.isTitleOwner(emptySequence()))
    }
}

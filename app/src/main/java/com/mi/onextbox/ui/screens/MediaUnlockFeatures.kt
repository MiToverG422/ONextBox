package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.MediaUnlockCatalog
import com.mi.onextbox.lsp.MediaUnlockSettings
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow

internal enum class MediaUnlockPage(val app: MediaUnlockCatalog.App, val group: MediaUnlockCatalog.Group?, val mode: FeaturePageMode) {
    CameraGeneral(MediaUnlockCatalog.App.Camera, null, FeaturePageMode.CameraGeneral),
    CameraModes(MediaUnlockCatalog.App.Camera, MediaUnlockCatalog.Group.Modes, FeaturePageMode.CameraModes),
    CameraControls(MediaUnlockCatalog.App.Camera, MediaUnlockCatalog.Group.Controls, FeaturePageMode.CameraControls),
    CameraFilters(MediaUnlockCatalog.App.Camera, MediaUnlockCatalog.Group.Filters, FeaturePageMode.CameraFilters),
    CameraWatermarks(MediaUnlockCatalog.App.Camera, MediaUnlockCatalog.Group.Watermarks, FeaturePageMode.CameraWatermarks),
    CameraLive(MediaUnlockCatalog.App.Camera, MediaUnlockCatalog.Group.Live, FeaturePageMode.CameraLive),
    GalleryGeneral(MediaUnlockCatalog.App.Gallery, null, FeaturePageMode.GalleryGeneral),
    GalleryFilters(MediaUnlockCatalog.App.Gallery, MediaUnlockCatalog.Group.Filters, FeaturePageMode.GalleryFilters),
    GalleryWatermarks(MediaUnlockCatalog.App.Gallery, MediaUnlockCatalog.Group.Watermarks, FeaturePageMode.GalleryWatermarks),
    GalleryEditing(MediaUnlockCatalog.App.Gallery, MediaUnlockCatalog.Group.Editing, FeaturePageMode.GalleryEditing),
    GalleryLive(MediaUnlockCatalog.App.Gallery, MediaUnlockCatalog.Group.Live, FeaturePageMode.GalleryLive),
    GalleryAI(MediaUnlockCatalog.App.Gallery, MediaUnlockCatalog.Group.AI, FeaturePageMode.GalleryAI);

    val title: Int get() = group?.title ?: R.string.media_unlock_group_general
    companion object {
        val byMode = entries.associateBy { it.mode }
        fun forOption(option: MediaUnlockCatalog.Option) = entries.single { it.app == option.app && it.group == option.group }
    }
}

internal object MediaUnlockFeatureDefinitions {
    val all = MediaUnlockCatalog.options.map { option -> SharedFeatureDefinitions.Toggle(
        option.title, 0, MediaUnlockPage.forOption(option).mode,
        { MediaUnlockSettings.enabled(it, option.key) },
        { context, enabled -> MediaUnlockSettings.set(context, option.key, enabled) },
        preferenceKey = option.key,
    ) }
}

@Composable
internal fun CameraFeaturesPage(onOpen: (FeaturePageMode) -> Unit) =
    MediaCategoriesPage(MediaUnlockCatalog.App.Camera, onOpen)

@Composable
internal fun MediaUnlockSettingsPage(mode: FeaturePageMode) {
    val page = MediaUnlockPage.byMode.getValue(mode)
    if (page == MediaUnlockPage.CameraGeneral) { CameraGeneralPage(); return }
    if (page == MediaUnlockPage.GalleryGeneral) { GalleryGeneralPage(); return }
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(MediaUnlockCatalog.keys, MediaUnlockSettings::state)
    val options = (MediaUnlockCatalog.byApp.getValue(page.app).filter { it.group == page.group } +
        if (page == MediaUnlockPage.CameraWatermarks) MediaUnlockCatalog.sharedStyleOptions
        else if (page == MediaUnlockPage.GalleryWatermarks) MediaUnlockCatalog.options.filter { it.key == com.mi.onextbox.lsp.MediaWatermarkSettings.ENABLED }
        else emptyList()).filter { it.key != com.mi.onextbox.lsp.MediaWatermarkSettings.ENABLED }
    SettingsGroup {
        val aiRetouch = page == MediaUnlockPage.GalleryAI
        if (aiRetouch) {
            GalleryAiRetouchRow(hasDividerBelow = options.isNotEmpty())
            if (options.isNotEmpty()) SettingsDivider()
        }
        options.forEachIndexed { index, option ->
            if (index > 0) SettingsDivider()
            val missingScene = option.key == com.mi.onextbox.lsp.CameraAiCompositionHooker.SCENE &&
                com.mi.onextbox.lsp.CameraCompositionSupport.missingSceneEngine
            SettingsToggleRow(title = stringResource(option.title),
                summary = if (missingScene) stringResource(R.string.camera_composition_scene_missing) else "",
                checked = editor.state[option.key] == true, interactionEnabled = editor.savingKey == null && !missingScene,
                hasDividerAbove = aiRetouch || index > 0, hasDividerBelow = index < options.lastIndex,
                onCheckedChange = { enabled ->
                    if (enabled && option.key in com.mi.onextbox.lsp.CameraPaletteSupport.options && !com.mi.onextbox.lsp.CameraPaletteSupport.available())
                        android.widget.Toast.makeText(context, R.string.camera_palette_resources_missing, android.widget.Toast.LENGTH_LONG).show()
                    else editor.save(option.key, editor.state + (option.key to enabled)) {
                            MediaUnlockSettings.set(context, option.key, enabled)
                        }
                })
        }
    }
    if (page.group == MediaUnlockCatalog.Group.Watermarks) MediaWatermarkSettingsGroup(page.app == MediaUnlockCatalog.App.Camera)
}

package com.mi.onextbox.ui.screens

import android.graphics.ImageDecoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import com.mi.onextbox.lsp.ArtworkTarget
import com.mi.onextbox.lsp.OtaArtwork
import com.mi.onextbox.lsp.OtaArtworkView
import com.mi.onextbox.lsp.OtaCrop
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.platform.findActivity
import com.mi.onextbox.ui.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun OtaArtworkSettings(target: ArtworkTarget = ArtworkTarget.OTA, preferenceKey: String = target.key) {
    val wallet = target == ArtworkTarget.WALLET
    val context = LocalContext.current
    val prefs = remember(context) { LspPreferenceStore.prefs(context) }
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(OtaArtwork.parse(prefs.getString(preferenceKey, null))) }
    val previous = remember { if (wallet) OtaArtwork.parse(prefs.getString(target.key, null)) else null }
    var hide by remember { mutableStateOf(prefs.getBoolean(OtaArtwork.HIDE, false)) }
    var busy by remember { mutableStateOf(false) }
    var editorPath by rememberSaveable { mutableStateOf<String?>(null) }
    var closingPath by remember { mutableStateOf<String?>(null) }
    var staging by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var x by rememberSaveable { mutableFloatStateOf(.5f) }
    var y by rememberSaveable { mutableFloatStateOf(.5f) }

    fun edit(file: File, temporary: Boolean, crop: OtaCrop) {
        staging = temporary
        zoom = crop.zoom
        x = crop.x
        y = crop.y
        editorPath = file.path
    }
    fun close() {
        closingPath = editorPath ?: return
        editorPath = null
    }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(context, R.string.ota_art_error, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) perform {
            var staged: File? = null
            try {
                withContext(Dispatchers.IO) { staged = OtaArtwork.stage(context, uri) }
                edit(requireNotNull(staged), true, OtaCrop())
                staged = null
            } finally { staged?.delete() }
        }
    }
    if (!wallet) SettingsSection(stringResource(R.string.ota_art_section))
    SettingsGroup {
        SettingsCardRow(stringResource(if (wallet) R.string.wallet_choose else R.string.ota_art_choose), "", showArrow = true, enabled = !busy,
            hasDividerBelow = true, onClick = { picker.launch("image/*") })
        if (previous != null) {
            SettingsDivider()
            SettingsCardRow(stringResource(R.string.wallet_previous_art), "", enabled = !busy, onClick = {
                perform {
                    saved = withContext(Dispatchers.IO) { OtaArtwork.save(context, OtaArtwork.file(context, previous.id, target),
                        null, previous.crop, target, preferenceKey) }
                }
            })
        }
        FeatureExpandableVisibility(visible = saved != null) {
            Column {
                SettingsDivider()
                SettingsCardRow(stringResource(R.string.ota_art_crop), "", showArrow = true,
                    enabled = !busy && saved != null, hasDividerAbove = true, hasDividerBelow = true,
                    onClick = { saved?.let { edit(OtaArtwork.file(context, it.id, target), false, it.crop) } })
                SettingsDivider()
                SettingsCardRow(stringResource(R.string.ota_art_restore), "", enabled = !busy && saved != null,
                    hasDividerAbove = true, hasDividerBelow = true, onClick = {
                        perform { saved = withContext(Dispatchers.IO) { OtaArtwork.save(context, null, null, OtaCrop(), target, preferenceKey) } }
                    })
            }
        }
        if (!wallet) {
            SettingsDivider()
            SettingsToggleRow(title = stringResource(R.string.ota_art_hide), summary = "", checked = hide,
            hasDividerAbove = true, interactionEnabled = !busy, onCheckedChange = { value -> perform {
                withContext(Dispatchers.IO) { ModernXposedPreferenceSync.setBooleanAndSync(context, OtaArtwork.HIDE, value) }
                hide = value
            } })
        }
    }

    val path = editorPath ?: closingPath ?: return
    DisposableEffect(path, staging) {
        val temporary = staging
        onDispose {
            if (temporary && (editorPath != path || context.findActivity()?.isChangingConfigurations != true)) File(path).delete()
        }
    }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val classic = LspConfig.isSettingsC15AboutLayoutEnabled(context)
    val cardRatio = remember(context, screenWidth, classic) {
        val classicRatio = (screenWidth - 32).coerceAtLeast(1) / 234f
        if (wallet) 1.586f else if (classic) classicRatio else runCatching {
            val host = context.packageManager.getResourcesForApplication("com.android.settings")
            fun dimension(name: String) = host.getDimension(host.getIdentifier(name, "dimen", "com.android.settings")) / host.displayMetrics.density
            (screenWidth - dimension("preference_card_margin_start") - dimension("preference_card_margin_end")) /
                dimension("about_device_ota_item_height")
        }.getOrDefault(classicRatio).coerceAtLeast(.1f)
    }
    val artwork = produceState<android.graphics.drawable.Drawable?>(null, path) {
        try {
            value = withContext(Dispatchers.IO) { OtaArtwork.decode(ImageDecoder.createSource(File(path)), 960) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            Toast.makeText(context, R.string.ota_art_error, Toast.LENGTH_LONG).show()
            close()
        }
    }
    var preview by remember(path) { mutableStateOf<OtaArtworkView?>(null) }
    CouiConfirmDialog(editorPath != null, stringResource(R.string.ota_art_crop), stringResource(R.string.ota_art_help),
        stringResource(android.R.string.cancel), stringResource(android.R.string.ok),
        onDismissRequest = { if (!busy) close() }, onDismissFinished = { closingPath = null }, onPositive = {
            if (editorPath != null && artwork.value != null) perform {
                val original = if (staging) File(path) else null
                val crop = OtaCrop(x, y, zoom)
                saved = withContext(Dispatchers.IO) { OtaArtwork.save(context, original, saved, crop, target, preferenceKey) }
                close()
            }
        }, content = {
            AndroidView(factory = { OtaArtworkView(it).also { view -> preview = view } },
                modifier = Modifier.fillMaxWidth().aspectRatio(cardRatio).pointerInput(path) {
                    detectTransformGestures { center, pan, delta, _ ->
                        val view = preview ?: return@detectTransformGestures
                        val d = view.drawable ?: return@detectTransformGestures
                        val next = (zoom * delta).coerceIn(1f, 4f)
                        val ratio = next / zoom
                        val scale = view.displayScale(zoom)
                        fun adjust(value: Float, extent: Int, source: Int, focus: Float, motion: Float): Float {
                            val oldExtra = (source * scale - extent).coerceAtLeast(0f)
                            val newExtra = source * scale * ratio - extent
                            if (newExtra <= 0f) return .5f
                            return ((value * oldExtra * ratio + focus * (ratio - 1f) - motion) / newExtra).coerceIn(0f, 1f)
                        }
                        x = adjust(x, size.width, d.intrinsicWidth, center.x, pan.x)
                        y = adjust(y, size.height, d.intrinsicHeight, center.y, pan.y)
                        zoom = next
                    }
                }, update = { view ->
                    if (view.drawable !== artwork.value) view.setImageDrawable(artwork.value)
                    view.crop = OtaCrop(x, y, zoom)
                })
        })
}

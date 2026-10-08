package com.mi.onextbox.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppIcons
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.ConfigBackup
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.common.ColorOsPopup
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.ONextBoxLogo
import com.mi.onextbox.ui.common.ONextBoxLogoFontFamily
import com.mi.onextbox.ui.common.isMonet
import com.mi.onextbox.ui.onboarding.OnboardingPreferences
import com.mi.onextbox.ui.home.rememberDeviceMarketName
import com.mi.onextbox.ui.home.rememberAppVersionName
import com.mi.onextbox.ui.platform.findActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text as MaterialText
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.squircle.squircleBackground
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ThemeColorSpec
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Locale

private const val COOLAPK_PROFILE_URL = "https://www.coolapk.com/u/29184225"
private const val GITHUB_PROFILE_URL = "https://github.com/MiToverG422"
private const val SUQI8_PROFILE_URL = "https://github.com/suqi8"
private const val COLORLARIS_PROFILE_URL = "https://github.com/Colorlaris"
private const val ROVE24_PROFILE_URL = "https://github.com/Rove24"
private const val GITHUB_REPOSITORY_URL = "https://github.com/MiToverG422/ONextBox"
private const val TELEGRAM_CHANNEL_URL = "https://t.me/ONextBox"
private const val SOFTWARE_UPDATE_CHECK_DELAY_MS = 2_000L
private val SoftwareUpdateContentHorizontalPadding = 16.dp
private val SoftwareUpdateVersionInfoExtraHorizontalPadding = 20.dp
private const val MITOVERG_AVATAR_URL = "https://github.com/MiToverG422.png?size=160"
private const val COLORLARIS_AVATAR_URL = "https://github.com/Colorlaris.png?size=160"
private const val ROVE24_AVATAR_URL = "https://github.com/Rove24.png?size=160"
private const val COUI_REPOSITORY_URL = "https://github.com/suqi8/coui"
private const val MIUIX_REPOSITORY_URL = "https://github.com/compose-miuix-ui/miuix"
private const val OSHIN_REPOSITORY_URL = "https://github.com/suqi8/OShin"
private const val LIBSU_REPOSITORY_URL = "https://github.com/topjohnwu/libsu"
private const val LUCKYTOOL_REPOSITORY_URL = "https://github.com/luckyzyx/LuckyTool"
private const val INXLOCKER_REPOSITORY_URL = "https://github.com/Chimioo/InxLocker"
private const val HIDDEN_API_BYPASS_REPOSITORY_URL = "https://github.com/LSPosed/AndroidHiddenApiBypass"
private const val JETPACK_COMPOSE_URL = "https://developer.android.com/jetpack/compose"
private const val ANDROIDX_URL = "https://developer.android.com/jetpack/androidx"
private const val ANDROIDX_PALETTE_URL = "https://developer.android.com/develop/ui/views/graphics/palette-colors"
private const val MATERIAL_ICONS_URL = "https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary"
private const val XPOSED_API_URL = "https://github.com/libxposed/api"
private const val GAZE_CAPSULE_URL = "https://github.com/Mocha-Realm/gaze"
private const val KOTLIN_URL = "https://kotlinlang.org/"
private const val KSP_URL = "https://github.com/google/ksp"
private const val ANDROID_GRADLE_PLUGIN_URL = "https://developer.android.com/build/releases/gradle-plugin"
private const val JUNIT_URL = "https://junit.org/junit4/"
private const val ESPRESSO_URL = "https://developer.android.com/training/testing/espresso"

@DrawableRes
private fun fallbackAvatarRes(avatarUrl: String): Int = when (avatarUrl) {
    MITOVERG_AVATAR_URL -> R.drawable.avatar_mitoverg
    COLORLARIS_AVATAR_URL -> R.drawable.avatar_colorlaris
    else -> R.drawable.ic_github
}

private data class AboutLinkItem(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val url: String,
    val leadingContent: (@Composable () -> Unit)? = null,
)

private data class AboutAgreementItem(
    val agreement: OnboardingPreferences.Agreement,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
)

private data class AvatarImageState(
    val bitmap: Bitmap?,
)

private object GitHubAvatarCache {
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private val failedUrls = mutableSetOf<String>()

    @Synchronized
    fun get(url: String): Bitmap? = bitmaps[url]

    @Synchronized
    fun put(url: String, bitmap: Bitmap) {
        bitmaps[url] = bitmap
        failedUrls.remove(url)
    }

    @Synchronized
    private fun markFailed(url: String) {
        failedUrls += url
    }

    @Synchronized
    private fun hasFailed(url: String): Boolean = url in failedUrls

    suspend fun load(context: Context, url: String): AvatarImageState = withContext(Dispatchers.IO) {
        get(url)?.let { bitmap ->
            return@withContext AvatarImageState(bitmap = bitmap)
        }
        val cachedBitmap = readFromDisk(context, url)
        if (cachedBitmap != null) {
            put(url, cachedBitmap)
            return@withContext AvatarImageState(bitmap = cachedBitmap)
        }
        if (hasFailed(url)) {
            return@withContext AvatarImageState(bitmap = null)
        }
        runCatching {
            URL(url).openConnection().apply {
                connectTimeout = 3_000
                readTimeout = 5_000
            }.getInputStream().use { stream ->
                BitmapFactory.decodeStream(stream)
            }?.also { bitmap ->
                put(url, bitmap)
                writeToDisk(context, url, bitmap)
            }
        }.fold(
            onSuccess = { bitmap ->
                AvatarImageState(bitmap = bitmap).also {
                    if (bitmap == null) markFailed(url)
                }
            },
            onFailure = {
                markFailed(url)
                AvatarImageState(bitmap = null)
            },
        )
    }

    private fun readFromDisk(context: Context, url: String): Bitmap? {
        val file = cacheFile(context, url)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun writeToDisk(context: Context, url: String, bitmap: Bitmap) {
        val file = cacheFile(context, url)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun cacheFile(context: Context, url: String): File {
        val name = url.hashCode().toUInt().toString(16)
        return File(context.cacheDir, "github_avatars/$name.png")
    }
}

suspend fun prefetchAboutAuthorAvatars(context: Context) {
    val appContext = context.applicationContext
    GitHubAvatarCache.load(appContext, MITOVERG_AVATAR_URL)
}

@Composable
fun AboutMainPage(
    onOpenAppSettings: () -> Unit,
    onOpenSoftwareUpdate: () -> Unit,
    onOpenContributors: () -> Unit,
    onOpenReferences: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            prefetchAboutAuthorAvatars(context.applicationContext)
        }
    }
    AboutAppCard()
    AboutGroupSpacer()
    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.setting_software_update),
            summary = "",
            showArrow = true,
            hasDividerBelow = true,
            onClick = onOpenSoftwareUpdate,
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.setting_theme_settings),
            summary = "",
            showArrow = true,
            hasDividerAbove = true,
            onClick = onOpenAppSettings,
        )
    }
    AboutGroupSpacer()
    AboutAuthorGroup(
        onOpenContributors = onOpenContributors,
        onOpenReferences = onOpenReferences,
    )
}

@Composable
private fun AboutGroupSpacer() {
    Spacer(modifier = Modifier.height(0.dp))
}

@Composable
private fun AboutAppCard() {
    val versionName = rememberAppVersionName()
    val versionCode = BuildConfig.APP_VERSION_CODE_LABEL
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapAt by remember { mutableLongStateOf(0L) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val onVersionTap: () -> Unit = {
        val now = SystemClock.elapsedRealtime()
        versionTapCount = if (now - lastVersionTapAt <= 1_500L) versionTapCount + 1 else 1
        lastVersionTapAt = now
        if (versionTapCount >= 5) {
            showDetails = !showDetails
            versionTapCount = 0
        }
    }

    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveAboutAppCard(
            versionName = versionName,
            versionCode = versionCode,
            showDetails = showDetails,
            onVersionTap = onVersionTap,
        )
        return
    }

    SettingsGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_onextbox_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(COUITheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ONextBoxLogo(
                color = COUITheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.about_version, versionName),
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onVersionTap() },
            )
            Spacer(modifier = Modifier.height(6.dp))
            BuildTypeBadge()
            AnimatedVisibility(
                visible = showDetails,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(durationMillis = 140)),
            ) {
                AboutVersionDetails(
                    versionName = versionName,
                    versionCode = versionCode,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.about_description),
                style = COUITheme.textStyles.body1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Material3ExpressiveAboutAppCard(
    versionName: String,
    versionCode: String,
    showDetails: Boolean,
    onVersionTap: () -> Unit,
) {
    SettingsGroup {
        Material3ExpressiveSegmentContentCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_onextbox_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                ONextBoxLogo(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                MaterialText(
                    text = stringResource(R.string.about_version, versionName),
                    modifier = Modifier.clickable { onVersionTap() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MaterialSurface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    MaterialText(
                        text = if (BuildConfig.DEBUG) "Debug" else "Release",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                AnimatedVisibility(
                    visible = showDetails,
                    enter = expandVertically(tween(260)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(140)),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val details = listOf(
                            "${stringResource(R.string.info_version)}: $versionName ($versionCode)",
                            "Build: ${BuildConfig.BUILD_TYPE}",
                            "${stringResource(R.string.info_package_name)}: ${BuildConfig.APPLICATION_ID}",
                            "${stringResource(R.string.about_detail_build_time)}: ${BuildConfig.APP_BUILD_TIME}",
                            "${stringResource(R.string.about_detail_build_timestamp)}: ${BuildConfig.APP_BUILD_TIMESTAMP}",
                            "${stringResource(R.string.about_reference_xposed_api_title)}: ${BuildConfig.LIBXPOSED_API_VERSION}",
                        )
                        details.forEach { detail ->
                            MaterialText(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                MaterialText(
                    text = stringResource(R.string.about_description_material3_expressive),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AboutVersionDetails(
    versionName: String,
    versionCode: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(COUITheme.colorScheme.dividerLine),
        )
        Spacer(modifier = Modifier.height(8.dp))
        AboutDetailText("${stringResource(R.string.info_version)}: $versionName ($versionCode)")
        AboutDetailText("Build: ${BuildConfig.BUILD_TYPE}")
        AboutDetailText("${stringResource(R.string.info_package_name)}: ${BuildConfig.APPLICATION_ID}")
        AboutDetailText("${stringResource(R.string.about_detail_build_time)}: ${BuildConfig.APP_BUILD_TIME}")
        AboutDetailText(
            "${stringResource(R.string.about_detail_build_timestamp)}: ${BuildConfig.APP_BUILD_TIMESTAMP}",
        )
        AboutDetailText(
            "${stringResource(R.string.about_reference_xposed_api_title)}: ${BuildConfig.LIBXPOSED_API_VERSION}",
        )
    }
}

@Composable
private fun AboutDetailText(
    text: String,
) {
    Text(
        text = text,
        style = COUITheme.textStyles.body1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun BuildTypeBadge() {
    val isDebug = BuildConfig.DEBUG
    val badgeText = if (isDebug) "Debug" else "Release"
    val badgeColor = if (isDebug) {
        Color(0xFFFFC928)
    } else {
        Color(0xFF34C759)
    }
    val textColor = if (isDebug) {
        Color(0xFF3B2A00)
    } else {
        Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(badgeColor)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badgeText,
            style = COUITheme.textStyles.body1,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
    }
}

data class SoftwareUpdateUiState(
    val checkingUpdates: Boolean,
    val availableUpdate: AvailableUpdate?,
    val downloadingUpdate: Boolean,
    val downloadProgress: Float?,
    val updateDownloaded: Boolean,
    val automaticSilentUpdate: Boolean,
    val updateNotificationsEnabled: Boolean,
    val selectedChannel: UpdateChannel,
    val selectedBuildType: UpdateBuildType,
    val statusText: String,
    val onCheckUpdates: () -> Unit,
    val onDownloadUpdate: () -> Unit,
    val onAutomaticSilentUpdateChange: (Boolean) -> Unit,
    val onUpdateNotificationsEnabledChange: (Boolean) -> Unit,
    val onUpdateChannelChange: (UpdateChannel) -> Unit,
    val onUpdateBuildTypeChange: (UpdateBuildType) -> Unit,
)

@Composable
fun rememberSoftwareUpdateUiState(
    autoCheckUpdateRequest: Int,
): SoftwareUpdateUiState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkingUpdates by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var automaticSilentUpdate by remember {
        mutableStateOf(UpdateChannelPreference.getAutomaticSilentUpdate(context))
    }
    var updateNotificationsEnabled by remember {
        mutableStateOf(UpdateChannelPreference.getUpdateNotificationsEnabled(context))
    }
    var selectedChannel by remember {
        mutableStateOf(UpdateChannelPreference.get(context))
    }
    var selectedBuildType by remember {
        mutableStateOf(UpdateChannelPreference.getBuildType(context))
    }
    var statusText by remember { mutableStateOf<String?>(null) }
    val checkingText = stringResource(R.string.update_checking)
    val noUpdateText = stringResource(R.string.update_no_update)
    val updateAvailableText = stringResource(R.string.update_available)
    val installFinishedText = stringResource(R.string.update_silent_install_finished)
    val installPermissionText = stringResource(R.string.update_install_permission_required)
    val failedText = stringResource(R.string.update_failed)
    val checkUpdates: () -> Unit = {
        if (!checkingUpdates && !downloadingUpdate) {
            scope.launch {
                checkingUpdates = true
                availableUpdate = null
                downloadedApk = null
                downloadProgress = null
                statusText = checkingText
                delay(SOFTWARE_UPDATE_CHECK_DELAY_MS)
                runCatching {
                    AppUpdater.checkForUpdate(selectedChannel, selectedBuildType)
                }.fold(
                    onSuccess = { update ->
                        availableUpdate = update
                        downloadedApk = update?.let { AppUpdater.findDownloadedUpdate(context, it) }
                        downloadProgress = if (downloadedApk != null) 1f else null
                        statusText = if (update == null) noUpdateText else updateAvailableText
                    },
                    onFailure = {
                        availableUpdate = null
                        statusText = failedText
                    },
                )
                checkingUpdates = false
            }
        }
    }
    val downloadUpdate: () -> Unit = {
        val update = availableUpdate
        if (update != null && !checkingUpdates && !downloadingUpdate) {
            scope.launch {
                val readyApk = downloadedApk?.takeIf(File::isFile)
                if (readyApk != null) {
                    statusText = when (
                        val result = AppUpdater.installDownloadedUpdate(context, update, readyApk)
                    ) {
                        UpdateResult.NoUpdate,
                        is UpdateResult.InstallStarted -> updateAvailableText
                        is UpdateResult.InstallFinished -> installFinishedText.format(result.versionName)
                        is UpdateResult.Failed -> {
                            if (result.reason == AppUpdater.INSTALL_PERMISSION_REQUIRED) {
                                installPermissionText
                            } else {
                                failedText
                            }
                        }
                    }
                } else {
                    downloadingUpdate = true
                    downloadProgress = 0f
                    try {
                        downloadedApk = AppUpdater.downloadUpdate(
                            context = context,
                            update = update,
                            onProgress = { progress ->
                                withContext(Dispatchers.Main.immediate) {
                                    downloadProgress = progress.fraction
                                }
                            },
                        )
                        downloadProgress = 1f
                        statusText = updateAvailableText
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        downloadedApk = null
                        downloadProgress = null
                        statusText = failedText
                    } finally {
                        downloadingUpdate = false
                    }
                }
            }
        }
    }

    LaunchedEffect(autoCheckUpdateRequest) {
        if (autoCheckUpdateRequest > 0) {
            checkUpdates()
        }
    }

    return SoftwareUpdateUiState(
        checkingUpdates = checkingUpdates,
        availableUpdate = availableUpdate,
        downloadingUpdate = downloadingUpdate,
        downloadProgress = downloadProgress,
        updateDownloaded = downloadedApk?.isFile == true,
        automaticSilentUpdate = automaticSilentUpdate,
        updateNotificationsEnabled = updateNotificationsEnabled,
        selectedChannel = selectedChannel,
        selectedBuildType = selectedBuildType,
        statusText = statusText ?: noUpdateText,
        onCheckUpdates = checkUpdates,
        onDownloadUpdate = downloadUpdate,
        onAutomaticSilentUpdateChange = { enabled ->
            automaticSilentUpdate = enabled
            UpdateChannelPreference.setAutomaticSilentUpdate(context, enabled)
            OnboardingPreferences.setBackgroundNetworkAllowed(
                context,
                enabled || updateNotificationsEnabled,
            )
        },
        onUpdateNotificationsEnabledChange = { enabled ->
            updateNotificationsEnabled = enabled
            UpdateChannelPreference.setUpdateNotificationsEnabled(context, enabled)
            OnboardingPreferences.setBackgroundNetworkAllowed(
                context,
                enabled || automaticSilentUpdate,
            )
            UpdateNotificationScheduler.configure(context, enabled)
        },
        onUpdateChannelChange = { channel ->
            selectedChannel = channel
            availableUpdate = null
            downloadedApk = null
            downloadProgress = null
            statusText = null
            UpdateChannelPreference.set(context, channel)
        },
        onUpdateBuildTypeChange = { buildType ->
            selectedBuildType = buildType
            availableUpdate = null
            downloadedApk = null
            downloadProgress = null
            statusText = null
            UpdateChannelPreference.setBuildType(context, buildType)
        },
    )
}

@Composable
fun SoftwareUpdatePage(
    state: SoftwareUpdateUiState,
    onOpenReleaseNotes: () -> Unit,
) {
    val versionName = rememberAppVersionName()
    val versionCode = BuildConfig.APP_VERSION_CODE_LABEL
    val deviceMarketName = rememberDeviceMarketName()
    val isLightTheme = COUITheme.colorScheme.background.luminance() > 0.5f
    val updateCardContentColor = if (isLightTheme) {
        COUITheme.colorScheme.onSurface
    } else {
        Color(0xFFECECEC)
    }
    val updateCardNumberSecondaryColor = if (isLightTheme) {
        Color(0xFF202020)
    } else {
        Color(0xFFE8E8E8)
    }
    val compactProgress by animateFloatAsState(
        targetValue = if (state.availableUpdate != null) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "softwareUpdateCardCompactProgress",
    )
    val updateCardAspectRatio = 0.65f + (1.08f - 0.65f) * compactProgress
    val updateCardSpacing = (24f - 12f * compactProgress).dp
    val updateCardShape = RoundedCornerShape((28f - 4f * compactProgress).dp)
    val cardContentStartPadding = (26f - 4f * compactProgress).dp
    val cardContentEndPadding = (36f - 6f * compactProgress).dp
    val cardNumberTopPadding = (28f - 8f * compactProgress).dp
    val cardNumberFontSize = (125f - 33f * compactProgress).sp
    val cardStatusBottomPadding = (114f - 19f * compactProgress).dp
    val cardStatusFontSize = (20f - 4f * compactProgress).sp
    val cardStatusLineHeight = (28f - 5f * compactProgress).sp
    val cardLogoBottomPadding = (58f - 12f * compactProgress).dp
    val cardLogoFontSize = (32f - 6f * compactProgress).sp
    val cardLogoLineHeight = (38f - 7f * compactProgress).sp
    val cardDeviceBottomPadding = (40f - 8f * compactProgress).dp
    val cardDeviceFontSize = (12f - 1f * compactProgress).sp
    val cardDeviceLineHeight = (20f - 4f * compactProgress).sp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(updateCardSpacing),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SoftwareUpdateContentHorizontalPadding),
            contentAlignment = Alignment.TopStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(updateCardAspectRatio)
                    .clip(updateCardShape)
                    .then(
                        if (isLightTheme) {
                            Modifier.border(
                                width = 0.5.dp,
                                color = COUITheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = updateCardShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Image(
                    painter = painterResource(R.drawable.software_update_wave_bg),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f
                            scaleY = 1f
                        },
                    contentScale = ContentScale.Crop,
                )
                Row(
                    modifier = Modifier.padding(
                        start = cardContentStartPadding,
                        top = cardNumberTopPadding,
                    ),
                    verticalAlignment = Alignment.Top,
                ) {
                    SoftwareUpdateCardNumberText(
                        text = "1",
                        color = Color(0xFFFF625D),
                        fontSize = cardNumberFontSize,
                        lineHeight = cardNumberFontSize)
                    SoftwareUpdateCardNumberText(
                        text = "7",
                        color = updateCardNumberSecondaryColor,
                        fontSize = cardNumberFontSize,
                        lineHeight = cardNumberFontSize,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = cardContentStartPadding,
                            end = cardContentEndPadding,
                            bottom = cardStatusBottomPadding,
                        ),
                ) {
                    Text(
                        text = state.statusText,
                        style = COUITheme.textStyles.title2,
                        color = updateCardContentColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = cardStatusFontSize,
                        lineHeight = cardStatusLineHeight,
                    )
                    if (state.checkingUpdates) {
                        Spacer(modifier = Modifier.width(6.dp))
                        SoftwareUpdateLoadingIndicator(
                            color = updateCardContentColor,
                            size = 16.dp,
                            strokeWidth = 0.5.dp,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
                ONextBoxLogo(
                    color = updateCardContentColor,
                    fontSize = cardLogoFontSize,
                    lineHeight = cardLogoLineHeight,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = cardContentStartPadding,
                            end = cardContentEndPadding,
                            bottom = cardLogoBottomPadding,
                        ),
                )
                Text(
                    text = deviceMarketName,
                    style = COUITheme.textStyles.title3,
                    color = updateCardContentColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = cardDeviceFontSize,
                    lineHeight = cardDeviceLineHeight,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = cardContentStartPadding,
                            end = cardContentEndPadding,
                            bottom = cardDeviceBottomPadding,
                        ),
                )
            }
        }

        val availableUpdate = state.availableUpdate
        if (availableUpdate != null) {
            SoftwareUpdateAvailableDetails(availableUpdate)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SoftwareUpdateContentHorizontalPadding +
                            SoftwareUpdateVersionInfoExtraHorizontalPadding,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.software_update_version_title),
                    style = COUITheme.textStyles.title3,
                    color = COUITheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$versionName($versionCode)",
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            onClick = onOpenReleaseNotes,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.software_update_release_notes),
                        style = COUITheme.textStyles.body1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = ">",
                        style = COUITheme.textStyles.body1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftwareUpdateAvailableDetails(update: AvailableUpdate) {
    val releaseNotesText = update.releaseNotes
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.software_update_release_notes_empty)
    val versionSummary = buildString {
        append(update.versionName)
        update.versionCode?.let { append("($it)") }
        update.fileSizeBytes?.let {
            append("  |  ")
            append(formatFileSize(it))
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsGroup(bottomPadding = 10.dp) {
            SettingsCardRow(
                title = stringResource(R.string.software_update_version_title),
                summary = versionSummary,
            )
        }
        SoftwareUpdateReleaseNotesGroup(
            releaseNotesText = releaseNotesText,
            stateKey = update.versionName,
            bottomPadding = 8.dp,
        )
    }
}

@Composable
fun SoftwareUpdateDownloadBar(state: SoftwareUpdateUiState) {
    if (state.availableUpdate == null) return

    val progress = state.downloadProgress
    val animatedProgress by animateFloatAsState(
        targetValue = when {
            state.updateDownloaded -> 1f
            state.downloadingUpdate -> progress ?: 0f
            else -> 1f
        },
        animationSpec = if (state.downloadingUpdate && (progress ?: 0f) <= 0f) {
            snap()
        } else {
            tween(durationMillis = 350, easing = LinearEasing)
        },
        label = "softwareUpdateDownloadProgress",
    )
    val primaryColor = COUITheme.colorScheme.primary
    val primaryContentColor = COUITheme.colorScheme.onPrimary
    val cardColor = COUITheme.colorScheme.surfaceContainer
    val cardContentColor = COUITheme.colorScheme.onSurfaceContainer
    val buttonShape = RoundedCornerShape(24.dp)
    val buttonText = when {
        state.updateDownloaded -> stringResource(R.string.software_update_install_update)
        !state.downloadingUpdate -> stringResource(R.string.software_update_download_install)
        progress == null -> stringResource(R.string.software_update_download_preparing)
        else -> stringResource(
            R.string.software_update_download_progress,
            (progress * 100f).toInt().coerceIn(0, 100),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Button(
            onClick = state.onDownloadUpdate,
            enabled = !state.downloadingUpdate,
            colors = ButtonDefaults.buttonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                contentColor = cardContentColor,
                disabledContentColor = cardContentColor,
            ),
            minHeight = 48.dp,
            cornerRadius = 24.dp,
            insideMargin = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(buttonShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cardColor),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(primaryColor),
                )
                Text(
                    text = buttonText,
                    style = COUITheme.textStyles.button,
                    color = cardContentColor,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = size.width * animatedProgress) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = buttonText,
                        style = COUITheme.textStyles.button,
                        color = primaryContentColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun SoftwareUpdateReleaseNotesPage(state: SoftwareUpdateUiState) {
    val context = LocalContext.current
    val currentVersionName = rememberAppVersionName()
    val currentVersionCode = BuildConfig.APP_VERSION_CODE_LABEL
    val availableUpdate = state.availableUpdate
    val versionName = availableUpdate?.versionName ?: currentVersionName
    val versionCode = availableUpdate?.versionCode?.toString()
        ?: currentVersionCode.takeIf { availableUpdate == null }
    val remoteRelease by produceState<AvailableUpdate?>(
        initialValue = availableUpdate,
        key1 = availableUpdate,
        key2 = versionName to state.selectedBuildType,
    ) {
        value = availableUpdate ?: runCatching {
            AppUpdater.findRelease(versionName, state.selectedBuildType)
        }.getOrNull()
    }
    val releaseNotesText = remoteRelease?.releaseNotes
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.software_update_release_notes_empty)
    val versionSummary = buildString {
        append(versionName)
        versionCode?.let {
            append('(')
            append(it)
            append(')')
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingsGroup {
            SettingsCardRow(
                title = stringResource(R.string.software_update_version_title),
                summary = versionSummary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SoftwareUpdateReleaseNotesGroup(
            releaseNotesText = releaseNotesText,
            stateKey = versionSummary,
        )

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SoftwareUpdateContentHorizontalPadding*2),
        ) {
            Text(
                text = stringResource(R.string.software_update_notice_title),
                style = COUITheme.textStyles.title3,
                color = COUITheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            val noticeItems = stringResource(R.string.software_update_notice_body)
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            noticeItems.forEachIndexed { index, notice ->
                Text(
                    text = notice,
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                )
                if (index < noticeItems.lastIndex) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.software_update_notice_more),
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                )
                Text(
                    text = stringResource(R.string.software_update_notice_community),
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            onClick = { openUrl(context, TELEGRAM_CHANNEL_URL) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun Material3ExpressiveExpandableCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    bottomPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val bodyVisible = remember { MutableTransitionState(expanded) }
    bodyVisible.targetState = expanded
    SettingsGroup(bottomPadding = bottomPadding) {
        Material3ExpressiveAnimatedSegmentPosition(
            index = 0,
            count = if (expanded) 2 else 1,
            durationMillis = if (expanded) 280 else 240,
        ) {
            SettingsCardRow(
                title = title,
                summary = summary,
                onClick = onToggle,
                showExpandArrow = true,
                expandArrowExpanded = expanded,
                hasDividerBelow = expanded,
            )
        }
        AnimatedVisibility(
            visibleState = bodyVisible,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f)),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f)),
        ) {
            Material3ExpressiveSegmentPosition(index = 1, count = 2) {
                Material3ExpressiveSegmentContentCard(content = content)
            }
        }
    }
}

@Composable
private fun SoftwareUpdateReleaseNotesGroup(
    releaseNotesText: String,
    stateKey: String,
    bottomPadding: Dp = 16.dp,
) {
    var releaseNotesExpanded by rememberSaveable(stateKey) { mutableStateOf(true) }
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveExpandableCard(
            title = stringResource(R.string.software_update_release_notes_title),
            summary = "",
            expanded = releaseNotesExpanded,
            onToggle = { releaseNotesExpanded = !releaseNotesExpanded },
            bottomPadding = bottomPadding,
        ) {
            MaterialText(
                text = releaseNotesText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    SettingsGroup(bottomPadding = bottomPadding) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsCardRow(
                title = stringResource(R.string.software_update_release_notes_title),
                summary = "",
                onClick = { releaseNotesExpanded = !releaseNotesExpanded },
                showExpandArrow = true,
                expandArrowExpanded = releaseNotesExpanded,
                hasDividerBelow = releaseNotesExpanded,
            )
            AnimatedVisibility(visible = releaseNotesExpanded) {
                Column {
                    SettingsDivider()
                    Text(
                        text = releaseNotesText,
                        style = COUITheme.textStyles.body1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    val sizeMb = sizeBytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.2f MB", sizeMb)
}

@Composable
private fun SoftwareUpdateCardNumberText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    val density = LocalDensity.current
    val strokeWidth = with(density) { 5.dp.toPx() }
    val numberStyle = COUITheme.textStyles.title1.copy(
        fontFamily = ONextBoxLogoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = lineHeight,
    )

    Box {
        Text(
            text = text,
            style = numberStyle.copy(drawStyle = Stroke(width = strokeWidth)),
            color = color,
        )
        Text(
            text = text,
            style = numberStyle,
            color = color,
        )
    }
}

@Composable
private fun SoftwareUpdateLoadingIndicator(
    color: Color,
    size: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "softwareUpdateLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "softwareUpdateLoadingRotation",
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
            },
    ) {
        val strokeWidthPx = strokeWidth.toPx()
        val diameter = this.size.minDimension - strokeWidthPx
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
            size = Size(diameter, diameter),
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoftwareUpdateTopBarActions(
    onOpenAutoUpdateSettings: () -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    Box {
        if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
            MaterialIconButton(onClick = { expanded.value = true }) {
                MaterialIcon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.software_update_auto_settings),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            DropdownMenuPopup(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false },
            ) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                    DropdownMenuItem(
                        text = { MaterialText(stringResource(R.string.software_update_auto_settings)) },
                        shape = MenuDefaults.standaloneItemShape,
                        onClick = {
                            expanded.value = false
                            onOpenAutoUpdateSettings()
                        },
                    )
                }
            }
            return@Box
        }
        ColorOsTopBarButton(
            onClick = { expanded.value = true },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(COUITheme.colorScheme.onBackground),
                    )
                }
            }
        }

        ColorOsPopup(
            show = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                SoftwareUpdateMenuRow(
                    title = stringResource(R.string.software_update_auto_settings),
                    onClick = {
                        expanded.value = false
                        onOpenAutoUpdateSettings()
                    },
                )
            }
        }
    }
}

@Composable
fun SoftwareUpdateAutoSettingsPage(
    state: SoftwareUpdateUiState,
) {
    val context = LocalContext.current
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
        state.onUpdateNotificationsEnabledChange(granted)
    }

    LaunchedEffect(state.updateNotificationsEnabled, notificationPermissionGranted) {
        if (state.updateNotificationsEnabled && !notificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SettingsGroup {
        Material3ExpressiveSegmentPosition(index = 0, count = 4) {
            SettingsToggleRow(
                title = stringResource(R.string.setting_auto_silent_update),
                summary = stringResource(R.string.setting_auto_silent_update_summary),
                checked = state.automaticSilentUpdate,
                onCheckedChange = state.onAutomaticSilentUpdateChange,
                hasDividerBelow = true,
            )
        }
        SettingsDivider()
        Material3ExpressiveSegmentPosition(index = 1, count = 4) {
            SettingsToggleRow(
                title = stringResource(R.string.setting_update_notifications),
                summary = stringResource(R.string.setting_update_notifications_summary),
                checked = state.updateNotificationsEnabled && notificationPermissionGranted,
                onCheckedChange = { enabled ->
                    if (enabled && !notificationPermissionGranted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        state.onUpdateNotificationsEnabledChange(enabled)
                    }
                },
                hasDividerAbove = true,
                hasDividerBelow = true,
            )
        }
        SettingsDivider()
        Material3ExpressiveSegmentPosition(index = 2, count = 4) {
            SettingsUpdateChannelDropdown(
                title = stringResource(R.string.about_update_channel_title),
                hasDividerAbove = true,
                hasDividerBelow = true,
                selectedChannel = state.selectedChannel,
                onChannelChange = state.onUpdateChannelChange,
            )
        }
        SettingsDivider()
        Material3ExpressiveSegmentPosition(index = 3, count = 4) {
            SettingsUpdateBuildTypeDropdown(
                title = stringResource(R.string.update_build_type_title),
                selectedBuildType = state.selectedBuildType,
                onBuildTypeChange = state.onUpdateBuildTypeChange,
            )
        }
    }
}

@Composable
private fun SoftwareUpdateMenuRow(
    title: String,
    trailing: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 80 else 180),
        label = "softwareUpdatePopupPress",
    )
    val darkTheme = COUITheme.colorScheme.background.luminance() < 0.5f
    val pressColor = if (darkTheme) Color(0x26FFFFFF) else Color(0x14000000)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .height(40.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .squircleBackground(
                    color = pressColor.copy(alpha = pressColor.alpha * pressProgress),
                    cornerRadius = 20.dp,
                    extension = 1f,
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = COUITheme.textStyles.body1,
                color = when {
                    selected -> COUITheme.colorScheme.primary
                    enabled -> COUITheme.colorScheme.onSurface
                    else -> COUITheme.colorScheme.onSurfaceVariantSummary
                },
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurfaceVariantActions,
                    fontWeight = FontWeight.Normal,
                )
            }
            if (selected) {
                Icon(
                    imageVector = COUIIcons.Ok,
                    contentDescription = null,
                    tint = COUITheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun SoftwareUpdateMenuDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(COUITheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
private fun AboutAuthorGroup(
    onOpenContributors: () -> Unit,
    onOpenReferences: () -> Unit,
) {
    val context = LocalContext.current

    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.about_author_info_title),
            summary = stringResource(R.string.about_author_info_summary),
            showArrow = true,
            hasDividerBelow = true,
            onClick = { openUrl(context, GITHUB_PROFILE_URL) },
            leadingContent = { GitHubAuthorAvatar(MITOVERG_AVATAR_URL) },
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.about_contributors_title),
            summary = "",
            showArrow = true,
            hasDividerAbove = true,
            hasDividerBelow = true,
            onClick = onOpenContributors,
            leadingContent = { AboutVectorIcon(AppIcons.ContributorsSolidC17) },
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.about_references_title),
            summary = stringResource(R.string.about_references_summary),
            showArrow = true,
            hasDividerAbove = true,
            hasDividerBelow = true,
            onClick = onOpenReferences,
            leadingContent = { AboutVectorIcon(AppIcons.AgreementsSolidC17) },
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.about_github_title),
            summary = stringResource(R.string.about_github_summary),
            showArrow = true,
            hasDividerAbove = true,
            hasDividerBelow = true,
            onClick = { openUrl(context, GITHUB_REPOSITORY_URL) },
            leadingContent = { AboutRowIcon(R.drawable.ic_github) },
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.about_telegram_title),
            summary = stringResource(R.string.about_telegram_summary),
            showArrow = true,
            hasDividerAbove = true,
            onClick = { openUrl(context, TELEGRAM_CHANNEL_URL) },
            leadingContent = { AboutVectorIcon(AppIcons.TelegramChannelSolidC17) },
        )
    }
}

@Composable
fun AboutContributorsPage() {
    val items = listOf(
        AboutLinkItem(
            titleRes = R.string.about_contributor_colorlaris_title,
            summaryRes = R.string.about_contributor_colorlaris_summary,
            url = COLORLARIS_PROFILE_URL,
            leadingContent = { GitHubAuthorAvatar(COLORLARIS_AVATAR_URL) },
        ),
        AboutLinkItem(
            titleRes = R.string.about_contributor_rove24_title,
            summaryRes = R.string.about_contributor_rove24_summary,
            url = ROVE24_PROFILE_URL,
            leadingContent = { GitHubAuthorAvatar(ROVE24_AVATAR_URL) },
        ),
    )
    AboutLinkGroup(items = items)
}

@Composable
fun AboutReferencesPage() {
    val uiReferences = listOf(
        AboutLinkItem(
            titleRes = R.string.about_reference_onextbox_coui_title,
            summaryRes = R.string.about_reference_onextbox_coui_summary,
            url = GITHUB_REPOSITORY_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_coui_title,
            summaryRes = R.string.about_reference_coui_summary,
            url = COUI_REPOSITORY_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_miuix_title,
            summaryRes = R.string.about_reference_miuix_summary,
            url = MIUIX_REPOSITORY_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_material_icons_title,
            summaryRes = R.string.about_reference_material_icons_summary,
            url = MATERIAL_ICONS_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_gaze_capsule_title,
            summaryRes = R.string.about_reference_gaze_capsule_summary,
            url = GAZE_CAPSULE_URL,
        ),
    )
    val featureReferences = listOf(
        AboutLinkItem(
            titleRes = R.string.about_reference_oshin_title,
            summaryRes = R.string.about_reference_oshin_summary,
            url = OSHIN_REPOSITORY_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_luckytool_title,
            summaryRes = R.string.about_reference_luckytool_summary,
            url = LUCKYTOOL_REPOSITORY_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_inxlocker_title,
            summaryRes = R.string.about_reference_inxlocker_summary,
            url = INXLOCKER_REPOSITORY_URL,
        ),
    )
    val libraries = listOf(
        AboutLinkItem(
            titleRes = R.string.about_reference_libsu_title,
            summaryRes = R.string.about_reference_libsu_summary,
            url = LIBSU_REPOSITORY_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_hidden_api_bypass_title,
            summaryRes = R.string.about_reference_hidden_api_bypass_summary,
            url = HIDDEN_API_BYPASS_REPOSITORY_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_xposed_api_title,
            summaryRes = R.string.about_reference_xposed_api_summary,
            url = XPOSED_API_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_jetpack_compose_title,
            summaryRes = R.string.about_reference_jetpack_compose_summary,
            url = JETPACK_COMPOSE_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_androidx_title,
            summaryRes = R.string.about_reference_androidx_summary,
            url = ANDROIDX_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_androidx_palette_title,
            summaryRes = R.string.about_reference_androidx_palette_summary,
            url = ANDROIDX_PALETTE_URL,
        ),
    )
    val developmentTools = listOf(
        AboutLinkItem(
            titleRes = R.string.about_reference_kotlin_title,
            summaryRes = R.string.about_reference_kotlin_summary,
            url = KOTLIN_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_android_gradle_plugin_title,
            summaryRes = R.string.about_reference_android_gradle_plugin_summary,
            url = ANDROID_GRADLE_PLUGIN_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_ksp_title,
            summaryRes = R.string.about_reference_ksp_summary,
            url = KSP_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_junit_title,
            summaryRes = R.string.about_reference_junit_summary,
            url = JUNIT_URL,
        ),
        AboutLinkItem(
            titleRes = R.string.about_reference_espresso_title,
            summaryRes = R.string.about_reference_espresso_summary,
            url = ESPRESSO_URL,
        ),
    )
    AboutAgreementsSection()
    SettingsSection(title = stringResource(R.string.about_references_ui_section))
    AboutLinkGroup(items = uiReferences)
    SettingsSection(title = stringResource(R.string.about_references_feature_section))
    AboutLinkGroup(items = featureReferences)
    SettingsSection(title = stringResource(R.string.about_references_library_section))
    AboutLinkGroup(items = libraries)
    SettingsSection(title = stringResource(R.string.about_references_development_section))
    AboutLinkGroup(items = developmentTools)
}

@Composable
private fun AboutAgreementsSection() {
    val context = LocalContext.current
    val items = listOf(
        AboutAgreementItem(
            agreement = OnboardingPreferences.Agreement.TermsAndRisk,
            titleRes = R.string.onboarding_agreement_terms_title,
            summaryRes = R.string.onboarding_agreement_terms_summary,
        ),
        AboutAgreementItem(
            agreement = OnboardingPreferences.Agreement.Privacy,
            titleRes = R.string.onboarding_agreement_privacy_title,
            summaryRes = R.string.onboarding_agreement_privacy_summary,
        ),
        AboutAgreementItem(
            agreement = OnboardingPreferences.Agreement.PrivilegedAccess,
            titleRes = R.string.onboarding_agreement_privileged_title,
            summaryRes = R.string.onboarding_agreement_privileged_summary,
        ),
        AboutAgreementItem(
            agreement = OnboardingPreferences.Agreement.EsimNetworkSecurity,
            titleRes = R.string.onboarding_agreement_esim_title,
            summaryRes = R.string.onboarding_agreement_esim_summary,
        ),
        AboutAgreementItem(
            agreement = OnboardingPreferences.Agreement.OpenSourceLicenses,
            titleRes = R.string.onboarding_agreement_licenses_title,
            summaryRes = R.string.onboarding_agreement_licenses_summary,
        ),
        AboutAgreementItem(
            agreement = OnboardingPreferences.Agreement.DeviceAuthorization,
            titleRes = R.string.onboarding_agreement_authorization_title,
            summaryRes = R.string.onboarding_agreement_authorization_summary,
        ),
    )
    var expandedAgreement by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsSection(title = stringResource(R.string.about_agreements_activation_section))
    items.forEachIndexed { index, item ->
        val agreementKey = item.agreement.name
        val expanded = expandedAgreement == agreementKey
        val body = remember(context, item.agreement) {
            context.resources.openRawResource(item.agreement.localizedTextResource)
                .bufferedReader(Charsets.UTF_8)
                .use { reader -> reader.readText().trim() }
        }
        if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
            Material3ExpressiveExpandableCard(
                title = stringResource(item.titleRes),
                summary = stringResource(item.summaryRes),
                expanded = expanded,
                onToggle = { expandedAgreement = agreementKey.takeUnless { expanded } },
            ) {
                MaterialText(
                    text = body,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
        SettingsGroup {
            SettingsCardRow(
                title = stringResource(item.titleRes),
                summary = stringResource(item.summaryRes),
                showExpandArrow = true,
                expandArrowExpanded = expanded,
                hasDividerBelow = expanded,
                onClick = {
                    expandedAgreement = agreementKey.takeUnless { expanded }
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(durationMillis = 140)),
            ) {
                Column {
                    SettingsDivider()
                    Text(
                        text = body,
                        style = COUITheme.textStyles.body1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            }
        }
        }
        if (index < items.lastIndex) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AboutNoticeCard(
    text: String,
) {
    var visible by rememberSaveable { mutableStateOf(true) }
    if (!visible) return

    SettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = COUITheme.textStyles.body1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(COUITheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    .clickable { visible = false },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "脳",
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AboutLinkGroup(
    items: List<AboutLinkItem>,
) {
    val context = LocalContext.current
    SettingsGroup {
        items.forEachIndexed { index, item ->
            SettingsCardRow(
                title = stringResource(item.titleRes),
                summary = stringResource(item.summaryRes),
                showArrow = true,
                hasDividerAbove = index > 0,
                hasDividerBelow = index < items.lastIndex,
                onClick = { openUrl(context, item.url) },
                leadingContent = item.leadingContent,
            )
            if (index < items.lastIndex) SettingsDivider()
        }
    }
}

@Composable
private fun GitHubAuthorAvatar(
    avatarUrl: String,
) {
    val context = LocalContext.current
    val resolvedAvatarUrl = remember(avatarUrl) { avatarUrl }
    val avatarState by produceState(
        initialValue = AvatarImageState(bitmap = GitHubAvatarCache.get(resolvedAvatarUrl)),
        key1 = resolvedAvatarUrl,
        key2 = context,
    ) {
        if (value.bitmap != null) return@produceState
        value = GitHubAvatarCache.load(context.applicationContext, resolvedAvatarUrl)
    }
    val iconModifier = Modifier
        .size(32.dp)
        .clip(RoundedCornerShape(9.dp))

    val bitmap = avatarState.bitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = iconModifier,
        )
    } else {
        Image(
            painter = painterResource(fallbackAvatarRes(resolvedAvatarUrl)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = iconModifier,
        )
    }
}

@Composable
private fun AboutRowIcon(
    @DrawableRes iconRes: Int,
) {
    val isMaterial = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
    val tint = if (isMaterial) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        COUITheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier.size(if (isMaterial) 32.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun AboutVectorIcon(
    imageVector: ImageVector,
) {
    val isMaterial = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
    val tint = if (isMaterial) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        COUITheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier.size(if (isMaterial) 32.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = imageVector,
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

private fun Context.documentDisplayName(uri: Uri): String? = runCatching {
    contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
    }
}.getOrNull()
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

@Composable
fun AppSettingsPage(
    blurEffectEnabled: Boolean,
    onBlurEffectEnabledChange: (Boolean) -> Unit,
    featurePageNewStyleEnabled: Boolean,
    onFeaturePageNewStyleEnabledChange: (Boolean) -> Unit,
    progressiveCardAnimationEnabled: Boolean,
    onProgressiveCardAnimationEnabledChange: (Boolean) -> Unit,
    featurePageVideoHidden: Boolean,
    onFeaturePageVideoHiddenChange: (Boolean) -> Unit,
    appLanguageTag: String,
    onAppLanguageChange: (String) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    appUiStyle: AppUiStyle,
    onAppUiStyleChange: (AppUiStyle) -> Unit,
    appThemeKeyColor: Long?,
    onAppThemeKeyColorChange: (Long?) -> Unit,
    appThemePaletteStyle: Int,
    onAppThemePaletteStyleChange: (Int) -> Unit,
    appThemeColorSpec: Int,
    onAppThemeColorSpecChange: (Int) -> Unit,
    liquidGlassBottomBarEnabled: Boolean,
    onLiquidGlassBottomBarEnabledChange: (Boolean) -> Unit,
    materialFloatingBottomBarEnabled: Boolean,
    onMaterialFloatingBottomBarEnabledChange: (Boolean) -> Unit,
    materialHapticsEnabled: Boolean,
    onMaterialHapticsEnabledChange: (Boolean) -> Unit,
    materialSwitchIconsEnabled: Boolean,
    onMaterialSwitchIconsEnabledChange: (Boolean) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val systemInDarkTheme = isSystemInDarkTheme()
    val featurePageVideoAvailable = featurePageNewStyleEnabled &&
        !appThemeMode.isMonet &&
        (
            appThemeMode == AppThemeMode.Dark ||
                (appThemeMode == AppThemeMode.System && systemInDarkTheme)
            )
    val scope = rememberCoroutineScope()
    var showClearConfigConfirm by rememberSaveable { mutableStateOf(false) }
    var isClearingConfig by remember { mutableStateOf(false) }
    val liquidGlassBottomBarSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val exportConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ConfigBackup.MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ConfigBackup.exportToUri(context, uri)
                    context.documentDisplayName(uri) ?: ConfigBackup.defaultFileName()
                }
            }.fold(
                onSuccess = { fileName ->
                    Toast.makeText(
                        context,
                        resources.getString(R.string.config_export_success, fileName),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        resources.getString(R.string.config_export_failed, error.localizedMessage.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }
    val importConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ConfigBackup.importFromUri(context, uri)
                }
            }.fold(
                onSuccess = {
                    Toast.makeText(
                        context,
                        resources.getString(
                            if (it.systemStateSynced) R.string.config_import_success
                            else R.string.config_import_partial_success,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                    context.findActivity()?.let { activity ->
                        activity.startActivity(Intent.makeRestartActivityTask(activity.componentName))
                    }
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        resources.getString(R.string.config_import_failed, error.localizedMessage.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    CouiConfirmDialog(
        show = showClearConfigConfirm,
        title = stringResource(R.string.config_clear_confirm_title),
        summary = stringResource(R.string.config_clear_confirm_summary),
        negativeText = stringResource(R.string.config_clear_confirm_cancel),
        positiveText = stringResource(R.string.config_clear_confirm_action),
        onDismissRequest = { showClearConfigConfirm = false },
        onPositive = {
            if (isClearingConfig) return@CouiConfirmDialog
            showClearConfigConfirm = false
            isClearingConfig = true
            scope.launch {
                runCatching {
                    ConfigBackup.resetToDefaults(context)
                }.fold(
                    onSuccess = { result ->
                        val messageRes = if (result.systemStateReset) {
                            R.string.config_clear_success
                        } else {
                            R.string.config_clear_partial_success
                        }
                        Toast.makeText(context, resources.getString(messageRes), Toast.LENGTH_LONG).show()
                        context.findActivity()?.let { activity ->
                            activity.startActivity(Intent.makeRestartActivityTask(activity.componentName))
                        }
                    },
                    onFailure = { error ->
                        isClearingConfig = false
                        Toast.makeText(
                            context,
                            resources.getString(
                                R.string.config_clear_failed,
                                error.localizedMessage.orEmpty(),
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        },
    )

    SettingsSection(title = stringResource(R.string.settings_section_display))
    if (appUiStyle == AppUiStyle.Material3Expressive) {
        Material3ExpressiveAppearanceSettingsGroup(
            appUiStyle = appUiStyle,
            onAppUiStyleChange = onAppUiStyleChange,
            appLanguageTag = appLanguageTag,
            onAppLanguageChange = onAppLanguageChange,
            appThemeMode = appThemeMode,
            onAppThemeModeChange = onAppThemeModeChange,
            appThemeKeyColor = appThemeKeyColor,
            onAppThemeKeyColorChange = onAppThemeKeyColorChange,
            materialFloatingBottomBarEnabled = materialFloatingBottomBarEnabled,
            onMaterialFloatingBottomBarEnabledChange = onMaterialFloatingBottomBarEnabledChange,
            materialHapticsEnabled = materialHapticsEnabled,
            onMaterialHapticsEnabledChange = onMaterialHapticsEnabledChange,
            materialSwitchIconsEnabled = materialSwitchIconsEnabled,
            onMaterialSwitchIconsEnabledChange = onMaterialSwitchIconsEnabledChange,
        )
    } else {
    SettingsGroup {
        SettingsUiStyleDropdown(
            selectedStyle = appUiStyle,
            onStyleChange = onAppUiStyleChange,
        )
        SettingsDivider()
        SettingsThemeModeDropdown(
            title = stringResource(R.string.setting_theme_mode),
            selectedMode = appThemeMode,
            onModeChange = onAppThemeModeChange,
            hasDividerBelow = true,
        )
        SettingsDivider()
        AnimatedVisibility(
            visible = appThemeMode.isMonet,
            enter = expandVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(160)),
        ) {
            Column {
                SettingsThemeKeyColorDropdown(
                    title = stringResource(R.string.setting_theme_key_color),
                    selectedKeyColor = appThemeKeyColor,
                    onKeyColorChange = onAppThemeKeyColorChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
            }
        }
        AnimatedVisibility(
            visible = appThemeMode.isMonet && appThemeKeyColor != null,
            enter = expandVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(160)),
        ) {
            Column {
                SettingsThemeTextDropdown(
                    title = stringResource(R.string.setting_theme_palette_style),
                    labels = ThemePaletteStyle.entries.map { it.name },
                    selectedIndex = appThemePaletteStyle,
                    onSelectedIndexChange = onAppThemePaletteStyleChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsThemeTextDropdown(
                    title = stringResource(R.string.setting_theme_color_spec),
                    labels = ThemeColorSpec.entries.map { it.name },
                    selectedIndex = appThemeColorSpec,
                    onSelectedIndexChange = onAppThemeColorSpecChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
            }
        }
        SettingsLanguageDropdown(
            title = stringResource(R.string.setting_app_language),
            summary = "",
            selectedLanguageTag = appLanguageTag,
            onLanguageChange = onAppLanguageChange,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        AnimatedVisibility(
            visible = isRuntimeShaderSupported(),
            enter = expandVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(160)),
        ) {
            Column {
                SettingsToggleRow(
                    title = stringResource(R.string.setting_blur_effect),
                    summary = "",
                    checked = blurEffectEnabled,
                    onCheckedChange = onBlurEffectEnabledChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
            }
        }
        SettingsToggleRow(
            title = stringResource(R.string.setting_feature_page_new_style),
            summary = "",
            checked = featurePageNewStyleEnabled,
            onCheckedChange = onFeaturePageNewStyleEnabledChange,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.setting_feature_page_hide_background_video),
            summary = stringResource(R.string.setting_feature_page_hide_background_video_summary),
            checked = featurePageVideoHidden,
            onCheckedChange = onFeaturePageVideoHiddenChange,
            hasDividerAbove = true,
            hasDividerBelow = true,
            enabled = featurePageVideoAvailable,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.setting_progressive_card_animation),
            summary = stringResource(R.string.setting_progressive_card_animation_summary),
            checked = progressiveCardAnimationEnabled,
            onCheckedChange = onProgressiveCardAnimationEnabledChange,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.setting_liquid_glass_bottom_bar),
            summary = stringResource(R.string.setting_liquid_glass_bottom_bar_summary),
            checked = liquidGlassBottomBarEnabled && liquidGlassBottomBarSupported,
            onCheckedChange = {
                if (liquidGlassBottomBarSupported) {
                    onLiquidGlassBottomBarEnabledChange(it)
                }
            },
            hasDividerAbove = true,
            hasDividerBelow = true,
            enabled = liquidGlassBottomBarSupported,
        )
    }
    }

    SettingsSection(title = stringResource(R.string.section_restore_backup))
    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.setting_export_config),
            summary = stringResource(R.string.setting_export_config_summary),
            showArrow = true,
            onClick = { exportConfigLauncher.launch(ConfigBackup.defaultFileName()) },
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.setting_import_config),
            summary = stringResource(R.string.setting_import_config_summary),
            showArrow = true,
            onClick = { importConfigLauncher.launch(arrayOf(ConfigBackup.MIME_TYPE, "text/*")) },
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.setting_clear_config),
            summary = stringResource(R.string.setting_clear_config_summary),
            onClick = {
                if (!isClearingConfig) showClearConfigConfirm = true
            },
            hasDividerAbove = true,
        )
    }

    SettingsSection(title = stringResource(R.string.settings_section_other))
    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.setting_developer_options),
            summary = "",
            showArrow = true,
            onClick = onOpenDeveloperOptions,
        )
    }
}

@Composable
private fun Material3ExpressiveAppearanceSettingsGroup(
    appUiStyle: AppUiStyle,
    onAppUiStyleChange: (AppUiStyle) -> Unit,
    appLanguageTag: String,
    onAppLanguageChange: (String) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    appThemeKeyColor: Long?,
    onAppThemeKeyColorChange: (Long?) -> Unit,
    materialFloatingBottomBarEnabled: Boolean,
    onMaterialFloatingBottomBarEnabledChange: (Boolean) -> Unit,
    materialHapticsEnabled: Boolean,
    onMaterialHapticsEnabledChange: (Boolean) -> Unit,
    materialSwitchIconsEnabled: Boolean,
    onMaterialSwitchIconsEnabledChange: (Boolean) -> Unit,
) {
    val paletteVisible = remember { MutableTransitionState(!appThemeMode.isMonet) }
    paletteVisible.targetState = !appThemeMode.isMonet
    val hasPaletteSlot = paletteVisible.currentState || paletteVisible.targetState || !paletteVisible.isIdle
    val interactionStartIndex = if (hasPaletteSlot) 5 else 4
    val segmentCount = interactionStartIndex + 3
    SettingsGroup {
        Material3ExpressiveSegmentPosition(index = 0, count = segmentCount) {
            SettingsUiStyleDropdown(
                selectedStyle = appUiStyle,
                onStyleChange = onAppUiStyleChange,
            )
        }
        Material3ExpressiveSegmentPosition(index = 1, count = segmentCount) {
            SettingsLanguageDropdown(
                title = stringResource(R.string.setting_app_language),
                summary = "",
                selectedLanguageTag = appLanguageTag,
                onLanguageChange = onAppLanguageChange,
                hasDividerAbove = true,
                hasDividerBelow = true,
            )
        }
        Material3ExpressiveSegmentPosition(index = 2, count = segmentCount) {
            Material3ExpressiveThemeModeDropdown(
                title = stringResource(R.string.setting_theme_mode),
                selectedMode = appThemeMode,
                onModeChange = onAppThemeModeChange,
            )
        }
        Material3ExpressiveAnimatedSegmentPosition(
            index = 3,
            count = segmentCount,
            durationMillis = if (appThemeMode.isMonet) 240 else 280,
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.setting_dynamic_color),
                summary = stringResource(
                    if (appThemeMode.isMonet) R.string.setting_dynamic_color_on_summary
                    else R.string.setting_dynamic_color_off_summary,
                ),
                checked = appThemeMode.isMonet,
                onCheckedChange = { enabled ->
                    onAppThemeModeChange(appThemeMode.withMaterial3ExpressiveDynamicColor(enabled))
                },
            )
        }
        AnimatedVisibility(
            visibleState = paletteVisible,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f)),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f)),
        ) {
            Material3ExpressiveSegmentPosition(index = 4, count = segmentCount) {
                Material3ExpressiveSegmentContentCard {
                    Material3ExpressiveThemeColorPicker(
                        appThemeMode = appThemeMode,
                        selectedKeyColor = appThemeKeyColor,
                        onKeyColorChange = onAppThemeKeyColorChange,
                    )
                }
            }
        }
        Material3ExpressiveSegmentPosition(index = interactionStartIndex, count = segmentCount) {
            SettingsToggleRow(
                title = stringResource(R.string.setting_material_floating_bottom_bar),
                summary = stringResource(R.string.setting_material_floating_bottom_bar_summary),
                checked = materialFloatingBottomBarEnabled,
                onCheckedChange = onMaterialFloatingBottomBarEnabledChange,
            )
        }
        Material3ExpressiveSegmentPosition(index = interactionStartIndex + 1, count = segmentCount) {
            SettingsToggleRow(
                title = stringResource(R.string.setting_material_haptics),
                summary = stringResource(R.string.setting_material_haptics_summary),
                checked = materialHapticsEnabled,
                onCheckedChange = onMaterialHapticsEnabledChange,
            )
        }
        Material3ExpressiveSegmentPosition(index = interactionStartIndex + 2, count = segmentCount) {
            SettingsToggleRow(
                title = stringResource(R.string.setting_material_switch_icons),
                summary = stringResource(R.string.setting_material_switch_icons_summary),
                checked = materialSwitchIconsEnabled,
                onCheckedChange = onMaterialSwitchIconsEnabledChange,
            )
        }
    }
}

@Composable
private fun Material3ExpressiveThemeColorPicker(
    appThemeMode: AppThemeMode,
    selectedKeyColor: Long?,
    onKeyColorChange: (Long?) -> Unit,
) {
    val expressiveSeedColors = listOf(
        0xFFF44336L, 0xFFE91E63L, 0xFF9C27B0L, 0xFF673AB7L, 0xFF3F51B5L,
        0xFF2196F3L, 0xFF00BCD4L, 0xFF009688L, 0xFF4FAF50L, 0xFFFFEB3BL,
        0xFFFFC107L, 0xFFFF9800L, 0xFF795548L, 0xFF607D8FL, 0xFFFF9CA8L,
    )
    val colors = if (selectedKeyColor != null && selectedKeyColor !in expressiveSeedColors) {
        listOf(selectedKeyColor) + expressiveSeedColors
    } else {
        expressiveSeedColors
    }
    val selectedColor = selectedKeyColor ?: 0xFF2196F3L
    val systemDark = isSystemInDarkTheme()
    val dark = when (appThemeMode.material3ExpressiveBrightness()) {
        AppThemeMode.Dark -> true
        AppThemeMode.Light -> false
        else -> systemDark
    }
    val paletteLabel = stringResource(R.string.setting_theme_palette)
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        MaterialText(
            text = paletteLabel,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            state = rememberLazyListState(colors.indexOf(selectedColor).coerceAtLeast(0)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            itemsIndexed(colors, key = { _, color -> color }) { _, option ->
                val swatch = remember(option, dark) {
                    dynamicColorScheme(
                        seedColor = Color(option),
                        isDark = dark,
                        style = PaletteStyle.TonalSpot,
                        specVersion = ColorSpec.SpecVersion.SPEC_2025,
                    )
                }
                val chosen = selectedColor == option
                MaterialSurface(
                    onClick = { onKeyColorChange(option.takeUnless { it == 0xFF2196F3L }) },
                    modifier = Modifier
                        .size(72.dp)
                        .semantics {
                            role = Role.RadioButton
                            selected = chosen
                            contentDescription = "$paletteLabel #${option.toString(16).takeLast(6).uppercase()}"
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = swatch.surfaceContainer,
                    border = if (chosen) BorderStroke(2.dp, swatch.primary) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(48.dp)) {
                            drawArc(swatch.primaryContainer, 180f, 180f, true)
                            drawArc(swatch.tertiaryContainer, 0f, 180f, true)
                        }
                        if (chosen) {
                            MaterialIcon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = swatch.onPrimaryContainer,
                            )
                        } else {
                            Canvas(Modifier.size(18.dp)) { drawCircle(swatch.primary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperOptionsPage(
    showFpsMonitor: Boolean,
    onShowFpsMonitorChange: (Boolean) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_section_display))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.setting_show_fps_monitor),
            summary = "",
            checked = showFpsMonitor,
            onCheckedChange = onShowFpsMonitorChange,
            hasDividerAbove = false,
            hasDividerBelow = false,
        )
    }

    if (LocalAppUiStyle.current != AppUiStyle.Material3Expressive) {
        SettingsSection(title = stringResource(R.string.settings_section_other))
        SettingsGroup {
            SettingsToggleRow(
                title = stringResource(R.string.setting_pop_follows_swipe_edge),
                summary = stringResource(R.string.setting_pop_follows_swipe_edge_summary),
                checked = false,
                onCheckedChange = {},
                hasDividerAbove = false,
                hasDividerBelow = false,
                enabled = false,
            )
        }
    }
}

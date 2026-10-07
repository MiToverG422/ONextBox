package com.mi.onextbox.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspStatus
import com.mi.onextbox.lsp.LsposedScopeRequester
import com.mi.onextbox.ui.common.ONextBoxLogo
import com.mi.onextbox.ui.common.AppLocale
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.ColorOsPopupMiniatureMaterial
import com.mi.onextbox.ui.common.ColorOsPopupPreviewShape
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.ConfigBackup
import com.mi.onextbox.ui.common.RootAccessInfo
import com.mi.onextbox.ui.common.RootAccessState
import com.mi.onextbox.ui.common.isMonet
import com.mi.onextbox.ui.common.queryRootAccess
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import com.mi.onextbox.ui.common.lspStatusText
import com.mi.onextbox.ui.common.LspMissingScopesNotice
import com.mi.onextbox.ui.onboarding.OnboardingPreferences.Agreement
import com.mi.onextbox.ui.onboarding.OnboardingPreferences.DraftStep
import com.mi.onextbox.ui.platform.findActivity
import com.mi.onextbox.ui.screens.FeatureLaunchIcon
import com.mi.onextbox.ui.screens.FeaturePageMode
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsTokens
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private val ActivationInk = Color(0xFF17181A)
private val ActivationMutedInk = Color(0xFF62666C)
private val ActivationBlue = Color(0xFF2B76F6)
private val ActivationGreen = Color(0xFF29A86B)
private val ActivationRed = Color(0xFFD94A4A)

private data class AgreementUi(
    val agreement: Agreement,
    @StringRes val title: Int,
    @StringRes val checkText: Int,
    @StringRes val summary: Int,
)

private data class ActivationPage(
    val step: DraftStep,
    val agreementIndex: Int = 0,
) {
    val sequence: Int
        get() = when (step) {
            DraftStep.Welcome -> 0
            DraftStep.Language -> 1
            DraftStep.Appearance -> 2
            DraftStep.Personalization -> 3
            DraftStep.Migration -> 4
            DraftStep.Agreements -> 5 + agreementIndex
            DraftStep.RootAccess -> 5 + Agreement.entries.size
            DraftStep.Lsposed -> 6 + Agreement.entries.size
            DraftStep.Complete -> 7 + Agreement.entries.size
        }
}

private sealed interface MigrationImportState {
    data object Idle : MigrationImportState
    data object Importing : MigrationImportState
    data object Imported : MigrationImportState
    data class Failed(val detail: String) : MigrationImportState
}

private val agreementRows = listOf(
    AgreementUi(
        Agreement.TermsAndRisk,
        R.string.onboarding_agreement_terms_title,
        R.string.onboarding_agreement_terms_check,
        R.string.onboarding_agreement_terms_summary,
    ),
    AgreementUi(
        Agreement.Privacy,
        R.string.onboarding_agreement_privacy_title,
        R.string.onboarding_agreement_privacy_check,
        R.string.onboarding_agreement_privacy_summary,
    ),
    AgreementUi(
        Agreement.PrivilegedAccess,
        R.string.onboarding_agreement_privileged_title,
        R.string.onboarding_agreement_privileged_check,
        R.string.onboarding_agreement_privileged_summary,
    ),
    AgreementUi(
        Agreement.EsimNetworkSecurity,
        R.string.onboarding_agreement_esim_title,
        R.string.onboarding_agreement_esim_check,
        R.string.onboarding_agreement_esim_summary,
    ),
    AgreementUi(
        Agreement.OpenSourceLicenses,
        R.string.onboarding_agreement_licenses_title,
        R.string.onboarding_agreement_licenses_check,
        R.string.onboarding_agreement_licenses_summary,
    ),
    AgreementUi(
        Agreement.DeviceAuthorization,
        R.string.onboarding_agreement_authorization_title,
        R.string.onboarding_agreement_authorization_check,
        R.string.onboarding_agreement_authorization_summary,
    ),
)

private fun android.content.Context.readAgreementText(agreement: Agreement): String =
    resources.openRawResource(agreement.localizedTextResource)
        .bufferedReader(Charsets.UTF_8)
        .use { reader -> reader.readText().trim() }

/**
 * Versioned first-run activation flow. It intentionally sits above the app navigation host so the
 * home screen and privileged probes cannot be composed before the agreements are accepted.
 */
@Composable
fun OnboardingScreen(
    appLanguageTag: String,
    onAppLanguageChange: (String) -> Unit,
    appUiStyle: AppUiStyle,
    onAppUiStyleChange: (AppUiStyle) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    featurePageNewStyleEnabled: Boolean,
    onFeaturePageNewStyleEnabledChange: (Boolean) -> Unit,
    featurePageVideoHidden: Boolean,
    onFeaturePageVideoHiddenChange: (Boolean) -> Unit,
    onDestinationPreparationRequested: () -> Unit,
    onActivationCommitted: (rootGranted: Boolean) -> Unit,
    onExitFinished: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val initialSnapshot = remember(context) { OnboardingPreferences.read(context) }
    var step by remember { mutableStateOf(initialSnapshot.draftStep) }
    var agreementIndex by remember(context) {
        mutableStateOf(OnboardingPreferences.readDraftAgreementIndex(context))
    }
    val agreementState = remember {
        mutableStateMapOf<Agreement, Boolean>().also { state ->
            Agreement.entries.forEach { agreement ->
                state[agreement] = agreement in initialSnapshot.acceptedAgreements
            }
        }
    }
    var allowBackgroundUpdates by remember {
        mutableStateOf(initialSnapshot.allowBackgroundUpdateChecks)
    }
    var rootInfo by remember(context) {
        mutableStateOf(readCachedRootAccessInfo(context) ?: RootAccessInfo(RootAccessState.NotGranted))
    }
    var rootVerifiedThisRun by remember { mutableStateOf(false) }
    val lspSnapshot by LsposedScopeRequester.states.collectAsState()
    var lspChecking by remember { mutableStateOf(false) }
    var lspRefreshPending by remember { mutableStateOf(false) }
    var lspRefreshFailed by remember { mutableStateOf(false) }
    var completionVerified by remember { mutableStateOf(false) }
    var lspOpenFailed by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }
    var completeButtonBoundsInRoot by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    val exitProgress = remember { Animatable(0f) }

    fun moveTo(newStep: DraftStep) {
        if (OnboardingPreferences.saveDraftStep(context, newStep)) {
            completionVerified = false
            step = newStep
        }
    }

    fun refreshLsp() {
        if (lspChecking) {
            lspRefreshPending = true
            return
        }
        lspChecking = true
        lspRefreshFailed = false
        completionVerified = false
        scope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    LsposedScopeRequester.refreshSnapshot(context)
                }
                if (step == DraftStep.Complete && !isExiting) {
                    if (refreshed.canContinue) {
                        completionVerified = true
                    } else {
                        moveTo(DraftStep.Lsposed)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                lspRefreshFailed = true
                if (step == DraftStep.Complete && !isExiting) moveTo(DraftStep.Lsposed)
            } finally {
                lspChecking = false
                if (isActive && lspRefreshPending) {
                    lspRefreshPending = false
                    if (step == DraftStep.Lsposed || step == DraftStep.Complete) refreshLsp()
                }
            }
        }
    }

    BackHandler(enabled = isExiting || step != DraftStep.Welcome) {
        if (!isExiting) {
            when (step) {
                DraftStep.Welcome -> Unit
                DraftStep.Language -> moveTo(DraftStep.Welcome)
                DraftStep.Appearance -> moveTo(DraftStep.Language)
                DraftStep.Personalization -> moveTo(DraftStep.Appearance)
                DraftStep.Migration -> moveTo(DraftStep.Personalization)
                DraftStep.Agreements -> {
                    if (agreementIndex > 0) {
                        agreementIndex -= 1
                        OnboardingPreferences.saveDraftAgreementIndex(context, agreementIndex)
                    } else {
                        moveTo(DraftStep.Migration)
                    }
                }
                DraftStep.RootAccess -> moveTo(DraftStep.Agreements)
                DraftStep.Lsposed -> moveTo(DraftStep.RootAccess)
                DraftStep.Complete -> moveTo(DraftStep.Lsposed)
            }
        }
    }

    LaunchedEffect(context, step) {
        if (step == DraftStep.Lsposed) refreshLsp()
        if (step == DraftStep.Complete) {
            onDestinationPreparationRequested()
            refreshLsp()
        }
    }
    DisposableEffect(lifecycleOwner, step) {
        var skipCurrentResume = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                (step == DraftStep.Lsposed || step == DraftStep.Complete)) {
                if (skipCurrentResume) skipCurrentResume = false
                else refreshLsp()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(step, lspSnapshot.moduleState, lspSnapshot.status, lspSnapshot.isRefreshing) {
        if (step == DraftStep.Complete && completionVerified && !isExiting && !lspChecking &&
            !lspSnapshot.isRefreshing &&
            lspSnapshot.status != LspStatus.CHECKING &&
            !lspSnapshot.canContinue) {
            moveTo(DraftStep.Lsposed)
        }
    }

    // LocalContext is deliberately re-localized in OnboardingActivity without recreating it.
    // The View still owns the real Activity context used for window/inset operations.
    val activity = LocalView.current.context.findActivity()
    DisposableEffect(activity) {
        activity?.window?.let { window ->
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { window ->
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    val controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Light) }
    COUITheme(controller = controller) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // C17 draws the final hollow path with PorterDuff.SRC over the already-rendered
                // page. The offscreen layer is essential: a sibling Canvas using normal SrcOver
                // can only paint white and cannot replace completed page pixels with transparency.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (!isExiting) return@drawWithContent

                    val progress = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
                        .transform(exitProgress.value.coerceIn(0f, 1f))
                    val bounds = completeButtonBoundsInRoot
                    val startHalfWidth = bounds?.width?.div(2f) ?: 80.dp.toPx()
                    val startHalfHeight = bounds?.height?.div(2f) ?: 40.dp.toPx()
                    val startCenterX = bounds?.center?.x ?: size.width / 2f
                    val startCenterY = bounds?.center?.y ?: size.height - 160.dp.toPx()
                    val targetCenterY = size.height / 2f
                    val targetRadius = kotlin.math.sqrt(
                        (startCenterX * startCenterX) +
                            (targetCenterY * targetCenterY),
                    )
                    val halfWidth = startHalfWidth +
                        (targetRadius - startHalfWidth) * progress
                    var halfHeight = startHalfHeight +
                        (targetRadius - startHalfHeight) * progress
                    if (halfWidth > halfHeight) halfHeight *= 1f + progress
                    if (halfHeight > halfWidth) halfHeight = halfWidth
                    val centerY = startCenterY -
                        (startCenterY - targetCenterY) * progress
                    val sourceAlpha = (1f - 1.5f * progress).coerceIn(0f, 1f)

                    drawRoundRect(
                        color = if (sourceAlpha > 0f) {
                            Color.White.copy(alpha = sourceAlpha)
                        } else {
                            Color.Transparent
                        },
                        topLeft = Offset(startCenterX - halfWidth, centerY - halfHeight),
                        size = Size(halfWidth * 2f, halfHeight * 2f),
                        cornerRadius = CornerRadius(halfWidth, halfWidth),
                        blendMode = if (sourceAlpha > 0f) BlendMode.Src else BlendMode.Clear,
                    )
                },
        ) {
            ActivationBackground()
            val activePage = ActivationPage(
                step = step,
                agreementIndex = if (step == DraftStep.Agreements) agreementIndex else 0,
            )
            AnimatedContent(
                targetState = activePage,
                transitionSpec = { c17ActivationPageTransition() },
                contentKey = { page -> page },
                label = "c17ActivationPage",
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // BootReg moves two opaque Activity/page surfaces in parallel. Keeping the
                    // background only below AnimatedContent makes the incoming page transparent,
                    // which lets the outgoing video, title and button bleed through it.
                    ActivationBackground()
                    when (page.step) {
                        DraftStep.Welcome -> WelcomePage(
                            onNext = { moveTo(DraftStep.Language) },
                        )
                        DraftStep.Language -> LanguagePage(
                            selectedLanguageTag = appLanguageTag,
                            onLanguageSelected = onAppLanguageChange,
                            onNext = { moveTo(DraftStep.Appearance) },
                            onBack = { moveTo(DraftStep.Welcome) },
                        )
                        DraftStep.Appearance -> AppearancePage(
                            selectedUiStyle = appUiStyle,
                            onUiStyleSelected = onAppUiStyleChange,
                            selectedThemeMode = appThemeMode,
                            onThemeModeSelected = onAppThemeModeChange,
                            onNext = { moveTo(DraftStep.Personalization) },
                            onBack = { moveTo(DraftStep.Language) },
                        )
                        DraftStep.Personalization -> PersonalizationPage(
                            appUiStyle = appUiStyle,
                            appThemeMode = appThemeMode,
                            newStyleEnabled = featurePageNewStyleEnabled,
                            onNewStyleEnabledChange = onFeaturePageNewStyleEnabledChange,
                            videoBackgroundEnabled = !featurePageVideoHidden,
                            onVideoBackgroundEnabledChange = { enabled ->
                                onFeaturePageVideoHiddenChange(!enabled)
                            },
                            onNext = { moveTo(DraftStep.Migration) },
                            onBack = { moveTo(DraftStep.Appearance) },
                        )
                        DraftStep.Migration -> MigrationPage(
                            onNext = { moveTo(DraftStep.Agreements) },
                            onBack = { moveTo(DraftStep.Personalization) },
                        )
                        DraftStep.Agreements -> AgreementSequencePage(
                            agreement = agreementRows[page.agreementIndex],
                            pageIndex = page.agreementIndex,
                            pageCount = agreementRows.size,
                            accepted = agreementState[agreementRows[page.agreementIndex].agreement] == true,
                            onAgreementChanged = { accepted ->
                                val agreement = agreementRows[page.agreementIndex].agreement
                                if (OnboardingPreferences.setAgreementAccepted(
                                        context,
                                        agreement,
                                        accepted,
                                    )
                                ) {
                                    agreementState[agreement] = accepted
                                }
                            },
                            onNext = {
                                if (agreementIndex < agreementRows.lastIndex) {
                                    agreementIndex += 1
                                    OnboardingPreferences.saveDraftAgreementIndex(
                                        context,
                                        agreementIndex,
                                    )
                                } else {
                                    moveTo(DraftStep.RootAccess)
                                }
                            },
                            onBack = {
                                if (agreementIndex > 0) {
                                    agreementIndex -= 1
                                    OnboardingPreferences.saveDraftAgreementIndex(
                                        context,
                                        agreementIndex,
                                    )
                                } else {
                                    moveTo(DraftStep.Migration)
                                }
                            },
                        )
                        DraftStep.RootAccess -> RootAccessPage(
                            rootInfo = rootInfo,
                            verified = rootVerifiedThisRun,
                            onPrimaryAction = {
                                if (rootVerifiedThisRun) {
                                    moveTo(DraftStep.Lsposed)
                                } else if (rootInfo.state != RootAccessState.Checking) {
                                    scope.launch {
                                        rootInfo = RootAccessInfo(RootAccessState.Checking)
                                        val result = queryRootAccess(context)
                                        rootInfo = result
                                        rootVerifiedThisRun = result.state == RootAccessState.Granted
                                    }
                                }
                            },
                            onBack = { moveTo(DraftStep.Agreements) },
                        )
                        DraftStep.Lsposed -> LsposedPage(
                            rootReady = rootVerifiedThisRun || rootInfo.state == RootAccessState.Granted,
                            snapshot = lspSnapshot,
                            checking = lspChecking,
                            refreshFailed = lspRefreshFailed,
                            openFailed = lspOpenFailed,
                            onOpenOrNext = {
                                if (!lspRefreshFailed && lspSnapshot.canContinue) {
                                    moveTo(DraftStep.Complete)
                                } else {
                                    scope.launch {
                                        lspOpenFailed = false
                                        val result = withContext(Dispatchers.IO) {
                                            LsposedScopeRequester.openManager(context)
                                        }
                                        lspOpenFailed =
                                            result.status == LsposedScopeRequester.ManagerOpenStatus.FAILED
                                    }
                                }
                            },
                            onRefresh = { refreshLsp() },
                            onBack = { moveTo(DraftStep.RootAccess) },
                        )
                        DraftStep.Complete -> CompletePage(
                            saveFailed = saveFailed,
                            exitProgress = exitProgress.value,
                            isExiting = isExiting,
                            canFinish = completionVerified && !lspChecking && !lspRefreshFailed &&
                                lspSnapshot.status != LspStatus.CHECKING && lspSnapshot.canContinue,
                            onButtonBoundsChanged = { completeButtonBoundsInRoot = it },
                            onFinish = {
                                if (!isExiting && completionVerified && !lspChecking &&
                                    !lspRefreshFailed && lspSnapshot.status != LspStatus.CHECKING &&
                                    lspSnapshot.canContinue) {
                                    // Prepare the destination underneath the completion animation.
                                    onDestinationPreparationRequested()
                                    isExiting = true
                                    saveFailed = false
                                    scope.launch {
                                        var committed = false
                                        try {
                                            exitProgress.snapTo(0f)
                                            exitProgress.animateTo(
                                                1f,
                                                tween(
                                                    durationMillis = 1_000,
                                                    easing = LinearEasing,
                                                ),
                                            )
                                            val refreshed = withContext(Dispatchers.IO) {
                                                LsposedScopeRequester.refreshSnapshot(context)
                                            }
                                            if (!refreshed.canContinue) {
                                                moveTo(DraftStep.Lsposed)
                                                return@launch
                                            }
                                            val saved = OnboardingPreferences.complete(
                                                context,
                                                allowBackgroundUpdates,
                                            )
                                            saveFailed = !saved
                                            if (saved) {
                                                committed = true
                                                onActivationCommitted(
                                                    rootVerifiedThisRun ||
                                                        rootInfo.state == RootAccessState.Granted,
                                                )
                                                withFrameNanos { }
                                                onExitFinished()
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            lspRefreshFailed = true
                                            moveTo(DraftStep.Lsposed)
                                        } finally {
                                            if (!committed) {
                                                isExiting = false
                                                exitProgress.snapTo(0f)
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<ActivationPage>.c17ActivationPageTransition(): ContentTransform {
    val easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
    val forward = targetState.sequence > initialState.sequence
    val duration = 350

    if (initialState.step == DraftStep.Welcome || targetState.step == DraftStep.Welcome) {
        val enter: EnterTransition = slideInHorizontally(
            animationSpec = tween(durationMillis = duration, easing = easing),
            initialOffsetX = { width -> if (forward) width else -(width * 0.3f).toInt() },
        )
        val exit: ExitTransition = slideOutHorizontally(
            animationSpec = tween(durationMillis = duration, easing = easing),
            targetOffsetX = { width -> if (forward) -(width * 0.3f).toInt() else width },
        )
        return enter togetherWith exit
    }

    val enter = slideInHorizontally(
        animationSpec = tween(durationMillis = duration, easing = easing),
        initialOffsetX = { width -> if (forward) width else -width },
    ) + scaleIn(
        animationSpec = tween(durationMillis = duration, easing = easing),
        initialScale = 0.9f,
    )
    val exit = slideOutHorizontally(
        animationSpec = tween(durationMillis = duration, easing = easing),
        targetOffsetX = { width -> if (forward) -width else width },
    ) + scaleOut(
        animationSpec = tween(durationMillis = duration, easing = easing),
        targetScale = 0.9f,
    )
    return enter togetherWith exit
}

@Composable
private fun ActivationBackground() {
    Image(
        painter = painterResource(R.drawable.c17_guide_page_image_background),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    val firstTitle = stringResource(R.string.onboarding_welcome_first)
    val secondTitle = stringResource(R.string.onboarding_welcome_second)
    var startupCompleted by remember { mutableStateOf(false) }
    var backgroundFirstFrame by remember { mutableStateOf(false) }
    var slideDownCompleted by remember { mutableStateOf(false) }
    var guideContentVisible by remember { mutableStateOf(false) }
    var leavingGuide by remember { mutableStateOf(false) }
    var shownTitle by remember { mutableStateOf("") }
    val panelOffset = remember { Animatable(0f) }
    val panelCorner = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(startupCompleted) {
        if (!startupCompleted) return@LaunchedEffect
        val slideEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        coroutineScope {
            launch {
                panelCorner.animateTo(
                    32f,
                    tween(durationMillis = 300, easing = slideEasing),
                )
            }
            launch {
                panelOffset.animateTo(
                    72f,
                    tween(durationMillis = 540, easing = slideEasing),
                )
                panelOffset.animateTo(
                    62f,
                    tween(durationMillis = 180, easing = slideEasing),
                )
                panelOffset.animateTo(
                    72f,
                    tween(durationMillis = 180, easing = slideEasing),
                )
            }
        }
        slideDownCompleted = true
    }
    LaunchedEffect(slideDownCompleted, firstTitle, secondTitle) {
        if (!slideDownCompleted) return@LaunchedEffect
        val secondParts = secondTitle.split('\n', limit = 2)
        val secondLead = secondParts.first() + if (secondParts.size > 1) "\n" else ""
        val characterCount = (firstTitle.length + secondTitle.length).coerceAtLeast(1)
        val intervalMillis = ((4_500L - 950L - 50L - 200L) / characterCount)
            .coerceAtLeast(22L)
        firstTitle.forEachIndexed { index, _ ->
            shownTitle = firstTitle.take(index + 1)
            delay(intervalMillis)
        }
        delay(950)
        shownTitle = ""
        delay(50)
        secondLead.forEachIndexed { index, _ ->
            shownTitle = secondLead.take(index + 1)
            delay(intervalMillis)
        }
        if (secondParts.size > 1) delay(200)
        secondParts.getOrNull(1).orEmpty().forEach { character ->
            shownTitle += character
            delay(intervalMillis)
        }
        delay(100)
        coroutineScope {
            launch {
                panelOffset.animateTo(
                    0f,
                    tween(
                        durationMillis = 600,
                        easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
                    ),
                )
            }
            launch {
                titleAlpha.animateTo(
                    0f,
                    tween(
                        durationMillis = 400,
                        easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
                    ),
                )
            }
        }
        delay(48)
        guideContentVisible = true
    }
    LaunchedEffect(leavingGuide) {
        if (!leavingGuide) return@LaunchedEffect
        // Commit the hidden TextureView before AnimatedContent retains the outgoing page. A real
        // frame boundary is required here; a fixed delay can still land in the same frame.
        withFrameNanos { }
        onNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = panelOffset.value.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = panelCorner.value.dp,
                        topEnd = panelCorner.value.dp,
                    ),
                ),
        ) {
            Image(
                painter = painterResource(R.drawable.c17_guide_page_image_background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            if (startupCompleted && !guideContentVisible) {
                BootregVideoPlayer(
                    videoResId = R.raw.c17_guide_video_background,
                    playCount = Int.MAX_VALUE,
                    scaleMode = BootregVideoScaleMode.Stretch,
                    backgroundColor = Color.Transparent,
                    onFirstFrame = { backgroundFirstFrame = true },
                    onError = { _, _ -> backgroundFirstFrame = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (guideContentVisible) {
                BootregVideoPlayer(
                    videoResId = R.raw.c17_guide_content_animation,
                    playCount = 5,
                    scaleMode = BootregVideoScaleMode.Stretch,
                    autoPlay = !leavingGuide,
                    visible = !leavingGuide,
                    backgroundColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .align(Alignment.TopCenter),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    color = ActivationInk,
                    fontSize = 36.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp)
                        .offset(y = 92.dp),
                )
                C17MorphArrowButton(
                    visible = true,
                    enabled = !leavingGuide,
                    contentDescription = stringResource(R.string.onboarding_get_started),
                    onClick = { leavingGuide = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            Text(
                text = shownTitle,
                color = ActivationInk,
                fontSize = 40.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                minLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp, start = 32.dp, end = 32.dp)
                    .graphicsLayer(alpha = titleAlpha.value),
            )
        }
        if (!backgroundFirstFrame) {
            BootregVideoPlayer(
                videoResId = R.raw.c17_guide_first_startup,
                playCount = 1,
                scaleMode = BootregVideoScaleMode.Stretch,
                backgroundColor = Color.Black,
                onCompleted = { startupCompleted = true },
                onError = { _, _ -> startupCompleted = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LanguagePage(
    selectedLanguageTag: String,
    onLanguageSelected: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val languageOptions = AppLocale.options(LocalContext.current)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        ActivationBackButton(onBack = onBack)
        Spacer(modifier = Modifier.height(26.dp))
        PageHeading(
            title = stringResource(R.string.onboarding_language_title),
            summary = stringResource(R.string.onboarding_language_summary),
        )
        Spacer(modifier = Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppUiTokens.CardCornerRadius,
            ) {
                languageOptions.forEachIndexed { index, (tag, label) ->
                    if (index > 0) SettingsDivider()
                    BasicComponent(
                        title = label,
                        summary = null,
                        insideMargin = SettingsTokens.RowInsideMargin,
                        endActions = {
                            C17RadioIndicator(selected = selectedLanguageTag == tag)
                        },
                        enabled = true,
                        onClick = { onLanguageSelected(tag) },
                    )
                }
            }
        }
        ActivationCapsuleButton(
            label = stringResource(R.string.onboarding_next),
            visible = true,
            enabled = true,
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun AppearancePage(
    selectedUiStyle: AppUiStyle,
    onUiStyleSelected: (AppUiStyle) -> Unit,
    selectedThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val uiStyles = listOf(
        AppUiStyle.ColorOs to stringResource(R.string.ui_style_coloros),
        AppUiStyle.Material3Expressive to stringResource(R.string.ui_style_material3_expressive),
    )
    val themeModes = listOf(
        AppThemeMode.System to stringResource(R.string.theme_mode_system),
        AppThemeMode.Light to stringResource(R.string.theme_mode_light),
        AppThemeMode.Dark to stringResource(R.string.theme_mode_dark),
    )
    val selectedBrightness = OnboardingAppearance.brightnessOf(selectedThemeMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        ActivationBackButton(onBack = onBack)
        Spacer(modifier = Modifier.height(26.dp))
        PageHeading(
            title = stringResource(R.string.onboarding_appearance_title),
            summary = stringResource(R.string.onboarding_appearance_summary),
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppearanceStylePreview(
            uiStyle = selectedUiStyle,
            themeMode = selectedThemeMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(194.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.setting_ui_style),
                color = ActivationMutedInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppUiTokens.CardCornerRadius,
            ) {
                uiStyles.forEachIndexed { index, (style, label) ->
                    if (index > 0) SettingsDivider()
                    BasicComponent(
                        title = label,
                        summary = null,
                        insideMargin = SettingsTokens.RowInsideMargin,
                        endActions = {
                            C17RadioIndicator(selected = selectedUiStyle == style)
                        },
                        onClick = { onUiStyleSelected(style) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.setting_theme_mode),
                color = ActivationMutedInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppUiTokens.CardCornerRadius,
            ) {
                themeModes.forEachIndexed { index, (mode, label) ->
                    if (index > 0) SettingsDivider()
                    BasicComponent(
                        title = label,
                        summary = null,
                        insideMargin = SettingsTokens.RowInsideMargin,
                        endActions = {
                            C17RadioIndicator(selected = selectedBrightness == mode)
                        },
                        onClick = { onThemeModeSelected(mode) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.onboarding_appearance_theme_note),
                color = ActivationMutedInk,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
        ActivationCapsuleButton(
            label = stringResource(R.string.onboarding_next),
            visible = true,
            enabled = true,
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun AppearanceStylePreview(
    uiStyle: AppUiStyle,
    themeMode: AppThemeMode,
    modifier: Modifier = Modifier,
) {
    val dark = when (OnboardingAppearance.brightnessOf(themeMode)) {
        AppThemeMode.Dark -> true
        AppThemeMode.Light -> false
        else -> isSystemInDarkTheme()
    }
    AnimatedContent(
        targetState = uiStyle,
        modifier = modifier.clip(RoundedCornerShape(28.dp)),
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.96f)) togetherWith
                (fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 1.03f))
        },
        label = "activationUiStylePreview",
    ) { style ->
        MiniAppearanceSettings(style = style, dark = dark, themeMode = themeMode)
    }
}

@Composable
private fun MiniAppearanceSettings(
    style: AppUiStyle,
    dark: Boolean,
    themeMode: AppThemeMode,
) {
    val material = style == AppUiStyle.Material3Expressive
    val background = when {
        material && dark -> Color(0xFF19151F)
        material -> Color(0xFFF4EFF7)
        dark -> Color(0xFF09090B)
        else -> Color(0xFFF2F3F5)
    }
    val surface = when {
        material && dark -> Color(0xFF312B38)
        material -> Color(0xFFE7E0EB)
        dark -> Color(0xFF1E1E20)
        else -> Color.White
    }
    val menuSurface = when {
        material && dark -> Color(0xFF40364B)
        material -> Color(0xFFF0E8F4)
        dark -> Color(0xFF19191B)
        else -> Color(0xFFFDFDFE)
    }
    val foreground = if (dark) Color(0xFFF7F5F8) else ActivationInk
    val secondary = if (dark) Color(0xFFB8B2BE) else ActivationMutedInk
    val accent = if (material) {
        if (dark) Color(0xFFD5BBFF) else Color(0xFF6750A4)
    } else {
        if (dark) Color(0xFF78AAFF) else ActivationBlue
    }
    // These are the same spring targets used by ColorOsPopup and Material 3's
    // DropdownMenuPopupContent. The preview is clipped to its miniature screen,
    // so it cannot mount a real platform Popup window here.
    val closedScale = if (material) 0.8f else 0f
    val menuScale = remember(style) { Animatable(closedScale) }
    val menuAlpha = remember(style) { Animatable(0f) }
    LaunchedEffect(style) {
        while (isActive) {
            delay(850)
            coroutineScope {
                launch {
                    menuScale.animateTo(
                        1f,
                        if (material) spring(dampingRatio = 0.6f, stiffness = 800f)
                        else spring(dampingRatio = 0.8f, stiffness = 322.27f),
                    )
                }
                launch {
                    menuAlpha.animateTo(
                        1f,
                        if (material) spring(dampingRatio = 1f, stiffness = 3800f)
                        else spring(dampingRatio = 0.8f, stiffness = 322.27f),
                    )
                }
            }
            delay(1_100)
            coroutineScope {
                launch {
                    menuScale.animateTo(
                        closedScale,
                        if (material) spring(dampingRatio = 0.6f, stiffness = 800f)
                        else spring(dampingRatio = 1f, stiffness = 438.65f),
                    )
                }
                launch {
                    menuAlpha.animateTo(
                        0f,
                        if (material) spring(dampingRatio = 1f, stiffness = 3800f)
                        else spring(dampingRatio = 1f, stiffness = 631.65f),
                    )
                }
            }
            delay(650)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (material) 3.dp else 11.dp,
                    end = if (material) 3.dp else 11.dp,
                    top = if (material) 6.dp else 9.dp,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (material) 38.dp else 35.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (material) 32.dp else 27.dp)
                        .then(
                            if (material) Modifier else Modifier
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            if (dark) Color(0xFF333336) else Color.White,
                                            if (dark) Color(0xFF202023) else Color(0xFFF1F1F2),
                                        ),
                                    ),
                                )
                                .drawBehind {
                                    drawCircle(
                                        color = if (dark) Color.White.copy(alpha = 0.16f)
                                        else Color.Black.copy(alpha = 0.10f),
                                        style = Stroke(width = 0.6.dp.toPx()),
                                    )
                                }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = COUIIcons.Back,
                        contentDescription = null,
                        tint = foreground,
                        modifier = Modifier.size(if (material) 19.dp else 16.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.tab_settings),
                    modifier = Modifier.padding(start = 7.dp),
                    color = foreground,
                    fontSize = if (material) 16.sp else 17.sp,
                    fontWeight = if (material) FontWeight.SemiBold else FontWeight(550),
                )
            }
            Spacer(modifier = Modifier.height(if (material) 5.dp else 7.dp))
            Text(
                text = stringResource(R.string.settings_section_display),
                color = secondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = if (material) 14.dp else 12.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (material) Modifier.padding(horizontal = 7.dp) else Modifier)
                    .then(
                        if (material) Modifier else Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(surface)
                    ),
                verticalArrangement = if (material) Arrangement.spacedBy(2.dp) else Arrangement.Top,
            ) {
                MiniAppearanceRow(
                    title = stringResource(R.string.setting_ui_style),
                    value = stringResource(
                        if (material) R.string.ui_style_material3_expressive
                        else R.string.ui_style_coloros,
                    ),
                    material = material,
                    surface = surface,
                    foreground = foreground,
                    secondary = secondary,
                    index = 0,
                )
                if (!material) MiniAppearanceDivider(dark)
                MiniAppearanceRow(
                    title = stringResource(
                        if (material) R.string.setting_app_language else R.string.setting_theme_mode,
                    ),
                    summary = if (material) stringResource(R.string.setting_app_language_summary) else null,
                    value = if (material) stringResource(R.string.language_system) else
                        stringResource(
                            when (OnboardingAppearance.brightnessOf(themeMode)) {
                                AppThemeMode.Light -> R.string.theme_mode_light
                                AppThemeMode.Dark -> R.string.theme_mode_dark
                                else -> R.string.theme_mode_system
                            },
                        ),
                    material = material,
                    surface = surface,
                    foreground = foreground,
                    secondary = secondary,
                    index = 1,
                )
                if (!material) MiniAppearanceDivider(dark)
                MiniAppearanceRow(
                    title = stringResource(
                        if (material) R.string.setting_theme_mode else R.string.setting_app_language,
                    ),
                    summary = if (material) null else stringResource(R.string.setting_app_language_summary),
                    value = if (material) stringResource(
                        when (OnboardingAppearance.brightnessOf(themeMode)) {
                            AppThemeMode.Light -> R.string.theme_mode_light
                            AppThemeMode.Dark -> R.string.theme_mode_dark
                            else -> R.string.theme_mode_system
                        },
                    ) else stringResource(R.string.language_system),
                    material = material,
                    surface = surface,
                    foreground = foreground,
                    secondary = secondary,
                    index = 2,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                // The real COUI popup touches the style row's lower edge; Material 3
                // anchors below the press position inside that row.
                .padding(end = 13.dp, top = if (material) 94.dp else 104.dp)
                .width(if (material) 194.dp else 168.dp)
                .graphicsLayer {
                    alpha = menuAlpha.value
                    scaleX = menuScale.value
                    scaleY = menuScale.value
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    shadowElevation = 8.dp.toPx() * menuAlpha.value
                    shape = if (material) RoundedCornerShape(18.dp) else ColorOsPopupPreviewShape
                    clip = true
                }
                .background(menuSurface),
        ) {
            if (!material) {
                ColorOsPopupMiniatureMaterial(
                    darkTheme = dark,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (material) 3.dp else 6.dp, vertical = if (material) 3.dp else 5.dp),
                verticalArrangement = if (material) Arrangement.spacedBy(2.dp) else Arrangement.Top,
            ) {
            val options = listOf(
                AppUiStyle.ColorOs to stringResource(R.string.ui_style_coloros),
                AppUiStyle.Material3Expressive to stringResource(R.string.ui_style_material3_expressive),
            )
            options.forEachIndexed { index, (option, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (material) 34.dp else 30.dp)
                        .then(
                            if (!material) Modifier else Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (index == 0) 15.dp else 4.dp,
                                        topEnd = if (index == 0) 15.dp else 4.dp,
                                        bottomStart = if (index == options.lastIndex) 15.dp else 4.dp,
                                        bottomEnd = if (index == options.lastIndex) 15.dp else 4.dp,
                                    ),
                                )
                                .background(
                                    if (option == style) accent.copy(alpha = if (dark) 0.17f else 0.13f)
                                    else surface,
                                )
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (material) {
                        Box(modifier = Modifier.width(22.dp)) {
                            if (option == style) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = label,
                        color = if (!material && option == style) accent else foreground,
                        fontSize = 11.sp,
                        fontWeight = if (option == style) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!material && option == style) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val x = size.width / 24f
                            val y = size.height / 24f
                            val check = Path().apply {
                                moveTo(9f * x, 12.435f * y)
                                lineTo(13.388f * x, 17f * y)
                                lineTo(23f * x, 7f * y)
                            }
                            drawPath(
                                path = check,
                                color = accent,
                                style = Stroke(
                                    width = 1.05.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MiniAppearanceRow(
    title: String,
    value: String?,
    summary: String? = null,
    material: Boolean,
    surface: Color,
    foreground: Color,
    secondary: Color,
    index: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (summary == null) 36.dp else 44.dp)
            .then(
                if (material) Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (index == 0) 16.dp else 4.dp,
                            topEnd = if (index == 0) 16.dp else 4.dp,
                            bottomStart = if (index == 2) 16.dp else 4.dp,
                            bottomEnd = if (index == 2) 16.dp else 4.dp,
                        ),
                    )
                    .background(surface)
                else Modifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    color = secondary,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                color = secondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(text = "⌄", color = secondary, fontSize = 14.sp)
    }
}

@Composable
private fun MiniAppearanceDivider(dark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(0.5.dp)
            .background(if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
    )
}

@Composable
private fun PersonalizationPage(
    appUiStyle: AppUiStyle,
    appThemeMode: AppThemeMode,
    newStyleEnabled: Boolean,
    onNewStyleEnabledChange: (Boolean) -> Unit,
    videoBackgroundEnabled: Boolean,
    onVideoBackgroundEnabledChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val systemInDarkTheme = isSystemInDarkTheme()
    val videoBackgroundAvailable = appUiStyle == AppUiStyle.ColorOs &&
        newStyleEnabled &&
        !appThemeMode.isMonet &&
        (
            appThemeMode == AppThemeMode.Dark ||
                (appThemeMode == AppThemeMode.System && systemInDarkTheme)
            )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        ActivationBackButton(onBack = onBack)
        Spacer(modifier = Modifier.height(20.dp))
        PageHeading(
            title = stringResource(R.string.onboarding_personalization_title),
            summary = stringResource(R.string.onboarding_personalization_summary),
        )
        Spacer(modifier = Modifier.height(16.dp))
        FeatureStylePreview(
            newStyleEnabled = newStyleEnabled,
            videoBackgroundEnabled = videoBackgroundEnabled && videoBackgroundAvailable,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.onboarding_feature_style_section),
                color = ActivationMutedInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppUiTokens.CardCornerRadius,
            ) {
                BasicComponent(
                    title = stringResource(R.string.onboarding_feature_style_new),
                    summary = stringResource(R.string.onboarding_feature_style_new_summary),
                    insideMargin = SettingsTokens.RowInsideMargin,
                    endActions = { C17RadioIndicator(selected = newStyleEnabled) },
                    onClick = { onNewStyleEnabledChange(true) },
                )
                SettingsDivider()
                BasicComponent(
                    title = stringResource(R.string.onboarding_feature_style_classic),
                    summary = stringResource(R.string.onboarding_feature_style_classic_summary),
                    insideMargin = SettingsTokens.RowInsideMargin,
                    endActions = { C17RadioIndicator(selected = !newStyleEnabled) },
                    onClick = { onNewStyleEnabledChange(false) },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.onboarding_feature_background_section),
                color = ActivationMutedInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppUiTokens.CardCornerRadius,
            ) {
                BasicComponent(
                    title = stringResource(R.string.onboarding_feature_video_title),
                    summary = stringResource(R.string.onboarding_feature_video_summary),
                    insideMargin = SettingsTokens.RowInsideMargin,
                    enabled = videoBackgroundAvailable,
                    endActions = {
                        Checkbox(
                            state = ToggleableState(videoBackgroundAvailable && videoBackgroundEnabled),
                            onClick = {
                                if (videoBackgroundAvailable) {
                                    onVideoBackgroundEnabledChange(!videoBackgroundEnabled)
                                }
                            },
                        )
                    },
                    onClick = {
                        if (videoBackgroundAvailable) {
                            onVideoBackgroundEnabledChange(!videoBackgroundEnabled)
                        }
                    },
                )
            }
            Text(
                text = stringResource(R.string.onboarding_feature_coui_only_note),
                color = ActivationMutedInk,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
        ActivationCapsuleButton(
            label = stringResource(R.string.onboarding_next),
            visible = true,
            enabled = true,
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun FeatureStylePreview(
    newStyleEnabled: Boolean,
    videoBackgroundEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val workspaceProgress = remember { Animatable(0f) }
    val firstWindowProgress = remember { Animatable(0f) }
    val secondWindowProgress = remember { Animatable(0f) }
    val classicPageProgress = remember { Animatable(0f) }
    var firstWindowActive by remember { mutableStateOf(false) }
    var secondWindowActive by remember { mutableStateOf(false) }
    var previewBoundsInRoot by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val iconBoundsInRoot = remember { mutableStateMapOf<FeaturePageMode, androidx.compose.ui.geometry.Rect>() }

    LaunchedEffect(newStyleEnabled) {
        firstWindowActive = false
        secondWindowActive = false
        workspaceProgress.snapTo(0f)
        firstWindowProgress.snapTo(0f)
        secondWindowProgress.snapTo(0f)
        classicPageProgress.snapTo(0f)

        if (newStyleEnabled) {
            while (isActive) {
                delay(720)
                firstWindowActive = true
                coroutineScope {
                    launch {
                        firstWindowProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = PREVIEW_OPEN_GEOMETRY_DAMPING,
                                stiffness = PREVIEW_OPEN_GEOMETRY_STIFFNESS,
                                visibilityThreshold = 0.0005f,
                            ),
                        )
                    }
                    launch {
                        workspaceProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = PREVIEW_OPEN_WORKSPACE_DAMPING,
                                stiffness = PREVIEW_OPEN_WORKSPACE_STIFFNESS,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    }
                }
                delay(420)
                coroutineScope {
                    val firstClose = launch {
                        try {
                            firstWindowProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = PREVIEW_CLOSE_GEOMETRY_DAMPING,
                                    stiffness = PREVIEW_CLOSE_GEOMETRY_STIFFNESS,
                                    visibilityThreshold = 0.0005f,
                                ),
                            )
                        } finally {
                            firstWindowActive = false
                        }
                    }
                    launch {
                        workspaceProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = PREVIEW_CLOSE_WORKSPACE_DAMPING,
                                stiffness = PREVIEW_CLOSE_WORKSPACE_STIFFNESS,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    }
                    // Start the second page while the first close leash is still alive. This is
                    // the same ownership overlap used by the real C17-style parallel transition.
                    delay(125)
                    secondWindowActive = true
                    val secondOpen = launch {
                        secondWindowProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = PREVIEW_OPEN_GEOMETRY_DAMPING,
                                stiffness = PREVIEW_OPEN_GEOMETRY_STIFFNESS,
                                visibilityThreshold = 0.0005f,
                            ),
                        )
                    }
                    launch {
                        workspaceProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = PREVIEW_OPEN_WORKSPACE_DAMPING,
                                stiffness = PREVIEW_OPEN_WORKSPACE_STIFFNESS,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    }
                    firstClose.join()
                    secondOpen.join()
                }
                delay(460)
                coroutineScope {
                    launch {
                        try {
                            secondWindowProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = PREVIEW_CLOSE_GEOMETRY_DAMPING,
                                    stiffness = PREVIEW_CLOSE_GEOMETRY_STIFFNESS,
                                    visibilityThreshold = 0.0005f,
                                ),
                            )
                        } finally {
                            secondWindowActive = false
                        }
                    }
                    launch {
                        workspaceProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = PREVIEW_CLOSE_WORKSPACE_DAMPING,
                                stiffness = PREVIEW_CLOSE_WORKSPACE_STIFFNESS,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    }
                }
                delay(620)
            }
        } else {
            while (isActive) {
                delay(850)
                classicPageProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                )
                delay(620)
                classicPageProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                )
                delay(620)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (newStyleEnabled) Color(0xFF202126) else Color(0xFFF2F3F5))
            .onGloballyPositioned { previewBoundsInRoot = it.boundsInRoot() },
    ) {
        if (newStyleEnabled) {
            val hiddenModes = buildSet {
                if (firstWindowActive) add(PREVIEW_FIRST_MODE)
                if (secondWindowActive) add(PREVIEW_SECOND_MODE)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = workspaceProgress.value.coerceIn(0f, 1f)
                        val scale = previewLerp(1f, 0.9f, progress)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - progress
                        transformOrigin = TransformOrigin.Center
                    }
                    .blur((18f * workspaceProgress.value.coerceIn(0f, 1f)).dp),
            ) {
                if (videoBackgroundEnabled) {
                    BootregVideoPlayer(
                        videoResId = R.raw.feature_page_background,
                        playCount = Int.MAX_VALUE,
                        scaleMode = BootregVideoScaleMode.Crop,
                        backgroundColor = Color(0xFF202126),
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.18f)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF4169E1),
                                        Color(0xFF9E5BE8),
                                        Color(0xFFE97883),
                                    ),
                                ),
                            ),
                    )
                }
                FeaturePreviewGrid(
                    hiddenModes = hiddenModes,
                    captureOrigins = hiddenModes.isEmpty(),
                    onIconBoundsChanged = { mode, bounds ->
                        iconBoundsInRoot[mode] = bounds
                    },
                )
            }

            fun localOrigin(mode: FeaturePageMode): androidx.compose.ui.geometry.Rect? {
                val bounds = iconBoundsInRoot[mode] ?: return null
                if (previewBoundsInRoot.width <= 0f || previewBoundsInRoot.height <= 0f) return null
                return androidx.compose.ui.geometry.Rect(
                    left = bounds.left - previewBoundsInRoot.left,
                    top = bounds.top - previewBoundsInRoot.top,
                    right = bounds.right - previewBoundsInRoot.left,
                    bottom = bounds.bottom - previewBoundsInRoot.top,
                )
            }

            if (firstWindowActive) {
                PreviewFeatureWindowLayer(
                    mode = PREVIEW_FIRST_MODE,
                    progress = firstWindowProgress.value,
                    sourceBounds = localOrigin(PREVIEW_FIRST_MODE),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (secondWindowActive) {
                PreviewFeatureWindowLayer(
                    mode = PREVIEW_SECOND_MODE,
                    progress = secondWindowProgress.value,
                    sourceBounds = localOrigin(PREVIEW_SECOND_MODE),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -size.width * 0.08f * classicPageProgress.value
                        alpha = 1f - 0.18f * classicPageProgress.value
                    },
            ) {
                ClassicFeatureListPreview()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * (1f - classicPageProgress.value)
                    }
                    .background(Color(0xFFF2F3F5)),
            ) {
                PreviewFeatureSubPage(PREVIEW_FIRST_MODE)
            }
        }
    }
}

private const val PREVIEW_OPEN_GEOMETRY_STIFFNESS = 420f
private const val PREVIEW_OPEN_GEOMETRY_DAMPING = 1.12f
private const val PREVIEW_OPEN_WORKSPACE_STIFFNESS = 420f
private const val PREVIEW_OPEN_WORKSPACE_DAMPING = 1f
private const val PREVIEW_CLOSE_GEOMETRY_STIFFNESS = 160f
private const val PREVIEW_CLOSE_GEOMETRY_DAMPING = 0.84f
private const val PREVIEW_CLOSE_WORKSPACE_STIFFNESS = 200f
private const val PREVIEW_CLOSE_WORKSPACE_DAMPING = 1f
private const val PREVIEW_CLOSE_MIN_PROGRESS = -0.012f
private val PREVIEW_FIRST_MODE = FeaturePageMode.Settings
private val PREVIEW_SECOND_MODE = FeaturePageMode.Assistant

private data class PreviewFeatureEntry(
    val mode: FeaturePageMode,
    @param:StringRes val titleRes: Int,
)

private val previewFeatureEntries = listOf(
    PreviewFeatureEntry(FeaturePageMode.Desktop, R.string.section_system_desktop),
    PreviewFeatureEntry(FeaturePageMode.SystemUi, R.string.section_lsp),
    PreviewFeatureEntry(FeaturePageMode.Settings, R.string.tab_settings),
    PreviewFeatureEntry(FeaturePageMode.MobileNetwork, R.string.feature_mobile_network_title),
    PreviewFeatureEntry(FeaturePageMode.Aod, R.string.feature_aod_enhance_title),
    PreviewFeatureEntry(FeaturePageMode.Assistant, R.string.feature_assistant_title),
    PreviewFeatureEntry(FeaturePageMode.AndroidSystem, R.string.feature_android_system_title),
    PreviewFeatureEntry(FeaturePageMode.Esim, R.string.feature_esim_title),
)

@Composable
private fun FeaturePreviewHeader(
    showBack: Boolean = false,
    title: String = stringResource(R.string.tab_features),
    foreground: Color = ActivationInk,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Text(
                text = "‹",
                color = foreground,
                fontSize = 28.sp,
                modifier = Modifier.width(24.dp),
            )
        }
        Text(
            text = title,
            color = foreground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!showBack) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .clip(CircleShape)
                    .background(foreground.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "↻", color = foreground, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun FeaturePreviewGrid(
    hiddenModes: Set<FeaturePageMode>,
    captureOrigins: Boolean,
    onIconBoundsChanged: (FeaturePageMode, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        FeaturePreviewHeader(foreground = Color.White)
        Spacer(modifier = Modifier.height(5.dp))
        previewFeatureEntries.chunked(4).forEachIndexed { rowIndex, entries ->
            Row(modifier = Modifier.fillMaxWidth()) {
                entries.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    if (captureOrigins) {
                                        onIconBoundsChanged(entry.mode, coordinates.boundsInRoot())
                                    }
                                }
                                .graphicsLayer {
                                    alpha = if (entry.mode in hiddenModes) 0f else 1f
                                },
                        ) {
                            FeatureLaunchIcon(
                                pageMode = entry.mode,
                                visualSize = 38.dp,
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(entry.titleRes),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (rowIndex == 0) Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Composable
private fun ClassicFeatureListPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        FeaturePreviewHeader()
        Spacer(modifier = Modifier.height(5.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White),
        ) {
            previewFeatureEntries.take(3).forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(51.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeatureLaunchIcon(
                        pageMode = entry.mode,
                        visualSize = 32.dp,
                    )
                    Text(
                        text = stringResource(entry.titleRes),
                        color = ActivationInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )
                    Text(text = "›", color = ActivationMutedInk, fontSize = 21.sp)
                }
                if (index < 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 54.dp, end = 12.dp)
                            .height(0.5.dp)
                            .background(Color.Black.copy(alpha = 0.09f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewFeatureWindowLayer(
    mode: FeaturePageMode,
    progress: Float,
    sourceBounds: androidx.compose.ui.geometry.Rect?,
    modifier: Modifier = Modifier,
) {
    if (sourceBounds == null) return
    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val viewportWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val physicalProgress = progress.coerceAtLeast(PREVIEW_CLOSE_MIN_PROGRESS)
        val boundedProgress = progress.coerceIn(0f, 1f)
        val sourceWidth = sourceBounds.width.coerceAtLeast(1f)
        val sourceHeight = sourceBounds.height.coerceAtLeast(1f)
        val sourceCenterX = sourceBounds.left + sourceWidth / 2f
        val animatedCenterX = previewLerp(sourceCenterX, viewportWidth / 2f, physicalProgress)
        val animatedTop = previewLerp(sourceBounds.top, 0f, physicalProgress)
        val animatedWidth = previewLerp(sourceWidth, viewportWidth, physicalProgress)
            .coerceAtLeast(1f)
        val animatedAspectRatio = previewLerp(
            sourceHeight / sourceWidth,
            viewportHeight / viewportWidth,
            physicalProgress,
        ).coerceAtLeast(0.01f)
        val animatedHeight = (animatedWidth * animatedAspectRatio).coerceAtLeast(1f)
        val animatedLeft = animatedCenterX - animatedWidth / 2f
        val taskAlpha = ((boundedProgress - 0.2f) / 0.3f).coerceIn(0f, 1f)
        val iconVisible = taskAlpha < 1f
        val animatedWidthDp = with(density) { animatedWidth.toDp() }
        val animatedHeightDp = with(density) { animatedHeight.toDp() }
        val iconSurfaceHeightDp = with(density) { max(animatedHeight, animatedWidth).toDp() }
        val cornerRadius = previewLerp(11f, 24f, boundedProgress).dp

        FeatureLaunchIcon(
            pageMode = mode,
            visualSize = animatedWidthDp,
            surfaceHeight = iconSurfaceHeightDp,
            modifier = Modifier
                .offset {
                    IntOffset(animatedLeft.roundToInt(), animatedTop.roundToInt())
                }
                .size(width = animatedWidthDp, height = iconSurfaceHeightDp)
                .graphicsLayer { alpha = if (iconVisible) 1f else 0f },
        )
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(animatedLeft.roundToInt(), animatedTop.roundToInt())
                }
                .size(width = animatedWidthDp, height = animatedHeightDp)
                .graphicsLayer { alpha = taskAlpha }
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color(0xFFF2F3F5)),
        ) {
            Layout(
                content = { PreviewFeatureSubPage(mode) },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, layoutConstraints ->
                val fullWidth = viewportWidth.roundToInt().coerceAtLeast(1)
                val fullHeight = viewportHeight.roundToInt().coerceAtLeast(1)
                val placeable = measurables.single().measure(
                    Constraints.fixed(fullWidth, fullHeight),
                )
                layout(layoutConstraints.maxWidth, layoutConstraints.maxHeight) {
                    placeable.placeWithLayer(0, 0) {
                        val uniformScale = animatedWidth / viewportWidth
                        scaleX = uniformScale
                        scaleY = uniformScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewFeatureSubPage(mode: FeaturePageMode) {
    val titleRes = previewFeatureEntries.firstOrNull { it.mode == mode }?.titleRes
        ?: R.string.tab_settings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        FeaturePreviewHeader(
            showBack = true,
            title = stringResource(titleRes),
        )
        Spacer(modifier = Modifier.height(8.dp))
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(47.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(if (index == 1) 88.dp else 112.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(ActivationInk.copy(alpha = 0.72f)),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(if (index == 2) 120.dp else 144.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(ActivationMutedInk.copy(alpha = 0.24f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .width(29.dp)
                        .height(17.dp)
                        .clip(CircleShape)
                        .background(if (index == 0) ActivationBlue else Color(0xFFD8DBDF)),
                    contentAlignment = if (index == 0) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
            if (index < 2) Spacer(modifier = Modifier.height(7.dp))
        }
    }
}

private fun previewLerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

@Composable
private fun MigrationPage(
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importState by remember { mutableStateOf<MigrationImportState>(MigrationImportState.Idle) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importState = MigrationImportState.Importing
            importState = runCatching {
                withContext(Dispatchers.IO) {
                    ConfigBackup.importFromUri(context, uri)
                }
            }.fold(
                onSuccess = { MigrationImportState.Imported },
                onFailure = { error ->
                    MigrationImportState.Failed(error.localizedMessage.orEmpty())
                },
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        ActivationBackButton(onBack = onBack)
        Spacer(modifier = Modifier.height(22.dp))
        PageHeading(
            title = stringResource(R.string.onboarding_migration_title),
            summary = stringResource(R.string.onboarding_migration_summary),
        )
        Spacer(modifier = Modifier.height(18.dp))
        MigrationTransferPreview(importState = importState)
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppUiTokens.CardCornerRadius))
                .background(Color.White.copy(alpha = 0.72f))
                .clickable(enabled = importState !is MigrationImportState.Importing) {
                    importLauncher.launch(arrayOf(ConfigBackup.MIME_TYPE, "text/*"))
                }
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = when (importState) {
                    MigrationImportState.Importing -> stringResource(R.string.onboarding_migration_importing)
                    MigrationImportState.Imported -> stringResource(R.string.onboarding_migration_imported)
                    else -> stringResource(R.string.onboarding_migration_import_action)
                },
                color = if (importState is MigrationImportState.Imported) {
                    ActivationGreen
                } else {
                    ActivationInk
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (val state = importState) {
                    is MigrationImportState.Failed -> {
                        stringResource(R.string.onboarding_migration_import_failed, state.detail)
                    }
                    MigrationImportState.Imported -> stringResource(R.string.onboarding_migration_imported_summary)
                    else -> stringResource(R.string.onboarding_migration_import_summary)
                },
                color = if (importState is MigrationImportState.Failed) ActivationRed else ActivationMutedInk,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.onboarding_migration_later),
            color = ActivationMutedInk,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        ActivationCapsuleButton(
            label = stringResource(R.string.onboarding_next),
            visible = true,
            enabled = importState !is MigrationImportState.Importing,
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun MigrationTransferPreview(importState: MigrationImportState) {
    val imported = importState is MigrationImportState.Imported
    val animationDuration = if (importState is MigrationImportState.Importing) 1_600 else 2_400
    val transferMotion = rememberInfiniteTransition(label = "migrationPreview")
    val animatedProgress by transferMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "migrationPreviewProgress",
    )
    val transferProgress = if (imported) 1f else animatedProgress
    val endFade = if (imported) 1f else {
        (1f - ((transferProgress - 0.9f) / 0.1f).coerceIn(0f, 1f))
    }
    val deliveryProgress = List(3) { index ->
        if (imported) {
            1f
        } else {
            val arrival = 0.5f + index * 0.16f
            migrationSmoothStep(arrival, arrival + 0.06f, transferProgress) * endFade
        }
    }
    val transferDescription = stringResource(R.string.onboarding_migration_preview_a11y)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .semantics { contentDescription = transferDescription },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MigrationDeviceCard(
                label = stringResource(R.string.onboarding_migration_preview_source),
                target = false,
                itemProgress = deliveryProgress.map { 1f - it * 0.55f },
            )
            MigrationTransferTrack(
                transferProgress = transferProgress,
                imported = imported,
                modifier = Modifier
                    .weight(1f)
                    .height(108.dp),
            )
            MigrationDeviceCard(
                label = stringResource(R.string.onboarding_migration_preview_target),
                target = true,
                itemProgress = deliveryProgress,
                imported = imported,
            )
        }
    }
}

@Composable
private fun MigrationTransferTrack(
    transferProgress: Float,
    imported: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackStart = 4.dp.toPx()
            val trackEnd = size.width - 4.dp.toPx()
            val laneGap = size.height / 4f
            repeat(3) { index ->
                val y = laneGap * (index + 1)
                drawLine(
                    color = Color(0xFFCED4DE),
                    start = Offset(trackStart, y),
                    end = Offset(trackEnd, y),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (imported) {
                    drawLine(
                        color = ActivationBlue.copy(alpha = 0.72f),
                        start = Offset(trackStart, y),
                        end = Offset(trackEnd, y),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(
                    color = if (imported) ActivationBlue else Color(0xFFCED4DE),
                    radius = 2.4.dp.toPx(),
                    center = Offset(trackEnd, y),
                )
                drawLine(
                    color = if (imported) ActivationBlue else Color(0xFFAEB6C2),
                    start = Offset(trackEnd - 5.dp.toPx(), y - 4.dp.toPx()),
                    end = Offset(trackEnd, y),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = if (imported) ActivationBlue else Color(0xFFAEB6C2),
                    start = Offset(trackEnd - 5.dp.toPx(), y + 4.dp.toPx()),
                    end = Offset(trackEnd, y),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        if (!imported) {
            val packetSize = 24.dp
            val travelDistance = maxWidth - packetSize
            repeat(3) { index ->
                val start = index * 0.16f
                val packetProgress = ((transferProgress - start) / 0.5f).coerceIn(0f, 1f)
                val edgeAlpha = (
                    migrationSmoothStep(0f, 0.1f, packetProgress) *
                        (1f - migrationSmoothStep(0.86f, 1f, packetProgress))
                    ).coerceIn(0f, 1f)
                MigrationDataPacket(
                    index = index,
                    modifier = Modifier
                        .offset(
                            x = travelDistance * packetProgress,
                            y = 15.dp + 27.dp * index,
                        )
                        .graphicsLayer {
                            alpha = edgeAlpha
                            val packetScale = 0.88f + 0.12f * edgeAlpha
                            scaleX = packetScale
                            scaleY = packetScale
                        },
                )
            }
        }
    }
}

@Composable
private fun MigrationDataPacket(
    index: Int,
    modifier: Modifier = Modifier,
) {
    val colors = listOf(
        Color(0xFF4D8BFF),
        Color(0xFF45B88A),
        Color(0xFF8B72E8),
    )
    Canvas(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors[index]),
    ) {
        val white = Color.White.copy(alpha = 0.94f)
        when (index) {
            0 -> {
                drawLine(white, Offset(size.width * 0.25f, size.height * 0.36f), Offset(size.width * 0.75f, size.height * 0.36f), 1.4.dp.toPx(), StrokeCap.Round)
                drawLine(white, Offset(size.width * 0.25f, size.height * 0.64f), Offset(size.width * 0.75f, size.height * 0.64f), 1.4.dp.toPx(), StrokeCap.Round)
                drawCircle(white, 2.1.dp.toPx(), Offset(size.width * 0.43f, size.height * 0.36f))
                drawCircle(white, 2.1.dp.toPx(), Offset(size.width * 0.61f, size.height * 0.64f))
            }
            1 -> repeat(3) { dot ->
                drawCircle(
                    color = white,
                    radius = 2.3.dp.toPx(),
                    center = Offset(size.width * (0.3f + dot * 0.2f), size.height * 0.5f),
                )
            }
            else -> repeat(4) { tile ->
                val column = tile % 2
                val row = tile / 2
                drawRoundRect(
                    color = white,
                    topLeft = Offset(size.width * (0.29f + column * 0.24f), size.height * (0.29f + row * 0.24f)),
                    size = Size(size.width * 0.17f, size.height * 0.17f),
                    cornerRadius = CornerRadius(1.2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun MigrationDeviceCard(
    label: String,
    target: Boolean,
    itemProgress: List<Float>,
    imported: Boolean = false,
) {
    Column(
        modifier = Modifier.width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(112.dp)
                .drawBehind {
                    val glow = if (target) itemProgress.maxOrNull() ?: 0f else 0f
                    drawRoundRect(
                        color = ActivationBlue.copy(alpha = glow * 0.12f),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                    )
                    drawRoundRect(
                        color = if (target && glow > 0.05f) {
                            ActivationBlue.copy(alpha = 0.35f + glow * 0.35f)
                        } else {
                            Color(0xFFBFC6D0)
                        },
                        cornerRadius = CornerRadius(19.dp.toPx()),
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                }
                .clip(RoundedCornerShape(19.dp))
                .background(Color(0xFFF4F6F9))
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFB7BEC8)),
                )
                Spacer(modifier = Modifier.height(5.dp))
                if (!target) {
                    Box(
                        modifier = Modifier
                            .size(27.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ActivationBlue.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(15.dp)) {
                            drawRoundRect(
                                color = ActivationBlue,
                                cornerRadius = CornerRadius(2.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                            repeat(2) { line ->
                                val y = size.height * (0.42f + line * 0.24f)
                                drawLine(
                                    color = ActivationBlue,
                                    start = Offset(size.width * 0.23f, y),
                                    end = Offset(size.width * 0.77f, y),
                                    strokeWidth = 1.2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }
                } else {
                    ActivationMark(
                        modifier = Modifier.size(27.dp),
                        showLogo = false,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                repeat(3) { index ->
                    val progress = itemProgress[index].coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(13.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (target) {
                                    Color.White.copy(alpha = 0.82f)
                                } else {
                                    Color(0xFFE7EAF0)
                                },
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (target) {
                                        ActivationBlue.copy(alpha = 0.18f + 0.82f * progress)
                                    } else {
                                        listOf(Color(0xFF4D8BFF), Color(0xFF45B88A), Color(0xFF8B72E8))[index]
                                            .copy(alpha = 0.35f + 0.65f * progress)
                                    },
                                ),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (target) {
                                        ActivationBlue.copy(alpha = 0.1f + 0.7f * progress)
                                    } else {
                                        ActivationMutedInk.copy(alpha = 0.18f + 0.3f * progress)
                                    },
                                ),
                        )
                    }
                    if (index < 2) Spacer(modifier = Modifier.height(3.dp))
                }
            }

            if (target && imported) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(ActivationGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = if (target) ActivationInk else ActivationMutedInk,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = if (target) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun migrationSmoothStep(start: Float, end: Float, value: Float): Float {
    if (start == end) return if (value < start) 0f else 1f
    val progress = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}

@Composable
private fun C17RadioIndicator(selected: Boolean) {
    val selectionProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        ),
        label = "c17RadioSelection",
    )
    Canvas(modifier = Modifier.size(24.dp)) {
        val offAlpha = 1f - selectionProgress
        if (offAlpha > 0f) {
            drawCircle(
                color = Color.Black.copy(alpha = (0x42 / 255f) * offAlpha),
                radius = 9.2.dp.toPx(),
                style = Stroke(width = 1.3.dp.toPx()),
            )
        }
        if (selectionProgress > 0f) {
            drawCircle(
                color = Color(0xFF0080FF).copy(alpha = selectionProgress),
                radius = (9.2f + 0.8f * selectionProgress).dp.toPx(),
            )
            drawCircle(
                color = Color.White.copy(alpha = selectionProgress),
                radius = (6f * selectionProgress).dp.toPx(),
            )
        }
    }
}

@Composable
private fun ActivationBackButton(onBack: () -> Unit) {
    Text(
        text = "‹  ${stringResource(R.string.onboarding_back)}",
        color = ActivationInk,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onBack)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun AgreementSequencePage(
    agreement: AgreementUi,
    pageIndex: Int,
    pageCount: Int,
    accepted: Boolean,
    onAgreementChanged: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val agreementText = remember(context, agreement.agreement) {
        context.readAgreementText(agreement.agreement)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹  ${stringResource(R.string.onboarding_back)}",
                color = ActivationInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${pageIndex + 1} / $pageCount",
                color = ActivationMutedInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(agreement.title),
            color = ActivationInk,
            fontSize = 30.sp,
            lineHeight = 37.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(agreement.summary),
            color = ActivationMutedInk,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(
                text = agreementText,
                color = ActivationInk,
                fontSize = 15.sp,
                lineHeight = 25.sp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.76f))
                .clickable { onAgreementChanged(!accepted) }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = ToggleableState(accepted),
                onClick = { onAgreementChanged(!accepted) },
            )
            Text(
                text = stringResource(agreement.checkText),
                color = ActivationInk,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 9.dp),
            )
        }
        ActivationCapsuleButton(
            label = stringResource(R.string.onboarding_next),
            visible = true,
            enabled = accepted,
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun AgreementsPage(
    agreementState: Map<Agreement, Boolean>,
    allowBackgroundUpdates: Boolean,
    onAgreementChanged: (Agreement, Boolean) -> Unit,
    onOpenAgreement: (AgreementUi) -> Unit,
    onBackgroundUpdatesChanged: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    val allAccepted = Agreement.entries.all { agreementState[it] == true }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 28.dp),
    ) {
        PageHeading(
            title = stringResource(R.string.onboarding_agreements_title),
            summary = stringResource(R.string.onboarding_agreements_summary),
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            agreementRows.forEach { row ->
                AgreementRow(
                    row = row,
                    accepted = agreementState[row.agreement] == true,
                    onAcceptedChange = { onAgreementChanged(row.agreement, it) },
                    onOpen = { onOpenAgreement(row) },
                )
            }
            OptionalNetworkRow(
                checked = allowBackgroundUpdates,
                onCheckedChange = onBackgroundUpdatesChanged,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        ActivationCapsuleButton(
            label = stringResource(R.string.onboarding_next),
            visible = true,
            enabled = allAccepted,
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 36.dp),
        )
    }
}

@Composable
private fun AgreementRow(
    row: AgreementUi,
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(row.title),
                        color = ActivationInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = stringResource(R.string.onboarding_required),
                        color = ActivationBlue,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(row.summary),
                    color = ActivationMutedInk,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.onboarding_view_details),
                color = ActivationBlue,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.52f))
                .clickable { onAcceptedChange(!accepted) }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = ToggleableState(accepted),
                onClick = { onAcceptedChange(!accepted) },
            )
            Text(
                text = stringResource(row.checkText),
                color = ActivationInk,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun OptionalNetworkRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.58f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            state = ToggleableState(checked),
            onClick = { onCheckedChange(!checked) },
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = stringResource(R.string.onboarding_background_updates_title),
                color = ActivationInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.onboarding_background_updates_summary),
                color = ActivationMutedInk,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun AgreementDetailPage(
    agreement: AgreementUi,
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val agreementText = remember(context, agreement.agreement) {
        context.readAgreementText(agreement.agreement)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Text(
                text = "‹  ${stringResource(R.string.onboarding_back)}",
                color = ActivationInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(agreement.title),
                color = ActivationInk,
                fontSize = 30.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.7f))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    text = agreementText,
                    color = ActivationInk,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.74f))
                    .clickable { onAcceptedChange(!accepted) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    state = ToggleableState(accepted),
                    onClick = { onAcceptedChange(!accepted) },
                )
                Text(
                    text = stringResource(agreement.checkText),
                    color = ActivationInk,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun RootAccessPage(
    rootInfo: RootAccessInfo,
    verified: Boolean,
    onPrimaryAction: () -> Unit,
    onBack: () -> Unit,
) {
    RuntimePageFrame(
        title = stringResource(R.string.onboarding_root_title),
        summary = stringResource(R.string.onboarding_root_summary),
        onBack = onBack,
        statusContent = {
            RuntimePair(
                rootReady = verified,
                lspReady = false,
            )
            Spacer(modifier = Modifier.height(16.dp))
            RuntimeDetailCard(
                title = stringResource(R.string.onboarding_root_card_title),
                detail = when {
                    rootInfo.state == RootAccessState.Checking ->
                        stringResource(R.string.onboarding_root_checking)
                    verified -> stringResource(
                        R.string.onboarding_root_granted,
                        rootInfo.managerVersion ?: "uid 0",
                    )
                    rootInfo.state == RootAccessState.NotGranted ||
                        rootInfo.state == RootAccessState.Error ->
                        stringResource(R.string.onboarding_root_denied)
                    else -> stringResource(R.string.onboarding_root_idle)
                },
                color = if (verified) ActivationGreen else ActivationBlue,
                loading = rootInfo.state == RootAccessState.Checking,
            )
        },
        primaryLabel = if (verified) {
            stringResource(R.string.onboarding_next)
        } else {
            stringResource(R.string.onboarding_request_root)
        },
        primaryEnabled = rootInfo.state != RootAccessState.Checking,
        onPrimaryAction = onPrimaryAction,
    )
}

@Composable
private fun LsposedPage(
    rootReady: Boolean,
    snapshot: LsposedScopeRequester.StatusSnapshot,
    checking: Boolean,
    refreshFailed: Boolean,
    openFailed: Boolean,
    onOpenOrNext: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val status = when {
        refreshFailed -> LspStatus.UNKNOWN
        else -> snapshot.status
    }
    val refreshing = checking || snapshot.isRefreshing || status == LspStatus.CHECKING
    RuntimePageFrame(
        title = stringResource(R.string.onboarding_lsp_title),
        summary = stringResource(R.string.onboarding_lsp_summary),
        onBack = onBack,
        statusContent = {
            RuntimePair(
                rootReady = rootReady,
                lspReady = snapshot.isReady && !refreshFailed,
            )
            Spacer(modifier = Modifier.height(16.dp))
            RuntimeDetailCard(
                title = stringResource(R.string.onboarding_lsp_card_title),
                detail = lspStatusText(status, snapshot.frameworkVersionText),
                color = when {
                    status == LspStatus.READY -> ActivationGreen
                    status == LspStatus.DISABLED -> ActivationRed
                    else -> ActivationBlue
                },
                loading = status == LspStatus.CHECKING,
            )
            if (status == LspStatus.MISSING_SCOPE && snapshot.missingScopes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LspMissingScopesNotice(status, snapshot.missingScopes)
            }
            if (openFailed) {
                Text(
                    text = stringResource(R.string.onboarding_lsposed_open_failed),
                    color = ActivationRed,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                )
            }
            Text(
                text = stringResource(R.string.onboarding_refresh),
                color = ActivationBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !refreshing, onClick = onRefresh)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        },
        primaryLabel = if (!refreshFailed && snapshot.moduleEnabled && status != LspStatus.API_UNSUPPORTED) {
            stringResource(R.string.onboarding_next)
        } else {
            stringResource(R.string.onboarding_open_lsposed)
        },
        primaryEnabled = !refreshing,
        onPrimaryAction = onOpenOrNext,
        scrollStatus = true,
    )
}

@Composable
private fun RuntimePageFrame(
    title: String,
    summary: String,
    onBack: () -> Unit,
    statusContent: @Composable ColumnScope.() -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimaryAction: () -> Unit,
    scrollStatus: Boolean = false,
) {
    val statusScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = 22.dp),
    ) {
        Text(
            text = "‹  ${stringResource(R.string.onboarding_back)}",
            color = ActivationInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(34.dp))
        PageHeading(title = title, summary = summary)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollStatus) Modifier.verticalScroll(statusScrollState) else Modifier)
                .padding(top = 42.dp),
            content = statusContent,
        )
        ActivationCapsuleButton(
            label = primaryLabel,
            visible = true,
            enabled = primaryEnabled,
            onClick = onPrimaryAction,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 72.dp),
        )
    }
}

@Composable
private fun RuntimePair(rootReady: Boolean, lspReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompactRuntimeCard(
            title = stringResource(R.string.onboarding_runtime_root),
            ready = rootReady,
            modifier = Modifier.weight(1f),
        )
        CompactRuntimeCard(
            title = stringResource(R.string.onboarding_runtime_lsp),
            ready = lspReady,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactRuntimeCard(
    title: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (ready) ActivationGreen else Color(0xFFAAAEB4),
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "runtimeIndicatorColor",
    )
    Column(
        modifier = modifier
            .requiredHeight(90.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
            Text(
                text = title,
                color = ActivationInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(9.dp))
        AnimatedContent(
            targetState = ready,
            transitionSpec = {
                (fadeIn(tween(240, delayMillis = 60)) +
                    slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 3 })
                    .togetherWith(
                        fadeOut(tween(140)) +
                            slideOutVertically(tween(180)) { -it / 4 },
                    )
            },
            contentKey = { it },
            label = "runtimeCompactStatus",
        ) { targetReady ->
            Text(
                text = stringResource(
                    if (targetReady) R.string.onboarding_runtime_ready
                    else R.string.onboarding_runtime_waiting,
                ),
                color = ActivationMutedInk,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun RuntimeDetailCard(
    title: String,
    detail: String,
    color: Color,
    loading: Boolean,
) {
    val animatedAccent by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "runtimeDetailAccent",
    )
    val loadingPulse = rememberInfiniteTransition(label = "runtimeDetailLoading")
    val pulseProgress by loadingPulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "runtimeDetailLoadingPulse",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .drawBehind {
                val pulse = if (loading) pulseProgress else 0f
                drawCircle(
                    color = animatedAccent.copy(alpha = 0.14f + pulse * 0.07f),
                    radius = (78.dp + 8.dp * pulse).toPx(),
                    center = Offset(size.width, 0f),
                )
            }
            .padding(20.dp),
    ) {
        Text(
            text = title,
            color = ActivationInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AnimatedContent(
            targetState = detail,
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 70)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 2 } +
                    scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = 0.98f))
                    .togetherWith(
                        fadeOut(tween(150)) +
                            slideOutVertically(tween(190)) { -it / 3 },
                    )
            },
            contentKey = { it },
            label = "runtimeDetailStatus",
        ) { targetDetail ->
            Text(
                text = targetDetail,
                color = ActivationMutedInk,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun CompletePage(
    saveFailed: Boolean,
    exitProgress: Float,
    isExiting: Boolean,
    canFinish: Boolean,
    onButtonBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onFinish: () -> Unit,
) {
    val logoAlpha = remember { Animatable(0f) }
    var typedTitle by remember { mutableStateOf("") }
    val title = stringResource(R.string.onboarding_complete_title)
    val logoExitProgress = CubicBezierEasing(0.31f, 0f, 0.11f, 0.97f)
        .transform(exitProgress)
    val textExitProgress = CubicBezierEasing(0.31f, 0f, 0.66f, 1f)
        .transform(exitProgress)
    LaunchedEffect(title) {
        delay(300)
        title.forEachIndexed { index, _ ->
            typedTitle = title.take(index + 1)
            delay(100)
        }
    }
    LaunchedEffect(Unit) {
        delay(1_000)
        logoAlpha.animateTo(1f, tween(durationMillis = 833, easing = LinearEasing))
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Image(
            painter = painterResource(R.drawable.c17_guide_page_image_background),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        BootregMatteVideoSandwich(
            videoResId = R.raw.c17_anim_of_complete_page,
            scaleMode = BootregVideoScaleMode.Stretch,
            playCount = 1,
            autoPlay = !isExiting,
            layersVisible = !isExiting,
            fadeInDurationMillis = 0,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-112).dp)
                    .graphicsLayer(
                        alpha = logoAlpha.value,
                        translationY = -202f * logoExitProgress,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ONextBoxLogo(
                    color = ActivationInk,
                    fontSize = 44.sp,
                    lineHeight = 52.sp,
                    strokeWidth = 0.4.dp,
                    maxLines = 1,
                )
            }
            Text(
                text = typedTitle,
                color = ActivationInk,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 254.dp)
                    .graphicsLayer(translationY = -142f * textExitProgress),
            )
            if (saveFailed) {
                Text(
                    text = stringResource(R.string.onboarding_save_failed),
                    color = ActivationRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 28.dp, end = 28.dp, bottom = 224.dp),
                )
            }
            C17FixedArrowButton(
                visible = !isExiting,
                enabled = !isExiting && canFinish,
                contentDescription = stringResource(R.string.onboarding_enter_app),
                onBoundsChanged = onButtonBoundsChanged,
                onClick = onFinish,
                modifier = Modifier
                    .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PageHeading(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = ActivationInk,
            fontSize = 32.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = summary,
            color = ActivationMutedInk,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun ActivationMark(
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
) {
    val infinite = rememberInfiniteTransition(label = "activationMark")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "activationMarkRotation",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(rotationZ = rotation),
        ) {
            val stroke = size.minDimension * 0.055f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF6FA8FF),
                        Color(0xFF8D77FF),
                        Color(0xFFFF8EA8),
                        Color(0xFFFFB56D),
                        Color(0xFF6FA8FF),
                    ),
                ),
                startAngle = 18f,
                sweepAngle = 304f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (showLogo) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.78f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                ONextBoxLogo(
                    color = ActivationInk,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    strokeWidth = 0.25.dp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun C17MorphArrowButton(
    visible: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val morphProgress = remember { Animatable(0f) }
    val pressScale = remember { Animatable(1f) }
    LaunchedEffect(visible) {
        if (!visible) {
            morphProgress.snapTo(0f)
        } else {
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                1f,
                tween(durationMillis = 900, easing = LinearEasing),
            )
        }
    }

    val progress = morphProgress.value
    val phase1 = c17SmoothStep((progress / (1f / 3f)).coerceIn(0f, 1f))
    val phase2 = c17SmoothStep(((progress - 1f / 3f) / (1f / 3f)).coerceIn(0f, 1f))
    val phase3Linear = ((progress - 2f / 3f) / (1f / 3f)).coerceIn(0f, 1f)
    val phase3 = CubicBezierEasing(0.25f, 0f, 0.15f, 1f).transform(phase3Linear)
    val width = when {
        progress <= 1f / 3f -> 255f
        progress <= 2f / 3f -> 255f + (200f - 255f) * phase2
        else -> 200f + (160f - 200f) * phase3
    }
    val height = when {
        progress <= 1f / 3f -> 255f
        progress <= 2f / 3f -> 255f + (95f - 255f) * phase2
        else -> 95f + (80f - 95f) * phase3
    }
    val translationY = when {
        progress <= 1f / 3f -> 255f + (206.55f - 255f) * phase1
        progress <= 2f / 3f -> 206.55f * (1f - phase2)
        else -> 0f
    }
    val bottomMargin = if (progress <= 2f / 3f) 0f else 120f * phase3
    val ready = visible && enabled && progress >= 0.999f

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (translationY - bottomMargin).dp)
                .width(width.dp)
                .height(height.dp)
                .graphicsLayer(
                    scaleX = pressScale.value,
                    scaleY = pressScale.value,
                    alpha = if (visible) 1f else 0f,
                )
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.0784f))
                .pointerInput(ready) {
                    if (!ready) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            coroutineScope {
                                val pressJob = launch {
                                    pressScale.animateTo(
                                        0.93f,
                                        tween(
                                            durationMillis = 200,
                                            easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                                        ),
                                    )
                                }
                                val released = tryAwaitRelease()
                                pressJob.cancel()
                                if (released) onClick()
                                pressScale.animateTo(
                                    1f,
                                    tween(
                                        durationMillis = 340,
                                        easing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
                                    ),
                                )
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.c17_arrow_next),
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer(alpha = phase3),
            )
        }
    }
}

@Composable
private fun C17FixedArrowButton(
    visible: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressScale = remember { Animatable(1f) }
    val ready = visible && enabled
    Box(
        modifier = modifier
            .offset(y = (-120).dp)
            .width(160.dp)
            .height(80.dp)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInRoot())
            }
            .graphicsLayer(
                alpha = if (visible) 1f else 0f,
                scaleX = pressScale.value,
                scaleY = pressScale.value,
            )
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.0784f))
            .pointerInput(ready) {
                if (!ready) return@pointerInput
                detectTapGestures(
                    onPress = {
                        coroutineScope {
                            val pressJob = launch {
                                pressScale.animateTo(
                                    0.93f,
                                    tween(
                                        durationMillis = 200,
                                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                                    ),
                                )
                            }
                            val released = tryAwaitRelease()
                            pressJob.cancel()
                            if (released) onClick()
                            pressScale.animateTo(
                                1f,
                                tween(
                                    durationMillis = 340,
                                    easing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
                                ),
                            )
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.c17_arrow_next),
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp),
        )
    }
}

private fun c17SmoothStep(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

@Composable
private fun ActivationCapsuleButton(
    label: String,
    visible: Boolean,
    enabled: Boolean,
    dramaticEntrance: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // C17 reserves 160 x 80 dp for the large arrow capsule on Guide/Complete pages. Text CTAs on
    // activation and agreement pages use the standard 220 x 44 dp bottom button instead.
    val width = remember { Animatable(if (dramaticEntrance) 255f else 220f) }
    val height = remember { Animatable(if (dramaticEntrance) 255f else 44f) }
    val verticalOffset = remember { Animatable(if (dramaticEntrance) 280f else 0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(visible, label, dramaticEntrance) {
        if (!visible) {
            width.snapTo(if (dramaticEntrance) 255f else 220f)
            height.snapTo(if (dramaticEntrance) 255f else 44f)
            verticalOffset.snapTo(if (dramaticEntrance) 280f else 0f)
            alpha.snapTo(0f)
        } else if (dramaticEntrance) {
            coroutineScope {
                launch {
                    verticalOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                }
                launch {
                    width.animateTo(200f, tween(600, easing = FastOutSlowInEasing))
                    width.animateTo(220f, tween(300, easing = FastOutSlowInEasing))
                }
                launch {
                    height.animateTo(95f, tween(600, easing = FastOutSlowInEasing))
                    height.animateTo(44f, tween(300, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(590)
                    alpha.animateTo(1f, tween(300))
                }
            }
        } else {
            alpha.animateTo(1f, tween(200))
        }
    }
    Box(
        modifier = modifier
            .width(width.value.dp)
            .height(height.value.dp)
            .graphicsLayer(
                alpha = if (visible) 1f else 0f,
                translationY = verticalOffset.value,
            )
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.Black.copy(alpha = 0.04f)
                    else -> Color.Black.copy(alpha = 0.08f)
                },
            )
            .clickable(enabled = visible && enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ActivationInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer(alpha = alpha.value)
                .padding(horizontal = 16.dp),
        )
    }
}

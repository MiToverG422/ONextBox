package com.mi.onextbox

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.mi.onextbox.ui.common.AppLocale
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.onboarding.OnboardingAppearance
import com.mi.onextbox.ui.onboarding.OnboardingPreferences
import com.mi.onextbox.ui.onboarding.OnboardingScreen
import com.mi.onextbox.ui.settings.UpdateChannelPreference
import com.mi.onextbox.ui.settings.UpdateNotificationScheduler

/** Onboarding activity with a prepared destination for its exit transition. */
class OnboardingActivity : ComponentActivity() {
    private companion object {
        const val APP_PREFS_NAME = "onextbox_prefs"
        const val PREF_FEATURE_PAGE_NEW_STYLE = "feature_page_new_style"
        const val PREF_FEATURE_PAGE_VIDEO_HIDDEN = "feature_page_video_hidden"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OnboardingPreferences.isCompleted(this)) {
            openMainAndFinish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val prefs = remember {
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE)
            }
            var appLanguageTag by remember {
                mutableStateOf(AppLocale.getSelectedLanguageTag(this@OnboardingActivity))
            }
            var appUiStyle by rememberSaveable {
                mutableStateOf(AppUiStyle.get(this@OnboardingActivity))
            }
            var appThemeMode by rememberSaveable {
                mutableStateOf(AppThemeMode.get(this@OnboardingActivity))
            }
            var featurePageNewStyleEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_FEATURE_PAGE_NEW_STYLE, true))
            }
            var featurePageVideoHidden by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_FEATURE_PAGE_VIDEO_HIDDEN, false))
            }
            val localizedContext = remember(appLanguageTag) {
                createOnboardingContext(appLanguageTag)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalResources provides localizedContext.resources,
                // The localized Context is a configuration context rather than the Activity.
                // Keep the ActivityResult owner explicit so pages such as configuration import
                // can register their picker while the language changes in-place.
                LocalActivityResultRegistryOwner provides this@OnboardingActivity,
            ) {
                OnboardingScreen(
                    appLanguageTag = appLanguageTag,
                    onAppLanguageChange = { languageTag ->
                        if (appLanguageTag != languageTag) {
                            AppLocale.setSelectedLanguageTag(this@OnboardingActivity, languageTag)
                            appLanguageTag = languageTag
                        }
                    },
                    appUiStyle = appUiStyle,
                    onAppUiStyleChange = { style ->
                        OnboardingAppearance.apply(this@OnboardingActivity, style, appThemeMode)
                            ?.let { effectiveMode ->
                                appUiStyle = style
                                appThemeMode = effectiveMode
                            }
                    },
                    appThemeMode = appThemeMode,
                    onAppThemeModeChange = { mode ->
                        OnboardingAppearance.apply(this@OnboardingActivity, appUiStyle, mode)
                            ?.let { effectiveMode -> appThemeMode = effectiveMode }
                    },
                    featurePageNewStyleEnabled = featurePageNewStyleEnabled,
                    onFeaturePageNewStyleEnabledChange = { enabled ->
                        featurePageNewStyleEnabled = enabled
                        prefs.edit()
                            .putBoolean(PREF_FEATURE_PAGE_NEW_STYLE, enabled)
                            .apply()
                    },
                    featurePageVideoHidden = featurePageVideoHidden,
                    onFeaturePageVideoHiddenChange = { hidden ->
                        featurePageVideoHidden = hidden
                        prefs.edit()
                            .putBoolean(PREF_FEATURE_PAGE_VIDEO_HIDDEN, hidden)
                            .apply()
                    },
                    onDestinationPreparationRequested = {},
                    onActivationCommitted = {
                        val allowBackgroundUpdates =
                            OnboardingPreferences.isBackgroundNetworkAllowed(this@OnboardingActivity)
                        UpdateChannelPreference.setUpdateNotificationsEnabled(
                            this@OnboardingActivity,
                            allowBackgroundUpdates,
                        )
                        if (allowBackgroundUpdates) {
                            UpdateNotificationScheduler.schedule(this@OnboardingActivity)
                            UpdateNotificationScheduler.checkNow(this@OnboardingActivity)
                        }
                    },
                    onExitFinished = {
                        openMainAndFinish()
                    },
                )
            }
        }
    }

/** Returns to MainActivity after the onboarding completion surface fades out. */
    private fun openMainAndFinish() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

/** Updates onboarding language without recreating the Activity or losing the current gesture. */
    private fun createOnboardingContext(languageTag: String): Context {
        val locales = if (languageTag.isBlank()) {
            Resources.getSystem().configuration.locales
        } else {
            LocaleList.forLanguageTags(AppLocale.resourceLanguageTag(languageTag))
        }
        val configuration = Configuration(resources.configuration).apply {
            setLocales(locales)
        }
        return createConfigurationContext(configuration)
    }
}

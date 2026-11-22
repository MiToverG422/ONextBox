package com.mi.onextbox

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.mi.onextbox.ui.common.AppLocale
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.onboarding.OnboardingAppearance
import com.mi.onextbox.ui.onboarding.OnboardingPreferences
import com.mi.onextbox.ui.onboarding.OnboardingScreen

/** Onboarding activity with a prepared destination for its exit transition. */
class OnboardingActivity : ComponentActivity() {
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
            var appLanguageTag by remember {
                mutableStateOf(AppLocale.getSelectedLanguageTag(this@OnboardingActivity))
            }
            var appUiStyle by rememberSaveable {
                mutableStateOf(AppUiStyle.get(this@OnboardingActivity))
            }
            var appThemeMode by rememberSaveable {
                mutableStateOf(AppThemeMode.get(this@OnboardingActivity))
            }
            val localizedContext = remember(appLanguageTag) {
                createOnboardingContext(appLanguageTag)
            }

            CompositionLocalProvider(
                LocalAppUiStyle provides appUiStyle,
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
                    onDestinationPreparationRequested = {},
                    onActivationCommitted = {},
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

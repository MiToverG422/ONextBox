import java.time.Instant

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val ciVersionName = (project.findProperty("ONEXTBOX_VERSION_NAME") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: "17.0"

// Android requires versionCode to be an integer. Keep the install-time value fixed at 1,
// and expose the product-facing EX code separately for the UI and build artifacts.
val androidVersionCode = 1
val appVersionCodeLabel = "EX01"

val ciSignReleaseWithDebug = (project.findProperty("ONEXTBOX_CI_SIGN_RELEASE_WITH_DEBUG") as String?)
    ?.toBooleanStrictOrNull()
    ?: false

val isGithubCi = System.getenv("GITHUB_ACTIONS") == "true"
val oNextBoxBuildTimestamp = System.currentTimeMillis()
val oNextBoxBuildTime = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC+8'")
    .withZone(ZoneId.of("Asia/Shanghai"))
    .format(Instant.ofEpochMilli(oNextBoxBuildTimestamp))
val libxposedApiVersion = libs.versions.libxposed.get()

val releaseStoreFilePath = (project.findProperty("ONEXTBOX_RELEASE_STORE_FILE") as String?)
    ?.takeIf { it.isNotBlank() }
val releaseStorePassword = (project.findProperty("ONEXTBOX_RELEASE_STORE_PASSWORD") as String?)
    ?.takeIf { it.isNotBlank() }
val releaseKeyAlias = (project.findProperty("ONEXTBOX_RELEASE_KEY_ALIAS") as String?)
    ?.takeIf { it.isNotBlank() }
val releaseKeyPassword = (project.findProperty("ONEXTBOX_RELEASE_KEY_PASSWORD") as String?)
    ?.takeIf { it.isNotBlank() }

val hasExternalReleaseSigning =
    !releaseStoreFilePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    file(releaseStoreFilePath).exists()

android {
    namespace = "com.mi.onextbox"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mi.onextbox"
        minSdk = 28
        targetSdk = 36
        versionCode = androidVersionCode
        versionName = ciVersionName
        buildConfigField("String", "APP_VERSION_CODE_LABEL", "\"$appVersionCodeLabel\"")
        buildConfigField("String", "APP_BUILD_TIME", "\"$oNextBoxBuildTime\"")
        buildConfigField("long", "APP_BUILD_TIMESTAMP", "${oNextBoxBuildTimestamp}L")
        buildConfigField("String", "LIBXPOSED_API_VERSION", "\"$libxposedApiVersion\"")
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasExternalReleaseSigning) {
            create("ciRelease") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (isGithubCi) {
                // Keep CI debug APK size smaller (e.g. Telegram upload limit).
                isMinifyEnabled = true
                isShrinkResources = true
                if (hasExternalReleaseSigning) {
                    // Ensure CI debug/release variants can share the same certificate when both are built.
                    signingConfig = signingConfigs.getByName("ciRelease")
                }
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
        create("debugSlim") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            when {
                hasExternalReleaseSigning -> {
                    signingConfig = signingConfigs.getByName("ciRelease")
                }
                ciSignReleaseWithDebug -> {
                    // CI fallback signing so release APK can be installed for testing.
                    signingConfig = signingConfigs.getByName("debug")
                }
                else -> {
                    // Last-resort fallback to avoid unsigned release outputs.
                    signingConfig = signingConfigs.getByName("debug")
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf(
            "en",
            "zh-rCN",
            "zh-rHK",
            "zh-rMO",
            "zh-rTW",
            "b+yue+Hant",
            // The novelty Japanese locale uses CN resources so AGP retains it independently
            // from normal Japanese, including builds that filter out locale variants.
            "ja-rCN",
            "b+zh+CN+catgirl",
            "ja",
            "ko",
            "b+ko+KP",
            "vi",
            "ru",
            "de",
            "fr",
            "b+id",
        )
    }
    bundle {
        // Runtime language switching needs every declared locale in the installed APK.
        language {
            enableSplit = false
        }
    }
    lint {
        // Community locale packs are intentionally allowed to fall back to the
        // complete base resources while translations are filled incrementally.
        warning += "MissingTranslation"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.material.kolor)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.coui.ui)
    implementation(libs.coui.preference)
    implementation(libs.coui.blur)
    implementation(libs.coui.icons)
    implementation(libs.coui.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kyant.capsule)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Root (Magisk) support
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)

    // LSPosed ecosystem utility
    implementation(libs.hidden.api.bypass)
    // Modern Xposed API is supplied by the framework in hooked processes.
    compileOnly(libs.libxposed.api)
    // Module-app communication for modern remote preferences.
    implementation(libs.libxposed.service)
}

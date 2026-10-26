import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "io.github.suqi8.coui.kmp.navigation3.ui"
        compileSdk = 37
        minSdk = 23
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":coui-squircle"))
            implementation(libs.coui.foundation)
            implementation(libs.coui.collection)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.coui.navigation.event)
            implementation(libs.coui.lifecycle.runtime)
            implementation(libs.coui.lifecycle.compose)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.coui.lifecycle.runtime)
            implementation(libs.androidx.activity.compose)
        }
    }
}

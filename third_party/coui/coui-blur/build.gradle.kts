import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "io.github.suqi8.coui.kmp.blur"
        compileSdk = 37
        minSdk = 33
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    sourceSets {
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.coui.lifecycle.runtime)
        }
        commonMain.dependencies {
            api(project(":coui-shader"))
            implementation(libs.coui.foundation)
        }
    }
}

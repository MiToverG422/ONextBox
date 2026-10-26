pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") {
            content { includeGroupByRegex("androidx\\.compose.*") }
        }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "ONextBox"
include(":app")

listOf("core", "shader", "squircle", "ui", "preference", "icons", "blur", "navigation3-ui")
    .forEach { component ->
        val module = "coui-$component"
        include(":$module")
        project(":$module").projectDir = file("third_party/coui/$module")
    }

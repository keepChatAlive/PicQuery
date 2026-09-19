buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:${libs.versions.objectboxGradlePlugin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.kotlin.kapt).apply(false)
    alias(libs.plugins.ksp).apply(false)
}

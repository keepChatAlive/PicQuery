plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    id("io.objectbox")
}

android {
    namespace = "me.grey.picquery"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.grey.picquery.mod"
        minSdk = 29
        targetSdk = 35
        versionCode = 25
        versionName = "1.3.7"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        debug {}

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        getByName("main") {
            // Package the verified local S2 pair directly without duplicating
            // the ~397 MB model files under src/main/assets.
            assets.srcDir("../local-models/mobileclip2-s2-onnx")
        }
    }

    androidResources {
        noCompress += "onnx"
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val kotlinVersion = "2.3.21"

configurations.matching { it.name == "composeMappingProducerClasspath" }.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:compose-group-mapping:$kotlinVersion")
}

dependencies {
    // Bill of Materials
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    // Implementation dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.dataStore)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Accompanist
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.permissions)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.compose.navigation)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // Logging
    implementation(libs.timber)

    // Image Loading
    implementation(libs.glide)
    implementation(libs.glide.compose)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.ffmpeg.kit.video)
    implementation(libs.smart.exception.java)

    // Other Libraries
    implementation(libs.zoomable)

    // AI & ML
    implementation(libs.onnx.runtime)
    implementation(libs.mlkit.translate)

    // ObjectBox
    implementation(libs.objectbox.kotlin)

    // Debug implementation
    debugImplementation(libs.compose.ui.tooling)

    // KSP
    ksp(libs.room.compiler)

}

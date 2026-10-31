plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// -Ppar.minify=false builds without R8 shrinking (bigger APK, fewer things that can go wrong).
val minify = (project.findProperty("par.minify") as String?) != "false"

android {
    namespace = "com.par.chat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.par.chat"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.0"
        // Phones are arm64 today: dropping the other CPU families cuts the APK to roughly a third.
        ndk { abiFilters += listOf("arm64-v8a") }
        resourceConfigurations += listOf("en", "fa")
    }
    

    buildTypes {
        release {
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release APK installs directly.
            // Create your own keystore before publishing to an app store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // Keeps CI fast and stops lint warnings from failing the build.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Maintained build of Google's libwebrtc for Android (package org.webrtc).
    implementation("io.getstream:stream-webrtc-android:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

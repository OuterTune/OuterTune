@file:Suppress("UnstableApiUsage")

val isFullBuild: Boolean by rootProject.extra

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.dd3boh.outertune"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dd3boh.outertune"
        minSdk = 24
        targetSdk = 35
        versionCode = 30
        versionName = "0.7.0-alpha1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            // isMinifyEnabled = true
            // isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

// build variants and stuff
    splits {
        abi {
            isEnable = true
            reset()

            // all common abis
            // include("x86_64", "x86", "armeabi-v7a", "arm64-v8a") // universal
            isUniversalApk = false
        }
    }

    flavorDimensions.add("abi")

    productFlavors {
        // universal
        create("universal") {
            isDefault = true
            dimension = "abi"
            ndk {
                abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
            }
        }
        // arm64 only
        create("arm64") {
            dimension = "abi"
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
        // x86_64 only
        create("x86_64") {
            dimension = "abi"
            ndk {
                abiFilters.add("x86_64")
            }
        }
        // for uncommon, but non-obscure architectures
        create("uncommon_abi") {
            dimension = "abi"
            ndk {
                abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a"))
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        jvmTarget = "17"
    }

    // for IzzyOnDroid
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    lint {
        disable += "MissingTranslation"
    }

}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)
    implementation(libs.compose.icons.extended)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(projects.materialColorUtilities)

    implementation(libs.coil)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation(projects.lrclib)
    implementation(projects.kizzy)


    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)

    /**
     * Custom FFmpeg metadata extractor
     *
     * My boss has requested prebuilt libraries by default. Shall you choose
     * to work on the scanner itself, switch the implementation below AND
     * include the project (uncomment the include line) in /settings.gradle.kts
     */
      implementation(files("prebuilt/ffMetadataEx-release.aar")) // prebuilt
//     implementation(project(":ffMetadataEx")) // self built
}
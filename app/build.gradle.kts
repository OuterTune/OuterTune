@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties


plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries)
    // Enable Firebase initialization via google-services.json
    alias(libs.plugins.google.services)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore: Boolean = if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    !keystoreProperties.isEmpty
} else false

android {
    namespace = "com.dd3boh.outertune"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dd3boh.outertune"
        minSdk = 24
        targetSdk = 36
        versionCode = 69
        versionName = "0.10.0-b3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Base URL for the Node.js party service; override via gradle property PARTY_SERVICE_BASE_URL or keep empty
        val partyServiceBaseUrl: String = (project.findProperty("PARTY_SERVICE_BASE_URL") as String?)
            ?: System.getenv("PARTY_SERVICE_BASE_URL")
            ?: ""
        buildConfigField("String", "PARTY_SERVICE_BASE_URL", "\"$partyServiceBaseUrl\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("ot_release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                (keystoreProperties["keyAlias"] as? String)?.let { keyAlias = it }
                (keystoreProperties["keyPassword"] as? String)?.let { keyPassword = it }
                (keystoreProperties["storePassword"] as? String)?.let { storePassword = it }
            }
        } else {
            // Fallback: use the debug signing config for local release builds when no release keystore is provided.
            // This enables generating an installable "final" APK without Play-signing.
            // Note: Replace with a proper release keystore before publishing.
            // No explicit creation of ot_release here; we'll point to debug below.
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("ot_release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }

        // userdebug is release builds without minify
        create("userdebug") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
//            isDebuggable = true
            isProfileable = true
            matchingFallbacks += listOf("release")
        }
    }

    // Cap R8/D8 memory to avoid native OOM on Windows during release builds
    androidComponents {
        onVariants { variant ->
            // Apply only to release and minified variants to prevent R8 from exhausting native memory
            if (variant.buildType == "release") {
                variant.packaging.jniLibs.useLegacyPackaging = false
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // build variants and stuff
    // Enable APK splits only when not building an App Bundle to avoid AGP issue with multiple shrunk resources.
    val isBundleBuild = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    if (!isBundleBuild) {
        splits {
            abi {
                isEnable = true
                reset()

                include("x86_64", "x86", "armeabi-v7a", "arm64-v8a")
                isUniversalApk = true
            }
        }
    }

    flavorDimensions.add("abi")

    productFlavors {
        // main version
        create("core") {
            isDefault = true
            dimension = "abi"
        }

        // fully featured version, large file size
        create("full") {
            dimension = "abi"
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                var outputFileName = "OuterTune-${variant.versionName}-${output.baseName}-${output.versionCode}.apk"
                output.outputFileName = outputFileName
            }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xannotation-default-target=param-property")

        }
    }

    tasks.withType<KotlinCompile> {
        if (!name.substringAfter("compile").lowercase().startsWith("full")) {
            exclude("**/*FFmpegScanner.kt")
            exclude("**/*NextRendersFactory.kt")
        } else {
            exclude("**/*FFmpegScannerDud.kt")
            exclude("**/*ffdecoderDud.kt")
        }
    }


    aboutLibraries {
        offlineMode = true

        collect {
            fetchRemoteLicense = false
            fetchRemoteFunding = false
            filterVariants.addAll("release")
        }

        export {
            // Remove the "generated" timestamp to allow for reproducible builds
            excludeFields = listOf("generated")
        }

        license {
            // Define the strict mode, will fail if the project uses licenses not allowed
            strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
            // Allowed set of licenses, this project will be able to use without build failure
            allowedLicenses.addAll("Apache-2.0", "BSD-3-Clause", "GNU LESSER GENERAL PUBLIC LICENSE, Version 2.1", "GPL-3.0-only", "EPL-2.0", "MIT", "MPL-2.0", "Public Domain", "CC0-1.0", "ASDKL", "Play Integrity API Terms of Service", "PCSDKToS")

            // Full license text for license IDs mentioned here will be included, even if no detected dependency uses them.
             additionalLicenses.addAll("apache_2_0", "gpl_2_1") // taglib, ffMpeg in ffMetadataEx
        }

        library {
            duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
            duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
        }
    }

    // for RB
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
        lintConfig = file("lint.xml")
    }

    androidResources {
        generateLocaleConfig = true
    }
}

    // Allow disabling Google Services processing tasks if needed via:
    //   -PdisableGoogleServices=true
    val disableGoogleServices: Boolean =
        ((findProperty("disableGoogleServices") as String?)?.equals("true", ignoreCase = true)) ?: false

    if (disableGoogleServices) {
        // Disable all process<Variant>GoogleServices tasks created by the plugin
        tasks.matching { it.name.contains("GoogleServices") && it.name.startsWith("process") }
            .configureEach {
                enabled = false
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
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    // compose
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)
    implementation(libs.compose.icons.extended)

    // ui
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.lazycolumnscrollbar)
    implementation(libs.shimmer)

    // material
    implementation(libs.adaptive)
    implementation(libs.material3)
    implementation(libs.palette)

    // viewmodel
    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.media3)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.session)
    implementation(libs.media3.workmanager)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    coreLibraryDesugaring(libs.desugaring)

    // Required Ktor dependencies for other parts of the app and for Gemini HTTP client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // modules
    implementation(project(":innertube"))
    implementation(project(":kugou"))
    implementation(project(":lrclib"))
    implementation(project(":material-color-utilities"))
    implementation(project(":taglib"))

    // firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    // Commented out to prevent startup crash - requires Crashlytics Gradle plugin
    // implementation(libs.firebase.crashlytics)

    // gemini ai - exclude all Ktor dependencies to use our own HTTP client
    implementation(libs.gemini.ai) {
        exclude(group = "io.ktor")
    }

    // misc
    implementation(libs.aboutlibraries.compose.m3)

    // sdk24 support
    // Support for N is officially unsupported even it the app should still work. Leave this outside of the version catalog.
    implementation("androidx.webkit:webkit:1.14.0")
}

afterEvaluate {
    dependencies {
        add("fullImplementation", project(":ffMetadataEx"))
    }
}

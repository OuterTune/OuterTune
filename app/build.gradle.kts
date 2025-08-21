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
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.dd3boh.outertune"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dd3boh.outertune"
        minSdk = 24
        targetSdk = 36
        versionCode = 67
        versionName = "0.9.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("ot_release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                (keystoreProperties["keyAlias"] as? String)?.let {
                    keyAlias = it
                }
                (keystoreProperties["keyPassword"] as? String)?.let {
                    keyPassword = it
                }
                (keystoreProperties["storePassword"] as? String)?.let {
                    storePassword = it
                }
            }
        } else {
            create("ot_release") { }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("ot_release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }

        // userdebug is release builds without minify
        create("userdebug") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
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

            include("x86_64", "x86", "armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
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
        exclude("**/*LibrariesScreen.kt")
        if (!name.substringAfter("compile").lowercase().startsWith("full")) {
            exclude("**/*FFMpegScanner.kt")
        } else {
            exclude("**/*FFMpegScannerDud.kt")
        }
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
        lintConfig = file("lint.xml")
    }

    androidResources {
        generateLocaleConfig = true
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
//    implementation(libs.lazycolumnscrollbar)
    implementation(files("../prebuilt/com.github.nanihadesuka_LazyColumnScrollbar_2.2.0.aar")) // prebuilt
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

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.serialization.json)

    // modules
    implementation(project(":innertube"))
    implementation(project(":kugou"))
    implementation(project(":lrclib"))
    implementation(project(":material-color-utilities"))

    // misc
    /*
    "JitPack builds are broken with the latest CMake version.
    Please download the [aar](https://github.com/Kyant0/taglib/releases) manually but not use maven."
     */
//    implementation(libs.taglib) // jitpack
    implementation(files("../prebuilt/taglib-1.0.2-outertune-universal-release.aar")) // prebuilt
//    implementation("com.kyant:taglib") // custom

    // sdk24 support
    // Support for N is officially unsupported even it the app should still work. Leave this outside of the version catalog.
    implementation("androidx.webkit:webkit:1.14.0")
}

afterEvaluate {
    dependencies {
        add("fullImplementation", project(":ffMetadataEx"))
    }
}

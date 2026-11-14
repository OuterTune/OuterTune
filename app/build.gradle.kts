@file:Suppress("UnstableApiUsage")

import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import com.android.apksig.SigningCertificateLineage
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Properties


plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "io.github.yuuichi_s.astertune"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.yuuichi_s.astertune"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // expose the TagLib library version (from the version catalog) for the About screen
        buildConfigField("String", "TAGLIB_VERSION", "\"${libs.versions.taglib.get()}\"")

        // Last.fm API credentials. Both values are required to show the Last.fm settings.
        buildConfigField("String", "LASTFM_API_KEY", "\"${keystoreProperties.getProperty("lastFmApiKey", "")}\"")
        buildConfigField("String", "LASTFM_SECRET", "\"${keystoreProperties.getProperty("lastFmSecret", "")}\"")
    }

    signingConfigs {
        // keystore.properties may contain Last.fm credentials without release signing credentials.
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
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
            create("release") { }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
                var outputFileName = "AsterTune-${variant.versionName}-${output.baseName}-${output.versionCode}.apk"
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
        // Tag extraction uses TagLib in every flavor. Only the FFmpeg playback decoder
        // (nextlib) is flavor-gated: the "full" flavor links it from the prebuilt AAR,
        // while other flavors compile the dud stub.
        if (name.substringAfter("compile").lowercase().startsWith("full")) {
            exclude("**/*ffdecoderDud.kt")
        }
    }


    aboutLibraries {
        offlineMode = true

        collect {
            configPath = file("config")
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
            allowedLicenses.addAll("Apache-2.0", "BSD-3-Clause", "GNU LESSER GENERAL PUBLIC LICENSE, Version 2.1", "GNU GENERAL PUBLIC LICENSE, Version 3", "GPL-3.0-only", "GPL-3.0-or-later", "EPL-2.0", "MIT", "MPL-2.0", "Public Domain")

            // Full license text for license IDs mentioned here will be included, even if no detected dependency uses them.
             additionalLicenses.addAll("apache_2_0", "gpl_2_1") // ffMpeg in ffMetadataEx
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
        // Robolectric needs this to set up SDK 36 on current JDKs (robolectric/robolectric#11434)
        unitTests.all { it.jvmArgs("--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED") }
    }

    lint {
        lintConfig = file("lint.xml")
    }

    androidResources {
        generateLocaleConfig = true
    }
}

// Re-signs APKs with the old and new keys and their signing lineage, so devices on Android 9+
// accept the new key as an update, while older devices keep verifying the old key.
abstract class RotateSigningKeyTask : DefaultTask() {
    @get:InputFiles
    abstract val inputApks: DirectoryProperty

    @get:OutputDirectory
    abstract val outputApks: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<RotateSigningKeyTask>>

    @get:Input
    abstract val missingSettings: ListProperty<String>

    @get:Optional
    @get:InputFile
    abstract val oldStoreFile: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val oldKeyAlias: Property<String>

    @get:Internal
    abstract val oldStorePassword: Property<String>

    @get:Optional
    @get:InputFile
    abstract val newStoreFile: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val newKeyAlias: Property<String>

    @get:Internal
    abstract val newStorePassword: Property<String>

    @get:Optional
    @get:InputFile
    abstract val lineageFile: RegularFileProperty

    @TaskAction
    fun sign() {
        if (missingSettings.get().isNotEmpty()) {
            throw GradleException("keystore.properties is missing: ${missingSettings.get().joinToString()}")
        }
        val signers = listOf(
            signerConfig(oldStoreFile, oldKeyAlias, oldStorePassword),
            signerConfig(newStoreFile, newKeyAlias, newStorePassword),
        )
        val lineage = SigningCertificateLineage.readFromFile(lineageFile.get().asFile)
        transformationRequest.get().submit(this) { artifact ->
            val input = File(artifact.outputFile)
            val output = outputApks.file(input.name).get().asFile
            ApkSigner.Builder(signers)
                .setSigningCertificateLineage(lineage)
                // apksig applies the rotated key from Android 13 unless told otherwise
                .setMinSdkVersionForRotation(28)
                .setInputApk(input)
                .setOutputApk(output)
                .build()
                .sign()
            output
        }

        // install tasks read the baseline profiles (.dm) from next to the APKs
        val inputProfiles = inputApks.dir("baselineProfiles").get().asFile
        val outputProfiles = outputApks.dir("baselineProfiles").get().asFile
        outputProfiles.deleteRecursively()
        if (inputProfiles.exists()) {
            inputProfiles.copyRecursively(outputProfiles)
        }
    }

    private fun signerConfig(
        storeFile: RegularFileProperty,
        keyAlias: Property<String>,
        storePassword: Property<String>,
    ): ApkSigner.SignerConfig {
        // PKCS12 key stores protect the key with the store password
        val password = storePassword.get().toCharArray()
        val keyStore = KeyStore.getInstance(storeFile.get().asFile, password)
        val key = keyStore.getKey(keyAlias.get(), password) as PrivateKey
        val certificates = keyStore.getCertificateChain(keyAlias.get()).map { it as X509Certificate }
        return ApkSigner.SignerConfig.Builder(keyAlias.get(), KeyConfig.Jca(key), certificates).build()
    }
}

// release builds fail when keystore.properties lacks any of these, rather than shipping APKs without the new key
val rotationSettings = listOf(
    "storeFile", "keyAlias", "storePassword", "newStoreFile", "newKeyAlias", "newStorePassword", "lineageFile"
)

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val task = tasks.register<RotateSigningKeyTask>(
            "rotateSigningKey${variant.name.replaceFirstChar { it.uppercase() }}"
        ) {
            missingSettings.set(rotationSettings.filterNot { keystoreProperties.containsKey(it) })
            keystoreProperties.getProperty("storeFile")?.let { oldStoreFile.set(file(it)) }
            oldKeyAlias.set(keystoreProperties.getProperty("keyAlias"))
            oldStorePassword.set(keystoreProperties.getProperty("storePassword"))
            keystoreProperties.getProperty("newStoreFile")?.let { newStoreFile.set(file(it)) }
            newKeyAlias.set(keystoreProperties.getProperty("newKeyAlias"))
            newStorePassword.set(keystoreProperties.getProperty("newStorePassword"))
            keystoreProperties.getProperty("lineageFile")?.let { lineageFile.set(file(it)) }
        }
        val request = variant.artifacts.use(task)
            .wiredWithDirectories(RotateSigningKeyTask::inputApks, RotateSigningKeyTask::outputApks)
            .toTransformMany(SingleArtifact.APK)
        task.configure { transformationRequest.set(request) }
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
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
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
    implementation(libs.material.color.utilities)

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
    implementation(project(":lastfm"))
    implementation(project(":betterlyrics"))

    // misc
    implementation(libs.aboutlibraries.compose.m3)

    // local metadata tag extraction (TagLib, all flavors)
    implementation(libs.taglib)

    // sdk24 support
    // Support for N is officially unsupported even it the app should still work. Leave this outside of the version catalog.
    implementation("androidx.webkit:webkit:1.14.0")

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// Runs JVM tests, then device tests; the device test step installs the app and leaves it installed.
listOf("Core", "Full").forEach { flavor ->
    val unitTest = "test${flavor}DebugUnitTest"
    val deviceTest = "connected${flavor}DebugAndroidTest"
    tasks.matching { it.name == deviceTest }.configureEach { mustRunAfter(unitTest) }
    tasks.register("testAndInstall${flavor}Debug") {
        group = "verification"
        dependsOn(unitTest, deviceTest)
    }
}

afterEvaluate {
    dependencies {
        add("fullImplementation", files("../prebuilt/ffMetadataEx-release.aar"))
    }
}

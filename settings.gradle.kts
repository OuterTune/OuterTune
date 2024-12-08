@file:Suppress("UnstableApiUsage")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "OuterTune"
include(":app")
include(":innertube")
include(":kugou")
include(":lrclib")
include(":material-color-utilities")
include(":kizzy")

// you must enable self built in \app\build.gradle.kts should you choose to uncomment this
//include(":ffMetadataEx")

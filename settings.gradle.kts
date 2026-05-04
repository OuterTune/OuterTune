@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

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
include(":ffMetadataEx")
include(":taglib")

includeBuild("../NewPipeExtractor") {
    dependencySubstitution {
        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
    }
}

//includeBuild(file("media").toPath().toRealPath().toAbsolutePath().toString()) {
//    dependencySubstitution {
//        substitute(module("androidx.media3:media3-common")).using(project(":lib-common"))
//        substitute(module("androidx.media3:media3-common-ktx")).using(project(":lib-common-ktx"))
//        substitute(module("androidx.media3:media3-datasource-okhttp")).using(project(":lib-datasource-okhttp"))
//        substitute(module("androidx.media3:media3-exoplayer")).using(project(":lib-exoplayer"))
//        substitute(module("androidx.media3:media3-exoplayer-workmanager")).using(project(":lib-exoplayer-workmanager"))
//        substitute(module("androidx.media3:media3-session")).using(project(":lib-session"))
//    }
//}

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

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that OuterTune and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in innertube/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.teamnewpipe:NewPipeExtractor")

//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}


// Use a local copy of taglib by uncommenting the lines below.
// We assume, that OuterTune and taglib have the same parent directory.
//
// Use a local copy of taglib
//includeBuild("../taglib") {
//    dependencySubstitution {
//        substitute(module("com.kyant:taglib"))
//    }
//}

include(":ffMetadataEx")

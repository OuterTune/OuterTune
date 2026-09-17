plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }
@file:Suppress("UnstableApiUsage")
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


// From:
//      add("fullImplementation", files("../prebuilt/ffMetadataEx-release.aar"))
// To:
//       add("fullImplementation", "wah.mikooomich:ffmetadataex")

//includeBuild("../ffMetadataEx") {
//    dependencySubstitution {
//        substitute(module("wah.mikooomich:ffmetadataex")).using(project(":ffMetadataEx"))
//    }
//}

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
// We assume, that NewPipe and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().

//includeBuild("../TubularExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.gechoto:NewPipeExtractor"))
//            .using(project(":extractor"))
//    }
//}

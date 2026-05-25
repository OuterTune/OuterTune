# Podcast Download Quick Start

This guide explains how to build, validate, and download the podcast-enabled APK for OuterTune.

## Requirements

- Git
- Android Studio or a local Android build environment
- JDK 17 or later for Gradle 9.x
- Internet access for the first dependency sync

## Project Setup

1. Clone your fork of the repository.
2. Switch to the feature branch for podcast work.
3. Open the project in Android Studio or use the command line.
4. Sync Gradle dependencies if needed.

## Build the APK

Use the standard debug build for the `core` flavor:

```bash
./gradlew assembleCoreDebug
```

The expected output APK is typically located at:

```text
app/build/outputs/apk/core/debug/
```

## Run the Tests

Before creating a release or sharing the APK, validate the podcast code with unit tests:

```bash
./gradlew test
```

If the local machine cannot run the build because of an older JVM, use GitHub Actions to build in the cloud.

## Download the APK from GitHub Actions

1. Open the repository on GitHub.
2. Go to the **Actions** tab.
3. Open the latest run for **Build podcast APK**.
4. Download the artifact named `OuterTune-core-debug-apk`.

## Podcast Feature Files

- `app/src/main/java/com/dd3boh/outertune/db/entities/PodcastEntity.kt`
- `app/src/main/java/com/dd3boh/outertune/db/PodcastDao.kt`
- `app/src/main/java/com/dd3boh/outertune/playback/PodcastDownloadUtil.kt`
- `app/src/main/java/com/dd3boh/outertune/viewmodels/PodcastDownloadViewModel.kt`
- `app/src/main/java/com/dd3boh/outertune/models/PodcastMetadata.kt`
- `app/src/main/java/com/dd3boh/outertune/extensions/PodcastExtensions.kt`

## Notes

- Keep Room entities and DAO queries aligned to avoid KSP compilation issues.
- If you change the schema, add the corresponding Room migration before shipping.
- Prefer GitHub Actions when your local Java version is not compatible with the Gradle wrapper.

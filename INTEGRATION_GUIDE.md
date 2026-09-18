# Integration Guide: Podcast Download Support for OuterTune

## Quick Summary

This guide explains how to integrate the podcast download feature into OuterTune.

---

## Steps

### 1. Review the Code
- `app/src/main/java/com/dd3boh/outertune/db/entities/PodcastEntity.kt`
- `app/src/main/java/com/dd3boh/outertune/db/PodcastDao.kt`
- `app/src/main/java/com/dd3boh/outertune/models/PodcastMetadata.kt`
- `app/src/main/java/com/dd3boh/outertune/extensions/PodcastExtensions.kt`
- `app/src/main/java/com/dd3boh/outertune/playback/PodcastDownloadUtil.kt`
- `app/src/main/java/com/dd3boh/outertune/viewmodels/PodcastDownloadViewModel.kt`

### 2. Update `MusicDatabase.kt`
- Add `PodcastEntity` and `PodcastEpisodeEntity`
- Add `podcastDao()`
- Increment the database version

### 3. Run Tests
```bash
./gradlew test
```

### 4. Create a Pull Request
- Create a feature branch
- Commit the changes
- Push to your fork
- Open a PR against the main repository

---

## Author

**Alvaro Manzo**

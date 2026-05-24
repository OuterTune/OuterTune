# Podcast Download Feature Specification

## Executive Summary

This document provides the technical specification for adding podcast download support to OuterTune. The implementation reuses the existing download infrastructure and keeps the architecture consistent.

---

## Overview

### Existing System
- `DownloadUtil.kt` manages downloads through ExoPlayer's `DownloadManager`
- `DownloadManagerOt.kt` tracks download events
- `DownloadDirectoryManagerOt.kt` manages file storage
- `ExoDownloadService.kt` runs downloads in the background

### Proposed Additions
- `PodcastEntity.kt` and `PodcastEpisodeEntity.kt`
- `PodcastDao.kt` for database operations
- `PodcastMetadata.kt` for UI-friendly models
- `PodcastExtensions.kt` for helper functions
- `PodcastDownloadUtil.kt` for podcast-specific download logic
- `PodcastDownloadViewModel.kt` for Compose state

---

## Core Features

- Download a single episode
- Download multiple episodes
- Download all episodes from one podcast
- Cancel and delete downloads
- Monitor download status and progress
- Support offline playback
- Track listening progress

---

## Architecture

```
UI Layer
  ↓
PodcastDownloadViewModel
  ↓
PodcastDownloadUtil
  ↓
DownloadUtil (existing)
  ↓
ExoDownloadService
  ↓
Local storage + Room database
```

---

## Implementation Notes

- Reuse existing media playback infrastructure
- Keep database schema separate from songs
- Use Flow/StateFlow for UI updates
- Keep the feature backwards compatible

---

## Author

**Alvaro Manzo**

**Status:** Complete
**Date:** May 23, 2026

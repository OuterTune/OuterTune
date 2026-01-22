# Design: Song Info Dialog for Online Songs

## 1. Overview
The "Song Info Dialog" feature currently exists for **Library Songs** (`SongMenu.kt`) but is missing for **Online Songs** (Search Results, Charts, etc. handled by `YouTubeSongMenu.kt`).
This design extends the feature to `YouTubeSongMenu.kt`, ensuring a consistent user experience across the app.

## 2. Changes Required

### A. Modify `app/src/main/java/com/dd3boh/outertune/ui/menu/YouTubeSongMenu.kt`

**1. Imports:**
   - Add `androidx.compose.material.icons.rounded.Info`
   - Add `androidx.compose.ui.platform.LocalClipboard`
   - Add `com.dd3boh.outertune.ui.dialog.DetailsDialog`

**2. State Management:**
   - Add a state variable to control the visibility of the details dialog.
   ```kotlin
   var showDetailsDialog by rememberSaveable { mutableStateOf(false) }
   ```
   - obtain `clipboardManager` via `LocalClipboard.current`.

**3. Menu Item:**
   - Add a "Details" item to the `GridMenu`.
   ```kotlin
   GridMenuItem(
       icon = Icons.Rounded.Info,
       title = R.string.details
   ) {
       showDetailsDialog = true
   }
   ```
   - **Placement:** Suggested placement is near the "Share" or "View Album" options, consistent with `SongMenu`.

**4. Dialog Implementation:**
   - Instantiate `DetailsDialog` when the state is true.
   ```kotlin
   if (showDetailsDialog) {
       DetailsDialog(
           mediaMetadata = song.toMediaMetadata(),
           currentFormat = null, // Online songs don't have cached format info yet
           currentPlayCount = 0, // Online songs don't have play counts
           clipboardManager = LocalClipboard.current,
           setVisibility = { showDetailsDialog = it }
       )
   }
   ```

## 3. Data Flow
1.  User taps the **three-dot menu** on a song in Search Results or YouTube Browse pages.
2.  `YouTubeSongMenu` opens.
3.  User taps **Details**.
4.  `showDetailsDialog` becomes `true`.
5.  `DetailsDialog` overlays the screen, displaying:
    -   Title, Artist (from `SongItem`)
    -   Media ID
    -   Since it's online, "File Path", "Bitrate", etc. will likely be hidden or show default values (handled gracefully by `DetailsDialog` which checks for nulls).
6.  User dismisses dialog -> `showDetailsDialog` becomes `false`.

## 4. Reusability
-   We reuse the existing `com.dd3boh.outertune.ui.dialog.DetailsDialog`.
-   We reuse `com.dd3boh.outertune.models.MediaMetadata`.
-   No changes to database or networking logic required.

## 5. Files to Touch
-   `app/src/main/java/com/dd3boh/outertune/ui/menu/YouTubeSongMenu.kt`

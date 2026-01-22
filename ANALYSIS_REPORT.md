# OuterTune Architecture Analysis & Extension Guide

## 1. Architecture Overview
OuterTune follows a modern Android architecture using **Jetpack Compose** for UI and **MVVM (Model-View-ViewModel)** for separation of concerns.

-   **UI Layer:** Pure Jetpack Compose (`app/src/main/java/com/dd3boh/outertune/ui`).
-   **ViewModel Layer:** Hilt-injected ViewModels (`app/src/main/java/com/dd3boh/outertune/viewmodels`) handle business logic and state.
-   **Data Layer:**
    -   **Local:** Room Database (`app/src/main/java/com/dd3boh/outertune/db`).
    -   **Remote:** `innertube` module for YouTube Music API.
    -   **Preferences:** DataStore (`app/src/main/java/com/dd3boh/outertune/utils/DataStore.kt`).

## 2. Main Modules
-   **`app`**: The main application module containing UI, DB, and Player logic.
-   **`innertube`**: Handles communication with YouTube Music (InnerTube API).
-   **`kugou` / `lrclib`**: specialized modules for fetching lyrics.
-   **`taglib` / `ffMetadataEx`**: Native libraries for reading/writing audio file metadata.

## 3. Safe Extension Points
For a "small UI level feature," these are the safest places to modify without breaking core playback or networking logic:

### A. Settings (Easiest)
Adding a new toggle or preference is very straightforward.
-   **File:** `app/src/main/java/com/dd3boh/outertune/ui/screens/settings/SettingsScreen.kt` (or specific category files like `AppearanceSettings.kt`).
-   **Persistence:** Define a key in a constants file (create `app/src/main/java/com/dd3boh/outertune/constants/SettingsKeys.kt` if needed) and use `rememberPreference` from `DataStore.kt`.
-   **UI:** Use `SwitchPreference`, `ListPreference`, or `PreferenceEntry`.

### B. New Screen / Tab
Adding a standalone screen for a new feature (e.g., "Statistics" or "Focus Mode").
-   **Definition:** Add a new object to `Screens.kt` (`app/src/main/java/com/dd3boh/outertune/ui/screens/Screens.kt`).
-   **Navigation:** Update the `NavHost` (likely in `MainActivity.kt` or a main scaffold file) to handle the new route.
-   **UI:** Create a new Composable in `app/src/main/java/com/dd3boh/outertune/ui/screens/`.

### C. Context Menus
Adding actions to songs/albums (e.g., "Share to generic text", "Copy JSON").
-   **File:** `app/src/main/java/com/dd3boh/outertune/ui/menu/`.
-   **Logic:** Modify `SongMenu.kt` or `AlbumMenu.kt` to add a new `GridMenu.Item` or dropdown entry.

## 4. Suggested Small UI Features
Based on the analysis, here are safe feature ideas:

1.  **"Sleep Timer" Quick Action:**
    -   Add a quick-access tile or button in the Player UI (`PlayerScreen.kt`) or menu (`PlayerMenu.kt`).
    -   Connects to existing service logic without deep engine changes.

2.  **Custom "Now Playing" Gradient:**
    -   Modify `AppearanceSettings.kt` to add a preference for "Solid vs Gradient" background.
    -   Read this preference in `PlayerScreen.kt` to adjust the background brush.

3.  **Song "Info" Dialog:**
    -   Add a "Details" option in `SongMenu.kt`.
    -   Create a simple dialog (`ui/dialog/`) showing raw metadata (Bitrate, Codec, File Path) which is already available in `MediaMetadata`.

4.  **Hiding Library Tabs:**
    -   Add a setting in `InterfaceSettings.kt` to toggle visibility of tabs (Songs, Albums, Artists) in the main library view.
    -   Modify `Library.kt` (component) to filter tabs based on this preference.

## 5. File Paths Reference
-   **Settings:** `app/src/main/java/com/dd3boh/outertune/ui/screens/settings/`
-   **Menus:** `app/src/main/java/com/dd3boh/outertune/ui/menu/`
-   **Screens:** `app/src/main/java/com/dd3boh/outertune/ui/screens/`
-   **Components:** `app/src/main/java/com/dd3boh/outertune/ui/component/`
-   **DataStore:** `app/src/main/java/com/dd3boh/outertune/utils/DataStore.kt`

/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.window.core.layout.WindowWidthSizeClass
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.dd3boh.outertune.constants.AppBarHeight
import com.dd3boh.outertune.constants.AutomaticScannerKey
import com.dd3boh.outertune.constants.DEFAULT_ENABLED_TABS
import com.dd3boh.outertune.constants.DarkMode
import com.dd3boh.outertune.constants.DarkModeKey
import com.dd3boh.outertune.constants.DefaultOpenTabKey
import com.dd3boh.outertune.constants.DynamicThemeKey
import com.dd3boh.outertune.constants.ENABLE_UPDATE_CHECKER
import com.dd3boh.outertune.constants.EnabledTabsKey
import com.dd3boh.outertune.constants.ExcludedScanPathsKey
import com.dd3boh.outertune.constants.LastLocalScanKey
import com.dd3boh.outertune.constants.LastVersionKey
import com.dd3boh.outertune.constants.LibraryFilterKey
import com.dd3boh.outertune.constants.LocalLibraryEnableKey
import com.dd3boh.outertune.constants.LookupYtmArtistsKey
import com.dd3boh.outertune.constants.MiniPlayerHeight
import com.dd3boh.outertune.constants.NavigationBarAnimationSpec
import com.dd3boh.outertune.constants.NavigationBarHeight
import com.dd3boh.outertune.constants.OOBE_VERSION
import com.dd3boh.outertune.constants.OobeStatusKey
import com.dd3boh.outertune.constants.PauseSearchHistoryKey
import com.dd3boh.outertune.constants.PureBlackKey
import com.dd3boh.outertune.constants.SCANNER_OWNER_LM
import com.dd3boh.outertune.constants.ScanPathsKey
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerImplKey
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.constants.ScannerSensitivityKey
import com.dd3boh.outertune.constants.ScannerStrictExtKey
import com.dd3boh.outertune.constants.SearchSource
import com.dd3boh.outertune.constants.SearchSourceKey
import com.dd3boh.outertune.constants.SlimNavBarKey
import com.dd3boh.outertune.constants.UpdateAvailableKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.SearchHistory
import com.dd3boh.outertune.extensions.tabMode
import com.dd3boh.outertune.playback.DownloadUtil
import com.dd3boh.outertune.playback.MediaControllerViewModel
import com.dd3boh.outertune.playback.MusicService
import com.dd3boh.outertune.playback.PlayerConnection
import com.dd3boh.outertune.ui.component.SearchBar
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.component.rememberBottomSheetState
import com.dd3boh.outertune.ui.component.shimmer.ShimmerTheme
import com.dd3boh.outertune.ui.menu.BottomSheetMenu
import com.dd3boh.outertune.ui.menu.MenuState
import com.dd3boh.outertune.ui.menu.YouTubeSongMenu
import com.dd3boh.outertune.ui.player.BottomSheetPlayer
import com.dd3boh.outertune.ui.screens.AccountScreen
import com.dd3boh.outertune.ui.screens.AlbumScreen
import com.dd3boh.outertune.ui.screens.BrowseScreen
import com.dd3boh.outertune.ui.screens.HistoryScreen
import com.dd3boh.outertune.ui.screens.HomeScreen
import com.dd3boh.outertune.ui.screens.LoginScreen
import com.dd3boh.outertune.ui.screens.MoodAndGenresScreen
import com.dd3boh.outertune.ui.screens.PlayerScreen
import com.dd3boh.outertune.ui.screens.Screens
import com.dd3boh.outertune.ui.screens.Screens.LibraryFilter
import com.dd3boh.outertune.ui.screens.SetupWizard
import com.dd3boh.outertune.ui.screens.StatsScreen
import com.dd3boh.outertune.ui.screens.YouTubeBrowseScreen
import com.dd3boh.outertune.ui.screens.artist.ArtistAlbumsScreen
import com.dd3boh.outertune.ui.screens.artist.ArtistItemsScreen
import com.dd3boh.outertune.ui.screens.artist.ArtistScreen
import com.dd3boh.outertune.ui.screens.artist.ArtistSongsScreen
import com.dd3boh.outertune.ui.screens.library.FolderScreen
import com.dd3boh.outertune.ui.screens.library.LibraryAlbumsScreen
import com.dd3boh.outertune.ui.screens.library.LibraryArtistsScreen
import com.dd3boh.outertune.ui.screens.library.LibraryFoldersScreen
import com.dd3boh.outertune.ui.screens.library.LibraryPlaylistsScreen
import com.dd3boh.outertune.ui.screens.library.LibraryScreen
import com.dd3boh.outertune.ui.screens.library.LibrarySongsScreen
import com.dd3boh.outertune.ui.screens.playlist.AutoPlaylistScreen
import com.dd3boh.outertune.ui.screens.playlist.LocalPlaylistScreen
import com.dd3boh.outertune.ui.screens.playlist.OnlinePlaylistScreen
import com.dd3boh.outertune.ui.screens.search.LocalSearchScreen
import com.dd3boh.outertune.ui.screens.search.OnlineSearchResult
import com.dd3boh.outertune.ui.screens.search.OnlineSearchScreen
import com.dd3boh.outertune.ui.screens.settings.AboutScreen
import com.dd3boh.outertune.ui.screens.settings.AccountSyncSettings
import com.dd3boh.outertune.ui.screens.settings.AppearanceSettings
import com.dd3boh.outertune.ui.screens.settings.AttributionScreen
import com.dd3boh.outertune.ui.screens.settings.BackupAndRestore
import com.dd3boh.outertune.ui.screens.settings.ExperimentalSettings
import com.dd3boh.outertune.ui.screens.settings.InterfaceSettings
import com.dd3boh.outertune.ui.screens.settings.LibrariesScreen
import com.dd3boh.outertune.ui.screens.settings.LibrarySettings
import com.dd3boh.outertune.ui.screens.settings.LocalPlayerSettings
import com.dd3boh.outertune.ui.screens.settings.LyricsSettings
import com.dd3boh.outertune.ui.screens.settings.PlayerSettings
import com.dd3boh.outertune.ui.screens.settings.SettingsScreen
import com.dd3boh.outertune.ui.screens.settings.StorageSettings
import com.dd3boh.outertune.ui.theme.ColorSaver
import com.dd3boh.outertune.ui.theme.DefaultThemeColor
import com.dd3boh.outertune.ui.theme.OuterTuneTheme
import com.dd3boh.outertune.ui.theme.extractThemeColor
import com.dd3boh.outertune.ui.utils.MEDIA_PERMISSION_LEVEL
import com.dd3boh.outertune.ui.utils.Updater
import com.dd3boh.outertune.ui.utils.appBarScrollBehavior
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.ui.utils.clearDtCache
import com.dd3boh.outertune.ui.utils.resetHeightOffset
import com.dd3boh.outertune.utils.ActivityLauncherHelper
import com.dd3boh.outertune.utils.LocalArtworkPath
import com.dd3boh.outertune.utils.NetworkConnectivityObserver
import com.dd3boh.outertune.utils.SyncUtils
import com.dd3boh.outertune.utils.coilCoroutine
import com.dd3boh.outertune.utils.compareVersion
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import com.dd3boh.outertune.utils.lmScannerCoroutine
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.reportException
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.destroyScanner
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerState
import com.dd3boh.outertune.utils.scanners.ScannerAbortException
import com.dd3boh.outertune.utils.urlEncode
import com.valentinilk.shimmer.LocalShimmerTheme
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    val MAIN_TAG = "MainOtActivity"

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    lateinit var activityLauncher: ActivityLauncherHelper
    lateinit var connectivityObserver: NetworkConnectivityObserver

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    val controllerViewModel: MediaControllerViewModel by viewModels()

    // storage permission helpers
    val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
//                Toast.makeText(this, "Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.scanner_missing_storage_perm), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onStart() {
        super.onStart()
        controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
            playerConnection = PlayerConnection(controllerViewModel, database)
        }
    }

    override fun onDestroy() {
        Log.i(MAIN_TAG, "onDestroy() called. isFinishing = $isFinishing")
        try {
            connectivityObserver.unregister()
        } catch (e: UninitializedPropertyAccessException) {
            // lol
        }
        // https://github.com/androidx/media/issues/805
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE && (playerConnection?.player?.playWhenReady != true || playerConnection?.player?.mediaItemCount == 0)) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(MusicService.NOTIFICATION_ID)
        }
        lifecycle.removeObserver(controllerViewModel)
        playerConnection = null

        super.onDestroy()
    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(controllerViewModel)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        activityLauncher = ActivityLauncherHelper(this)

        setContent {
            val coroutineScope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current
            val snackbarHostState = remember { SnackbarHostState() }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }
            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            try {
                connectivityObserver.unregister()
            } catch (e: UninitializedPropertyAccessException) {
                // lol
            }
            connectivityObserver = NetworkConnectivityObserver(this@MainActivity)
            val isNetworkConnected by connectivityObserver.networkStatus.collectAsState(true)

            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    coroutineScope.launch(coilCoroutine) {
                        var ret = DefaultThemeColor
                        if (song != null) {
                            val uri = (if (song.isLocal) song.localPath else song.thumbnailUrl)?.toUri()
                            if (uri != null) {
                                val model = if (uri.toString().startsWith("/storage/")) {
                                    LocalArtworkPath(uri.toString(), 100, 100)
                                } else {
                                    uri
                                }

                                val result = applicationContext.imageLoader.execute(
                                    ImageRequest.Builder(applicationContext)
                                        .data(model)
                                        .allowHardware(false)
                                        .build()
                                )

                                ret = result.image?.toBitmap()?.extractThemeColor() ?: DefaultThemeColor
                            }
                        }
                        themeColor = ret
                    }
                }
            }

            val (oobeStatus) = rememberPreference(OobeStatusKey, defaultValue = 0)
            val (localLibEnable) = rememberPreference(LocalLibraryEnableKey, defaultValue = true)

            // auto scanner
            val (scannerSensitivity) = rememberEnumPreference(
                key = ScannerSensitivityKey,
                defaultValue = ScannerMatchCriteria.LEVEL_2
            )
            val (scannerImpl) = rememberEnumPreference(
                key = ScannerImplKey,
                defaultValue = ScannerImpl.TAGLIB
            )
            val (scanPaths) = rememberPreference(ScanPathsKey, defaultValue = "")
            val (excludedScanPaths) = rememberPreference(ExcludedScanPathsKey, defaultValue = "")
            val (strictExtensions) = rememberPreference(ScannerStrictExtKey, defaultValue = false)
            val (lookupYtmArtists) = rememberPreference(LookupYtmArtistsKey, defaultValue = false)
            val (autoScan) = rememberPreference(AutomaticScannerKey, defaultValue = false)
            val (lastLocalScan, onLastLocalScanChange) = rememberPreference(
                LastLocalScanKey,
                LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
            )

            // updater
            val (updateAvailable, onUpdateAvailableChange) = rememberPreference(
                UpdateAvailableKey,
                defaultValue = false
            )
            val (lastVer, onLastVerChange) = rememberPreference(LastVersionKey, defaultValue = "0.0.0")

            LaunchedEffect(Unit) {
                downloadUtil.resumeDownloadsOnStart()

                coroutineScope.launch((lmScannerCoroutine)) {
                    val perms = checkSelfPermission(MEDIA_PERMISSION_LEVEL)
                    // Check if the permissions for local media access
                    if (scannerState.value <= 0 && autoScan && oobeStatus >= OOBE_VERSION && localLibEnable) {
                        if (perms == PackageManager.PERMISSION_GRANTED) {
                            // equivalent to (quick scan)
                            try {
                                withContext(Dispatchers.Main) {
                                    playerConnection?.player?.pause()
                                }
                                val scanner = LocalMediaScanner.getScanner(
                                    this@MainActivity, scannerImpl, SCANNER_OWNER_LM
                                )
                                val uris = scanner.scanLocal(scanPaths, excludedScanPaths)
                                scanner.quickSync(database, uris, scannerSensitivity, strictExtensions)

                                // start artist linking job
                                if (lookupYtmArtists && scannerState.value <= 0) {
                                    CoroutineScope(lmScannerCoroutine).launch {
                                        try {
                                            scanner.localToRemoteArtist(database)
                                        } catch (e: ScannerAbortException) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "${this@MainActivity.getString(R.string.scanner_scan_fail)}: ${e.message}",
                                                    withDismissAction = true,
                                                    duration = SnackbarDuration.Long
                                                )
                                            }
                                        }
                                    }
                                }
                            } catch (e: ScannerAbortException) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "${this@MainActivity.getString(R.string.scanner_scan_fail)}: ${e.message}",
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } finally {
                                destroyScanner(SCANNER_OWNER_LM)
                            }

                            // post scan actions
                            onLastLocalScanChange(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli())
                            clearDtCache()
                            playerConnection?.service?.initQueue()
                        } else if (perms == PackageManager.PERMISSION_DENIED) {
                            // Request the permission using the permission launcher
                            permissionLauncher.launch(MEDIA_PERMISSION_LEVEL)
                        }
                    }
                }

                coroutineScope.launch(Dispatchers.IO) {
                    if (!ENABLE_UPDATE_CHECKER) return@launch
                    if (compareVersion(lastVer, BuildConfig.VERSION_NAME) <= 0) {
                        onLastVerChange(BuildConfig.VERSION_NAME)
                        onUpdateAvailableChange(false)
                        Log.d(MAIN_TAG, "App version is >= latest. Tracking current version")
                    }

                    Updater.tryCheckUpdate(this@MainActivity as Context)?.let {
                        if (compareVersion(lastVer, it) < 0) {
                            onUpdateAvailableChange(true)
                            Log.d(MAIN_TAG, "Update available. UpdateAvailable set to true")
                        } else {
                            Log.d(MAIN_TAG, "No new updates available")
                        }
                    }
                }
            }

            OuterTuneTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

                    val tabMode = this@MainActivity.tabMode()
                    val useRail by remember {
                        derivedStateOf {
                            windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED && !tabMode
                        }
                    }

                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBars
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                    val cutoutInsets = WindowInsets.displayCutout

                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val inSelectMode =
                        navBackStackEntry?.savedStateHandle?.getStateFlow("inSelectMode", false)?.collectAsState()
                    val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                    val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                    val (enabledTabs) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
                    val navigationItems = Screens.getScreens(enabledTabs)
                    val (defaultOpenTab, onDefaultOpenTabChange) = rememberPreference(
                        DefaultOpenTabKey,
                        defaultValue = Screens.Home.route
                    )
                    // reset to home if somehow this gets set to a disabled tab
                    if (Screens.getScreens(enabledTabs).none { it.route == defaultOpenTab }) {
                        onDefaultOpenTabChange("home")
                    }

                    val tabOpenedFromShortcut = remember {
                        // reroute to library page for new layout is handled in NavHost section
                        when (intent?.action) {
                            ACTION_SONGS -> if (navigationItems.contains(Screens.Songs)) Screens.Songs else Screens.Library
                            ACTION_ALBUMS -> if (navigationItems.contains(Screens.Albums)) Screens.Albums else Screens.Library
                            ACTION_PLAYLISTS -> if (navigationItems.contains(Screens.Playlists)) Screens.Playlists else Screens.Library
                            else -> null
                        }
                    }
                    // setup filters for new layout
                    if (tabOpenedFromShortcut != null && navigationItems.contains(Screens.Library)) {
                        var filter by rememberEnumPreference(LibraryFilterKey, LibraryFilter.ALL)
                        filter = when (intent?.action) {
                            ACTION_SONGS -> LibraryFilter.SONGS
                            ACTION_ALBUMS -> LibraryFilter.ALBUMS
                            ACTION_PLAYLISTS -> LibraryFilter.PLAYLISTS
                            ACTION_SEARCH -> filter // do change filter for search
                            else -> LibraryFilter.ALL
                        }
                    }

                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }

                    /**
                     * Directly navigate to a YouTube page given an YouTube url
                     */
                    fun youtubeNavigator(uri: Uri): Boolean {
                        when (val path = uri.pathSegments.firstOrNull()) {
                            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                                if (playlistId.startsWith("OLAK5uy_")) {
                                    coroutineScope.launch {
                                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                                navController.navigate("album/$browseId")
                                            }
                                        }.onFailure {
                                            reportException(it)
                                        }
                                    }
                                } else {
                                    navController.navigate("online_playlist/$playlistId")
                                }
                            }

                            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                                navController.navigate("artist/$artistId")
                            }

                            else -> when {
                                path == "watch" -> uri.getQueryParameter("v")
                                uri.host == "youtu.be" -> path
                                else -> return false
                            }?.let { videoId ->
                                val playlistId = uri.getQueryParameter("list")
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        YouTube.queue(listOf(videoId), playlistId)
                                    }.onSuccess {
                                        sharedSong = it.firstOrNull()
                                        if (sharedSong == null) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = getString(R.string.err_invalid_ytm_song),
                                                    withDismissAction = true,
                                                    duration = SnackbarDuration.Long
                                                )
                                            }
                                        }
                                    }.onFailure {
                                        reportException(it)
                                    }
                                }
                            }
                        }

                        return true
                    }


                    val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(TextFieldValue())
                    }
                    var searchActive by rememberSaveable {
                        mutableStateOf(false)
                    }
                    val onSearchActiveChange: (Boolean) -> Unit = { newActive ->
                        searchActive = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }
                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }

                    val onSearch: (String) -> Unit = {
                        if (it.isNotEmpty()) {
                            onSearchActiveChange(false)
                            if (youtubeNavigator(it.toUri())) {
                                // don't do anything
                            } else {
                                navController.navigate("search/${it.urlEncode()}")
                                if (dataStore[PauseSearchHistoryKey] != true) {
                                    database.query {
                                        insert(SearchHistory(query = it))
                                    }
                                }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    val shouldHideNavAndPlayer = remember(navBackStackEntry) {
                        navBackStackEntry?.destination?.route?.let {
                            ((it.startsWith("settings") && !tabMode) || it == "setup_wizard" || it == "login")
                        } == true
                    }

                    val shouldShowSearchBar = remember(searchActive, navBackStackEntry, inSelectMode?.value) {
                        (searchActive || navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                navBackStackEntry?.destination?.route?.startsWith("search/") == true)
                                && inSelectMode?.value != true
                    }

                    val shouldShowNavigationBar = remember(navBackStackEntry, searchActive, shouldHideNavAndPlayer) {
                        (!useRail || tabMode) && !searchActive && !shouldHideNavAndPlayer && navBackStackEntry?.destination?.route?.startsWith(
                            "settings"
                        ) != true
                    }

                    val shouldShowNavigationRail = remember(navBackStackEntry, searchActive, shouldHideNavAndPlayer) {
                        useRail && !searchActive && !shouldHideNavAndPlayer
                    }

                    fun getNavPadding(): Dp {
                        return if (shouldShowNavigationBar && (!shouldShowNavigationRail || tabMode)) {
                            if (slimNav) 52.dp else 68.dp
                        } else {
                            0.dp
                        }
                    }

                    val navigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar) NavigationBarHeight else 0.dp,
                        animationSpec = NavigationBarAnimationSpec,
                        label = ""
                    )

                    val playerBottomSheetState = rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound = if (!shouldHideNavAndPlayer) bottomInset + (if (!tabMode) getNavPadding() else 0.dp) + MiniPlayerHeight else 0.dp,
                        expandedBound = maxHeight,
                    )

                    val playerAwareWindowInsets =
                        remember(
                            bottomInset,
                            shouldShowNavigationBar,
                            playerBottomSheetState.isDismissed,
                            shouldShowNavigationRail
                        ) {
                            var bottom = bottomInset

                            if (!playerBottomSheetState.isDismissed && !tabMode) bottom += MiniPlayerHeight
                            if (!useRail) {
                                if (shouldShowNavigationBar && !tabMode) bottom += NavigationBarHeight
                                windowsInsets
                                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                                    .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                            } else {
                                windowsInsets
                                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                                    .add(cutoutInsets.only(WindowInsetsSides.Horizontal))
                                    .add(
                                        WindowInsets(
                                            left = if (tabMode || !shouldShowNavigationRail) 0.dp else NavigationBarHeight,
                                            top = AppBarHeight,
                                            bottom = bottom
                                        )
                                    )
                            }
                        }

                    val scrollBehavior = appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

                    val searchBarScrollBehavior = appBarScrollBehavior(
                        state = rememberTopAppBarState(),
                        canScroll = {
                            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } && !tabMode
                                    && (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

                    //Getting song from deep link when app is not running does not work otherwise
                    LaunchedEffect(Unit) {
                        val initIntentUri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri()
                        if (initIntentUri != null) {
                            youtubeNavigator(initIntentUri)
                        }
                    }

                    LaunchedEffect(navBackStackEntry) {
                        if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                            val searchQuery = withContext(Dispatchers.IO) {
                                navBackStackEntry?.arguments?.getString("query")!!
                            }
                            onQueryChange(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            onQueryChange(TextFieldValue())
                        }

                        if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route })
                            if (navigationItems.fastAny { it.route == previousTab })
                                searchBarScrollBehavior.state.resetHeightOffset()

                        if (navBackStackEntry?.destination?.route?.startsWith("settings/") == true && playerConnection?.isPlaying?.value == true) {
                            playerBottomSheetState.collapseSoft()
                        }

                        navController.currentBackStackEntry?.destination?.route?.let {
                            setPreviousTab(it)
                        }

                        /*
                         * If the current back stack entry matches one of the main screens, but
                         * is not in the current navigation items, we need to remove the entry
                         * to avoid entering a "ghost" screen.
                         */
                        if (Screens.getScreens(enabledTabs)
                                .fastAny { it.route == navBackStackEntry?.destination?.route }
                        ) {
                            if (!navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                navController.popBackStack()
                                navController.navigate(Screens.Home.route)
                            }
                        }
                    }

                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            val uri =
                                intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri()
                                ?: return@Consumer
                            youtubeNavigator(uri)
                        }

                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    CompositionLocalProvider(
                        LocalDatabase provides database,
                        LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalMenuState provides MenuState(rememberModalBottomSheetState()),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        LocalNetworkConnected provides isNetworkConnected,
                        LocalSnackbarHostState provides snackbarHostState,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                        ) {

                            val navHost: @Composable() (() -> Unit) = @Composable {
                                NavHost(
                                    navController = navController,
                                    startDestination = (tabOpenedFromShortcut ?: Screens.getAllScreens()
                                        .find { it.route == defaultOpenTab })?.route
                                        ?: Screens.Home.route,
                                    enterTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }
                                        val previousRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }

                                        if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                                            slideInHorizontally { it / 2 } + fadeIn(tween(250))
                                        else
                                            slideInHorizontally { -it / 2 } + fadeIn(tween(250))
                                    },
                                    exitTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }
                                        val targetRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }

                                        if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                                            slideOutHorizontally { -it / 2 } + fadeOut(tween(250))
                                        else
                                            slideOutHorizontally { it / 2 } + fadeOut(tween(250))
                                    },
                                    popEnterTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }
                                        val previousRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }

                                        if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                                            slideInHorizontally { it / 2 } + fadeIn(tween(250))
                                        else
                                            slideInHorizontally { -it / 2 } + fadeIn(tween(250))
                                    },
                                    popExitTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }
                                        val targetRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }

                                        if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                                            slideOutHorizontally { -it / 2 } + fadeOut(tween(250))
                                        else
                                            slideOutHorizontally { it / 2 } + fadeOut(tween(250))
                                    },
                                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                                ) {
                                    composable(Screens.Home.route) {
                                        HomeScreen(navController)
                                    }
                                    composable(Screens.Songs.route) {
                                        LibrarySongsScreen(navController)
                                    }
                                    composable(Screens.Folders.route) {
                                        LibraryFoldersScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "${Screens.Folders.route}/{path}",
                                        arguments = listOf(
                                            navArgument("path") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        FolderScreen(navController, scrollBehavior)
                                    }
                                    composable(Screens.Artists.route) {
                                        LibraryArtistsScreen(navController)
                                    }
                                    composable(Screens.Albums.route) {
                                        LibraryAlbumsScreen(navController)
                                    }
                                    composable(Screens.Playlists.route) {
                                        LibraryPlaylistsScreen(navController)
                                    }
                                    composable(Screens.Library.route) {
                                        LibraryScreen(navController, scrollBehavior)
                                    }
                                    composable("history") {
                                        HistoryScreen(navController)
                                    }
                                    composable("stats") {
                                        StatsScreen(navController)
                                    }
                                    composable("mood_and_genres") {
                                        MoodAndGenresScreen(navController, scrollBehavior)
                                    }
                                    composable("account") {
                                        AccountScreen(navController, scrollBehavior)
                                    }

                                    composable(
                                        route = "browse/{browseId}",
                                        arguments = listOf(
                                            navArgument("browseId") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        BrowseScreen(
                                            navController,
                                            scrollBehavior,
                                            it.arguments?.getString("browseId")
                                        )
                                    }
                                    composable(
                                        route = "search/{query}",
                                        arguments = listOf(
                                            navArgument("query") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        OnlineSearchResult(navController)
                                    }
                                    composable(
                                        route = "album/{albumId}",
                                        arguments = listOf(
                                            navArgument("albumId") {
                                                type = NavType.StringType
                                            },
                                        )
                                    ) {
                                        AlbumScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "artist/{artistId}",
                                        arguments = listOf(
                                            navArgument("artistId") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        ArtistScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "artist/{artistId}/songs",
                                        arguments = listOf(
                                            navArgument("artistId") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        ArtistSongsScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "artist/{artistId}/albums",
                                        arguments = listOf(
                                            navArgument("artistId") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        ArtistAlbumsScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
                                        arguments = listOf(
                                            navArgument("artistId") {
                                                type = NavType.StringType
                                            },
                                            navArgument("browseId") {
                                                type = NavType.StringType
                                                nullable = true
                                            },
                                            navArgument("params") {
                                                type = NavType.StringType
                                                nullable = true
                                            }
                                        )
                                    ) {
                                        ArtistItemsScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "online_playlist/{playlistId}",
                                        arguments = listOf(
                                            navArgument("playlistId") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        OnlinePlaylistScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "local_playlist/{playlistId}",
                                        arguments = listOf(
                                            navArgument("playlistId") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        LocalPlaylistScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "auto_playlist/{playlistId}",
                                        arguments = listOf(
                                            navArgument("playlistId") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        AutoPlaylistScreen(navController, scrollBehavior)
                                    }
                                    composable(
                                        route = "youtube_browse/{browseId}?params={params}",
                                        arguments = listOf(
                                            navArgument("browseId") {
                                                type = NavType.StringType
                                                nullable = true
                                            },
                                            navArgument("params") {
                                                type = NavType.StringType
                                                nullable = true
                                            }
                                        )
                                    ) {
                                        YouTubeBrowseScreen(navController, scrollBehavior)
                                    }
                                    composable("settings") {
                                        SettingsScreen(navController, scrollBehavior)
                                    }
                                    composable("settings/appearance") {
                                        AppearanceSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/interface") {
                                        InterfaceSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/library") {
                                        LibrarySettings(navController, scrollBehavior)
                                    }
                                    composable("settings/library/lyrics") {
                                        LyricsSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/account_sync") {
                                        AccountSyncSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/player") {
                                        PlayerSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/storage") {
                                        StorageSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/backup_restore") {
                                        BackupAndRestore(navController, scrollBehavior)
                                    }
                                    composable("settings/local") {
                                        LocalPlayerSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/experimental") {
                                        ExperimentalSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/about") {
                                        AboutScreen(navController, scrollBehavior)
                                    }
                                    composable("settings/about/attribution") {
                                        AttributionScreen(navController, scrollBehavior)
                                    }
                                    composable("settings/about/oss_licenses") {
                                        LibrariesScreen(navController, scrollBehavior)
                                    }
                                    composable("login") {
                                        LoginScreen(navController)
                                    }

                                    composable("setup_wizard") {
                                        SetupWizard(navController)
                                    }
                                }
                            }

                            val searchBar: @Composable() (() -> Unit) = @Composable {
                                AnimatedVisibility(
                                    visible = shouldShowSearchBar,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    SearchBar(
                                        query = query,
                                        onQueryChange = onQueryChange,
                                        onSearch = onSearch,
                                        active = searchActive,
                                        onActiveChange = onSearchActiveChange,
                                        scrollBehavior = searchBarScrollBehavior,
                                        placeholder = {
                                            Text(
                                                text = stringResource(
                                                    if (!searchActive) R.string.search
                                                    else when (searchSource) {
                                                        SearchSource.LOCAL -> R.string.search_library
                                                        SearchSource.ONLINE -> R.string.search_yt_music
                                                    }
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            IconButton(
                                                onClick = {
                                                    when {
                                                        searchActive -> onSearchActiveChange(false)

                                                        !searchActive && navBackStackEntry?.destination?.route?.startsWith(
                                                            "search"
                                                        ) == true -> {
                                                            navController.navigateUp()
                                                        }

                                                        else -> onSearchActiveChange(true)
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    imageVector =
                                                        if (searchActive || navBackStackEntry?.destination?.route?.startsWith(
                                                                "search"
                                                            ) == true
                                                        ) {
                                                            Icons.AutoMirrored.Rounded.ArrowBack
                                                        } else {
                                                            Icons.Rounded.Search
                                                        },
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            if (searchActive) {
                                                if (query.text.isNotEmpty()) {
                                                    IconButton(
                                                        onClick = { onQueryChange(TextFieldValue("")) }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = null
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        searchSource =
                                                            if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = when (searchSource) {
                                                            SearchSource.LOCAL -> Icons.Rounded.LibraryMusic
                                                            SearchSource.ONLINE -> Icons.Rounded.Language
                                                        },
                                                        contentDescription = null
                                                    )
                                                }
                                            } else if (navBackStackEntry?.destination?.route in Screens.getAllScreens()
                                                    .map { it.route }
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            navController.navigate("settings")
                                                        }
                                                ) {
                                                    BadgedBox(
                                                        badge = {
                                                            if (ENABLE_UPDATE_CHECKER && updateAvailable) {
                                                                Badge()
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Settings,
                                                            contentDescription = null
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        focusRequester = searchBarFocusRequester,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .windowInsetsPadding(
                                                if (shouldShowNavigationRail) {
                                                    WindowInsets(left = NavigationBarHeight)
                                                } else {
                                                    // please shield your eyes.
                                                    WindowInsets(0.dp)
                                                }
                                            )
                                    ) {
                                        Crossfade(
                                            targetState = searchSource,
                                            label = "",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp)
                                                .navigationBarsPadding()
                                        ) { searchSource ->
                                            when (searchSource) {
                                                SearchSource.LOCAL -> LocalSearchScreen(
                                                    query = query.text,
                                                    navController = navController,
                                                    onDismiss = { onSearchActiveChange(false) }
                                                )

                                                SearchSource.ONLINE -> OnlineSearchScreen(
                                                    query = query.text,
                                                    onQueryChange = onQueryChange,
                                                    navController = navController,
                                                    onSearch = {
                                                        if (youtubeNavigator(it.toUri())) {
                                                            return@OnlineSearchScreen
                                                        } else {
                                                            navController.navigate("search/${it.urlEncode()}")
                                                            if (dataStore[PauseSearchHistoryKey] != true) {
                                                                database.query {
                                                                    insert(SearchHistory(query = it))
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDismiss = { onSearchActiveChange(false) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            @Composable
                            fun navRail(alignment: Alignment = Alignment.BottomStart) {
                                if (useRail && shouldShowNavigationRail) {
                                    Column(
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier
                                            .align(alignment)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                            .windowInsetsPadding(
                                                playerAwareWindowInsets
                                                    .only(WindowInsetsSides.Bottom)
                                                    .add(windowsInsets.only(WindowInsetsSides.Start + WindowInsetsSides.Top))
                                                    .add(cutoutInsets.only(WindowInsetsSides.Start))
                                            )
                                    ) {
                                        NavigationRail(
                                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                                            header = {
                                                Spacer(Modifier.height(8.dp))
                                                Image(
                                                    modifier = Modifier

                                                        .size(36.dp)
                                                        .padding(start = 8.dp),
                                                    painter = painterResource(R.drawable.small_icon),
                                                    contentDescription = null
                                                )
                                            }
                                        ) {
                                            navigationItems.fastForEach { screen ->
                                                // TODO: display selection when based on root page user entered
//                                                val isSelected = navBackStackEntry?.destination?.hierarchy?.any {
//                                                    it.route?.substringBefore("?")?.substringBefore("/") == screen.route
//                                                } == true
                                                NavigationRailItem(
                                                    selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true,
                                                    icon = {
                                                        Icon(
                                                            screen.icon,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    label = {
                                                        if (!slimNav) {
                                                            Text(
                                                                text = stringResource(screen.titleId),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        if (playerBottomSheetState.isExpanded) {
                                                            playerBottomSheetState.collapseSoft()
                                                        }

                                                        if (navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true) {
                                                            navBackStackEntry?.savedStateHandle?.set(
                                                                "scrollToTop",
                                                                true
                                                            )

                                                            coroutineScope.launch {
                                                                searchBarScrollBehavior.state.resetHeightOffset()
                                                            }
                                                        } else {
                                                            navController.backToMain()
                                                            navController.navigate(screen.route) {
                                                                popUpTo(navController.graph.startDestinationId) {
                                                                    saveState = true
                                                                }

                                                                launchSingleTop = true
                                                                restoreState = true
                                                            }

                                                            while (navController.currentDestination?.route.let { it != null && it != screen.route }) {
                                                                navController.popBackStack()
                                                            }
                                                        }

                                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val navbar: @Composable() (() -> Unit) = @Composable {
                                NavigationBar(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .height(bottomInset + getNavPadding())
                                        .offset {
                                            // hax
                                            if (tabMode) return@offset IntOffset(
                                                x = 0,
                                                y = 0
                                            )
                                            if (navigationBarHeight == 0.dp) {
                                                IntOffset(
                                                    x = 0,
                                                    y = (bottomInset + NavigationBarHeight).roundToPx()
                                                )
                                            } else {
                                                val slideOffset =
                                                    (bottomInset + NavigationBarHeight) * playerBottomSheetState.progress.coerceIn(
                                                        0f,
                                                        1f
                                                    )
                                                val hideOffset =
                                                    (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                                IntOffset(
                                                    x = 0,
                                                    y = (slideOffset + hideOffset).roundToPx()
                                                )
                                            }
                                        },
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                                ) {
                                    navigationItems.fastForEach { screen ->
                                        // TODO: display selection when based on root page user entered
//                                        val isSelected = navBackStackEntry?.destination?.hierarchy?.any {
//                                            it.route?.substringBefore("?")?.substringBefore("/") == screen.route
//                                        } == true
                                        NavigationBarItem(
                                            selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true,
                                            icon = {
                                                Icon(
                                                    screen.icon,
                                                    contentDescription = null
                                                )
                                            },
                                            label = {
                                                if (!slimNav) {
                                                    Text(
                                                        text = stringResource(screen.titleId),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            onClick = {
                                                if (navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true) {
                                                    navBackStackEntry?.savedStateHandle?.set(
                                                        "scrollToTop",
                                                        true
                                                    )
                                                    coroutineScope.launch {
                                                        searchBarScrollBehavior.state.resetHeightOffset()
                                                    }
                                                } else {
                                                    navController.backToMain()
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }

                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            }
                                        )
                                    }
                                }
                            }

                            val bottomSheetMenu: @Composable() (() -> Unit) = @Composable {
                                BottomSheetMenu(
                                    state = LocalMenuState.current,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }

                            // tab
                            if (tabMode) {
                                Row {
                                    if (oobeStatus >= OOBE_VERSION) {
                                        PlayerScreen(
                                            navController,
                                            playerBottomSheetState,
                                            modifier = Modifier.weight(0.4f)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier.weight(0.6f)
                                    ) {
                                        navHost()
                                        searchBar()
                                        navbar()
                                        bottomSheetMenu()
                                    }
                                }
                            } else {
                                // phone
                                navHost()
                                searchBar()
                                navRail()
                                if (oobeStatus >= OOBE_VERSION) {
                                    BottomSheetPlayer(
                                        state = playerBottomSheetState,
                                        navController = navController
                                    )
                                }
                                navbar()
                                bottomSheetMenu()
                            }

                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                                    .align(Alignment.BottomCenter)
                            )

                            sharedSong?.let { song ->
                                playerConnection?.let {
                                    Dialog(
                                        onDismissRequest = { sharedSong = null },
                                        properties = DialogProperties(usePlatformDefaultWidth = false)
                                    ) {
                                        Surface(
                                            modifier = Modifier.padding(24.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            color = AlertDialogDefaults.containerColor,
                                            tonalElevation = AlertDialogDefaults.TonalElevation
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                YouTubeSongMenu(
                                                    song = song,
                                                    navController = navController,
                                                    onDismiss = { sharedSong = null }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Setup wizard
                            LaunchedEffect(Unit) {
                                if (oobeStatus < OOBE_VERSION) {
                                    navController.navigate("setup_wizard")
                                }
                            }

                            if (BuildConfig.DEBUG) {
                                val debugColour = Color.Red
                                Column(
                                    modifier = Modifier.padding(start = 50.dp, top = 100.dp)
                                ) {
                                    Text(
                                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${BuildConfig.FLAVOR}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = debugColour
                                    )
                                    Text(
                                        text = "${BuildConfig.APPLICATION_ID} | ${BuildConfig.BUILD_TYPE}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = debugColour
                                    )
                                    Text(
                                        text = "${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = debugColour
                                    )
                                    Text(
                                        text = "${Build.VERSION.SDK_INT} (${Build.ID})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = debugColour
                                    )
                                }
                            }
                        }
                    }

                    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                        if (shouldShowSearchBar && openSearchImmediately) {
                            onSearchActiveChange(true)
                            searchBarFocusRequester.requestFocus()
                            openSearchImmediately = false
                        }
                    }
                }
            }
        }
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        // sdk24 support
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.dd3boh.outertune.action.SEARCH"
        const val ACTION_SONGS = "com.dd3boh.outertune.action.SONGS"
        const val ACTION_ALBUMS = "com.dd3boh.outertune.action.ALBUMS"
        const val ACTION_PLAYLISTS = "com.dd3boh.outertune.action.PLAYLISTS"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalMenuState = staticCompositionLocalOf<MenuState> { error("No menu state provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No player WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalNetworkConnected = staticCompositionLocalOf<Boolean> { error("No Network Status provided") }
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

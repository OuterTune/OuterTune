/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.yuuichi_s.astertune.constants.AppBarHeight
import io.github.yuuichi_s.astertune.constants.CustomThemeColorKey
import io.github.yuuichi_s.astertune.constants.CustomThemeKey
import io.github.yuuichi_s.astertune.constants.DEFAULT_ENABLED_TABS
import io.github.yuuichi_s.astertune.constants.DefaultOpenTabKey
import io.github.yuuichi_s.astertune.constants.DynamicThemeKey
import io.github.yuuichi_s.astertune.constants.EnabledTabsKey
import io.github.yuuichi_s.astertune.constants.LibraryFilterKey
import io.github.yuuichi_s.astertune.constants.MinMiniPlayerHeight
import io.github.yuuichi_s.astertune.constants.MiniPlayerHeight
import io.github.yuuichi_s.astertune.constants.NavigationBarAnimationSpec
import io.github.yuuichi_s.astertune.constants.NavigationBarHeight
import io.github.yuuichi_s.astertune.constants.OOBE_VERSION
import io.github.yuuichi_s.astertune.constants.OobeStatusKey
import io.github.yuuichi_s.astertune.constants.PureBlackKey
import io.github.yuuichi_s.astertune.constants.SlimNavBarKey
import io.github.yuuichi_s.astertune.constants.TabletUiKey
import io.github.yuuichi_s.astertune.constants.UI_DEBUG
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.extensions.isTabMode
import io.github.yuuichi_s.astertune.extensions.usesNavRail
import io.github.yuuichi_s.astertune.playback.DownloadUtil
import io.github.yuuichi_s.astertune.playback.MediaControllerViewModel
import io.github.yuuichi_s.astertune.playback.MusicService
import io.github.yuuichi_s.astertune.playback.PlayerConnection
import io.github.yuuichi_s.astertune.ui.component.UpdateAnnouncement
import io.github.yuuichi_s.astertune.ui.component.announceUpdate
import io.github.yuuichi_s.astertune.ui.component.UpdateInstaller
import io.github.yuuichi_s.astertune.ui.component.rememberBottomSheetState
import io.github.yuuichi_s.astertune.ui.component.shimmer.ShimmerTheme
import io.github.yuuichi_s.astertune.ui.menu.BottomSheetMenu
import io.github.yuuichi_s.astertune.ui.menu.MenuState
import io.github.yuuichi_s.astertune.ui.player.BottomSheetPlayer
import io.github.yuuichi_s.astertune.ui.screens.AccountScreen
import io.github.yuuichi_s.astertune.ui.screens.AlbumScreen
import io.github.yuuichi_s.astertune.ui.screens.BrowseScreen
import io.github.yuuichi_s.astertune.ui.screens.HistoryScreen
import io.github.yuuichi_s.astertune.ui.screens.HomeScreen
import io.github.yuuichi_s.astertune.ui.screens.LastFmLoginScreen
import io.github.yuuichi_s.astertune.ui.screens.LoginScreen
import io.github.yuuichi_s.astertune.ui.screens.MoodAndGenresScreen
import io.github.yuuichi_s.astertune.ui.screens.PlayerScreen
import io.github.yuuichi_s.astertune.ui.screens.Screens
import io.github.yuuichi_s.astertune.ui.screens.SetupWizard
import io.github.yuuichi_s.astertune.ui.screens.StatsScreen
import io.github.yuuichi_s.astertune.ui.screens.YouTubeBrowseScreen
import io.github.yuuichi_s.astertune.ui.screens.artist.ArtistAlbumsScreen
import io.github.yuuichi_s.astertune.ui.screens.artist.ArtistItemsScreen
import io.github.yuuichi_s.astertune.ui.screens.artist.ArtistScreen
import io.github.yuuichi_s.astertune.ui.screens.artist.ArtistSongsScreen
import io.github.yuuichi_s.astertune.ui.screens.library.FolderScreen
import io.github.yuuichi_s.astertune.ui.screens.library.LibraryAlbumsScreen
import io.github.yuuichi_s.astertune.ui.screens.library.LibraryArtistsScreen
import io.github.yuuichi_s.astertune.ui.screens.library.LibraryFoldersScreen
import io.github.yuuichi_s.astertune.ui.screens.library.LibraryPlaylistsScreen
import io.github.yuuichi_s.astertune.ui.screens.library.LibraryScreen
import io.github.yuuichi_s.astertune.ui.screens.library.LibrarySongsScreen
import io.github.yuuichi_s.astertune.ui.screens.library.LocalScreen
import io.github.yuuichi_s.astertune.ui.screens.playlist.AutoPlaylistScreen
import io.github.yuuichi_s.astertune.ui.screens.playlist.LocalPlaylistScreen
import io.github.yuuichi_s.astertune.ui.screens.playlist.OnlinePlaylistScreen
import io.github.yuuichi_s.astertune.ui.screens.search.OnlineSearchResult
import io.github.yuuichi_s.astertune.ui.screens.search.SearchBarContainer
import io.github.yuuichi_s.astertune.ui.screens.settings.AboutScreen
import io.github.yuuichi_s.astertune.ui.screens.settings.AccountSyncSettings
import io.github.yuuichi_s.astertune.ui.screens.settings.AppearanceSettings
import io.github.yuuichi_s.astertune.ui.screens.settings.AttributionScreen
import io.github.yuuichi_s.astertune.ui.screens.settings.BackupAndRestore
import io.github.yuuichi_s.astertune.ui.screens.settings.ExperimentalSettings
import io.github.yuuichi_s.astertune.ui.screens.settings.HomeSectionsSettings
import io.github.yuuichi_s.astertune.ui.screens.settings.LibrariesScreen
import io.github.yuuichi_s.astertune.ui.screens.settings.LibrarySettings
import io.github.yuuichi_s.astertune.ui.screens.settings.LocalPlayerSettings
import io.github.yuuichi_s.astertune.ui.screens.settings.LyricsSettings
import io.github.yuuichi_s.astertune.ui.screens.settings.PlayerSettings
import io.github.yuuichi_s.astertune.ui.screens.settings.PrivacySettings
import io.github.yuuichi_s.astertune.ui.screens.settings.SettingsScreen
import io.github.yuuichi_s.astertune.ui.screens.settings.StorageSettings
import io.github.yuuichi_s.astertune.ui.theme.AsterTuneTheme
import io.github.yuuichi_s.astertune.ui.theme.DefaultThemeColor
import io.github.yuuichi_s.astertune.ui.theme.rememberUseDarkTheme
import io.github.yuuichi_s.astertune.ui.utils.appBarScrollBehavior
import io.github.yuuichi_s.astertune.utils.AccountImageFetcher
import io.github.yuuichi_s.astertune.utils.ActivityLauncherHelper
import io.github.yuuichi_s.astertune.utils.NetworkConnectivityObserver
import io.github.yuuichi_s.astertune.utils.ObserveImageLoadNavigation
import io.github.yuuichi_s.astertune.utils.SyncUtils
import io.github.yuuichi_s.astertune.utils.lmScannerCoroutine
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference
import io.github.yuuichi_s.astertune.utils.updater.UpdateChecker
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var accountImageFetcher: AccountImageFetcher

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

    override fun onDestroy() {
        if (UI_DEBUG) Log.i(MAIN_TAG, "onDestroy() called. isFinishing = $isFinishing")
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
        lifecycleScope.launch { accountImageFetcher.fetchOnce() }
        controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
            playerConnection = PlayerConnection(controllerViewModel, database)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        activityLauncher = ActivityLauncherHelper(this)

        setContent {
            if (UI_DEBUG) Log.v(MAIN_TAG, "RC-1")
            val coroutineScope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current
            val snackbarHostState = remember { SnackbarHostState() }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val customTheme by rememberPreference(CustomThemeKey, defaultValue = false)
            val customThemeColorArgb by rememberPreference(CustomThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
            val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = rememberUseDarkTheme(isSystemInDarkTheme)

            val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
            val configuration = LocalConfiguration.current
            val tabletUi by rememberPreference(TabletUiKey, defaultValue = false)
            val tabMode = isTabMode(configuration, tabletUi)
            val useNavRail = remember(windowSizeClass, configuration, tabMode) {
                this@MainActivity.usesNavRail(windowSizeClass, configuration, tabMode)
            }


            val (oobeStatus) = rememberPreference(OobeStatusKey, defaultValue = 0)

            var filter by rememberEnumPreference(LibraryFilterKey, Screens.LibraryFilter.ALL)
            val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
            val (enabledTabs) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
            val navigationItems = remember {
                Screens.getScreens(enabledTabs)
            }
            val (defaultOpenTab, onDefaultOpenTabChange) = rememberPreference(
                DefaultOpenTabKey,
                defaultValue = Screens.Home.route
            )




            LaunchedEffect(Unit) {
                // local media & download folders auto scan
                coroutineScope.launch(lmScannerCoroutine) {
                    scanInit(
                        this@MainActivity, database, downloadUtil, coroutineScope, playerConnection,
                        snackbarHostState
                    )
                }
            }


            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            try {
                connectivityObserver.unregister()
            } catch (e: UninitializedPropertyAccessException) {
                // lol
            }
            connectivityObserver = NetworkConnectivityObserver(this@MainActivity)
            val isNetworkConnected by connectivityObserver.networkStatus.collectAsState(true)


            AsterTuneTheme(
                context = this@MainActivity,
                playerConnection = playerConnection,
                enableDynamicTheme = enableDynamicTheme,
                isSystemInDarkTheme = isSystemInDarkTheme,
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                customTheme = customTheme,
                customThemeColor = Color(customThemeColorArgb),
            ) {
                if (UI_DEBUG) Log.v(MAIN_TAG, "RC-2.1")
                val density = LocalDensity.current
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                val cutoutInsets = WindowInsets.displayCutout

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                ObserveImageLoadNavigation(navController)

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
                    filter = when (intent?.action) {
                        ACTION_SONGS -> Screens.LibraryFilter.SONGS
                        ACTION_ALBUMS -> Screens.LibraryFilter.ALBUMS
                        ACTION_PLAYLISTS -> Screens.LibraryFilter.PLAYLISTS
                        ACTION_SEARCH -> {
                            navController.navigate("search")
                            filter
                        } // do change filter for search
                        else -> Screens.LibraryFilter.ALL
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val maxW = maxWidth
                    if (UI_DEBUG) Log.v(MAIN_TAG, "RC-2.2")

                    fun getNavPadding(): Dp {
                        return if (!useNavRail) (if (slimNav) 52.dp else 68.dp) else MinMiniPlayerHeight
                    }

                    val playerBottomSheetState = rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound = bottomInset + MiniPlayerHeight + getNavPadding(),
                        expandedBound = maxHeight,
                    )

                    val playerAwareWindowInsets =
                        remember(
                            bottomInset,
                            playerBottomSheetState.isDismissed,
                            useNavRail,
                            tabMode,
                        ) {
                            // TODO: Navbar is shown in all screens except for oobe (which doesn't use these insets). Idk what do to tbh
                            var bottom = bottomInset + if (!useNavRail) NavigationBarHeight else 0.dp

                            if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                            if (!tabMode) {
                                windowsInsets
                                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                                    .add(cutoutInsets.only(WindowInsetsSides.Horizontal))
                                    .add(
                                        WindowInsets(
                                            left = if (!useNavRail) 0.dp else NavigationBarHeight,
                                            top = AppBarHeight,
                                            bottom = bottom
                                        )
                                    )
                            } else {
                                windowsInsets
                                    .only(WindowInsetsSides.Top)
                                    .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                            }
                        }

                    val scrollBehavior = appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )


                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            val uri =
                                intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri()
                                ?: return@Consumer
                            youtubeNavigator(
                                this@MainActivity,
                                navController,
                                coroutineScope,
                                playerConnection,
                                snackbarHostState,
                                uri
                            )
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
                        LocalTabMode provides tabMode,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        LocalAccountImageFetcher provides accountImageFetcher,
                        LocalNetworkConnected provides isNetworkConnected,
                        LocalSnackbarHostState provides snackbarHostState,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            if (UI_DEBUG) Log.v(MAIN_TAG, "RC-3")


                            val navHost: @Composable() (() -> Unit) = @Composable {
                                NavHost(
                                    navController = navController,
                                    startDestination = (Screens.getAllScreens()
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
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        else
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                    },
                                    exitTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }
                                        val targetRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }

                                        if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(100))
                                        else
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(100))
                                    },
                                    popEnterTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }
                                        val previousRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }

                                        if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        else
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                    },
                                    popExitTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }
                                        val targetRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }

                                        if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(100))
                                        else
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(100))
                                    },
                                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                                )
                                {
                                    composable(Screens.Home.route) {
                                        HomeScreen(navController)
                                    }
                                    composable(Screens.Songs.route) {
                                        LibrarySongsScreen(navController)
                                    }
                                    composable(Screens.Folders.route) {
                                        LibraryFoldersScreen(navController)
                                    }
                                    composable(
                                        route = "${Screens.Folders.route}/{path}",
                                        arguments = listOf(
                                            navArgument("path") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) {
                                        FolderScreen(navController)
                                    }
                                    composable(Screens.LocalFiles.route) {
                                        LocalScreen(navController)
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
                                        LibraryScreen(navController)
                                    }
                                    composable(Screens.Player.route) {
                                        PlayerScreen(navController, bottomPadding = getNavPadding())
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
                                        route = "search",
                                    ) {
                                        SearchBarContainer(navController, scrollBehavior)
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
                                    composable("settings/home_sections") {
                                        HomeSectionsSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/library") {
                                        LibrarySettings(navController, scrollBehavior)
                                    }
                                    composable("settings/lyrics") {
                                        LyricsSettings(navController, scrollBehavior)
                                    }
                                    composable("settings/privacy") {
                                        PrivacySettings(navController, scrollBehavior)
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
                                    composable("lastfm_login") {
                                        LastFmLoginScreen(navController)
                                    }

                                    composable("setup_wizard") {
                                        SetupWizard(navController)
                                    }
                                }
                            }

                            val navbar: @Composable() (() -> Unit) = @Composable {
                                val navigationBarHeight by animateDpAsState(
                                    targetValue = NavigationBarHeight,
                                    animationSpec = NavigationBarAnimationSpec,
                                    label = ""
                                )

                                NavigationBar(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .height(bottomInset + getNavPadding())
                                        .offset {
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
                                                if (playerBottomSheetState.isExpanded) {
                                                    playerBottomSheetState.collapseSoft()
                                                }

                                                val isCurrentTab = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                                val isInCurrentTabStack = !isCurrentTab && runCatching { navController.getBackStackEntry(screen.route) }.isSuccess

                                                if (isCurrentTab) {
                                                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                    scrollBehavior.state.heightOffset = 0f
                                                    scrollBehavior.state.contentOffset = 0f
                                                } else if (screen.route == Screens.Home.route && isInCurrentTabStack) {
                                                    // Keep the Home entry and its lifecycle for the chip BackHandler
                                                    navController.popBackStack(screen.route, inclusive = false, saveState = true)
                                                } else if (screen.route == Screens.Home.route) {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                    }
                                                } else if (isInCurrentTabStack) {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(screen.route)
                                                        launchSingleTop = true
                                                    }
                                                } else {
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

                            @Composable
                            fun navRail(alignment: Alignment) {
                                val layoutDirection = LocalLayoutDirection.current
                                val navigationBarHeight by animateDpAsState(
                                    targetValue = NavigationBarHeight,
                                    animationSpec = NavigationBarAnimationSpec,
                                    label = ""
                                )
                                val leftInset = remember {
                                    derivedStateOf {
                                        playerAwareWindowInsets.getLeft(density, layoutDirection).dp
                                    }
                                }
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                                    modifier = Modifier
                                        .align(alignment)
                                        .offset {
                                            if (navigationBarHeight == 0.dp) {
                                                IntOffset(
                                                    x = 0,
                                                    y = (bottomInset + NavigationBarHeight).roundToPx()
                                                )
                                            } else {
                                                val slideOffset =
                                                    (bottomInset + NavigationBarHeight + leftInset.value) *
                                                            playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                val hideOffset =
                                                    (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                                IntOffset(
                                                    x = -(slideOffset + hideOffset).roundToPx(),
                                                    y = 0
                                                )
                                            }
                                        }
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
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
                                                val isCurrentTab = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                                val isInCurrentTabStack = !isCurrentTab && runCatching { navController.getBackStackEntry(screen.route) }.isSuccess

                                                if (isCurrentTab) {
                                                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                    scrollBehavior.state.heightOffset = 0f
                                                    scrollBehavior.state.contentOffset = 0f
                                                } else if (screen.route == Screens.Home.route && isInCurrentTabStack) {
                                                    // Keep the Home entry and its lifecycle for the chip BackHandler
                                                    navController.popBackStack(screen.route, inclusive = false, saveState = true)
                                                } else if (screen.route == Screens.Home.route) {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                    }
                                                } else if (isInCurrentTabStack) {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(screen.route)
                                                        launchSingleTop = true
                                                    }
                                                } else {
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

                            // phone
                            if (!tabMode) {
                                navHost()

                                SearchBarContainer(navController, scrollBehavior)

                                if (oobeStatus >= OOBE_VERSION) {
                                    if (!navigationItems.contains(Screens.Player)) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController
                                        )
                                    }

                                    if (!useNavRail) {
                                        navbar()
                                    } else {
                                        navRail(if (LocalLayoutDirection.current == LayoutDirection.Rtl) Alignment.BottomEnd else Alignment.BottomStart)
                                    }
                                }
                                bottomSheetMenu()

                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier
                                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                                        .align(Alignment.BottomCenter)
                                )
                            } else {
                                // Even forced tablet mode requires a window width of 600 dp.
                                // Keep the player pane between 320 and 500 dp.
                                val playerW = (maxW.value * 0.4).coerceIn(320.0, 500.0)
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(playerW.dp)
                                    ) {
                                        if (oobeStatus >= OOBE_VERSION && !navigationItems.contains(Screens.Player)) {
                                            PlayerScreen(navController)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                    ) {
                                        navHost()

                                        SearchBarContainer(navController, scrollBehavior)

                                        if (oobeStatus >= OOBE_VERSION) {
                                            navbar()
                                        }
                                        bottomSheetMenu()

                                        SnackbarHost(
                                            hostState = snackbarHostState,
                                            modifier = Modifier
                                                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                                                .align(Alignment.BottomCenter)
                                        )
                                    }
                                }

                            }

                            // Setup wizard
                            LaunchedEffect(Unit) {
                                if (oobeStatus < OOBE_VERSION) {
                                    navController.navigate("setup_wizard")
                                }
                            }

                            LaunchedEffect(Unit) {
                                if (oobeStatus < OOBE_VERSION) return@LaunchedEffect
                                // Not announced while an install started from About is running.
                                val release = UpdateChecker.checkOnStart(this@MainActivity)
                                    ?.takeUnless { UpdateInstaller.isRunning }
                                    ?: return@LaunchedEffect
                                when (announceUpdate(this@MainActivity, snackbarHostState, release)) {
                                    UpdateAnnouncement.INSTALL -> navController.navigate("settings/about") {
                                        launchSingleTop = true
                                    }

                                    UpdateAnnouncement.CLOSED -> UpdateChecker.dismiss(this@MainActivity, release)
                                    UpdateAnnouncement.TIMED_OUT -> Unit
                                }
                            }
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
        const val ACTION_SEARCH = "io.github.yuuichi_s.astertune.action.SEARCH"
        const val ACTION_SONGS = "io.github.yuuichi_s.astertune.action.SONGS"
        const val ACTION_ALBUMS = "io.github.yuuichi_s.astertune.action.ALBUMS"
        const val ACTION_PLAYLISTS = "io.github.yuuichi_s.astertune.action.PLAYLISTS"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalMenuState = staticCompositionLocalOf<MenuState> { error("No menu state provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No player WindowInsets provided") }
val LocalTabMode = compositionLocalOf { false }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalAccountImageFetcher = staticCompositionLocalOf<AccountImageFetcher> { error("No AccountImageFetcher provided") }
val LocalNetworkConnected = staticCompositionLocalOf<Boolean> { error("No Network Status provided") }
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

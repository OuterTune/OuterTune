package io.github.yuuichi_s.astertune.ui.screens.search

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.yuuichi_s.astertune.BuildConfig
import io.github.yuuichi_s.astertune.LocalDatabase
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.LocalTabMode
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.AppBarHeight
import io.github.yuuichi_s.astertune.constants.DEFAULT_ENABLED_TABS
import io.github.yuuichi_s.astertune.constants.EnabledTabsKey
import io.github.yuuichi_s.astertune.constants.LocalLibraryEnableKey
import io.github.yuuichi_s.astertune.constants.PauseSearchHistoryKey
import io.github.yuuichi_s.astertune.constants.SearchSource
import io.github.yuuichi_s.astertune.constants.SearchSourceKey
import io.github.yuuichi_s.astertune.constants.UI_DEBUG
import io.github.yuuichi_s.astertune.db.entities.SearchHistory
import io.github.yuuichi_s.astertune.ui.component.AccountAvatar
import io.github.yuuichi_s.astertune.ui.component.InputFieldHeight
import io.github.yuuichi_s.astertune.ui.component.SearchBar
import io.github.yuuichi_s.astertune.ui.component.SearchBarHorizontalPadding
import io.github.yuuichi_s.astertune.ui.component.SearchBarVerticalPadding
import io.github.yuuichi_s.astertune.ui.screens.Screens
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference
import io.github.yuuichi_s.astertune.utils.urlEncode
import io.github.yuuichi_s.astertune.youtubeNavigator
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarContainer(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    if (UI_DEBUG) Log.v("SearchBarContainer", "SB-1")
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current

    val enabledTabs by rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    val localLibEnable by rememberPreference(LocalLibraryEnableKey, defaultValue = true)

    val navigationItems = remember { Screens.getScreens(enabledTabs) }
    val searchBarFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()

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

    val onSearch: (String) -> Unit = {
        if (it.isNotEmpty()) {
            if (searchSource == SearchSource.LOCAL) {
                focusManager.clearFocus(true)
            } else {
                onSearchActiveChange(false)
                if (youtubeNavigator(
                        context,
                        navController,
                        coroutineScope,
                        playerConnection,
                        snackbarHostState,
                        it.toUri()
                    )
                ) {
                    // don't do anything
                } else {
                    navController.navigate("search/${it.urlEncode()}")
                    if (context.dataStore[PauseSearchHistoryKey] != true) {
                        database.query {
                            insert(SearchHistory(query = it))
                        }
                    }
                }
            }
        }
    }


    val isTabRoute = navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
    val isSearchRoute = navBackStackEntry?.destination?.route?.startsWith("search/") == true
    val showSearchBar = searchActive || isSearchRoute
    val showIconRow = isTabRoute && !searchActive && !isSearchRoute

    LaunchedEffect(searchActive) {
        if (searchActive) {
            delay(100)
            searchBarFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val savedHeightOffsets = remember { mutableMapOf<String, Float>() }
    var previousRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(navBackStackEntry) {
        if (searchActive) {
            onSearchActiveChange(false)
        }
        val currentRoute = navBackStackEntry?.destination?.route
        previousRoute?.let { savedHeightOffsets[it] = scrollBehavior.state.heightOffset }
        if (currentRoute?.startsWith("${Screens.Folders.route}/") == true) {
            savedHeightOffsets[Screens.Folders.route] = 0f
        } else {
            scrollBehavior.state.heightOffset = savedHeightOffsets[currentRoute] ?: 0f
            scrollBehavior.state.contentOffset = 0f
        }
        previousRoute = currentRoute
    }

    AnimatedVisibility(
        visible = showSearchBar,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val searchBarInset = if (!LocalTabMode.current) {
            WindowInsets.safeDrawing.union(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Start))
        }
        else {
            WindowInsets.systemBars.only(WindowInsetsSides.Top)
        }
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            active = searchActive,
            onActiveChange = onSearchActiveChange,
            scrollBehavior = scrollBehavior,
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
                            if (searchActive || navBackStackEntry?.destination?.route?.startsWith("search") == true) {
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
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                navController.navigate("settings")
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null
                        )
                    }
                }
            },
            windowInsets = searchBarInset,
            focusRequester = searchBarFocusRequester,
        ) {
            if (UI_DEBUG) Log.v("SearchBarContainer", "SB-2")
            Crossfade(
                targetState = searchSource,
                label = "",
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) { searchSource ->
                when (searchSource) {
                    SearchSource.LOCAL -> LocalSearchScreen(
                        query = query.text,
                        navController = navController,
                        onDismiss = { onSearchActiveChange(false) },
                    )

                    SearchSource.ONLINE -> OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = onQueryChange,
                        navController = navController,
                        onSearch = {
                            if (youtubeNavigator(
                                    context,
                                    navController,
                                    coroutineScope,
                                    playerConnection,
                                    snackbarHostState,
                                    it.toUri()
                                )
                            ) {
                                return@OnlineSearchScreen
                            } else {
                                navController.navigate("search/${it.urlEncode()}")
                                if (context.dataStore[PauseSearchHistoryKey] != true) {
                                    database.query {
                                        insert(SearchHistory(query = it))
                                    }
                                }
                            }
                        },
                        onDismiss = { onSearchActiveChange(false) },
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showIconRow,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val iconRowInset = if (!LocalTabMode.current) {
            WindowInsets.safeDrawing.union(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Start))
        } else {
            WindowInsets.systemBars.only(WindowInsetsSides.Top)
        }
        TopIconBar(
            scrollBehavior = scrollBehavior,
            windowInsets = iconRowInset,
            localLibEnable = localLibEnable,
            onHistoryClick = { navController.navigate("history") },
            onStatsClick = { navController.navigate("stats") },
            onScannerClick = { navController.navigate("settings/local") },
            onSettingsClick = { navController.navigate("settings") },
            onAccountClick = { navController.navigate("settings/account_sync") },
            onSearchClick = { onSearchActiveChange(true) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopIconBar(
    scrollBehavior: TopAppBarScrollBehavior,
    windowInsets: WindowInsets,
    localLibEnable: Boolean,
    onHistoryClick: () -> Unit,
    onStatsClick: () -> Unit,
    onScannerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val heightOffsetLimit = with(LocalDensity.current) {
        -(AppBarHeight.toPx() + WindowInsets.systemBars.getTop(this))
    }
    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != heightOffsetLimit) {
            scrollBehavior.state.heightOffsetLimit = heightOffsetLimit
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .offset {
                IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt())
            }
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = SearchBarHorizontalPadding, vertical = SearchBarVerticalPadding)
            .height(InputFieldHeight)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            if (BuildConfig.DEBUG) {
                Text(
                    text = "Debug",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        // app_logo leaves about 3dp of empty space below the mark at 36dp
                        .padding(bottom = 3.dp)
                        // End the measured height at the baseline so the letters sit on the logo's bottom edge
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable[FirstBaseline]) {
                                placeable.place(0, 0)
                            }
                        }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search)
            )
        }
        IconButton(onClick = onHistoryClick) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.history)
            )
        }
        IconButton(onClick = onStatsClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                contentDescription = stringResource(R.string.stats)
            )
        }
        if (localLibEnable) {
            IconButton(onClick = onScannerClick) {
                Icon(
                    imageVector = Icons.Rounded.SdCard,
                    contentDescription = stringResource(R.string.scanner_local_title)
                )
            }
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings)
            )
        }
        IconButton(onClick = onAccountClick) {
            AccountAvatar(contentDescription = stringResource(R.string.account)) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = stringResource(R.string.account)
                )
            }
        }
    }
}

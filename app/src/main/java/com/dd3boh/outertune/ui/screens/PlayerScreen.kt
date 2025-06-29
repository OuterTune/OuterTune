package com.dd3boh.outertune.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.constants.DarkMode
import com.dd3boh.outertune.constants.DarkModeKey
import com.dd3boh.outertune.constants.PlayerBackgroundStyle
import com.dd3boh.outertune.constants.PlayerBackgroundStyleKey
import com.dd3boh.outertune.ui.component.BottomSheetState
import com.dd3boh.outertune.ui.player.BottomSheetPlayer
import com.dd3boh.outertune.ui.player.QueueScreen
import com.dd3boh.outertune.utils.rememberEnumPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController,
//    scrollBehavior: TopAppBarScrollBehavior,
    playerBottomSheetState: BottomSheetState,
    modifier: Modifier,
) {
    val playerConnection = LocalPlayerConnection.current
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        QueueScreen(
            playerBottomSheetState = playerBottomSheetState,
            onTerminate = {
                playerConnection?.service?.queueBoard?.detachedHead = false
            },
            onBackgroundColor = onBackgroundColor,
            navController = navController
        )
        BottomSheetPlayer(
            state = playerBottomSheetState,
            navController = navController
        )

    }
}

package com.dd3boh.outertune.ui.screens.library

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFoldersScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    filterContent: @Composable (() -> Unit)? = null
) {
    FolderScreen(navController, scrollBehavior, libraryFilterContent = filterContent)
}

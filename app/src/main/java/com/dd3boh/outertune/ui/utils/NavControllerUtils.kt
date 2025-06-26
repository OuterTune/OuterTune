package com.dd3boh.outertune.ui.utils

import androidx.navigation.NavController

val NavController.canNavigateUp: Boolean
    get() = currentBackStackEntry?.destination?.parent?.route != null

fun NavController.backToMain() {
    while (canNavigateUp) { navigateUp() }
}

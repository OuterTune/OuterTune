package com.dd3boh.outertune.extensions

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.LikedAutoDownloadKey
import com.dd3boh.outertune.constants.LikedAutodownloadMode
import com.dd3boh.outertune.constants.TabletUiKey
import com.dd3boh.outertune.constants.YtmSyncKey
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import com.zionhuang.innertube.utils.parseCookieString

fun Context.isAutoSyncEnabled(): Boolean {
    return dataStore.get(YtmSyncKey, true) && isUserLoggedIn()
}

fun Context.isUserLoggedIn(): Boolean {
    val cookie = dataStore.get(InnerTubeCookieKey, "")
    return "SAPISID" in parseCookieString(cookie)
}

fun Context.isInternetConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}

fun Context.getLikeAutoDownload(): LikedAutodownloadMode {
    return dataStore[LikedAutoDownloadKey].toEnum(LikedAutodownloadMode.OFF)
}

fun Context.tabMode(): Boolean {
    val config = resources.configuration
    val isTablet = config.smallestScreenWidthDp >= 600
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val forceTabMode = dataStore.get(TabletUiKey, isTablet)
    return (isTablet || forceTabMode) && isLandscape
}

fun Context.isPowerSaver(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isPowerSaveMode
}
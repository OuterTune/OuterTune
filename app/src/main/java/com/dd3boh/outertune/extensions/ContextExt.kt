package com.dd3boh.outertune.extensions

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.datastore.preferences.core.Preferences
import com.dd3boh.outertune.constants.AccessTokenKey
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.TabletUiKey
import com.dd3boh.outertune.constants.YtmSyncKey
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import com.zionhuang.innertube.utils.parseCookieString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun Context.isAutoSyncEnabled(): Boolean {
    return dataStore.get(YtmSyncKey, true) && isUserLoggedIn()
}

/**
 * Core Authentication Logic: Check if user is logged in to YouTube Music
 * 
 * The authentication token is the definitive proof of login status.
 * This function implements the core principle of checking for a valid access token
 * stored in the device's persistent DataStore.
 */
fun Context.isUserLoggedIn(): Boolean {
    // Primary method: Check for access token in DataStore
    val accessToken = dataStore.get(AccessTokenKey, "")
    if (accessToken.isNotBlank()) {
        return true
    }
    
    // Fallback method: Check SAPISID in cookie (for backward compatibility)
    val cookie = dataStore.get(InnerTubeCookieKey, "")
    return "SAPISID" in parseCookieString(cookie)
}

/**
 * Reactive Authentication Status: Continuously observe login status changes
 * 
 * This Flow-based function enables automatic UI updates when authentication status changes.
 * The UI subscribes to this Flow and automatically re-renders when the user signs in or out.
 */
fun Context.observeUserLoggedIn(): Flow<Boolean> {
    return dataStore.data.map { preferences: Preferences ->
        // Primary method: Check for access token
        val accessToken = preferences[AccessTokenKey] ?: ""
        if (accessToken.isNotBlank()) {
            return@map true
        }
        
        // Fallback method: Check SAPISID in cookie (for backward compatibility)
        val cookie = preferences[InnerTubeCookieKey] ?: ""
        "SAPISID" in parseCookieString(cookie)
    }
}

fun Context.isInternetConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}

fun Context.supportsWideScreen() : Boolean {
    val config = resources.configuration
    return config.screenWidthDp >= 600
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
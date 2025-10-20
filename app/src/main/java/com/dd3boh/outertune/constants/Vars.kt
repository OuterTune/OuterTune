package com.dd3boh.outertune.constants

import android.os.Build
import com.dd3boh.outertune.BuildConfig

/**
 * Feature flags
 */

const val ENABLE_UPDATE_CHECKER = BuildConfig.FLAVOR == "full"

const val ENABLE_FFMETADATAEX = BuildConfig.FLAVOR == "full"


/**
 * Extra configuration
 */

// maximum concurrent image resolution jobs
const val MAX_COIL_JOBS = 16

// maximum concurrent download jobs allowed
const val MAX_DL_JOBS = 5

// maximum concurrent scanner jobs allowed
const val MAX_LM_SCANNER_JOBS = 7 // 1 dispatcher + 6 workers

// maximum concurrent scanner jobs allowed
const val MAX_YTM_SYNC_JOBS = 3

// maximum concurrent scanner jobs allowed
const val MAX_YTM_CONTENT_JOBS = 16


/**
 * Constants
 */

/**
 * The minimum amount of time the automatic scanner in between successful auto scanner runs
 */
const val AUTO_SCAN_COOLDOWN = 39600000L // 11 hours

/**
 * The minimum amount of time the automatic scanner in between auto scanner runs, regardless of failure or success.
 * This value should always be less than AUTO_SCAN_COOLDOWN
 */
const val AUTO_SCAN_SOFT_COOLDOWN = 7200000L // 2 hours
const val LYRIC_FETCH_TIMEOUT = 60000L
const val SNACKBAR_VERY_SHORT = 2000L

const val OOBE_VERSION = 5

const val SCANNER_OWNER_DL = 32
const val SCANNER_OWNER_LM = 1
const val SCANNER_OWNER_M3U = 2

const val SYNC_CD = 60000 * 30

const val MAX_PLAYER_CONSECUTIVE_ERR = 3

val DEFAULT_PLAYER_BACKGROUND =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PlayerBackgroundStyle.BLUR else PlayerBackgroundStyle.GRADIENT

/**
 * Debug
 */
// crash at first extractor scanner error. Currently not implemented
const val SCANNER_CRASH_AT_FIRST_ERROR = false

// true will not use multithreading for scanner
const val SYNC_SCANNER = false

// enable verbose debugging details for scanner
const val SCANNER_DEBUG = false

// enable verbose debugging details for extractor
const val EXTRACTOR_DEBUG = false

// enable printing of *ALL* data that extractor reads
const val DEBUG_SAVE_OUTPUT = false // ignored (will be false) when EXTRACTOR_DEBUG IS false

const val QUEUE_DEBUG = false

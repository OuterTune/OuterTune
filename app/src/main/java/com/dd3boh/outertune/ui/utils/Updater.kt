package com.dd3boh.outertune.ui.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.dd3boh.outertune.constants.LastUpdateCheckKey
import com.dd3boh.outertune.extensions.isInternetConnected
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import com.dd3boh.outertune.utils.reportException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.time.Duration.Companion.days

object Updater {
    private val TAG = "OtUpdater"
    private val client = HttpClient()
    private var isCheckingForUpdate = false
    var lastCheckTime = -1L
        private set

    suspend fun tryCheckUpdate(context: Context, force: Boolean = false): String? {
        var ret: String? = null
        val timeNow = System.currentTimeMillis()
        if (lastCheckTime < 0) {
            lastCheckTime = context.dataStore.get(LastUpdateCheckKey, timeNow - 8.days.inWholeMilliseconds)
        }
        Log.d(TAG, "Force check: $force, last check: $lastCheckTime should check = ${timeNow - lastCheckTime > 7.days.inWholeMilliseconds}")
        if (context.isInternetConnected() && !isCheckingForUpdate
            && (force || timeNow - lastCheckTime > 7.days.inWholeMilliseconds)) {
            ret = checkForUpdate()
            if (ret != null) {
                context.dataStore.edit { settings ->
                    settings[LastUpdateCheckKey] = System.currentTimeMillis()
                }
            }
        }

        Log.d(TAG, "Update check status: $ret")
        return ret
    }

    private suspend fun checkForUpdate(): String? {
        try {
            isCheckingForUpdate = true
            val response = client.get("https://api.github.com/repos/OuterTune/OuterTune/releases/latest").bodyAsText()
            val json = JSONObject(response)
            val versionName = json.getString("name")
            lastCheckTime = System.currentTimeMillis()
            return versionName
        } catch (e: Exception) {
            Log.d(TAG, "Error checking for update")
            reportException(e)
            return null
        } finally {
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000)
                isCheckingForUpdate = false
            }
        }
    }
}
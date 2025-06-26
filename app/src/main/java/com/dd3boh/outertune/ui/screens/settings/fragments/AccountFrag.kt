/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.screens.settings.fragments

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dd3boh.outertune.App.Companion.forgetAccount
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AccountChannelHandleKey
import com.dd3boh.outertune.constants.AccountEmailKey
import com.dd3boh.outertune.constants.AccountNameKey
import com.dd3boh.outertune.constants.DataSyncIdKey
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.UseLoginForBrowse
import com.dd3boh.outertune.constants.VisitorDataKey
import com.dd3boh.outertune.ui.component.InfoLabel
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.TextFieldDialog
import com.dd3boh.outertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.utils.parseCookieString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.AccountFrag(navController: NavController) {
    val context = LocalContext.current

    val (accountName, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)

    // temp vars
    var showToken: Boolean by remember {
        mutableStateOf(false)
    }
    var showTokenEditor by remember {
        mutableStateOf(false)
    }

    PreferenceEntry(
        title = { Text(if (isLoggedIn) accountName else stringResource(R.string.login)) },
        description = if (isLoggedIn) {
            accountEmail.takeIf { it.isNotEmpty() }
                ?: accountChannelHandle.takeIf { it.isNotEmpty() }
        } else null,
        icon = { Icon(Icons.Rounded.Person, null) },
        onClick = { navController.navigate("login") }
    )
    if (isLoggedIn) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.action_logout)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
            onClick = {
                forgetAccount(context)
            }
        )
    }

    PreferenceEntry(
        title = {
            if (showToken) {
                Text(stringResource(R.string.token_shown))
                Text(
                    text = if (isLoggedIn) innerTubeCookie else stringResource(R.string.not_logged_in),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1 // just give a preview so user knows it's at least there
                )
            } else {
                Text(stringResource(R.string.token_hidden))
            }
        },
        onClick = {
            if (showToken == false) {
                showToken = true
            } else {
                showTokenEditor = true
            }
        },
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.use_login_for_browse)) },
        description = stringResource(R.string.use_login_for_browse_desc),
        icon = { Icon(Icons.Rounded.Person, null) },
        checked = useLoginForBrowse,
        onCheckedChange = {
            YouTube.useLoginForBrowse = it
            onUseLoginForBrowseChange(it)
        }
    )


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showTokenEditor) {
        val text =
            "***INNERTUBE COOKIE*** =${innerTubeCookie}\n\n***VISITOR DATA*** =${visitorData}\n\n***DATASYNC ID*** =${dataSyncId}\n\n***ACCOUNT NAME*** =${accountName}\n\n***ACCOUNT EMAIL*** =${accountEmail}\n\n***ACCOUNT CHANNEL HANDLE*** =${accountChannelHandle}"
        TextFieldDialog(
            modifier = Modifier,
            initialTextFieldValue = TextFieldValue(text),
            onDone = { data ->
                data.split("\n").forEach {
                    if (it.startsWith("***INNERTUBE COOKIE*** =")) {
                        onInnerTubeCookieChange(it.substringAfter("***INNERTUBE COOKIE*** ="))
                    } else if (it.startsWith("***VISITOR DATA*** =")) {
                        onVisitorDataChange(it.substringAfter("***VISITOR DATA*** ="))
                    } else if (it.startsWith("***DATASYNC ID*** =")) {
                        onDataSyncIdChange(it.substringAfter("***DATASYNC ID*** ="))
                    } else if (it.startsWith("***ACCOUNT NAME*** =")) {
                        onAccountNameChange(it.substringAfter("***ACCOUNT NAME*** ="))
                    } else if (it.startsWith("***ACCOUNT EMAIL*** =")) {
                        onAccountEmailChange(it.substringAfter("***ACCOUNT EMAIL*** ="))
                    } else if (it.startsWith("***ACCOUNT CHANNEL HANDLE*** =")) {
                        onAccountChannelHandleChange(it.substringAfter("***ACCOUNT CHANNEL HANDLE*** ="))
                    }
                }
            },
            onDismiss = { showTokenEditor = false },
            singleLine = false,
            maxLines = 20,
            isInputValid = {
                it.isNotEmpty() &&
                        try {
                            "SAPISID" in parseCookieString(it)
                            true
                        } catch (e: Exception) {
                            false
                        }
            },
            extraContent = {
                InfoLabel(text = stringResource(R.string.token_adv_login_description))
            }
        )
    }
}
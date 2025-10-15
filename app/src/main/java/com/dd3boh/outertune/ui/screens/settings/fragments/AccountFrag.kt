/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.screens.settings.fragments

import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dd3boh.outertune.App.Companion.forgetAccount
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.GeminiApiKey
import com.dd3boh.outertune.constants.AccountChannelHandleKey
import com.dd3boh.outertune.constants.AccountEmailKey
import com.dd3boh.outertune.constants.AccountNameKey
import com.dd3boh.outertune.constants.DataSyncIdKey
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.UseLoginForBrowse
import com.dd3boh.outertune.constants.VisitorDataKey
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.SettingsClickToReveal
import com.dd3boh.outertune.ui.dialog.InfoLabel
import com.dd3boh.outertune.ui.dialog.TextFieldDialog
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
        Spacer(Modifier.height(8.dp))
        InfoLabel(stringResource(R.string.action_logout_tooltip))
        Spacer(Modifier.height(24.dp))
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

@Composable
fun ColumnScope.AccountExtrasFrag() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val (geminiKey, setGeminiKey) = rememberPreference(GeminiApiKey, "")
    var tempGeminiKey by remember { mutableStateOf(geminiKey) }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)

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

    // Gemini API key management (moved from Experimental to Account & Sync)
    Spacer(Modifier.height(16.dp))
    SettingsClickToReveal(title = stringResource(R.string.prefs_gemini_api_group)) {
        // Title row
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.prefs_gemini_api_title), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.prefs_gemini_api_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = tempGeminiKey,
            onValueChange = { tempGeminiKey = it.trim() },
            label = { Text(stringResource(R.string.prefs_gemini_api_label)) },
            placeholder = { Text("AIza... or AIza...") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            Button(onClick = {
                setGeminiKey(tempGeminiKey)
                Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") }) {
                Text(stringResource(R.string.get_api_key))
            }
            Spacer(Modifier.width(12.dp))
            if (geminiKey.isNotEmpty()) {
                Button(onClick = {
                    tempGeminiKey = ""
                    setGeminiKey("")
                    Toast.makeText(context, context.getString(R.string.cleared), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.clear)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (geminiKey.isEmpty()) stringResource(R.string.prefs_gemini_api_status_off) else stringResource(R.string.prefs_gemini_api_status_on),
            color = if (geminiKey.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Accordion: How to get an API key
        Spacer(Modifier.height(12.dp))
        SettingsClickToReveal(title = stringResource(R.string.prefs_gemini_api_howto)) {
            Text(
                text = stringResource(R.string.prefs_gemini_api_howto_steps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                Button(onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") }) {
                    Text(stringResource(R.string.get_api_key))
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { uriHandler.openUri("https://ai.google.dev/gemini-api/docs/api-key") }) {
                    Text(stringResource(R.string.learn_more))
                }
            }
        }
    }
}

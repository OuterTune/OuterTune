/*
 * Android TV helpers: detection and focusable modifier for D-pad navigation
 */
package com.dd3boh.outertune.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Returns true if running on Android TV/leanback device */
fun Context.isTvDevice(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    // Fallback: feature flag
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            || packageManager.hasSystemFeature("android.software.leanback")
}

@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember { context.isTvDevice() }
}

/**
 * Apply TV focus behavior: focusable, subtle scale on focus, and a light border.
 * No-ops on non-TV devices.
 */
fun Modifier.tvFocusable(isTv: Boolean, interactionSource: MutableInteractionSource? = null): Modifier =
    if (!isTv) this else composed {
        val src = interactionSource ?: remember { MutableInteractionSource() }
        var focused by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()
        LaunchedEffect(src) {
            scope.launch {
                src.interactions.collect { inter ->
                    when (inter) {
                        is FocusInteraction.Focus -> focused = true
                        is FocusInteraction.Unfocus -> focused = false
                    }
                }
            }
        }

        val scale by animateFloatAsState(targetValue = if (focused) 1.04f else 1f, label = "tv-scale")
        this
            .scale(scale)
            .border(BorderStroke(if (focused) 2.dp else 0.dp, Color(0x66FFFFFF)))
            .padding(if (focused) 0.dp else 2.dp)
            .focusable(interactionSource = src)
    }

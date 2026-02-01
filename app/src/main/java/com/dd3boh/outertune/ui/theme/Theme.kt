/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import coil3.toUri
import com.dd3boh.outertune.playback.PlayerConnection
import com.dd3boh.outertune.utils.LocalArtworkPath
import com.dd3boh.outertune.utils.coilCoroutine
import com.google.material.color.dynamiccolor.DynamicScheme
import com.google.material.color.hct.Hct
import com.google.material.color.scheme.SchemeTonalSpot
import com.google.material.color.score.Score
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// TODO: support for custom accent
val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun OuterTuneTheme(
    context: Context,
    playerConnection: PlayerConnection?,
    enableDynamicTheme: Boolean,
    isSystemInDarkTheme: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    highContrastCompat: Boolean,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var themeColor by rememberSaveable(stateSaver = ColorSaver) {
        mutableStateOf(DefaultThemeColor)
    }

    LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme) {
        val playerConnection = playerConnection
        if (!enableDynamicTheme || playerConnection == null) {
            themeColor = DefaultThemeColor
            return@LaunchedEffect
        }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    coroutineScope.launch(coilCoroutine) {
                        var ret = DefaultThemeColor
                        if (song != null) {
                            val uri = (if (song.isLocal) song.localPath else song.thumbnailUrl)?.toUri()
                            if (uri != null) {
                                val model = if (uri.toString().startsWith("/storage/")) {
                                    LocalArtworkPath(uri.toString(), 100, 100)
                                } else {
                                    uri
                                }

                                val result = context.imageLoader.execute(
                                    ImageRequest.Builder(context)
                                        .data(model)
                                        .allowHardware(false)
                                        .build()
                                )

                                ret = result.image?.toBitmap()?.extractThemeColor() ?: DefaultThemeColor
                            }
                        }
                        themeColor = ret
                    }
                }
    }


    val colorScheme = remember(darkTheme, pureBlack, themeColor) {
       if (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val systemTheme = if (darkTheme) {
                dynamicDarkColorScheme(context).pureBlack(pureBlack)
            } else {
                dynamicLightColorScheme(context)
            }


            // when high contrast mode Android collapses all accent colours into (more or less) one shade. We use
            // secondaryContainer and onSecondaryContainer weirdly in several places in terms of theming so just replace
            // those with shades that make sense
            if (highContrastCompat) {
                systemTheme.copy(
                    secondaryContainer = systemTheme.surfaceContainerHigh,
                    onSecondaryContainer = systemTheme.secondary,
                )
            } else {
                systemTheme
            }
        } else {
            SchemeTonalSpot(Hct.fromInt(themeColor.toArgb()), darkTheme, 0.0)
                .toColorScheme()
                .pureBlack(darkTheme && pureBlack)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(16)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun DynamicScheme.toColorScheme() = ColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(primary),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest),
    primaryFixed = Color(primaryFixed),
    primaryFixedDim = Color(primaryFixedDim),
    onPrimaryFixed = Color(onPrimaryFixed),
    onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
    secondaryFixed = Color(secondaryFixed),
    secondaryFixedDim = Color(secondaryFixedDim),
    onSecondaryFixed = Color(onSecondaryFixed),
    onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
    tertiaryFixed = Color(tertiaryFixed),
    tertiaryFixedDim = Color(tertiaryFixedDim),
    onTertiaryFixed = Color(onTertiaryFixed),
    onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}

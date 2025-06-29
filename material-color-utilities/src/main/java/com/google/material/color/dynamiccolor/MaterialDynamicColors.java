/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.material.color.dynamiccolor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** Named colors, otherwise known as tokens, or roles, in the Material Design system. */
// Prevent lint for Function.apply not being available on Android before API level 14 (4.0.1).
// "AndroidJdkLibsChecker" for Function, "NewApi" for Function.apply().
// A java_library Bazel rule with an Android constraint cannot skip these warnings without this
// annotation; another solution would be to create an android_library rule and supply
// AndroidManifest with an SDK set higher than 14.
@SuppressWarnings({"AndroidJdkLibsChecker", "NewApi"})
public final class MaterialDynamicColors {

  private static final ColorSpec colorSpec = new ColorSpec2025();

  @NonNull
  public DynamicColor highestSurface(@NonNull DynamicScheme s) {
    return colorSpec.highestSurface(s);
  }

  ////////////////////////////////////////////////////////////////
  // Main Palettes                                              //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor primaryPaletteKeyColor() {
    return colorSpec.primaryPaletteKeyColor();
  }

  @NonNull
  public DynamicColor secondaryPaletteKeyColor() {
    return colorSpec.secondaryPaletteKeyColor();
  }

  @NonNull
  public DynamicColor tertiaryPaletteKeyColor() {
    return colorSpec.tertiaryPaletteKeyColor();
  }

  @NonNull
  public DynamicColor neutralPaletteKeyColor() {
    return colorSpec.neutralPaletteKeyColor();
  }

  @NonNull
  public DynamicColor neutralVariantPaletteKeyColor() {
    return colorSpec.neutralVariantPaletteKeyColor();
  }

  @NonNull
  public DynamicColor errorPaletteKeyColor() {
    return colorSpec.errorPaletteKeyColor();
  }

  ////////////////////////////////////////////////////////////////
  // Surfaces [S]                                               //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor background() {
    return colorSpec.background();
  }

  @NonNull
  public DynamicColor onBackground() {
    return colorSpec.onBackground();
  }

  @NonNull
  public DynamicColor surface() {
    return colorSpec.surface();
  }

  @NonNull
  public DynamicColor surfaceDim() {
    return colorSpec.surfaceDim();
  }

  @NonNull
  public DynamicColor surfaceBright() {
    return colorSpec.surfaceBright();
  }

  @NonNull
  public DynamicColor surfaceContainerLowest() {
    return colorSpec.surfaceContainerLowest();
  }

  @NonNull
  public DynamicColor surfaceContainerLow() {
    return colorSpec.surfaceContainerLow();
  }

  @NonNull
  public DynamicColor surfaceContainer() {
    return colorSpec.surfaceContainer();
  }

  @NonNull
  public DynamicColor surfaceContainerHigh() {
    return colorSpec.surfaceContainerHigh();
  }

  @NonNull
  public DynamicColor surfaceContainerHighest() {
    return colorSpec.surfaceContainerHighest();
  }

  @NonNull
  public DynamicColor onSurface() {
    return colorSpec.onSurface();
  }

  @NonNull
  public DynamicColor surfaceVariant() {
    return colorSpec.surfaceVariant();
  }

  @NonNull
  public DynamicColor onSurfaceVariant() {
    return colorSpec.onSurfaceVariant();
  }

  @NonNull
  public DynamicColor inverseSurface() {
    return colorSpec.inverseSurface();
  }

  @NonNull
  public DynamicColor inverseOnSurface() {
    return colorSpec.inverseOnSurface();
  }

  @NonNull
  public DynamicColor outline() {
    return colorSpec.outline();
  }

  @NonNull
  public DynamicColor outlineVariant() {
    return colorSpec.outlineVariant();
  }

  @NonNull
  public DynamicColor shadow() {
    return colorSpec.shadow();
  }

  @NonNull
  public DynamicColor scrim() {
    return colorSpec.scrim();
  }

  @NonNull
  public DynamicColor surfaceTint() {
    return colorSpec.surfaceTint();
  }

  ////////////////////////////////////////////////////////////////
  // Primaries [P]                                              //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor primary() {
    return colorSpec.primary();
  }

  @Nullable
  public DynamicColor primaryDim() {
    return colorSpec.primaryDim();
  }

  @NonNull
  public DynamicColor onPrimary() {
    return colorSpec.onPrimary();
  }

  @NonNull
  public DynamicColor primaryContainer() {
    return colorSpec.primaryContainer();
  }

  @NonNull
  public DynamicColor onPrimaryContainer() {
    return colorSpec.onPrimaryContainer();
  }

  @NonNull
  public DynamicColor inversePrimary() {
    return colorSpec.inversePrimary();
  }

  /////////////////////////////////////////////////////////////////
  // Primary Fixed Colors [PF]                                   //
  /////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor primaryFixed() {
    return colorSpec.primaryFixed();
  }

  @NonNull
  public DynamicColor primaryFixedDim() {
    return colorSpec.primaryFixedDim();
  }

  @NonNull
  public DynamicColor onPrimaryFixed() {
    return colorSpec.onPrimaryFixed();
  }

  @NonNull
  public DynamicColor onPrimaryFixedVariant() {
    return colorSpec.onPrimaryFixedVariant();
  }

  ////////////////////////////////////////////////////////////////
  // Secondaries [Q]                                            //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor secondary() {
    return colorSpec.secondary();
  }

  @Nullable
  public DynamicColor secondaryDim() {
    return colorSpec.secondaryDim();
  }

  @NonNull
  public DynamicColor onSecondary() {
    return colorSpec.onSecondary();
  }

  @NonNull
  public DynamicColor secondaryContainer() {
    return colorSpec.secondaryContainer();
  }

  @NonNull
  public DynamicColor onSecondaryContainer() {
    return colorSpec.onSecondaryContainer();
  }

  /////////////////////////////////////////////////////////////////
  // Secondary Fixed Colors [QF]                                 //
  /////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor secondaryFixed() {
    return colorSpec.secondaryFixed();
  }

  @NonNull
  public DynamicColor secondaryFixedDim() {
    return colorSpec.secondaryFixedDim();
  }

  @NonNull
  public DynamicColor onSecondaryFixed() {
    return colorSpec.onSecondaryFixed();
  }

  @NonNull
  public DynamicColor onSecondaryFixedVariant() {
    return colorSpec.onSecondaryFixedVariant();
  }

  ////////////////////////////////////////////////////////////////
  // Tertiaries [T]                                             //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor tertiary() {
    return colorSpec.tertiary();
  }

  @Nullable
  public DynamicColor tertiaryDim() {
    return colorSpec.tertiaryDim();
  }

  @NonNull
  public DynamicColor onTertiary() {
    return colorSpec.onTertiary();
  }

  @NonNull
  public DynamicColor tertiaryContainer() {
    return colorSpec.tertiaryContainer();
  }

  @NonNull
  public DynamicColor onTertiaryContainer() {
    return colorSpec.onTertiaryContainer();
  }

  /////////////////////////////////////////////////////////////////
  // Tertiary Fixed Colors [TF]                                  //
  /////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor tertiaryFixed() {
    return colorSpec.tertiaryFixed();
  }

  @NonNull
  public DynamicColor tertiaryFixedDim() {
    return colorSpec.tertiaryFixedDim();
  }

  @NonNull
  public DynamicColor onTertiaryFixed() {
    return colorSpec.onTertiaryFixed();
  }

  @NonNull
  public DynamicColor onTertiaryFixedVariant() {
    return colorSpec.onTertiaryFixedVariant();
  }

  ////////////////////////////////////////////////////////////////
  // Errors [E]                                                 //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor error() {
    return colorSpec.error();
  }

  @Nullable
  public DynamicColor errorDim() {
    return colorSpec.errorDim();
  }

  @NonNull
  public DynamicColor onError() {
    return colorSpec.onError();
  }

  @NonNull
  public DynamicColor errorContainer() {
    return colorSpec.errorContainer();
  }

  @NonNull
  public DynamicColor onErrorContainer() {
    return colorSpec.onErrorContainer();
  }

  ////////////////////////////////////////////////////////////////
  // Android-only colors                                        //
  ////////////////////////////////////////////////////////////////

  /**
   * These colors were present in Android framework before Android U, and used by MDC controls. They
   * should be avoided, if possible. It's unclear if they're used on multiple backgrounds, and if
   * they are, they can't be adjusted for contrast.* For now, they will be set with no background,
   * and those won't adjust for contrast, avoiding issues.
   *
   * <p>* For example, if the same color is on a white background _and_ black background, there's no
   * way to increase contrast with either without losing contrast with the other.
   */

  // colorControlActivated documented as colorAccent in M3 & GM3.
  // colorAccent documented as colorSecondary in M3 and colorPrimary in GM3.
  // Android used Material's Container as Primary/Secondary/Tertiary at launch.
  // Therefore, this is a duplicated version of Primary Container.
  @NonNull
  public DynamicColor controlActivated() {
    return colorSpec.controlActivated();
  }

  // colorControlNormal documented as textColorSecondary in M3 & GM3.
  // In Material, textColorSecondary points to onSurfaceVariant in the non-disabled state,
  // which is Neutral Variant T30/80 in light/dark.
  @NonNull
  public DynamicColor controlNormal() {
    return colorSpec.controlNormal();
  }

  // colorControlHighlight documented, in both M3 & GM3:
  // Light mode: #1f000000 dark mode: #33ffffff.
  // These are black and white with some alpha.
  // 1F hex = 31 decimal; 31 / 255 = 12% alpha.
  // 33 hex = 51 decimal; 51 / 255 = 20% alpha.
  // DynamicColors do not support alpha currently, and _may_ not need it for this use case,
  // depending on how MDC resolved alpha for the other cases.
  // Returning black in dark mode, white in light mode.
  @NonNull
  public DynamicColor controlHighlight() {
    return colorSpec.controlHighlight();
  }

  // textColorPrimaryInverse documented, in both M3 & GM3, documented as N10/N90.
  @NonNull
  public DynamicColor textPrimaryInverse() {
    return colorSpec.textPrimaryInverse();
  }

  // textColorSecondaryInverse and textColorTertiaryInverse both documented, in both M3 & GM3, as
  // NV30/NV80
  @NonNull
  public DynamicColor textSecondaryAndTertiaryInverse() {
    return colorSpec.textSecondaryAndTertiaryInverse();
  }

  // textColorPrimaryInverseDisableOnly documented, in both M3 & GM3, as N10/N90
  @NonNull
  public DynamicColor textPrimaryInverseDisableOnly() {
    return colorSpec.textPrimaryInverseDisableOnly();
  }

  // textColorSecondaryInverse and textColorTertiaryInverse in disabled state both documented,
  // in both M3 & GM3, as N10/N90
  @NonNull
  public DynamicColor textSecondaryAndTertiaryInverseDisabled() {
    return colorSpec.textSecondaryAndTertiaryInverseDisabled();
  }

  // textColorHintInverse documented, in both M3 & GM3, as N10/N90
  @NonNull
  public DynamicColor textHintInverse() {
    return colorSpec.textHintInverse();
  }

  ////////////////////////////////////////////////////////////////
  // All Colors                                                 //
  ////////////////////////////////////////////////////////////////

  /** All dynamic colors in Material Design system. */
  public final List<Supplier<DynamicColor>> allDynamicColors() {
    return Arrays.asList(
        this::primaryPaletteKeyColor,
        this::secondaryPaletteKeyColor,
        this::tertiaryPaletteKeyColor,
        this::neutralPaletteKeyColor,
        this::neutralVariantPaletteKeyColor,
        this::errorPaletteKeyColor,
        this::background,
        this::onBackground,
        this::surface,
        this::surfaceDim,
        this::surfaceBright,
        this::surfaceContainerLowest,
        this::surfaceContainerLow,
        this::surfaceContainer,
        this::surfaceContainerHigh,
        this::surfaceContainerHighest,
        this::onSurface,
        this::surfaceVariant,
        this::onSurfaceVariant,
        this::outline,
        this::outlineVariant,
        this::inverseSurface,
        this::inverseOnSurface,
        this::shadow,
        this::scrim,
        this::surfaceTint,
        this::primary,
        this::primaryDim,
        this::onPrimary,
        this::primaryContainer,
        this::onPrimaryContainer,
        this::primaryFixed,
        this::primaryFixedDim,
        this::onPrimaryFixed,
        this::onPrimaryFixedVariant,
        this::inversePrimary,
        this::secondary,
        this::secondaryDim,
        this::onSecondary,
        this::secondaryContainer,
        this::onSecondaryContainer,
        this::secondaryFixed,
        this::secondaryFixedDim,
        this::onSecondaryFixed,
        this::onSecondaryFixedVariant,
        this::tertiary,
        this::tertiaryDim,
        this::onTertiary,
        this::tertiaryContainer,
        this::onTertiaryContainer,
        this::tertiaryFixed,
        this::tertiaryFixedDim,
        this::onTertiaryFixed,
        this::onTertiaryFixedVariant,
        this::error,
        this::errorDim,
        this::onError,
        this::errorContainer,
        this::onErrorContainer,
        this::controlActivated,
        this::controlNormal,
        this::controlHighlight,
        this::textPrimaryInverse,
        this::textSecondaryAndTertiaryInverse,
        this::textPrimaryInverseDisableOnly,
        this::textSecondaryAndTertiaryInverseDisabled,
        this::textHintInverse);
  }
}

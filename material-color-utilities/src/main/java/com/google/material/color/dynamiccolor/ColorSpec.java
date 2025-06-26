/*
 * Copyright 2025 Google LLC
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
import com.google.material.color.dynamiccolor.DynamicScheme.Platform;
import com.google.material.color.hct.Hct;
import com.google.material.color.palettes.TonalPalette;
import java.util.Optional;

/** An interface defining all the necessary methods that could be different between specs. */
public interface ColorSpec {

  /** All available spec versions. */
  public enum SpecVersion {
    SPEC_2021,
    SPEC_2025,
  }

  ////////////////////////////////////////////////////////////////
  // Main Palettes                                              //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor primaryPaletteKeyColor();

  @NonNull
  public DynamicColor secondaryPaletteKeyColor();

  @NonNull
  public DynamicColor tertiaryPaletteKeyColor();

  @NonNull
  public DynamicColor neutralPaletteKeyColor();

  @NonNull
  public DynamicColor neutralVariantPaletteKeyColor();

  @NonNull
  public DynamicColor errorPaletteKeyColor();

  ////////////////////////////////////////////////////////////////
  // Surfaces [S]                                               //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor background();

  @NonNull
  public DynamicColor onBackground();

  @NonNull
  public DynamicColor surface();

  @NonNull
  public DynamicColor surfaceDim();

  @NonNull
  public DynamicColor surfaceBright();

  @NonNull
  public DynamicColor surfaceContainerLowest();

  @NonNull
  public DynamicColor surfaceContainerLow();

  @NonNull
  public DynamicColor surfaceContainer();

  @NonNull
  public DynamicColor surfaceContainerHigh();

  @NonNull
  public DynamicColor surfaceContainerHighest();

  @NonNull
  public DynamicColor onSurface();

  @NonNull
  public DynamicColor surfaceVariant();

  @NonNull
  public DynamicColor onSurfaceVariant();

  @NonNull
  public DynamicColor inverseSurface();

  @NonNull
  public DynamicColor inverseOnSurface();

  @NonNull
  public DynamicColor outline();

  @NonNull
  public DynamicColor outlineVariant();

  @NonNull
  public DynamicColor shadow();

  @NonNull
  public DynamicColor scrim();

  @NonNull
  public DynamicColor surfaceTint();

  ////////////////////////////////////////////////////////////////
  // Primaries [P]                                              //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor primary();

  @Nullable
  public DynamicColor primaryDim();

  @NonNull
  public DynamicColor onPrimary();

  @NonNull
  public DynamicColor primaryContainer();

  @NonNull
  public DynamicColor onPrimaryContainer();

  @NonNull
  public DynamicColor inversePrimary();

  ////////////////////////////////////////////////////////////////
  // Secondaries [Q]                                            //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor secondary();

  @Nullable
  public DynamicColor secondaryDim();

  @NonNull
  public DynamicColor onSecondary();

  @NonNull
  public DynamicColor secondaryContainer();

  @NonNull
  public DynamicColor onSecondaryContainer();

  ////////////////////////////////////////////////////////////////
  // Tertiaries [T]                                             //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor tertiary();

  @Nullable
  public DynamicColor tertiaryDim();

  @NonNull
  public DynamicColor onTertiary();

  @NonNull
  public DynamicColor tertiaryContainer();

  @NonNull
  public DynamicColor onTertiaryContainer();

  ////////////////////////////////////////////////////////////////
  // Errors [E]                                                 //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor error();

  @Nullable
  public DynamicColor errorDim();

  @NonNull
  public DynamicColor onError();

  @NonNull
  public DynamicColor errorContainer();

  @NonNull
  public DynamicColor onErrorContainer();

  ////////////////////////////////////////////////////////////////
  // Primary Fixed Colors [PF]                                  //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor primaryFixed();

  @NonNull
  public DynamicColor primaryFixedDim();

  @NonNull
  public DynamicColor onPrimaryFixed();

  @NonNull
  public DynamicColor onPrimaryFixedVariant();

  ////////////////////////////////////////////////////////////////
  // Secondary Fixed Colors [QF]                                //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor secondaryFixed();

  @NonNull
  public DynamicColor secondaryFixedDim();

  @NonNull
  public DynamicColor onSecondaryFixed();

  @NonNull
  public DynamicColor onSecondaryFixedVariant();

  ////////////////////////////////////////////////////////////////
  // Tertiary Fixed Colors [TF]                                 //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor tertiaryFixed();

  @NonNull
  public DynamicColor tertiaryFixedDim();

  @NonNull
  public DynamicColor onTertiaryFixed();

  @NonNull
  public DynamicColor onTertiaryFixedVariant();

  //////////////////////////////////////////////////////////////////
  // Android-only Colors                                          //
  //////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor controlActivated();

  @NonNull
  public DynamicColor controlNormal();

  @NonNull
  public DynamicColor controlHighlight();

  @NonNull
  public DynamicColor textPrimaryInverse();

  @NonNull
  public DynamicColor textSecondaryAndTertiaryInverse();

  @NonNull
  public DynamicColor textPrimaryInverseDisableOnly();

  @NonNull
  public DynamicColor textSecondaryAndTertiaryInverseDisabled();

  @NonNull
  public DynamicColor textHintInverse();

  ////////////////////////////////////////////////////////////////
  // Other                                                      //
  ////////////////////////////////////////////////////////////////

  @NonNull
  public DynamicColor highestSurface(@NonNull DynamicScheme s);

  /////////////////////////////////////////////////////////////////
  // Color value calculations                                    //
  /////////////////////////////////////////////////////////////////

  Hct getHct(DynamicScheme scheme, DynamicColor color);

  double getTone(DynamicScheme scheme, DynamicColor color);

  //////////////////////////////////////////////////////////////////
  // Scheme Palettes                                              //
  //////////////////////////////////////////////////////////////////

  @NonNull
  public TonalPalette getPrimaryPalette(
      Variant variant, Hct sourceColorHct, boolean isDark, Platform platform, double contrastLevel);

  @NonNull
  public TonalPalette getSecondaryPalette(
      Variant variant, Hct sourceColorHct, boolean isDark, Platform platform, double contrastLevel);

  @NonNull
  public TonalPalette getTertiaryPalette(
      Variant variant, Hct sourceColorHct, boolean isDark, Platform platform, double contrastLevel);

  @NonNull
  public TonalPalette getNeutralPalette(
      Variant variant, Hct sourceColorHct, boolean isDark, Platform platform, double contrastLevel);

  @NonNull
  public TonalPalette getNeutralVariantPalette(
      Variant variant, Hct sourceColorHct, boolean isDark, Platform platform, double contrastLevel);

  @NonNull
  public Optional<TonalPalette> getErrorPalette(
      Variant variant, Hct sourceColorHct, boolean isDark, Platform platform, double contrastLevel);
}

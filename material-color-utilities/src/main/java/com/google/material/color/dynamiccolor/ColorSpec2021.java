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

import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.errorprone.annotations.Var;
import com.google.material.color.contrast.Contrast;
import com.google.material.color.dislike.DislikeAnalyzer;
import com.google.material.color.dynamiccolor.DynamicScheme.Platform;
import com.google.material.color.hct.Hct;
import com.google.material.color.palettes.TonalPalette;
import com.google.material.color.temperature.TemperatureCache;
import com.google.material.color.utils.MathUtils;
import java.util.ArrayList;
import java.util.Optional;

/** {@link ColorSpec} implementation for the 2021 spec. */
class ColorSpec2021 implements ColorSpec {

  ////////////////////////////////////////////////////////////////
  // Main Palettes                                              //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor primaryPaletteKeyColor() {
    return new DynamicColor.Builder()
        .setName("primary_palette_key_color")
        .setPalette((s) -> s.primaryPalette)
        .setTone((s) -> s.primaryPalette.getKeyColor().getTone())
        .build();
  }

  @NonNull
  @Override
  public DynamicColor secondaryPaletteKeyColor() {
    return new DynamicColor.Builder()
        .setName("secondary_palette_key_color")
        .setPalette((s) -> s.secondaryPalette)
        .setTone((s) -> s.secondaryPalette.getKeyColor().getTone())
        .build();
  }

  @NonNull
  @Override
  public DynamicColor tertiaryPaletteKeyColor() {
    return new DynamicColor.Builder()
        .setName("tertiary_palette_key_color")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone((s) -> s.tertiaryPalette.getKeyColor().getTone())
        .build();
  }

  @NonNull
  @Override
  public DynamicColor neutralPaletteKeyColor() {
    return new DynamicColor.Builder()
        .setName("neutral_palette_key_color")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.neutralPalette.getKeyColor().getTone())
        .build();
  }

  @NonNull
  @Override
  public DynamicColor neutralVariantPaletteKeyColor() {
    return new DynamicColor.Builder()
        .setName("neutral_variant_palette_key_color")
        .setPalette((s) -> s.neutralVariantPalette)
        .setTone((s) -> s.neutralVariantPalette.getKeyColor().getTone())
        .build();
  }

  @NonNull
  @Override
  public DynamicColor errorPaletteKeyColor() {
    return new DynamicColor.Builder()
        .setName("error_palette_key_color")
        .setPalette((s) -> s.errorPalette)
        .setTone((s) -> s.errorPalette.getKeyColor().getTone())
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Surfaces [S]                                               //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor background() {
    return new DynamicColor.Builder()
        .setName("background")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 6.0 : 98.0)
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onBackground() {
    return new DynamicColor.Builder()
        .setName("on_background")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 90.0 : 10.0)
        .setBackground((s) -> background())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 3.0, 4.5, 7.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surface() {
    return new DynamicColor.Builder()
        .setName("surface")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 6.0 : 98.0)
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceDim() {
    return new DynamicColor.Builder()
        .setName("surface_dim")
        .setPalette((s) -> s.neutralPalette)
        .setTone(
            (s) -> s.isDark ? 6.0 : new ContrastCurve(87.0, 87.0, 80.0, 75.0).get(s.contrastLevel))
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceBright() {
    return new DynamicColor.Builder()
        .setName("surface_bright")
        .setPalette((s) -> s.neutralPalette)
        .setTone(
            (s) -> s.isDark ? new ContrastCurve(24.0, 24.0, 29.0, 34.0).get(s.contrastLevel) : 98.0)
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceContainerLowest() {
    return new DynamicColor.Builder()
        .setName("surface_container_lowest")
        .setPalette((s) -> s.neutralPalette)
        .setTone(
            (s) -> s.isDark ? new ContrastCurve(4.0, 4.0, 2.0, 0.0).get(s.contrastLevel) : 100.0)
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceContainerLow() {
    return new DynamicColor.Builder()
        .setName("surface_container_low")
        .setPalette((s) -> s.neutralPalette)
        .setTone(
            (s) ->
                s.isDark
                    ? new ContrastCurve(10.0, 10.0, 11.0, 12.0).get(s.contrastLevel)
                    : new ContrastCurve(96.0, 96.0, 96.0, 95.0).get(s.contrastLevel))
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceContainer() {
    return new DynamicColor.Builder()
        .setName("surface_container")
        .setPalette((s) -> s.neutralPalette)
        .setTone(
            (s) ->
                s.isDark
                    ? new ContrastCurve(12.0, 12.0, 16.0, 20.0).get(s.contrastLevel)
                    : new ContrastCurve(94.0, 94.0, 92.0, 90.0).get(s.contrastLevel))
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceContainerHigh() {
    return new DynamicColor.Builder()
        .setName("surface_container_high")
        .setPalette((s) -> s.neutralPalette)
        .setTone(
            (s) ->
                s.isDark
                    ? new ContrastCurve(17.0, 17.0, 21.0, 25.0).get(s.contrastLevel)
                    : new ContrastCurve(92.0, 92.0, 88.0, 85.0).get(s.contrastLevel))
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceContainerHighest() {
    return new DynamicColor.Builder()
        .setName("surface_container_highest")
        .setPalette((s) -> s.neutralPalette)
        .setTone(
            (s) ->
                s.isDark
                    ? new ContrastCurve(22.0, 22.0, 26.0, 30.0).get(s.contrastLevel)
                    : new ContrastCurve(90.0, 90.0, 84.0, 80.0).get(s.contrastLevel))
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onSurface() {
    return new DynamicColor.Builder()
        .setName("on_surface")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 90.0 : 10.0)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceVariant() {
    return new DynamicColor.Builder()
        .setName("surface_variant")
        .setPalette((s) -> s.neutralVariantPalette)
        .setTone((s) -> s.isDark ? 30.0 : 90.0)
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onSurfaceVariant() {
    return new DynamicColor.Builder()
        .setName("on_surface_variant")
        .setPalette((s) -> s.neutralVariantPalette)
        .setTone((s) -> s.isDark ? 80.0 : 30.0)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 11.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor inverseSurface() {
    return new DynamicColor.Builder()
        .setName("inverse_surface")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 90.0 : 20.0)
        .setIsBackground(true)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor inverseOnSurface() {
    return new DynamicColor.Builder()
        .setName("inverse_on_surface")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 20.0 : 95.0)
        .setBackground((s) -> inverseSurface())
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor outline() {
    return new DynamicColor.Builder()
        .setName("outline")
        .setPalette((s) -> s.neutralVariantPalette)
        .setTone((s) -> s.isDark ? 60.0 : 50.0)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.5, 3.0, 4.5, 7.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor outlineVariant() {
    return new DynamicColor.Builder()
        .setName("outline_variant")
        .setPalette((s) -> s.neutralVariantPalette)
        .setTone((s) -> s.isDark ? 30.0 : 80.0)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor shadow() {
    return new DynamicColor.Builder()
        .setName("shadow")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> 0.0)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor scrim() {
    return new DynamicColor.Builder()
        .setName("scrim")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> 0.0)
        .build();
  }

  @NonNull
  @Override
  public DynamicColor surfaceTint() {
    return new DynamicColor.Builder()
        .setName("surface_tint")
        .setPalette((s) -> s.primaryPalette)
        .setTone((s) -> s.isDark ? 80.0 : 40.0)
        .setIsBackground(true)
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Primaries [P]                                              //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor primary() {
    return new DynamicColor.Builder()
        .setName("primary")
        .setPalette((s) -> s.primaryPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 100.0 : 0.0;
              }
              return s.isDark ? 80.0 : 40.0;
            })
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 7.0))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(primaryContainer(), primary(), 10.0, TonePolarity.NEARER, false))
        .build();
  }

  @Nullable
  @Override
  public DynamicColor primaryDim() {
    return null;
  }

  @NonNull
  @Override
  public DynamicColor onPrimary() {
    return new DynamicColor.Builder()
        .setName("on_primary")
        .setPalette((s) -> s.primaryPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 10.0 : 90.0;
              }
              return s.isDark ? 20.0 : 100.0;
            })
        .setBackground((s) -> primary())
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor primaryContainer() {
    return new DynamicColor.Builder()
        .setName("primary_container")
        .setPalette((s) -> s.primaryPalette)
        .setTone(
            (s) -> {
              if (isFidelity(s)) {
                return s.sourceColorHct.getTone();
              }
              if (isMonochrome(s)) {
                return s.isDark ? 85.0 : 25.0;
              }
              return s.isDark ? 30.0 : 90.0;
            })
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(primaryContainer(), primary(), 10.0, TonePolarity.NEARER, false))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onPrimaryContainer() {
    return new DynamicColor.Builder()
        .setName("on_primary_container")
        .setPalette((s) -> s.primaryPalette)
        .setTone(
            (s) -> {
              if (isFidelity(s)) {
                return DynamicColor.foregroundTone(primaryContainer().tone.apply(s), 4.5);
              }
              if (isMonochrome(s)) {
                return s.isDark ? 0.0 : 100.0;
              }
              return s.isDark ? 90.0 : 30.0;
            })
        .setBackground((s) -> primaryContainer())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 11.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor inversePrimary() {
    return new DynamicColor.Builder()
        .setName("inverse_primary")
        .setPalette((s) -> s.primaryPalette)
        .setTone((s) -> s.isDark ? 40.0 : 80.0)
        .setBackground((s) -> inverseSurface())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 7.0))
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Secondaries [Q]                                            //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor secondary() {
    return new DynamicColor.Builder()
        .setName("secondary")
        .setPalette((s) -> s.secondaryPalette)
        .setTone((s) -> s.isDark ? 80.0 : 40.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 7.0))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    secondaryContainer(), secondary(), 10.0, TonePolarity.NEARER, false))
        .build();
  }

  @Nullable
  @Override
  public DynamicColor secondaryDim() {
    return null;
  }

  @NonNull
  @Override
  public DynamicColor onSecondary() {
    return new DynamicColor.Builder()
        .setName("on_secondary")
        .setPalette((s) -> s.secondaryPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 10.0 : 100.0;
              }
              return s.isDark ? 20.0 : 100.0;
            })
        .setBackground((s) -> secondary())
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor secondaryContainer() {
    return new DynamicColor.Builder()
        .setName("secondary_container")
        .setPalette((s) -> s.secondaryPalette)
        .setTone(
            (s) -> {
              final double initialTone = s.isDark ? 30.0 : 90.0;
              if (isMonochrome(s)) {
                return s.isDark ? 30.0 : 85.0;
              }
              if (!isFidelity(s)) {
                return initialTone;
              }
              return findDesiredChromaByTone(
                  s.secondaryPalette.getHue(),
                  s.secondaryPalette.getChroma(),
                  initialTone,
                  !s.isDark);
            })
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    secondaryContainer(), secondary(), 10.0, TonePolarity.NEARER, false))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onSecondaryContainer() {
    return new DynamicColor.Builder()
        .setName("on_secondary_container")
        .setPalette((s) -> s.secondaryPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 90.0 : 10.0;
              }
              if (!isFidelity(s)) {
                return s.isDark ? 90.0 : 30.0;
              }
              return DynamicColor.foregroundTone(secondaryContainer().tone.apply(s), 4.5);
            })
        .setBackground((s) -> secondaryContainer())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 11.0))
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Tertiaries [T]                                             //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor tertiary() {
    return new DynamicColor.Builder()
        .setName("tertiary")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 90.0 : 25.0;
              }
              return s.isDark ? 80.0 : 40.0;
            })
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 7.0))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    tertiaryContainer(), tertiary(), 10.0, TonePolarity.NEARER, false))
        .build();
  }

  @Nullable
  @Override
  public DynamicColor tertiaryDim() {
    return null;
  }

  @NonNull
  @Override
  public DynamicColor onTertiary() {
    return new DynamicColor.Builder()
        .setName("on_tertiary")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 10.0 : 90.0;
              }
              return s.isDark ? 20.0 : 100.0;
            })
        .setBackground((s) -> tertiary())
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor tertiaryContainer() {
    return new DynamicColor.Builder()
        .setName("tertiary_container")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 60.0 : 49.0;
              }
              if (!isFidelity(s)) {
                return s.isDark ? 30.0 : 90.0;
              }
              final Hct proposedHct = s.tertiaryPalette.getHct(s.sourceColorHct.getTone());
              return DislikeAnalyzer.fixIfDisliked(proposedHct).getTone();
            })
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    tertiaryContainer(), tertiary(), 10.0, TonePolarity.NEARER, false))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onTertiaryContainer() {
    return new DynamicColor.Builder()
        .setName("on_tertiary_container")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 0.0 : 100.0;
              }
              if (!isFidelity(s)) {
                return s.isDark ? 90.0 : 30.0;
              }
              return DynamicColor.foregroundTone(tertiaryContainer().tone.apply(s), 4.5);
            })
        .setBackground((s) -> tertiaryContainer())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 11.0))
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Errors [E]                                                 //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor error() {
    return new DynamicColor.Builder()
        .setName("error")
        .setPalette((s) -> s.errorPalette)
        .setTone((s) -> s.isDark ? 80.0 : 40.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 7.0))
        .setToneDeltaPair(
            (s) -> new ToneDeltaPair(errorContainer(), error(), 10.0, TonePolarity.NEARER, false))
        .build();
  }

  @Nullable
  @Override
  public DynamicColor errorDim() {
    return null;
  }

  @NonNull
  @Override
  public DynamicColor onError() {
    return new DynamicColor.Builder()
        .setName("on_error")
        .setPalette((s) -> s.errorPalette)
        .setTone((s) -> s.isDark ? 20.0 : 100.0)
        .setBackground((s) -> error())
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor errorContainer() {
    return new DynamicColor.Builder()
        .setName("error_container")
        .setPalette((s) -> s.errorPalette)
        .setTone((s) -> s.isDark ? 30.0 : 90.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) -> new ToneDeltaPair(errorContainer(), error(), 10.0, TonePolarity.NEARER, false))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onErrorContainer() {
    return new DynamicColor.Builder()
        .setName("on_error_container")
        .setPalette((s) -> s.errorPalette)
        .setTone(
            (s) -> {
              if (isMonochrome(s)) {
                return s.isDark ? 90.0 : 10.0;
              }
              return s.isDark ? 90.0 : 30.0;
            })
        .setBackground((s) -> errorContainer())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 11.0))
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Primary Fixed Colors [PF]                                  //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor primaryFixed() {
    return new DynamicColor.Builder()
        .setName("primary_fixed")
        .setPalette((s) -> s.primaryPalette)
        .setTone((s) -> isMonochrome(s) ? 40.0 : 90.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    this.primaryFixed(), this.primaryFixedDim(), 10.0, TonePolarity.LIGHTER, true))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor primaryFixedDim() {
    return new DynamicColor.Builder()
        .setName("primary_fixed_dim")
        .setPalette((s) -> s.primaryPalette)
        .setTone((s) -> isMonochrome(s) ? 30.0 : 80.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    primaryFixed(), primaryFixedDim(), 10.0, TonePolarity.LIGHTER, true))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onPrimaryFixed() {
    return new DynamicColor.Builder()
        .setName("on_primary_fixed")
        .setPalette((s) -> s.primaryPalette)
        .setTone((s) -> isMonochrome(s) ? 100.0 : 10.0)
        .setBackground((s) -> primaryFixedDim())
        .setSecondBackground((s) -> primaryFixed())
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onPrimaryFixedVariant() {
    return new DynamicColor.Builder()
        .setName("on_primary_fixed_variant")
        .setPalette((s) -> s.primaryPalette)
        .setTone((s) -> isMonochrome(s) ? 90.0 : 30.0)
        .setBackground((s) -> primaryFixedDim())
        .setSecondBackground((s) -> primaryFixed())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 11.0))
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Secondary Fixed Colors [QF]                                //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor secondaryFixed() {
    return new DynamicColor.Builder()
        .setName("secondary_fixed")
        .setPalette((s) -> s.secondaryPalette)
        .setTone((s) -> isMonochrome(s) ? 80.0 : 90.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    secondaryFixed(), secondaryFixedDim(), 10.0, TonePolarity.LIGHTER, true))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor secondaryFixedDim() {
    return new DynamicColor.Builder()
        .setName("secondary_fixed_dim")
        .setPalette((s) -> s.secondaryPalette)
        .setTone((s) -> isMonochrome(s) ? 70.0 : 80.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    secondaryFixed(), secondaryFixedDim(), 10.0, TonePolarity.LIGHTER, true))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onSecondaryFixed() {
    return new DynamicColor.Builder()
        .setName("on_secondary_fixed")
        .setPalette((s) -> s.secondaryPalette)
        .setTone((s) -> 10.0)
        .setBackground((s) -> secondaryFixedDim())
        .setSecondBackground((s) -> secondaryFixed())
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onSecondaryFixedVariant() {
    return new DynamicColor.Builder()
        .setName("on_secondary_fixed_variant")
        .setPalette((s) -> s.secondaryPalette)
        .setTone((s) -> isMonochrome(s) ? 25.0 : 30.0)
        .setBackground((s) -> secondaryFixedDim())
        .setSecondBackground((s) -> secondaryFixed())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 11.0))
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Tertiary Fixed Colors [TF]                                 //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor tertiaryFixed() {
    return new DynamicColor.Builder()
        .setName("tertiary_fixed")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone((s) -> isMonochrome(s) ? 40.0 : 90.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    tertiaryFixed(), tertiaryFixedDim(), 10.0, TonePolarity.LIGHTER, true))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor tertiaryFixedDim() {
    return new DynamicColor.Builder()
        .setName("tertiary_fixed_dim")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone((s) -> isMonochrome(s) ? 30.0 : 80.0)
        .setIsBackground(true)
        .setBackground(this::highestSurface)
        .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 3.0, 4.5))
        .setToneDeltaPair(
            (s) ->
                new ToneDeltaPair(
                    tertiaryFixed(), tertiaryFixedDim(), 10.0, TonePolarity.LIGHTER, true))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onTertiaryFixed() {
    return new DynamicColor.Builder()
        .setName("on_tertiary_fixed")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone((s) -> isMonochrome(s) ? 100.0 : 10.0)
        .setBackground((s) -> tertiaryFixedDim())
        .setSecondBackground((s) -> tertiaryFixed())
        .setContrastCurve((s) -> new ContrastCurve(4.5, 7.0, 11.0, 21.0))
        .build();
  }

  @NonNull
  @Override
  public DynamicColor onTertiaryFixedVariant() {
    return new DynamicColor.Builder()
        .setName("on_tertiary_fixed_variant")
        .setPalette((s) -> s.tertiaryPalette)
        .setTone((s) -> isMonochrome(s) ? 90.0 : 30.0)
        .setBackground((s) -> tertiaryFixedDim())
        .setSecondBackground((s) -> tertiaryFixed())
        .setContrastCurve((s) -> new ContrastCurve(3.0, 4.5, 7.0, 11.0))
        .build();
  }

  //////////////////////////////////////////////////////////////////
  // Android-only Colors                                          //
  //////////////////////////////////////////////////////////////////

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
  @Override
  public DynamicColor controlActivated() {
    return new DynamicColor.Builder()
        .setName("control_activated")
        .setPalette((s) -> s.primaryPalette)
        .setTone((s) -> s.isDark ? 30.0 : 90.0)
        .setIsBackground(true)
        .build();
  }

  // colorControlNormal documented as textColorSecondary in M3 & GM3.
  // In Material, textColorSecondary points to onSurfaceVariant in the non-disabled state,
  // which is Neutral Variant T30/80 in light/dark.
  @NonNull
  @Override
  public DynamicColor controlNormal() {
    return new DynamicColor.Builder()
        .setName("control_normal")
        .setPalette((s) -> s.neutralVariantPalette)
        .setTone((s) -> s.isDark ? 80.0 : 30.0)
        .build();
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
  @Override
  public DynamicColor controlHighlight() {
    return new DynamicColor.Builder()
        .setName("control_highlight")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 100.0 : 0.0)
        .setOpacity((s) -> s.isDark ? 0.20 : 0.12)
        .build();
  }

  // textColorPrimaryInverse documented, in both M3 & GM3, documented as N10/N90.
  @NonNull
  @Override
  public DynamicColor textPrimaryInverse() {
    return new DynamicColor.Builder()
        .setName("text_primary_inverse")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 10.0 : 90.0)
        .build();
  }

  // textColorSecondaryInverse and textColorTertiaryInverse both documented, in both M3 & GM3, as
  // NV30/NV80
  @NonNull
  @Override
  public DynamicColor textSecondaryAndTertiaryInverse() {
    return new DynamicColor.Builder()
        .setName("text_secondary_and_tertiary_inverse")
        .setPalette((s) -> s.neutralVariantPalette)
        .setTone((s) -> s.isDark ? 30.0 : 80.0)
        .build();
  }

  // textColorPrimaryInverseDisableOnly documented, in both M3 & GM3, as N10/N90
  @NonNull
  @Override
  public DynamicColor textPrimaryInverseDisableOnly() {
    return new DynamicColor.Builder()
        .setName("text_primary_inverse_disable_only")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 10.0 : 90.0)
        .build();
  }

  // textColorSecondaryInverse and textColorTertiaryInverse in disabled state both documented,
  // in both M3 & GM3, as N10/N90
  @NonNull
  @Override
  public DynamicColor textSecondaryAndTertiaryInverseDisabled() {
    return new DynamicColor.Builder()
        .setName("text_secondary_and_tertiary_inverse_disabled")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 10.0 : 90.0)
        .build();
  }

  // textColorHintInverse documented, in both M3 & GM3, as N10/N90
  @NonNull
  @Override
  public DynamicColor textHintInverse() {
    return new DynamicColor.Builder()
        .setName("text_hint_inverse")
        .setPalette((s) -> s.neutralPalette)
        .setTone((s) -> s.isDark ? 10.0 : 90.0)
        .build();
  }

  ////////////////////////////////////////////////////////////////
  // Other                                                      //
  ////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public DynamicColor highestSurface(@NonNull DynamicScheme s) {
    return s.isDark ? surfaceBright() : surfaceDim();
  }

  private boolean isFidelity(DynamicScheme scheme) {
    return scheme.variant == Variant.FIDELITY || scheme.variant == Variant.CONTENT;
  }

  private static boolean isMonochrome(DynamicScheme scheme) {
    return scheme.variant == Variant.MONOCHROME;
  }

  private static double findDesiredChromaByTone(
      double hue, double chroma, double tone, boolean byDecreasingTone) {
    double answer = tone;

    Hct closestToChroma = Hct.from(hue, chroma, tone);
    if (closestToChroma.getChroma() < chroma) {
      double chromaPeak = closestToChroma.getChroma();
      while (closestToChroma.getChroma() < chroma) {
        answer += byDecreasingTone ? -1.0 : 1.0;
        Hct potentialSolution = Hct.from(hue, chroma, answer);
        if (chromaPeak > potentialSolution.getChroma()) {
          break;
        }
        if (Math.abs(potentialSolution.getChroma() - chroma) < 0.4) {
          break;
        }

        double potentialDelta = Math.abs(potentialSolution.getChroma() - chroma);
        double currentDelta = Math.abs(closestToChroma.getChroma() - chroma);
        if (potentialDelta < currentDelta) {
          closestToChroma = potentialSolution;
        }
        chromaPeak = Math.max(chromaPeak, potentialSolution.getChroma());
      }
    }

    return answer;
  }

  /////////////////////////////////////////////////////////////////
  // Color value calculations                                    //
  /////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public Hct getHct(DynamicScheme scheme, DynamicColor color) {
    // This is crucial for aesthetics: we aren't simply the taking the standard color
    // and changing its tone for contrast. Rather, we find the tone for contrast, then
    // use the specified chroma from the palette to construct a new color.
    //
    // For example, this enables colors with standard tone of T90, which has limited chroma, to
    // "recover" intended chroma as contrast increases.
    double tone = getTone(scheme, color);
    return color.palette.apply(scheme).getHct(tone);
  }

  @Override
  public double getTone(DynamicScheme scheme, DynamicColor color) {
    boolean decreasingContrast = scheme.contrastLevel < 0;
    ToneDeltaPair toneDeltaPair =
        color.toneDeltaPair == null ? null : color.toneDeltaPair.apply(scheme);

    // Case 1: dual foreground, pair of colors with delta constraint.
    if (toneDeltaPair != null) {
      DynamicColor roleA = toneDeltaPair.getRoleA();
      DynamicColor roleB = toneDeltaPair.getRoleB();
      double delta = toneDeltaPair.getDelta();
      TonePolarity polarity = toneDeltaPair.getPolarity();
      boolean stayTogether = toneDeltaPair.getStayTogether();

      boolean aIsNearer =
          (polarity == TonePolarity.NEARER
              || (polarity == TonePolarity.LIGHTER && !scheme.isDark)
              || (polarity == TonePolarity.DARKER && !scheme.isDark));
      DynamicColor nearer = aIsNearer ? roleA : roleB;
      DynamicColor farther = aIsNearer ? roleB : roleA;
      boolean amNearer = color.name.equals(nearer.name);
      double expansionDir = scheme.isDark ? 1 : -1;
      @Var double nTone = nearer.tone.apply(scheme);
      @Var double fTone = farther.tone.apply(scheme);

      // 1st round: solve to min, each
      if (color.background != null
          && nearer.contrastCurve != null
          && farther.contrastCurve != null) {
        DynamicColor bg = color.background.apply(scheme);
        ContrastCurve nContrastCurve = nearer.contrastCurve.apply(scheme);
        ContrastCurve fContrastCurve = farther.contrastCurve.apply(scheme);
        if (bg != null && nContrastCurve != null && fContrastCurve != null) {
          double nContrast = nContrastCurve.get(scheme.contrastLevel);
          double fContrast = fContrastCurve.get(scheme.contrastLevel);
          double bgTone = bg.getTone(scheme);

          // If a color is good enough, it is not adjusted.
          // Initial and adjusted tones for `nearer`
          if (Contrast.ratioOfTones(bgTone, nTone) < nContrast) {
            nTone = DynamicColor.foregroundTone(bgTone, nContrast);
          }
          // Initial and adjusted tones for `farther`
          if (Contrast.ratioOfTones(bgTone, fTone) < fContrast) {
            fTone = DynamicColor.foregroundTone(bgTone, fContrast);
          }

          if (decreasingContrast) {
            // If decreasing contrast, adjust color to the "bare minimum"
            // that satisfies contrast.
            nTone = DynamicColor.foregroundTone(bgTone, nContrast);
            fTone = DynamicColor.foregroundTone(bgTone, fContrast);
          }
        }
      }

      // If constraint is not satisfied, try another round.
      if ((fTone - nTone) * expansionDir < delta) {
        // 2nd round: expand farther to match delta.
        fTone = MathUtils.clampDouble(0, 100, nTone + delta * expansionDir);
        // If constraint is not satisfied, try another round.
        if ((fTone - nTone) * expansionDir < delta) {
          // 3rd round: contract nearer to match delta.
          nTone = MathUtils.clampDouble(0, 100, fTone - delta * expansionDir);
        }
      }

      // Avoids the 50-59 awkward zone.
      if (50 <= nTone && nTone < 60) {
        // If `nearer` is in the awkward zone, move it away, together with
        // `farther`.
        if (expansionDir > 0) {
          nTone = 60;
          fTone = max(fTone, nTone + delta * expansionDir);
        } else {
          nTone = 49;
          fTone = min(fTone, nTone + delta * expansionDir);
        }
      } else if (50 <= fTone && fTone < 60) {
        if (stayTogether) {
          // Fixes both, to avoid two colors on opposite sides of the "awkward
          // zone".
          if (expansionDir > 0) {
            nTone = 60;
            fTone = max(fTone, nTone + delta * expansionDir);
          } else {
            nTone = 49;
            fTone = min(fTone, nTone + delta * expansionDir);
          }
        } else {
          // Not required to stay together; fixes just one.
          if (expansionDir > 0) {
            fTone = 60;
          } else {
            fTone = 49;
          }
        }
      }

      // Returns `nTone` if this color is `nearer`, otherwise `fTone`.
      return amNearer ? nTone : fTone;
    } else {
      // Case 2: No contrast pair; just solve for itself.
      @Var double answer = color.tone.apply(scheme);

      if (color.background == null
          || color.background.apply(scheme) == null
          || color.contrastCurve == null
          || color.contrastCurve.apply(scheme) == null) {
        return answer; // No adjustment for colors with no background.
      }

      double bgTone = color.background.apply(scheme).getTone(scheme);
      double desiredRatio = color.contrastCurve.apply(scheme).get(scheme.contrastLevel);

      if (Contrast.ratioOfTones(bgTone, answer) >= desiredRatio) {
        // Don't "improve" what's good enough.
      } else {
        // Rough improvement.
        answer = DynamicColor.foregroundTone(bgTone, desiredRatio);
      }

      if (decreasingContrast) {
        answer = DynamicColor.foregroundTone(bgTone, desiredRatio);
      }

      if (color.isBackground && 50 <= answer && answer < 60) {
        // Must adjust
        if (Contrast.ratioOfTones(49, bgTone) >= desiredRatio) {
          answer = 49;
        } else {
          answer = 60;
        }
      }

      if (color.secondBackground == null || color.secondBackground.apply(scheme) == null) {
        return answer;
      }

      // Case 3: Adjust for dual backgrounds.
      double bgTone1 = color.background.apply(scheme).getTone(scheme);
      double bgTone2 = color.secondBackground.apply(scheme).getTone(scheme);

      double upper = max(bgTone1, bgTone2);
      double lower = min(bgTone1, bgTone2);

      if (Contrast.ratioOfTones(upper, answer) >= desiredRatio
          && Contrast.ratioOfTones(lower, answer) >= desiredRatio) {
        return answer;
      }

      // The darkest light tone that satisfies the desired ratio,
      // or -1 if such ratio cannot be reached.
      double lightOption = Contrast.lighter(upper, desiredRatio);

      // The lightest dark tone that satisfies the desired ratio,
      // or -1 if such ratio cannot be reached.
      double darkOption = Contrast.darker(lower, desiredRatio);

      // Tones suitable for the foreground.
      ArrayList<Double> availables = new ArrayList<>();
      if (lightOption != -1) {
        availables.add(lightOption);
      }
      if (darkOption != -1) {
        availables.add(darkOption);
      }

      boolean prefersLight =
          DynamicColor.tonePrefersLightForeground(bgTone1)
              || DynamicColor.tonePrefersLightForeground(bgTone2);
      if (prefersLight) {
        return (lightOption == -1) ? 100 : lightOption;
      }
      if (availables.size() == 1) {
        return availables.get(0);
      }
      return (darkOption == -1) ? 0 : darkOption;
    }
  }

  //////////////////////////////////////////////////////////////////
  // Scheme Palettes                                              //
  //////////////////////////////////////////////////////////////////

  @NonNull
  @Override
  public TonalPalette getPrimaryPalette(
      Variant variant,
      Hct sourceColorHct,
      boolean isDark,
      Platform platform,
      double contrastLevel) {
    return switch (variant) {
      case CONTENT, FIDELITY ->
          TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), sourceColorHct.getChroma());
      case FRUIT_SALAD ->
          TonalPalette.fromHueAndChroma(
              MathUtils.sanitizeDegreesDouble(sourceColorHct.getHue() - 50.0), 48.0);
      case MONOCHROME -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 0.0);
      case NEUTRAL -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 12.0);
      case RAINBOW -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 48.0);
      case TONAL_SPOT -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 36.0);
      case EXPRESSIVE ->
          TonalPalette.fromHueAndChroma(
              MathUtils.sanitizeDegreesDouble(sourceColorHct.getHue() + 240), 40);
      case VIBRANT -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 200.0);
    };
  }

  @NonNull
  @Override
  public TonalPalette getSecondaryPalette(
      Variant variant,
      Hct sourceColorHct,
      boolean isDark,
      Platform platform,
      double contrastLevel) {
    return switch (variant) {
      case CONTENT, FIDELITY ->
          TonalPalette.fromHueAndChroma(
              sourceColorHct.getHue(),
              max(sourceColorHct.getChroma() - 32.0, sourceColorHct.getChroma() * 0.5));
      case FRUIT_SALAD ->
          TonalPalette.fromHueAndChroma(
              MathUtils.sanitizeDegreesDouble(sourceColorHct.getHue() - 50.0), 36.0);
      case MONOCHROME -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 0.0);
      case NEUTRAL -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 8.0);
      case RAINBOW -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 16.0);
      case TONAL_SPOT -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 16.0);
      case EXPRESSIVE ->
          TonalPalette.fromHueAndChroma(
              DynamicScheme.getRotatedHue(
                  sourceColorHct,
                  new double[] {0, 21, 51, 121, 151, 191, 271, 321, 360},
                  new double[] {45, 95, 45, 20, 45, 90, 45, 45, 45}),
              24.0);
      case VIBRANT ->
          TonalPalette.fromHueAndChroma(
              DynamicScheme.getRotatedHue(
                  sourceColorHct,
                  new double[] {0, 41, 61, 101, 131, 181, 251, 301, 360},
                  new double[] {18, 15, 10, 12, 15, 18, 15, 12, 12}),
              24.0);
    };
  }

  @NonNull
  @Override
  public TonalPalette getTertiaryPalette(
      Variant variant,
      Hct sourceColorHct,
      boolean isDark,
      Platform platform,
      double contrastLevel) {
    return switch (variant) {
      case CONTENT ->
          TonalPalette.fromHct(
              DislikeAnalyzer.fixIfDisliked(
                  new TemperatureCache(sourceColorHct)
                      .getAnalogousColors(/* count= */ 3, /* divisions= */ 6)
                      .get(2)));
      case FIDELITY ->
          TonalPalette.fromHct(
              DislikeAnalyzer.fixIfDisliked(new TemperatureCache(sourceColorHct).getComplement()));
      case FRUIT_SALAD -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 36.0);
      case MONOCHROME -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 0.0);
      case NEUTRAL -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 16.0);
      case RAINBOW, TONAL_SPOT ->
          TonalPalette.fromHueAndChroma(
              MathUtils.sanitizeDegreesDouble(sourceColorHct.getHue() + 60.0), 24.0);
      case EXPRESSIVE ->
          TonalPalette.fromHueAndChroma(
              DynamicScheme.getRotatedHue(
                  sourceColorHct,
                  new double[] {0, 21, 51, 121, 151, 191, 271, 321, 360},
                  new double[] {120, 120, 20, 45, 20, 15, 20, 120, 120}),
              32.0);
      case VIBRANT ->
          TonalPalette.fromHueAndChroma(
              DynamicScheme.getRotatedHue(
                  sourceColorHct,
                  new double[] {0, 41, 61, 101, 131, 181, 251, 301, 360},
                  new double[] {35, 30, 20, 25, 30, 35, 30, 25, 25}),
              32.0);
    };
  }

  @NonNull
  @Override
  public TonalPalette getNeutralPalette(
      Variant variant,
      Hct sourceColorHct,
      boolean isDark,
      Platform platform,
      double contrastLevel) {
    return switch (variant) {
      case CONTENT, FIDELITY ->
          TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), sourceColorHct.getChroma() / 8.0);
      case FRUIT_SALAD -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 10.0);
      case MONOCHROME -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 0.0);
      case NEUTRAL -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 2.0);
      case RAINBOW -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 0.0);
      case TONAL_SPOT -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 6.0);
      case EXPRESSIVE ->
          TonalPalette.fromHueAndChroma(
              MathUtils.sanitizeDegreesDouble(sourceColorHct.getHue() + 15), 8);
      case VIBRANT -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 10);
    };
  }

  @NonNull
  @Override
  public TonalPalette getNeutralVariantPalette(
      Variant variant,
      Hct sourceColorHct,
      boolean isDark,
      Platform platform,
      double contrastLevel) {
    return switch (variant) {
      case CONTENT ->
          TonalPalette.fromHueAndChroma(
              sourceColorHct.getHue(), (sourceColorHct.getChroma() / 8.0) + 4.0);
      case FIDELITY ->
          TonalPalette.fromHueAndChroma(
              sourceColorHct.getHue(), (sourceColorHct.getChroma() / 8.0) + 4.0);
      case FRUIT_SALAD -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 16.0);
      case MONOCHROME -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 0.0);
      case NEUTRAL -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 2.0);
      case RAINBOW -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 0.0);
      case TONAL_SPOT -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 8.0);
      case EXPRESSIVE ->
          TonalPalette.fromHueAndChroma(
              MathUtils.sanitizeDegreesDouble(sourceColorHct.getHue() + 15), 12);
      case VIBRANT -> TonalPalette.fromHueAndChroma(sourceColorHct.getHue(), 12);
    };
  }

  @NonNull
  @Override
  public Optional<TonalPalette> getErrorPalette(
      Variant variant,
      Hct sourceColorHct,
      boolean isDark,
      Platform platform,
      double contrastLevel) {
    return switch (variant) {
      case CONTENT,
          FIDELITY,
          FRUIT_SALAD,
          MONOCHROME,
          NEUTRAL,
          RAINBOW,
          TONAL_SPOT,
          EXPRESSIVE,
          VIBRANT ->
          Optional.empty();
    };
  }
}

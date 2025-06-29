/*
 * Copyright 2022 Google LLC
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

import static java.lang.Math.min;

import com.google.material.color.dynamiccolor.ColorSpec.SpecVersion;
import com.google.material.color.hct.Hct;
import com.google.material.color.palettes.TonalPalette;
import com.google.material.color.utils.MathUtils;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Provides important settings for creating colors dynamically, and 6 color palettes. Requires: 1. A
 * color. (source color) 2. A theme. (Variant) 3. Whether or not its dark mode. 4. Contrast level.
 * (-1 to 1, currently contrast ratio 3.0 and 7.0)
 */
public class DynamicScheme {

  public static final SpecVersion DEFAULT_SPEC_VERSION = SpecVersion.SPEC_2021;
  public static final Platform DEFAULT_PLATFORM = Platform.PHONE;

  /** The platform on which this scheme is intended to be used. */
  public enum Platform {
    PHONE,
    WATCH
  }

  /** The source color of the scheme in ARGB format. */
  public final int sourceColorArgb;

  /** The source color of the scheme in HCT format. */
  public final Hct sourceColorHct;

  /** The variant of the scheme. */
  public final Variant variant;

  /** Whether or not the scheme is dark mode. */
  public final boolean isDark;

  /** The platform on which this scheme is intended to be used. */
  public final Platform platform;

  /**
   * Value from -1 to 1. -1 represents minimum contrast. 0 represents standard (i.e. the design as
   * spec'd), and 1 represents maximum contrast.
   */
  public final double contrastLevel;

  /** The spec version of the scheme. */
  public final SpecVersion specVersion;

  public final TonalPalette primaryPalette;
  public final TonalPalette secondaryPalette;
  public final TonalPalette tertiaryPalette;
  public final TonalPalette neutralPalette;
  public final TonalPalette neutralVariantPalette;
  public final TonalPalette errorPalette;

  public DynamicScheme(
      Hct sourceColorHct,
      Variant variant,
      boolean isDark,
      double contrastLevel,
      TonalPalette primaryPalette,
      TonalPalette secondaryPalette,
      TonalPalette tertiaryPalette,
      TonalPalette neutralPalette,
      TonalPalette neutralVariantPalette) {
    this(
        sourceColorHct,
        variant,
        isDark,
        contrastLevel,
        primaryPalette,
        secondaryPalette,
        tertiaryPalette,
        neutralPalette,
        neutralVariantPalette,
        Optional.empty());
  }

  public DynamicScheme(
      Hct sourceColorHct,
      Variant variant,
      boolean isDark,
      double contrastLevel,
      TonalPalette primaryPalette,
      TonalPalette secondaryPalette,
      TonalPalette tertiaryPalette,
      TonalPalette neutralPalette,
      TonalPalette neutralVariantPalette,
      Optional<TonalPalette> errorPalette) {
    this(
        sourceColorHct,
        variant,
        isDark,
        contrastLevel,
        Platform.PHONE,
        SpecVersion.SPEC_2021,
        primaryPalette,
        secondaryPalette,
        tertiaryPalette,
        neutralPalette,
        neutralVariantPalette,
        errorPalette);
  }

  public DynamicScheme(
      Hct sourceColorHct,
      Variant variant,
      boolean isDark,
      double contrastLevel,
      Platform platform,
      SpecVersion specVersion,
      TonalPalette primaryPalette,
      TonalPalette secondaryPalette,
      TonalPalette tertiaryPalette,
      TonalPalette neutralPalette,
      TonalPalette neutralVariantPalette,
      Optional<TonalPalette> errorPalette) {
    this.sourceColorArgb = sourceColorHct.toInt();
    this.sourceColorHct = sourceColorHct;
    this.variant = variant;
    this.isDark = isDark;
    this.contrastLevel = contrastLevel;
    this.platform = platform;
    this.specVersion = specVersion;

    this.primaryPalette = primaryPalette;
    this.secondaryPalette = secondaryPalette;
    this.tertiaryPalette = tertiaryPalette;
    this.neutralPalette = neutralPalette;
    this.neutralVariantPalette = neutralVariantPalette;
    this.errorPalette = errorPalette.orElse(TonalPalette.fromHueAndChroma(25.0, 84.0));
  }

  public static DynamicScheme from(DynamicScheme other, boolean isDark) {
    return new DynamicScheme(
        other.sourceColorHct,
        other.variant,
        isDark,
        other.contrastLevel,
        other.platform,
        other.specVersion,
        other.primaryPalette,
        other.secondaryPalette,
        other.tertiaryPalette,
        other.neutralPalette,
        other.neutralVariantPalette,
        Optional.of(other.errorPalette));
  }

  /**
   * Returns a new hue based on a piecewise function and input color hue.
   *
   * <p>For example, for the following function:
   *
   * <pre>
   * result = 26, if 0 <= hue < 101;
   * result = 39, if 101 <= hue < 210;
   * result = 28, if 210 <= hue < 360.
   * </pre>
   *
   * <p>call the function as:
   *
   * <pre>
   * double[] hueBreakpoints = {0, 101, 210, 360};
   * double[] hues = {26, 39, 28};
   * double result = scheme.piecewise(sourceColor, hueBreakpoints, hues);
   * </pre>
   *
   * @param sourceColorHct The input value.
   * @param hueBreakpoints The breakpoints, in sorted order. No default lower or upper bounds are
   *     assumed.
   * @param hues The hues that should be applied when source color's hue is >= the same index in
   *     hueBreakpoints array, and < the hue at the next index in hueBreakpoints array. Otherwise,
   *     the source color's hue is returned.
   */
  public static double getPiecewiseValue(
      Hct sourceColorHct, double[] hueBreakpoints, double[] hues) {
    int size = min(hueBreakpoints.length - 1, hues.length);
    double sourceHue = sourceColorHct.getHue();
    for (int i = 0; i < size; i++) {
      if (sourceHue >= hueBreakpoints[i] && sourceHue < hueBreakpoints[i + 1]) {
        return MathUtils.sanitizeDegreesDouble(hues[i]);
      }
    }
    // No condition matched, return the source value.
    return sourceHue;
  }

  /**
   * Returns a shifted hue based on a piecewise function and input color hue.
   *
   * <p>For example, for the following function:
   *
   * <pre>
   * result = hue + 26, if 0 <= hue < 101;
   * result = hue - 39, if 101 <= hue < 210;
   * result = hue + 28, if 210 <= hue < 360.
   * </pre>
   *
   * <p>call the function as:
   *
   * <pre>
   * double[] hueBreakpoints = {0, 101, 210, 360};
   * double[] rotations = {26, -39, 28};
   * double result = scheme.getRotatedHue(sourceColor, hueBreakpoints, rotations);
   *
   * @param sourceColorHct the source color of the theme, in HCT.
   * @param hueBreakpoints The "breakpoints", i.e. the hues at which a rotation should be apply. No
   * default lower or upper bounds are assumed.
   * @param rotations The rotation that should be applied when source color's hue is >= the same
   *     index in hues array, and < the hue at the next index in hues array. Otherwise, the source
   *     color's hue is returned.
   */
  public static double getRotatedHue(
      Hct sourceColorHct, double[] hueBreakpoints, double[] rotations) {
    double rotation = getPiecewiseValue(sourceColorHct, hueBreakpoints, rotations);
    if (min(hueBreakpoints.length - 1, rotations.length) <= 0) {
      // No condition matched, return the source hue.
      rotation = 0;
    }
    return MathUtils.sanitizeDegreesDouble(sourceColorHct.getHue() + rotation);
  }

  public Hct getHct(DynamicColor dynamicColor) {
    return dynamicColor.getHct(this);
  }

  public int getArgb(DynamicColor dynamicColor) {
    return dynamicColor.getArgb(this);
  }

  @Override
  public String toString() {
    return String.format(
        "Scheme: variant=%s, mode=%s, platform=%s, contrastLevel=%s, seed=%s, specVersion=%s",
        variant.name(),
        isDark ? "dark" : "light",
        platform.name().toLowerCase(Locale.ENGLISH),
        new DecimalFormat("0.0").format(contrastLevel),
        sourceColorHct,
        specVersion);
  }

  public int getPrimaryPaletteKeyColor() {
    return getArgb(new MaterialDynamicColors().primaryPaletteKeyColor());
  }

  public int getSecondaryPaletteKeyColor() {
    return getArgb(new MaterialDynamicColors().secondaryPaletteKeyColor());
  }

  public int getTertiaryPaletteKeyColor() {
    return getArgb(new MaterialDynamicColors().tertiaryPaletteKeyColor());
  }

  public int getNeutralPaletteKeyColor() {
    return getArgb(new MaterialDynamicColors().neutralPaletteKeyColor());
  }

  public int getNeutralVariantPaletteKeyColor() {
    return getArgb(new MaterialDynamicColors().neutralVariantPaletteKeyColor());
  }

  public int getBackground() {
    return getArgb(new MaterialDynamicColors().background());
  }

  public int getOnBackground() {
    return getArgb(new MaterialDynamicColors().onBackground());
  }

  public int getSurface() {
    return getArgb(new MaterialDynamicColors().surface());
  }

  public int getSurfaceDim() {
    return getArgb(new MaterialDynamicColors().surfaceDim());
  }

  public int getSurfaceBright() {
    return getArgb(new MaterialDynamicColors().surfaceBright());
  }

  public int getSurfaceContainerLowest() {
    return getArgb(new MaterialDynamicColors().surfaceContainerLowest());
  }

  public int getSurfaceContainerLow() {
    return getArgb(new MaterialDynamicColors().surfaceContainerLow());
  }

  public int getSurfaceContainer() {
    return getArgb(new MaterialDynamicColors().surfaceContainer());
  }

  public int getSurfaceContainerHigh() {
    return getArgb(new MaterialDynamicColors().surfaceContainerHigh());
  }

  public int getSurfaceContainerHighest() {
    return getArgb(new MaterialDynamicColors().surfaceContainerHighest());
  }

  public int getOnSurface() {
    return getArgb(new MaterialDynamicColors().onSurface());
  }

  public int getSurfaceVariant() {
    return getArgb(new MaterialDynamicColors().surfaceVariant());
  }

  public int getOnSurfaceVariant() {
    return getArgb(new MaterialDynamicColors().onSurfaceVariant());
  }

  public int getInverseSurface() {
    return getArgb(new MaterialDynamicColors().inverseSurface());
  }

  public int getInverseOnSurface() {
    return getArgb(new MaterialDynamicColors().inverseOnSurface());
  }

  public int getOutline() {
    return getArgb(new MaterialDynamicColors().outline());
  }

  public int getOutlineVariant() {
    return getArgb(new MaterialDynamicColors().outlineVariant());
  }

  public int getShadow() {
    return getArgb(new MaterialDynamicColors().shadow());
  }

  public int getScrim() {
    return getArgb(new MaterialDynamicColors().scrim());
  }

  public int getSurfaceTint() {
    return getArgb(new MaterialDynamicColors().surfaceTint());
  }

  public int getPrimary() {
    return getArgb(new MaterialDynamicColors().primary());
  }

  public int getOnPrimary() {
    return getArgb(new MaterialDynamicColors().onPrimary());
  }

  public int getPrimaryContainer() {
    return getArgb(new MaterialDynamicColors().primaryContainer());
  }

  public int getOnPrimaryContainer() {
    return getArgb(new MaterialDynamicColors().onPrimaryContainer());
  }

  public int getInversePrimary() {
    return getArgb(new MaterialDynamicColors().inversePrimary());
  }

  public int getSecondary() {
    return getArgb(new MaterialDynamicColors().secondary());
  }

  public int getOnSecondary() {
    return getArgb(new MaterialDynamicColors().onSecondary());
  }

  public int getSecondaryContainer() {
    return getArgb(new MaterialDynamicColors().secondaryContainer());
  }

  public int getOnSecondaryContainer() {
    return getArgb(new MaterialDynamicColors().onSecondaryContainer());
  }

  public int getTertiary() {
    return getArgb(new MaterialDynamicColors().tertiary());
  }

  public int getOnTertiary() {
    return getArgb(new MaterialDynamicColors().onTertiary());
  }

  public int getTertiaryContainer() {
    return getArgb(new MaterialDynamicColors().tertiaryContainer());
  }

  public int getOnTertiaryContainer() {
    return getArgb(new MaterialDynamicColors().onTertiaryContainer());
  }

  public int getError() {
    return getArgb(new MaterialDynamicColors().error());
  }

  public int getOnError() {
    return getArgb(new MaterialDynamicColors().onError());
  }

  public int getErrorContainer() {
    return getArgb(new MaterialDynamicColors().errorContainer());
  }

  public int getOnErrorContainer() {
    return getArgb(new MaterialDynamicColors().onErrorContainer());
  }

  public int getPrimaryFixed() {
    return getArgb(new MaterialDynamicColors().primaryFixed());
  }

  public int getPrimaryFixedDim() {
    return getArgb(new MaterialDynamicColors().primaryFixedDim());
  }

  public int getOnPrimaryFixed() {
    return getArgb(new MaterialDynamicColors().onPrimaryFixed());
  }

  public int getOnPrimaryFixedVariant() {
    return getArgb(new MaterialDynamicColors().onPrimaryFixedVariant());
  }

  public int getSecondaryFixed() {
    return getArgb(new MaterialDynamicColors().secondaryFixed());
  }

  public int getSecondaryFixedDim() {
    return getArgb(new MaterialDynamicColors().secondaryFixedDim());
  }

  public int getOnSecondaryFixed() {
    return getArgb(new MaterialDynamicColors().onSecondaryFixed());
  }

  public int getOnSecondaryFixedVariant() {
    return getArgb(new MaterialDynamicColors().onSecondaryFixedVariant());
  }

  public int getTertiaryFixed() {
    return getArgb(new MaterialDynamicColors().tertiaryFixed());
  }

  public int getTertiaryFixedDim() {
    return getArgb(new MaterialDynamicColors().tertiaryFixedDim());
  }

  public int getOnTertiaryFixed() {
    return getArgb(new MaterialDynamicColors().onTertiaryFixed());
  }

  public int getOnTertiaryFixedVariant() {
    return getArgb(new MaterialDynamicColors().onTertiaryFixedVariant());
  }

  public int getControlActivated() {
    return getArgb(new MaterialDynamicColors().controlActivated());
  }

  public int getControlNormal() {
    return getArgb(new MaterialDynamicColors().controlNormal());
  }

  public int getControlHighlight() {
    return getArgb(new MaterialDynamicColors().controlHighlight());
  }

  public int getTextPrimaryInverse() {
    return getArgb(new MaterialDynamicColors().textPrimaryInverse());
  }

  public int getTextSecondaryAndTertiaryInverse() {
    return getArgb(new MaterialDynamicColors().textSecondaryAndTertiaryInverse());
  }

  public int getTextPrimaryInverseDisableOnly() {
    return getArgb(new MaterialDynamicColors().textPrimaryInverseDisableOnly());
  }

  public int getTextSecondaryAndTertiaryInverseDisabled() {
    return getArgb(new MaterialDynamicColors().textSecondaryAndTertiaryInverseDisabled());
  }

  public int getTextHintInverse() {
    return getArgb(new MaterialDynamicColors().textHintInverse());
  }
}

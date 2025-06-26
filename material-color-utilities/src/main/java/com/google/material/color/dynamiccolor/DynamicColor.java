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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.material.color.contrast.Contrast;
import com.google.material.color.dynamiccolor.ColorSpec.SpecVersion;
import com.google.material.color.hct.Hct;
import com.google.material.color.palettes.TonalPalette;
import com.google.material.color.utils.MathUtils;
import java.util.HashMap;
import java.util.function.Function;

/**
 * A color that adjusts itself based on UI state, represented by DynamicScheme.
 *
 * <p>This color automatically adjusts to accommodate a desired contrast level, or other adjustments
 * such as differing in light mode versus dark mode, or what the theme is, or what the color that
 * produced the theme is, etc.
 *
 * <p>Colors without backgrounds do not change tone when contrast changes. Colors with backgrounds
 * become closer to their background as contrast lowers, and further when contrast increases.
 *
 * <p>Prefer the static constructors. They provide a much more simple interface, such as requiring
 * just a hexcode, or just a hexcode and a background.
 *
 * <p>Ultimately, each component necessary for calculating a color, adjusting it for a desired
 * contrast level, and ensuring it has a certain lightness/tone difference from another color, is
 * provided by a function that takes a DynamicScheme and returns a value. This ensures ultimate
 * flexibility, any desired behavior of a color for any design system, but it usually unnecessary.
 * See the default constructor for more information.
 */
// Prevent lint for Function.apply not being available on Android before API level 14 (4.0.1).
// "AndroidJdkLibsChecker" for Function, "NewApi" for Function.apply().
// A java_library Bazel rule with an Android constraint cannot skip these warnings without this
// annotation; another solution would be to create an android_library rule and supply
// AndroidManifest with an SDK set higher than 14.
@SuppressWarnings({"AndroidJdkLibsChecker", "NewApi"})
public final class DynamicColor {
  public final String name;
  public final Function<DynamicScheme, TonalPalette> palette;
  public final Function<DynamicScheme, Double> tone;
  public final boolean isBackground;
  public final Function<DynamicScheme, Double> chromaMultiplier;
  public final Function<DynamicScheme, DynamicColor> background;
  public final Function<DynamicScheme, DynamicColor> secondBackground;
  public final Function<DynamicScheme, ContrastCurve> contrastCurve;
  public final Function<DynamicScheme, ToneDeltaPair> toneDeltaPair;

  public final Function<DynamicScheme, Double> opacity;

  private final HashMap<DynamicScheme, Hct> hctCache = new HashMap<>();

  /**
   * A constructor for DynamicColor.
   *
   * <p>_Strongly_ prefer using one of the convenience constructors. This class is arguably too
   * flexible to ensure it can support any scenario. Functional arguments allow overriding without
   * risks that come with subclasses.
   *
   * <p>For example, the default behavior of adjust tone at max contrast to be at a 7.0 ratio with
   * its background is principled and matches accessibility guidance. That does not mean it's the
   * desired approach for _every_ design system, and every color pairing, always, in every case.
   *
   * <p>For opaque colors (colors with alpha = 100%).
   *
   * @param name The name of the dynamic color.
   * @param palette Function that provides a TonalPalette given DynamicScheme. A TonalPalette is
   *     defined by a hue and chroma, so this replaces the need to specify hue/chroma. By providing
   *     a tonal palette, when contrast adjustments are made, intended chroma can be preserved.
   * @param tone Function that provides a tone, given a DynamicScheme.
   * @param isBackground Whether this dynamic color is a background, with some other color as the
   *     foreground.
   * @param background The background of the dynamic color (as a function of a `DynamicScheme`), if
   *     it exists.
   * @param secondBackground A second background of the dynamic color (as a function of a
   *     `DynamicScheme`), if it exists.
   * @param contrastCurve A `ContrastCurve` object specifying how its contrast against its
   *     background should behave in various contrast levels options.
   * @param toneDeltaPair A `ToneDeltaPair` object specifying a tone delta constraint between two
   *     colors. One of them must be the color being constructed.
   */
  public DynamicColor(
      @NonNull String name,
      @NonNull Function<DynamicScheme, TonalPalette> palette,
      @NonNull Function<DynamicScheme, Double> tone,
      boolean isBackground,
      @Nullable Function<DynamicScheme, DynamicColor> background,
      @Nullable Function<DynamicScheme, DynamicColor> secondBackground,
      @Nullable ContrastCurve contrastCurve,
      @Nullable Function<DynamicScheme, ToneDeltaPair> toneDeltaPair) {
    this(
        name,
        palette,
        tone,
        isBackground,
        background,
        secondBackground,
        contrastCurve,
        toneDeltaPair,
        /* opacity= */ null);
  }

  /**
   * A constructor for DynamicColor.
   *
   * <p>_Strongly_ prefer using one of the convenience constructors. This class is arguably too
   * flexible to ensure it can support any scenario. Functional arguments allow overriding without
   * risks that come with subclasses.
   *
   * <p>For example, the default behavior of adjust tone at max contrast to be at a 7.0 ratio with
   * its background is principled and matches accessibility guidance. That does not mean it's the
   * desired approach for _every_ design system, and every color pairing, always, in every case.
   *
   * <p>For opaque colors (colors with alpha = 100%).
   *
   * @param name The name of the dynamic color.
   * @param palette Function that provides a TonalPalette given DynamicScheme. A TonalPalette is
   *     defined by a hue and chroma, so this replaces the need to specify hue/chroma. By providing
   *     a tonal palette, when contrast adjustments are made, intended chroma can be preserved.
   * @param tone Function that provides a tone, given a DynamicScheme.
   * @param isBackground Whether this dynamic color is a background, with some other color as the
   *     foreground.
   * @param background The background of the dynamic color (as a function of a `DynamicScheme`), if
   *     it exists.
   * @param secondBackground A second background of the dynamic color (as a function of a
   *     `DynamicScheme`), if it exists.
   * @param contrastCurve A `ContrastCurve` object specifying how its contrast against its
   *     background should behave in various contrast levels options.
   * @param toneDeltaPair A `ToneDeltaPair` object specifying a tone delta constraint between two
   *     colors. One of them must be the color being constructed.
   * @param opacity A function returning the opacity of a color, as a number between 0 and 1.
   */
  public DynamicColor(
      @NonNull String name,
      @NonNull Function<DynamicScheme, TonalPalette> palette,
      @NonNull Function<DynamicScheme, Double> tone,
      boolean isBackground,
      @Nullable Function<DynamicScheme, DynamicColor> background,
      @Nullable Function<DynamicScheme, DynamicColor> secondBackground,
      @Nullable ContrastCurve contrastCurve,
      @Nullable Function<DynamicScheme, ToneDeltaPair> toneDeltaPair,
      @Nullable Function<DynamicScheme, Double> opacity) {
    this(
        name,
        palette,
        tone,
        isBackground,
        null,
        background,
        secondBackground,
        (s) -> contrastCurve,
        toneDeltaPair,
        opacity);
  }

  public DynamicColor(
      @NonNull String name,
      @NonNull Function<DynamicScheme, TonalPalette> palette,
      @NonNull Function<DynamicScheme, Double> tone,
      boolean isBackground,
      @Nullable Function<DynamicScheme, Double> chromaMultiplier,
      @Nullable Function<DynamicScheme, DynamicColor> background,
      @Nullable Function<DynamicScheme, DynamicColor> secondBackground,
      @Nullable Function<DynamicScheme, ContrastCurve> contrastCurve,
      @Nullable Function<DynamicScheme, ToneDeltaPair> toneDeltaPair,
      @Nullable Function<DynamicScheme, Double> opacity) {
    this.name = name;
    this.palette = palette;
    this.tone = tone;
    this.isBackground = isBackground;
    this.chromaMultiplier = chromaMultiplier;
    this.background = background;
    this.secondBackground = secondBackground;
    this.contrastCurve = contrastCurve;
    this.toneDeltaPair = toneDeltaPair;
    this.opacity = opacity;
  }

  /**
   * A convenience constructor for DynamicColor.
   *
   * <p>_Strongly_ prefer using one of the convenience constructors. This class is arguably too
   * flexible to ensure it can support any scenario. Functional arguments allow overriding without
   * risks that come with subclasses.
   *
   * <p>For example, the default behavior of adjust tone at max contrast to be at a 7.0 ratio with
   * its background is principled and matches accessibility guidance. That does not mean it's the
   * desired approach for _every_ design system, and every color pairing, always, in every case.
   *
   * <p>For opaque colors (colors with alpha = 100%).
   *
   * <p>For colors that are not backgrounds, and do not have backgrounds.
   *
   * @param name The name of the dynamic color.
   * @param palette Function that provides a TonalPalette given DynamicScheme. A TonalPalette is
   *     defined by a hue and chroma, so this replaces the need to specify hue/chroma. By providing
   *     a tonal palette, when contrast adjustments are made, intended chroma can be preserved.
   * @param tone Function that provides a tone, given a DynamicScheme.
   */
  @NonNull
  public static DynamicColor fromPalette(
      @NonNull String name,
      @NonNull Function<DynamicScheme, TonalPalette> palette,
      @NonNull Function<DynamicScheme, Double> tone) {
    return new DynamicColor(
        name,
        palette,
        tone,
        /* isBackground= */ false,
        /* background= */ null,
        /* secondBackground= */ null,
        /* contrastCurve= */ null,
        /* toneDeltaPair= */ null);
  }

  /**
   * A convenience constructor for DynamicColor.
   *
   * <p>_Strongly_ prefer using one of the convenience constructors. This class is arguably too
   * flexible to ensure it can support any scenario. Functional arguments allow overriding without
   * risks that come with subclasses.
   *
   * <p>For example, the default behavior of adjust tone at max contrast to be at a 7.0 ratio with
   * its background is principled and matches accessibility guidance. That does not mean it's the
   * desired approach for _every_ design system, and every color pairing, always, in every case.
   *
   * <p>For opaque colors (colors with alpha = 100%).
   *
   * <p>For colors that do not have backgrounds.
   *
   * @param name The name of the dynamic color.
   * @param palette Function that provides a TonalPalette given DynamicScheme. A TonalPalette is
   *     defined by a hue and chroma, so this replaces the need to specify hue/chroma. By providing
   *     a tonal palette, when contrast adjustments are made, intended chroma can be preserved.
   * @param tone Function that provides a tone, given a DynamicScheme.
   * @param isBackground Whether this dynamic color is a background, with some other color as the
   *     foreground.
   */
  @NonNull
  public static DynamicColor fromPalette(
      @NonNull String name,
      @NonNull Function<DynamicScheme, TonalPalette> palette,
      @NonNull Function<DynamicScheme, Double> tone,
      boolean isBackground) {
    return new DynamicColor(
        name,
        palette,
        tone,
        isBackground,
        /* background= */ null,
        /* secondBackground= */ null,
        /* contrastCurve= */ null,
        /* toneDeltaPair= */ null);
  }

  /**
   * Create a DynamicColor from a hex code.
   *
   * <p>Result has no background; thus no support for increasing/decreasing contrast for a11y.
   *
   * @param name The name of the dynamic color.
   * @param argb The source color from which to extract the hue and chroma.
   */
  @NonNull
  public static DynamicColor fromArgb(@NonNull String name, int argb) {
    Hct hct = Hct.fromInt(argb);
    TonalPalette palette = TonalPalette.fromInt(argb);
    return DynamicColor.fromPalette(name, (s) -> palette, (s) -> hct.getTone());
  }

  /**
   * Returns an ARGB integer (i.e. a hex code).
   *
   * @param scheme Defines the conditions of the user interface, for example, whether or not it is
   *     dark mode or light mode, and what the desired contrast level is.
   */
  public int getArgb(@NonNull DynamicScheme scheme) {
    int argb = getHct(scheme).toInt();
    if (opacity == null || opacity.apply(scheme) == null) {
      return argb;
    }
    double percentage = opacity.apply(scheme);
    int alpha = MathUtils.clampInt(0, 255, (int) Math.round(percentage * 255));
    return (argb & 0x00ffffff) | (alpha << 24);
  }

  /**
   * Returns an HCT object.
   *
   * @param scheme Defines the conditions of the user interface, for example, whether or not it is
   *     dark mode or light mode, and what the desired contrast level is.
   */
  @NonNull
  public Hct getHct(@NonNull DynamicScheme scheme) {
    Hct cachedAnswer = hctCache.get(scheme);
    if (cachedAnswer != null) {
      return cachedAnswer;
    }

    Hct answer = ColorSpecs.get(scheme.specVersion).getHct(scheme, this);
    // NOMUTANTS--trivial test with onerous dependency injection requirement.
    if (hctCache.size() > 4) {
      hctCache.clear();
    }
    // NOMUTANTS--trivial test with onerous dependency injection requirement.
    hctCache.put(scheme, answer);
    return answer;
  }

  /** Returns the tone in HCT, ranging from 0 to 100, of the resolved color given scheme. */
  public double getTone(@NonNull DynamicScheme scheme) {
    return ColorSpecs.get(scheme.specVersion).getTone(scheme, this);
  }

  /**
   * Given a background tone, find a foreground tone, while ensuring they reach a contrast ratio
   * that is as close to ratio as possible.
   */
  public static double foregroundTone(double bgTone, double ratio) {
    double lighterTone = Contrast.lighterUnsafe(bgTone, ratio);
    double darkerTone = Contrast.darkerUnsafe(bgTone, ratio);
    double lighterRatio = Contrast.ratioOfTones(lighterTone, bgTone);
    double darkerRatio = Contrast.ratioOfTones(darkerTone, bgTone);
    boolean preferLighter = tonePrefersLightForeground(bgTone);

    if (preferLighter) {
      // "Neglible difference" handles an edge case where the initial contrast ratio is high
      // (ex. 13.0), and the ratio passed to the function is that high ratio, and both the lighter
      // and darker ratio fails to pass that ratio.
      //
      // This was observed with Tonal Spot's On Primary Container turning black momentarily between
      // high and max contrast in light mode. PC's standard tone was T90, OPC's was T10, it was
      // light mode, and the contrast level was 0.6568521221032331.
      boolean negligibleDifference =
          Math.abs(lighterRatio - darkerRatio) < 0.1 && lighterRatio < ratio && darkerRatio < ratio;
      if (lighterRatio >= ratio || lighterRatio >= darkerRatio || negligibleDifference) {
        return lighterTone;
      } else {
        return darkerTone;
      }
    } else {
      return darkerRatio >= ratio || darkerRatio >= lighterRatio ? darkerTone : lighterTone;
    }
  }

  /**
   * Adjust a tone down such that white has 4.5 contrast, if the tone is reasonably close to
   * supporting it.
   */
  public static double enableLightForeground(double tone) {
    if (tonePrefersLightForeground(tone) && !toneAllowsLightForeground(tone)) {
      return 49.0;
    }
    return tone;
  }

  /**
   * People prefer white foregrounds on ~T60-70. Observed over time, and also by Andrew Somers
   * during research for APCA.
   *
   * <p>T60 used as to create the smallest discontinuity possible when skipping down to T49 in order
   * to ensure light foregrounds.
   *
   * <p>Since `tertiaryContainer` in dark monochrome scheme requires a tone of 60, it should not be
   * adjusted. Therefore, 60 is excluded here.
   */
  public static boolean tonePrefersLightForeground(double tone) {
    return Math.round(tone) < 60;
  }

  /** Tones less than ~T50 always permit white at 4.5 contrast. */
  public static boolean toneAllowsLightForeground(double tone) {
    return Math.round(tone) <= 49;
  }

  public static Function<DynamicScheme, Double> getInitialToneFromBackground(
      @Nullable Function<DynamicScheme, DynamicColor> background) {
    if (background == null) {
      return (s) -> 50.0;
    }
    return (s) -> background.apply(s) != null ? background.apply(s).getTone(s) : 50.0;
  }

  public Builder toBuilder() {
    return new Builder()
        .setName(this.name)
        .setPalette(this.palette)
        .setTone(this.tone)
        .setIsBackground(this.isBackground)
        .setChromaMultiplier(this.chromaMultiplier)
        .setBackground(this.background)
        .setSecondBackground(this.secondBackground)
        .setContrastCurve(this.contrastCurve)
        .setToneDeltaPair(this.toneDeltaPair)
        .setOpacity(this.opacity);
  }

  /** Builder for {@link DynamicColor}. */
  public static class Builder {
    private String name;
    private Function<DynamicScheme, TonalPalette> palette;
    private Function<DynamicScheme, Double> tone;
    private boolean isBackground;
    private Function<DynamicScheme, Double> chromaMultiplier;
    private Function<DynamicScheme, DynamicColor> background;
    private Function<DynamicScheme, DynamicColor> secondBackground;
    private Function<DynamicScheme, ContrastCurve> contrastCurve;
    private Function<DynamicScheme, ToneDeltaPair> toneDeltaPair;
    private Function<DynamicScheme, Double> opacity;

    @CanIgnoreReturnValue
    public Builder setName(@NonNull String name) {
      this.name = name;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPalette(@NonNull Function<DynamicScheme, TonalPalette> palette) {
      this.palette = palette;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTone(@NonNull Function<DynamicScheme, Double> tone) {
      this.tone = tone;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIsBackground(boolean isBackground) {
      this.isBackground = isBackground;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setChromaMultiplier(@NonNull Function<DynamicScheme, Double> chromaMultiplier) {
      this.chromaMultiplier = chromaMultiplier;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setBackground(@NonNull Function<DynamicScheme, DynamicColor> background) {
      this.background = background;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSecondBackground(
        @NonNull Function<DynamicScheme, DynamicColor> secondBackground) {
      this.secondBackground = secondBackground;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setContrastCurve(@NonNull Function<DynamicScheme, ContrastCurve> contrastCurve) {
      this.contrastCurve = contrastCurve;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setToneDeltaPair(@NonNull Function<DynamicScheme, ToneDeltaPair> toneDeltaPair) {
      this.toneDeltaPair = toneDeltaPair;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setOpacity(@NonNull Function<DynamicScheme, Double> opacity) {
      this.opacity = opacity;
      return this;
    }

    @CanIgnoreReturnValue
    Builder extendSpecVersion(SpecVersion specVersion, DynamicColor extendedColor) {
      validateExtendedColor(specVersion, extendedColor);

      return new Builder()
          .setName(this.name)
          .setIsBackground(this.isBackground)
          .setPalette(
              (s) -> {
                Function<DynamicScheme, TonalPalette> palette =
                    s.specVersion == specVersion ? extendedColor.palette : this.palette;
                return palette != null ? palette.apply(s) : null;
              })
          .setTone(
              (s) -> {
                Function<DynamicScheme, Double> tone =
                    s.specVersion == specVersion ? extendedColor.tone : this.tone;
                return tone != null ? tone.apply(s) : null;
              })
          .setChromaMultiplier(
              (s) -> {
                Function<DynamicScheme, Double> chromaMultiplier =
                    s.specVersion == specVersion
                        ? extendedColor.chromaMultiplier
                        : this.chromaMultiplier;
                return chromaMultiplier != null ? chromaMultiplier.apply(s) : 1.0;
              })
          .setBackground(
              (s) -> {
                Function<DynamicScheme, DynamicColor> background =
                    s.specVersion == specVersion ? extendedColor.background : this.background;
                return background != null ? background.apply(s) : null;
              })
          .setSecondBackground(
              (s) -> {
                Function<DynamicScheme, DynamicColor> secondBackground =
                    s.specVersion == specVersion
                        ? extendedColor.secondBackground
                        : this.secondBackground;
                return secondBackground != null ? secondBackground.apply(s) : null;
              })
          .setContrastCurve(
              (s) -> {
                Function<DynamicScheme, ContrastCurve> contrastCurve =
                    s.specVersion == specVersion ? extendedColor.contrastCurve : this.contrastCurve;
                return contrastCurve != null ? contrastCurve.apply(s) : null;
              })
          .setToneDeltaPair(
              (s) -> {
                Function<DynamicScheme, ToneDeltaPair> toneDeltaPair =
                    s.specVersion == specVersion ? extendedColor.toneDeltaPair : this.toneDeltaPair;
                return toneDeltaPair != null ? toneDeltaPair.apply(s) : null;
              })
          .setOpacity(
              (s) -> {
                Function<DynamicScheme, Double> opacity =
                    s.specVersion == specVersion ? extendedColor.opacity : this.opacity;
                return opacity != null ? opacity.apply(s) : null;
              });
    }

    public DynamicColor build() {
      if (this.background == null && this.secondBackground != null) {
        throw new IllegalArgumentException(
            "Color " + name + " has secondBackground defined, but background is not defined.");
      }
      if (this.background == null && this.contrastCurve != null) {
        throw new IllegalArgumentException(
            "Color " + name + " has contrastCurve defined, but background is not defined.");
      }
      if (this.background != null && this.contrastCurve == null) {
        throw new IllegalArgumentException(
            "Color " + name + " has background defined, but contrastCurve is not defined.");
      }
      return new DynamicColor(
          this.name,
          this.palette,
          this.tone == null ? getInitialToneFromBackground(this.background) : this.tone,
          this.isBackground,
          this.chromaMultiplier,
          this.background,
          this.secondBackground,
          this.contrastCurve,
          this.toneDeltaPair,
          this.opacity);
    }

    private void validateExtendedColor(SpecVersion specVersion, DynamicColor extendedColor) {
      if (!this.name.equals(extendedColor.name)) {
        throw new IllegalArgumentException(
            "Attempting to extend color "
                + this.name
                + " with color "
                + extendedColor.name
                + " of different name for spec version "
                + specVersion
                + ".");
      }
      if (this.isBackground != extendedColor.isBackground) {
        throw new IllegalArgumentException(
            "Attempting to extend color "
                + this.name
                + " as a "
                + (this.isBackground ? "background" : "foreground")
                + " with color "
                + extendedColor.name
                + " as a "
                + (extendedColor.isBackground ? "background" : "foreground")
                + " for spec version "
                + specVersion
                + ".");
      }
    }
  }
}

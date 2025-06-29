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

/**
 * Documents a constraint between two DynamicColors, in which their tones must have a certain
 * distance from each other.
 *
 * <p>Prefer a DynamicColor with a background, this is for special cases when designers want tonal
 * distance, literally contrast, between two colors that don't have a background / foreground
 * relationship or a contrast guarantee.
 */
public final class ToneDeltaPair {
  /** Describes how to fulfill a tone delta pair constraint. */
  public enum DeltaConstraint {
    EXACT,
    NEARER,
    FARTHER
  }

  /** The first role in a pair. */
  private final DynamicColor roleA;

  /** The second role in a pair. */
  private final DynamicColor roleB;

  /** Required difference between tones. Absolute value, negative values have undefined behavior. */
  private final double delta;

  /** The relative relation between tones of roleA and roleB, as described above. */
  private final TonePolarity polarity;

  /**
   * Whether these two roles should stay on the same side of the "awkward zone" (T50-59). This is
   * necessary for certain cases where one role has two backgrounds.
   */
  private final boolean stayTogether;

  /** How to fulfill the tone delta pair constraint. */
  private final DeltaConstraint constraint;

  /**
   * Documents a constraint in tone distance between two DynamicColors.
   *
   * <p>The polarity is an adjective that describes "A", compared to "B".
   *
   * <p>For instance, ToneDeltaPair(A, B, 15, 'darker', stayTogether) states that A's tone should be
   * at least 15 darker than B's.
   *
   * <p>'relative_darker' and 'relative_lighter' describes the tone adjustment relative to the
   * surface color trend (white in light mode; black in dark mode). For instance, ToneDeltaPair(A,
   * B, 10, 'relative_lighter', 'farther') states that A should be at least 10 lighter than B in
   * light mode, and at least 10 darker than B in dark mode.
   *
   * @param roleA The first role in a pair.
   * @param roleB The second role in a pair.
   * @param delta Required difference between tones. Absolute value, negative values have undefined
   *     behavior.
   * @param polarity The relative relation between tones of roleA and roleB, as described above.
   * @param stayTogether Whether these two roles should stay on the same side of the "awkward zone"
   *     (T50-59). This is necessary for certain cases where one role has two backgrounds.
   */
  public ToneDeltaPair(
      DynamicColor roleA,
      DynamicColor roleB,
      double delta,
      TonePolarity polarity,
      boolean stayTogether) {
    this.roleA = roleA;
    this.roleB = roleB;
    this.delta = delta;
    this.polarity = polarity;
    this.stayTogether = stayTogether;
    this.constraint = DeltaConstraint.EXACT;
  }

  /**
   * Documents a constraint in tone distance between two DynamicColors.
   *
   * @see #ToneDeltaPair(DynamicColor, DynamicColor, double, TonePolarity, boolean)
   * @param roleA The first role in a pair.
   * @param roleB The second role in a pair.
   * @param delta Required difference between tones. Absolute value, negative values have undefined
   *     behavior.
   * @param polarity The relative relation between tones of roleA and roleB, as described above.
   * @param constraint How to fulfill the tone delta pair constraint.
   */
  public ToneDeltaPair(
      DynamicColor roleA,
      DynamicColor roleB,
      double delta,
      TonePolarity polarity,
      DeltaConstraint constraint) {
    this.roleA = roleA;
    this.roleB = roleB;
    this.delta = delta;
    this.polarity = polarity;
    this.stayTogether = true;
    this.constraint = constraint;
  }

  @NonNull
  public DynamicColor getRoleA() {
    return roleA;
  }

  @NonNull
  public DynamicColor getRoleB() {
    return roleB;
  }

  public double getDelta() {
    return delta;
  }

  @NonNull
  public TonePolarity getPolarity() {
    return polarity;
  }

  public boolean getStayTogether() {
    return stayTogether;
  }

  @NonNull
  public DeltaConstraint getConstraint() {
    return constraint;
  }
}

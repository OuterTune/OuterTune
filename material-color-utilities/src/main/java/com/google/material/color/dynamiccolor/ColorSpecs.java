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

import com.google.material.color.dynamiccolor.ColorSpec.SpecVersion;

/** A utility class to get the correct color spec for a given spec version. */
public final class ColorSpecs {

  private static final ColorSpec SPEC_2021 = new ColorSpec2021();
  private static final ColorSpec SPEC_2025 = new ColorSpec2025();

  public static final ColorSpec get() {
    return get(SpecVersion.SPEC_2021);
  }

  public static final ColorSpec get(SpecVersion specVersion) {
    return get(specVersion, false);
  }

  public static final ColorSpec get(SpecVersion specVersion, boolean isExtendedFidelity) {
    return specVersion == SpecVersion.SPEC_2025 ? SPEC_2025 : SPEC_2021;
  }

  private ColorSpecs() {}
}

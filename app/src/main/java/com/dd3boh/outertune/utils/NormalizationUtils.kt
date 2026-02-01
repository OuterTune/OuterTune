package com.dd3boh.outertune.utils

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Compute normalization gain factor based on track loudness and target LUFS.
 *
 * loudnessDb  - measured integrated loudness of the track (LUFS, typically negative).
 * targetLufs  - desired target loudness (e.g. -14).
 * invert      - controls whether the normalization is amplify-only or reduce-only:
 *               - if true: apply amplification only (gain is clamped to >= 1.0), so quieter
 *                 tracks may be turned up but louder tracks are never turned down;
 *               - if false: apply attenuation only (gain is clamped to <= 1.0), so louder
 *                 tracks may be turned down but quieter tracks are never turned up.
 *
 * Returns a linear gain factor to multiply the player volume by.
 */
fun computeNormalizeFactor(loudnessDb: Float, targetLufs: Int, invert: Boolean): Float {
    // gain = 10^{(target - loudness) / 20}
    val gain = 10f.pow((targetLufs - loudnessDb) / 20f)
    return if (invert) max(gain, 1f) else min(gain, 1f)
}

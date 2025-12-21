package com.dd3boh.outertune.utils

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Compute normalization gain factor based on track loudness and target LUFS.
 *
 * loudnessDb  - measured integrated loudness of the track (LUFS, typically negative).
 * targetLufs  - desired target loudness (e.g. -14).
 * invert      - if true: only amplify quieter tracks (increase-only),
 *               otherwise only reduce louder tracks (reduce-only).
 *
 * Returns a linear gain factor to multiply the player volume by.
 */
fun computeNormalizeFactor(loudnessDb: Float, targetLufs: Int, invert: Boolean): Float {
    // gain = 10^{(target - loudness) / 20}
    val gain = 10f.pow((targetLufs - loudnessDb) / 20f)
    return if (invert) max(gain, 1f) else min(gain, 1f)
}

package io.github.yuuichi_s.astertune.lyrics

import io.github.yuuichi_s.astertune.db.entities.LyricsEntity
import io.github.yuuichi_s.astertune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boundaries of [shouldFetchLyrics]: the single decision for whether a fetch runs, given the stored row
 * and the current provider signature.
 */
class ShouldFetchLyricsTest {

    private val sig = "kugou,lrclib,youtube"
    private val now = 1_000_000_000_000L
    private val ttl = NEGATIVE_CACHE_TTL_MS

    private fun negativeRow(lastCheckedAt: Long?, signature: String?) =
        LyricsEntity(id = "v", lyrics = LYRICS_NOT_FOUND, provider = null, lastCheckedAt = lastCheckedAt, providerSignature = signature)

    @Test
    fun noRow_fetches() {
        assertTrue(shouldFetchLyrics(null, sig, now, forceRefresh = false))
    }

    @Test
    fun positiveCache_doesNotFetch() {
        val row = LyricsEntity(id = "v", lyrics = "[00:01.00]hi", provider = "LrcLib", lastCheckedAt = now, providerSignature = sig)
        assertFalse(shouldFetchLyrics(row, sig, now, forceRefresh = false))
    }

    @Test
    fun positiveCache_fetchesWhenForced() {
        val row = LyricsEntity(id = "v", lyrics = "[00:01.00]hi", provider = "LrcLib", lastCheckedAt = now, providerSignature = sig)
        assertTrue(shouldFetchLyrics(row, sig, now, forceRefresh = true))
    }

    @Test
    fun freshNegative_sameSignature_doesNotFetch() {
        val row = negativeRow(lastCheckedAt = now - ttl / 2, signature = sig)
        assertFalse(shouldFetchLyrics(row, sig, now, forceRefresh = false))
    }

    @Test
    fun negative_ttlExpired_fetches() {
        val row = negativeRow(lastCheckedAt = now - ttl, signature = sig)
        assertTrue(shouldFetchLyrics(row, sig, now, forceRefresh = false))
    }

    @Test
    fun negative_signatureChanged_fetches() {
        val row = negativeRow(lastCheckedAt = now - 1, signature = "kugou,lrclib")
        assertTrue(shouldFetchLyrics(row, sig, now, forceRefresh = false))
    }

    @Test
    fun negative_nullTimestamp_fetches() {
        val row = negativeRow(lastCheckedAt = null, signature = sig)
        assertTrue(shouldFetchLyrics(row, sig, now, forceRefresh = false))
    }

    @Test
    fun negative_nullSignature_fetches() {
        val row = negativeRow(lastCheckedAt = now - 1, signature = null)
        assertTrue(shouldFetchLyrics(row, sig, now, forceRefresh = false))
    }

    @Test
    fun negative_clockRewind_fetches() {
        // Stored timestamp is in the future relative to now: the device clock moved backwards.
        val row = negativeRow(lastCheckedAt = now + 5_000L, signature = sig)
        assertTrue(shouldFetchLyrics(row, sig, now, forceRefresh = false))
    }

    @Test
    fun negative_exactlyAtTtl_fetches() {
        val row = negativeRow(lastCheckedAt = now - ttl, signature = sig)
        assertTrue(shouldFetchLyrics(row, sig, now, forceRefresh = false))
    }
}

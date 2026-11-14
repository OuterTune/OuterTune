package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yuuichi_s.astertune.constants.LYRICS_DEBUG
import io.github.yuuichi_s.astertune.constants.LyricSourcePrefKey
import io.github.yuuichi_s.astertune.constants.LyricTrimKey
import io.github.yuuichi_s.astertune.constants.MultilineLrcKey
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.LyricsEntity
import io.github.yuuichi_s.astertune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.astertune.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Role of a remote fetch, used to correlate log lines when several fetches run for different songs.
 */
enum class LyricsFetchRole(val log: String) {
    CURRENT("current"),
    PREFETCH("prefetch"),
    MANUAL("manual"),
}

/** Resolves and stores lyrics and collects manual-search candidates. */
@Singleton
class LyricsHelper internal constructor(
    private val context: Context,
    val database: MusicDatabase,
    private val lyricsProviders: List<LyricsProvider>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
    ) : this(
        context,
        database,
        listOf(
            BetterLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            YouTubeLyricsProvider,
            YouTubeSubtitleLyricsProvider,
        ),
    )

    private data class ManualSearchKey(
        val mediaId: String,
        val title: String,
        val artists: String,
        val duration: Int,
    )

    private val cache = LruCache<ManualSearchKey, List<LyricsResult>>(MAX_CACHE_SIZE)

    /**
     * Per-videoId mutexes that make [fetchAndStoreRemote] single-flight: at most one fetch runs for a
     * given videoId at a time, and each fetch re-checks the database under the lock before starting.
     * Entries are never removed, so the map grows by the number of songs fetched during the process
     * lifetime; it is released when the process ends.
     */
    private val fetchMutexes = HashMap<String, Mutex>()
    private val fetchMutexesGuard = Mutex()

    private suspend fun fetchMutexFor(videoId: String): Mutex =
        fetchMutexesGuard.withLock { fetchMutexes.getOrPut(videoId) { Mutex() } }

    /**
     * Resolves stored, local, or remote lyrics according to the source preference.
     * Tries local lyrics first when they are preferred; otherwise reuses stored lyrics.
     * Queries remote providers only when [shouldFetch] requires it.
     * Uses local lyrics as fallback when remote lyrics are preferred and no positive stored
     * result is available.
     */
    suspend fun getLyrics(mediaMetadata: MediaMetadata): SemanticLyrics? {
        val parserOptions = getParserOptions()
        val prefLocal = isLocalPreferred()

        val dbLyrics = database.lyrics(mediaMetadata.id).let { it.first()?.lyrics }
        val hasPositive = dbLyrics != null && dbLyrics != LYRICS_NOT_FOUND
        if (hasPositive && !prefLocal) {
            return LrcUtils.parseLyrics(dbLyrics, null, parserOptions, null)
        }

        val localLyrics: SemanticLyrics? = getLocalLyrics(mediaMetadata, parserOptions)

        // fallback to secondary provider when primary is unavailable
        if (prefLocal) {
            if (localLyrics != null) {
                return localLyrics
            }
            if (hasPositive) {
                return LrcUtils.parseLyrics(dbLyrics, null, parserOptions, null)
            }
        }

        // No usable positive cache in the preferred source. Fetch only when there is no row or the
        // negative cache is stale/invalid; a fresh negative cache is not re-fetched. LYRICS_NOT_FOUND is
        // never treated as a plain "row exists".
        if (shouldFetch(mediaMetadata.id)) {
            fetchAndStoreRemote(mediaMetadata, LyricsFetchRole.MANUAL)
            val fetched = database.lyrics(mediaMetadata.id).let { it.first()?.lyrics }
            if (fetched != null && fetched != LYRICS_NOT_FOUND) {
                return LrcUtils.parseLyrics(fetched, null, parserOptions, null)
            }
        }
        return if (!prefLocal) localLyrics else null
    }

    /**
     * Determines whether remote lyrics need fetching for [videoId].
     * Found lyrics are reused unless [forceRefresh] is true.
     * Missing entries and stale or invalid cached absence require a fetch.
     */
    suspend fun shouldFetch(videoId: String, forceRefresh: Boolean = false): Boolean {
        val entity = database.lyrics(videoId).first()
        val signature = ProviderSelection.snapshot(context, lyricsProviders).signature
        return shouldFetchLyrics(entity, signature, System.currentTimeMillis(), forceRefresh)
    }

    /**
     * Fetches and caches remote lyrics unless a reusable cache exists and [forceRefresh] is false.
     *
     * Stores lyrics or confirmed absence; inconclusive results leave existing data unchanged.
     *
     * @param role Caller category used for logging
     */
    suspend fun fetchAndStoreRemote(
        mediaMetadata: MediaMetadata,
        role: LyricsFetchRole,
        forceRefresh: Boolean = false,
    ) {
        fetchMutexFor(mediaMetadata.id).withLock {
            try {
                // The enabled providers and their signature are pinned once here so the search set, the
                // all-NotFound decision and the stored signature all use the same snapshot.
                val selection = ProviderSelection.snapshot(context, lyricsProviders)
                val existing = database.lyrics(mediaMetadata.id).first()
                if (!shouldFetchLyrics(existing, selection.signature, System.currentTimeMillis(), forceRefresh)) {
                    return
                }
                val result = getRemoteLyrics(mediaMetadata, role, selection)
                val now = System.currentTimeMillis()
                val entity = when (result) {
                    is RemoteLyricsResult.Found ->
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = result.raw,
                            provider = result.provider,
                            lastCheckedAt = now,
                            providerSignature = selection.signature,
                        )

                    RemoteLyricsResult.DefinitiveNotFound ->
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = LYRICS_NOT_FOUND,
                            provider = null,
                            lastCheckedAt = now,
                            providerSignature = selection.signature,
                        )

                    RemoteLyricsResult.Indeterminate, RemoteLyricsResult.Skipped -> null
                }
                if (entity != null) {
                    withContext(Dispatchers.IO) {
                        database.upsert(entity)
                    }
                    Log.d(TAG, "saved: videoId=${mediaMetadata.id} role=${role.log} provider=${(result as? RemoteLyricsResult.Found)?.provider ?: "NOT_FOUND"}")
                } else {
                    Log.d(TAG, "not saved: videoId=${mediaMetadata.id} role=${role.log} result=${result::class.simpleName}")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "cancelled: videoId=${mediaMetadata.id} role=${role.log}")
                throw e
            }
        }
    }

    /**
     * Read the lyric parsing preferences (trim / multiline) shared by all resolution paths
     */
    suspend fun getParserOptions(): LrcUtils.LrcParserOptions {
        val trim = context.dataStore.get(LyricTrimKey, defaultValue = false)
        val multiline = context.dataStore.get(MultilineLrcKey, defaultValue = true)
        return LrcUtils.LrcParserOptions(trim, multiline, "Unable to parse lyrics")
    }

    suspend fun isLocalPreferred(): Boolean = context.dataStore.get(LyricSourcePrefKey, true)

    /**
     * Run a single provider lookup behind the isolation boundary for the parallel fetch path. A
     * provider that honours the contract returns [LyricsFetchResult]; one that throws instead has its
     * exception normalized to [LyricsFetchResult.Failed]. Cancellation is always re-thrown.
     */
    private suspend fun LyricsProvider.fetchIsolated(
        mediaMetadata: MediaMetadata,
        artistName: String,
    ): LyricsFetchResult =
        try {
            getLyrics(mediaMetadata.id, mediaMetadata.title, artistName, mediaMetadata.duration)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LyricsFetchResult.Failed(e)
        }

    /**
     * Resolves lyrics from remote providers.
     *
     * Every provider in [selection] runs at once. Results are judged as they arrive: the first synced
     * result wins immediately and the remaining providers are cancelled; an unsynced result is kept as
     * a fallback and used only if no synced result arrives before every provider finishes or the overall
     * timeout is reached. Each provider has an individual timeout; the whole resolution is bounded by an
     * overall cap.
     *
     * The possible outcomes are: [RemoteLyricsResult.Found] when a usable result was adopted,
     * [RemoteLyricsResult.DefinitiveNotFound] only when every provider reported a definitive absence,
     * [RemoteLyricsResult.Indeterminate] when no usable result was found but at least one provider failed,
     * never reported, or returned an unparseable result, and [RemoteLyricsResult.Skipped] when no
     * provider was enabled.
     */
    private suspend fun getRemoteLyrics(
        mediaMetadata: MediaMetadata,
        role: LyricsFetchRole,
        selection: ProviderSelection,
    ): RemoteLyricsResult {
        val artistName = mediaMetadata.artists
            .filter { it.id != null }
            .joinToString { it.name.removeSuffix(" - Topic") }
            .ifEmpty { mediaMetadata.artists.joinToString { it.name } }
        val start = System.currentTimeMillis()
        Log.d(TAG, "start: videoId=${mediaMetadata.id} role=${role.log} title=\"${mediaMetadata.title}\" artist=\"${artistName}\"")

        lyricsProviders.filterNot { it in selection.providers }.forEach { provider ->
            Log.d(TAG, "${provider.name} SKIPPED (disabled) videoId=${mediaMetadata.id} role=${role.log}")
        }
        val enabled = selection.providers
        if (enabled.isEmpty()) {
            Log.d(TAG, "end: skipped videoId=${mediaMetadata.id} role=${role.log} total=0ms (no enabled providers)")
            return RemoteLyricsResult.Skipped
        }

        // errorText = null so adoption sees an unparseable input as null/exception rather than a
        // synthesized UnsyncedLyrics; the user-facing errorText is only used by the display path.
        val verifyOptions = getParserOptions().copy(errorText = null)

        return coroutineScope {
            val channel = Channel<ProviderOutcome>(Channel.UNLIMITED)
            val fetchJobs = enabled.map { provider ->
                launch {
                    val t0 = System.currentTimeMillis()
                    val result = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        provider.fetchIsolated(mediaMetadata, artistName)
                    }
                    val elapsed = System.currentTimeMillis() - t0
                    val outcome: LyricsFetchResult = when (result) {
                        null -> {
                            Log.d(TAG, "${provider.name} FAILURE in ${elapsed}ms videoId=${mediaMetadata.id} role=${role.log}: timeout after ${PROVIDER_TIMEOUT_MS}ms")
                            LyricsFetchResult.Failed()
                        }

                        is LyricsFetchResult.Found -> {
                            Log.d(TAG, "${provider.name} SUCCESS in ${elapsed}ms videoId=${mediaMetadata.id} role=${role.log}")
                            result
                        }

                        LyricsFetchResult.NotFound -> {
                            Log.d(TAG, "${provider.name} NOT_FOUND in ${elapsed}ms videoId=${mediaMetadata.id} role=${role.log}")
                            result
                        }

                        is LyricsFetchResult.Failed -> {
                            Log.d(TAG, "${provider.name} FAILURE in ${elapsed}ms videoId=${mediaMetadata.id} role=${role.log}: ${result.cause?.message}")
                            result
                        }
                    }
                    channel.send(ProviderOutcome(provider.name, outcome))
                }
            }
            // Close the channel once every provider has reported so exhaustion is detected promptly
            // instead of waiting for the overall timeout.
            val closer = launch {
                fetchJobs.joinAll()
                channel.close()
            }

            val aggregator = RemoteLyricsAggregator(enabled.size)
            // Classify a Found result for adoption. errorText = null means an unparseable input surfaces
            // as null or an exception rather than a synthesized UnsyncedLyrics.
            val classifyFound: (String) -> FoundKind = { raw ->
                val parsed = try {
                    LrcUtils.parseLyrics(raw, null, verifyOptions, null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                when (parsed) {
                    is SemanticLyrics.SyncedLyrics -> FoundKind.SYNCED
                    is SemanticLyrics.UnsyncedLyrics -> FoundKind.UNSYNCED
                    null -> FoundKind.UNPARSEABLE
                }
            }
            val deadline = start + OVERALL_TIMEOUT_MS

            try {
                while (true) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    val outcome = withTimeoutOrNull(remaining) {
                        channel.receiveCatching().getOrNull()
                    } ?: break // overall timeout, or every provider has reported
                    // A synced result was adopted: stop and cancel the remaining providers.
                    if (aggregator.offer(outcome.providerName, outcome.result, classifyFound)) break
                }
            } finally {
                fetchJobs.forEach { it.cancel() }
                closer.cancel()
                channel.close()
            }

            val totalMs = System.currentTimeMillis() - start
            val result = aggregator.result()
            when (result) {
                is RemoteLyricsResult.Found ->
                    Log.d(TAG, "adopted: videoId=${mediaMetadata.id} role=${role.log} provider=${result.provider} synced=${result.synced} total=${totalMs}ms")

                RemoteLyricsResult.DefinitiveNotFound ->
                    Log.d(TAG, "end: not found videoId=${mediaMetadata.id} role=${role.log} total=${totalMs}ms")

                RemoteLyricsResult.Indeterminate ->
                    Log.d(TAG, "end: indeterminate videoId=${mediaMetadata.id} role=${role.log} total=${totalMs}ms")

                RemoteLyricsResult.Skipped -> {} // handled above
            }
            result
        }
    }

    /** Loads and parses a local .lrc file. */
    fun getLocalLyrics(
        mediaMetadata: MediaMetadata,
        parserOptions: LrcUtils.LrcParserOptions
    ): SemanticLyrics? {
        if (LocalLyricsProvider.isEnabled(context) && mediaMetadata.localPath != null) {
            return LocalLyricsProvider.getLyricsNew(
                mediaMetadata.localPath,
                parserOptions
            )
        }

        return null
    }

    /**
     * Runs one provider's manual search and reports whether it completed normally.
     * Search exceptions yield false; cancellation is re-thrown.
     * Candidates already delivered through [callback] are retained by the caller.
     *
     * @return true when the search completed normally, false when it failed
     */
    private suspend fun LyricsProvider.searchIsolated(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (String) -> Unit,
    ): Boolean =
        try {
            getAllLyrics(mediaId, songTitle, songArtists, duration, callback)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is LyricsSearchFailedException || LYRICS_DEBUG) {
                reportException(e)
            }
            false
        }

    /**
     * Delivers manual lyrics search candidates through [callback].
     *
     * Reuses cached results for the same [mediaId], [songTitle], [songArtists], and [duration].
     * On a cache miss, delivers candidates as enabled providers return them and caches the results
     * only if every enabled provider completes successfully.
     */
    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        val cacheKey = ManualSearchKey(mediaId, songTitle, songArtists, duration)
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        var anyFailed = false
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                val succeeded = provider.searchIsolated(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
                if (!succeeded) anyFailed = true
            }
        }
        if (!anyFailed) cache.put(cacheKey, allResult)
    }

    companion object {
        private const val TAG = "LyricsHelper"
        private const val MAX_CACHE_SIZE = 3

        /** Per-provider timeout for a single remote lookup. */
        private const val PROVIDER_TIMEOUT_MS = 8000L

        /** Upper bound for resolving lyrics across all providers of a single song. */
        private const val OVERALL_TIMEOUT_MS = 12000L
    }
}

/** Time a negative cache (LYRICS_NOT_FOUND) is trusted before a fresh remote fetch is attempted. */
const val NEGATIVE_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Determines whether remote lyrics need fetching.
 *
 * Reuses found lyrics unless [forceRefresh] is true. Cached absence is checked against
 * [ttlMs] and the provider [signature].
 */
internal fun shouldFetchLyrics(
    entity: LyricsEntity?,
    signature: String,
    now: Long,
    forceRefresh: Boolean,
    ttlMs: Long = NEGATIVE_CACHE_TTL_MS,
): Boolean {
    if (forceRefresh) return true
    if (entity == null) return true
    if (entity.lyrics != LYRICS_NOT_FOUND) return false
    val lastChecked = entity.lastCheckedAt ?: return true
    val storedSignature = entity.providerSignature ?: return true
    if (storedSignature != signature) return true
    if (now < lastChecked) return true
    return now - lastChecked >= ttlMs
}

/** Lyrics candidate with its provider name. */
data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

/**
 * Outcome reported by a single provider during parallel resolution.
 */
private data class ProviderOutcome(
    val providerName: String,
    val result: LyricsFetchResult,
)

/**
 * Snapshot of the enabled providers taken once at the start of a fetch, together with a signature
 * derived from their stable ids (sorted, so provider order never affects it). The same snapshot drives
 * the search set, the all-NotFound decision and the stored signature, so a provider-configuration
 * change during a fetch cannot desync them.
 */
data class ProviderSelection(
    val providers: List<LyricsProvider>,
    val signature: String,
) {
    companion object {
        fun snapshot(context: Context, all: List<LyricsProvider>): ProviderSelection {
            val enabled = all.filter { it.isEnabled(context) }
            val signature = enabled.map { it.id }.sorted().joinToString(",")
            return ProviderSelection(enabled, signature)
        }
    }
}

/**
 * Aggregate outcome of resolving lyrics across every enabled provider for one song.
 */
sealed interface RemoteLyricsResult {
    data class Found(val provider: String, val raw: String, val synced: Boolean) : RemoteLyricsResult
    data object DefinitiveNotFound : RemoteLyricsResult
    data object Indeterminate : RemoteLyricsResult
    data object Skipped : RemoteLyricsResult
}

/** How a [LyricsFetchResult.Found] parses when judged for adoption. */
enum class FoundKind { SYNCED, UNSYNCED, UNPARSEABLE }

/**
 * Accumulates provider outcomes and derives the aggregate [RemoteLyricsResult]. The rules, independent
 * of the concurrency around it: the first synced Found wins; an unsynced Found is held as a fallback; a
 * DefinitiveNotFound is reported only when every enabled provider reported a definitive NotFound (a
 * Failed, an unparseable Found, or a provider that never reported all block it, yielding Indeterminate).
 *
 * @param enabledCount number of providers that were expected to report
 */
class RemoteLyricsAggregator(private val enabledCount: Int) {
    private var adoptedSynced: RemoteLyricsResult.Found? = null
    private var heldUnsynced: RemoteLyricsResult.Found? = null
    private var notFoundCount = 0
    private var nonNotFoundCount = 0

    /**
     * Fold one provider outcome into the running result.
     *
     * @return true once a synced result has been adopted, signalling that no further outcomes are needed.
     */
    fun offer(providerName: String, result: LyricsFetchResult, classifyFound: (String) -> FoundKind): Boolean {
        when (result) {
            is LyricsFetchResult.Found -> when (classifyFound(result.raw)) {
                FoundKind.SYNCED -> {
                    if (adoptedSynced == null) {
                        adoptedSynced = RemoteLyricsResult.Found(providerName, result.raw, synced = true)
                    }
                    return true
                }

                FoundKind.UNSYNCED -> if (heldUnsynced == null) {
                    heldUnsynced = RemoteLyricsResult.Found(providerName, result.raw, synced = false)
                }

                FoundKind.UNPARSEABLE -> nonNotFoundCount++ // not a usable result, but not an absence either
            }

            LyricsFetchResult.NotFound -> notFoundCount++
            is LyricsFetchResult.Failed -> nonNotFoundCount++
        }
        return false
    }

    fun result(): RemoteLyricsResult = when {
        adoptedSynced != null -> adoptedSynced as RemoteLyricsResult.Found
        heldUnsynced != null -> heldUnsynced as RemoteLyricsResult.Found
        nonNotFoundCount == 0 && notFoundCount == enabledCount -> RemoteLyricsResult.DefinitiveNotFound
        else -> RemoteLyricsResult.Indeterminate
    }
}

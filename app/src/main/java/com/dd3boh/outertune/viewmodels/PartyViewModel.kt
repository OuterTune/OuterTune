package com.dd3boh.outertune.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.playback.PlayerConnection
import com.dd3boh.outertune.playback.queues.ListQueue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ServerValue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import javax.inject.Inject
import java.util.UUID

/**
 * OPTIMAL PARTY VIEWMODEL
 * 
 * Implements the "load and ready" state machine with elastic sync
 * for smooth, error-free real-time synchronization.
 * 
 * Key Features:
 * - Authoritative state from web service
 * - Pre-buffering of current and next tracks
 * - Elastic speed adjustment for drift correction
 * - Clean separation: command path for input, state path for output
 */

data class PartyTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0,
    val duration: Long = 0, // Alias for durationMs
    val thumbnailUrl: String = "",
    val addedBy: String = "",
    val addedAt: Long = 0
)

data class PartyState(
    val code: String = "",
    val name: String = "",
    val hostId: String = "",
    val isPlaying: Boolean = false,
    val isPartyEnding: Boolean = false,
    val progressMs: Long = 0,
    val currentPositionMs: Long = 0, // Alias for progressMs
    val currentTrack: PartyTrack? = null,
    val nextTrack: PartyTrack? = null,
    val queue: List<PartyTrack> = emptyList(),
    val lastCommandId: String = "",
    val lastUpdated: Long = 0
)

sealed class PartyEvent {
    data class PartyCreated(val code: String) : PartyEvent()
    data class PartyJoined(val code: String) : PartyEvent()
    object PartyLeft : PartyEvent()
    object PartyEnded : PartyEvent()
    data class Error(val message: String) : PartyEvent()
}

@HiltViewModel
class PartyViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val database: FirebaseDatabase? by lazy {
        runCatching { FirebaseDatabase.getInstance() }.getOrNull()
    }
    private val auth: FirebaseAuth? by lazy {
        runCatching { FirebaseAuth.getInstance() }.getOrNull()
    }

    private val _partyState = MutableStateFlow<PartyState?>(null)
    val partyState: StateFlow<PartyState?> = _partyState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<PartyEvent?>(null)
    val events: StateFlow<PartyEvent?> = _events.asStateFlow()

    // Player connection
    private var playerConnection: PlayerConnection? = null
    private var playerEventListener: Player.Listener? = null

    // Buffering readiness flags
    private var isCurrentTrackReady = false
    private var isNextTrackReady = false

    // Last executed command ID (to detect new decrees vs heartbeats)
    private var lastExecutedCommandId: String? = null

    // Sync jobs
    private var stateSyncJob: Job? = null
    private var periodicSeekSyncJob: Job? = null

    // Party listener
    private var partyListener: ValueEventListener? = null
    private var currentPartyCode: String? = null
    // Track whether we've toggled MusicService into an isolated party session
    private var partySessionActive: Boolean = false

    // Rolling latency smoothing (last 3 samples of now - lastUpdated)
    private val latencySamplesMs: ArrayDeque<Long> = ArrayDeque(3)

    // Firebase server time offset (ms). serverNow = System.currentTimeMillis() + serverTimeOffsetMs
    private var serverTimeOffsetMs: Long = 0L
    private var serverTimeOffsetListener: ValueEventListener? = null

    // SEEK-BASED SYNC CONSTANTS (fixed 1s lag behind RTDB for all clients)
    private val IN_SYNC_TOLERANCE_MS = 80L      // small drift is tolerated to avoid jitter
    private val HARD_DRIFT_THRESHOLD_MS = 300L  // correct quicker when drift grows beyond this
    private val HARD_DRIFT_SUSTAIN_MS = 1000L   // require sustained drift > threshold for 1s
    private val MAX_SEEK_INTERVAL_MS = 3000L    // never correct more than once every 3s
    // Phase-lock: snap to the authoritative position at each server-time 1s boundary
    private val PHASE_LOCK_INTERVAL_MS = 1000L
    private val PHASE_LOCK_TOLERANCE_MS = 60L
    private val SPEED_NORMAL = 1.0f
    private val CLIENT_LAG_MS = 1000L          // fixed client lag
    private var lastSeekCorrectionAtMs: Long = 0L
    private var driftOverThresholdSinceMs: Long? = null
    private var lastPhaseLockBoundaryMs: Long = 0L

    /**
     * Compute the authoritative target position for now based on the server's lastUpdated
     * timestamp and progressMs, with an intentional client-side lag for stability.
     */
    private fun computeLaggedAuthoritativePosition(party: PartyState): Long {
        val base = party.progressMs.coerceAtLeast(0L)
        // Use Firebase server time offset to neutralize device clock skew
        val serverNow = System.currentTimeMillis() + serverTimeOffsetMs
        val ageSinceUpdate = if (party.lastUpdated > 0L && serverNow > party.lastUpdated) (serverNow - party.lastUpdated) else 0L
        // Project strictly based on server time elapsed since lastUpdated, avoiding per-client smoothing
        val advanced = if (party.isPlaying) base + ageSinceUpdate else base
        val lagged = (advanced - CLIENT_LAG_MS).coerceAtLeast(0L)
        val duration = party.currentTrack?.durationMs ?: 0L
        return if (duration > 0) lagged.coerceAtMost(duration) else lagged
    }

    /**
     * Whether playback should be audible now: requires RTDB to have advanced at least CLIENT_LAG_MS
     * since the last update so we maintain a fixed delay behind the master clock.
     */
    private fun shouldStartPlayback(party: PartyState): Boolean {
        val serverNow = System.currentTimeMillis() + serverTimeOffsetMs
        val age = if (party.lastUpdated > 0L && serverNow > party.lastUpdated) (serverNow - party.lastUpdated) else 0L
        return age >= CLIENT_LAG_MS
    }

    /** Return true if the player has at least minMs of buffered audio ahead of the current position. */
    private fun hasBufferedAheadMs(connection: PlayerConnection, minMs: Long): Boolean {
        return try {
            val player = connection.player
            val ahead = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
            ahead >= minMs
        } catch (_: Exception) { false }
    }

    /** Gate audible start: require 1s RTDB age and >=5s buffered ahead. */
    private fun canAudiblyPlay(party: PartyState, connection: PlayerConnection): Boolean {
        return shouldStartPlayback(party) && hasBufferedAheadMs(connection, 5_000L)
    }

    init {
        viewModelScope.launch {
            val a = auth
            val db = database
            if (a == null || db == null) {
                _events.value = PartyEvent.Error(
                    "OuterConnect requires Firebase. Add google-services.json to use this feature."
                )
                return@launch
            }

            // Ensure anonymous authentication
            if (a.currentUser == null) {
                runCatching { a.signInAnonymously().await() }
                    .onSuccess { Log.d("PartyVM", "Anonymous auth successful") }
                    .onFailure { e ->
                        Log.e("PartyVM", "Anonymous auth failed", e)
                        _events.value = PartyEvent.Error("Failed to authenticate: ${e.message}")
                    }
            }
        }
    }

    /**
     * Create a new party
     */
    fun createParty(partyName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val a = auth ?: run {
                    _events.value = PartyEvent.Error("Firebase not configured")
                    return@launch
                }
                val db = database ?: run {
                    _events.value = PartyEvent.Error("Firebase not configured")
                    return@launch
                }

                var user = a.currentUser
                if (user == null) {
                    val result = a.signInAnonymously().await()
                    user = result.user
                }

                if (user == null) {
                    _events.value = PartyEvent.Error("Authentication failed")
                    return@launch
                }

                val partyCode = generatePartyCode()
                val hostDisplayName = if (user.isAnonymous) {
                    "Host ${user.uid.takeLast(4)}"
                } else {
                    user.displayName ?: "Host ${user.uid.takeLast(4)}"
                }

                val partyRef = db.reference.child("parties").child(partyCode)

                // Initialize party structure
                val partyData = mapOf(
                    "code" to partyCode,
                    "name" to partyName,
                    "hostId" to user.uid,
                    "createdAt" to ServerValue.TIMESTAMP,
                    "state" to mapOf(
                        "isPlaying" to false,
                        "isPartyEnding" to false,
                        "progressMs" to 0,
                        "currentTrack" to null,
                        "nextTrack" to null,
                        "lastCommandId" to "",
                        "lastUpdated" to ServerValue.TIMESTAMP
                    ),
                    "queue" to emptyList<Any>(),
                    "command" to null
                )

                partyRef.setValue(partyData).await()

                // Join the party
                joinParty(partyCode)
                _events.value = PartyEvent.PartyCreated(partyCode)
                Log.d("PartyVM", "Party created: $partyCode")

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to create party", e)
                _events.value = PartyEvent.Error("Failed to create party: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Create a new party with authentication (alias for createParty)
     */
    fun createPartyWithAuth(partyName: String) {
        createParty(partyName)
    }

    /**
     * Join an existing party
     */
    fun joinParty(code: String) {
        viewModelScope.launch {
            try {
                val a = auth ?: run {
                    _events.value = PartyEvent.Error("Firebase not configured")
                    return@launch
                }
                val db = database ?: run {
                    _events.value = PartyEvent.Error("Firebase not configured")
                    return@launch
                }

                val user = a.currentUser ?: run {
                    a.signInAnonymously().await()
                    a.currentUser
                }

                if (user == null) {
                    _events.value = PartyEvent.Error("Authentication failed")
                    return@launch
                }

                // Check if party exists
                val partyRef = db.reference.child("parties").child(code)
                val snapshot = partyRef.get().await()

                if (!snapshot.exists()) {
                    _events.value = PartyEvent.Error("Party not found")
                    return@launch
                }

                // Start listening to party state
                startPartyListener(code)
                currentPartyCode = code
                // Start server time offset tracking to remove device clock skew
                startServerTimeOffsetListener()
                _isConnected.value = true
                _events.value = PartyEvent.PartyJoined(code)
                // Immediately switch MusicService into isolated party session. Host ID will be updated
                // on first state snapshot.
                playerConnection?.service?.setPartySession(
                    active = true,
                    hostId = null,
                    currentUserId = a.currentUser?.uid
                )
                partySessionActive = true
                
                Log.d("PartyVM", "Joined party: $code")

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to join party", e)
                _events.value = PartyEvent.Error("Failed to join party: ${e.message}")
            }
        }
    }

    /**
     * Leave the current party
     */
    fun leaveParty() {
        viewModelScope.launch {
            try {
                val party = _partyState.value
                val partyCode = currentPartyCode

                if (party != null && partyCode != null) {
                    if (isHost()) {
                        // Host: issue final decree and wait for executor (web service) to dismantle
                        issueCommand("end_party")
                        // Do not tear down local listeners/session here; wait for RTDB removal
                        return@launch
                    } else {
                        _events.value = PartyEvent.PartyLeft
                    }
                }

                // Clean up
                stopPartyListener()
                stopServerTimeOffsetListener()
                stopMusicServiceSync()
                currentPartyCode = null
                _partyState.value = null
                _isConnected.value = false
                lastExecutedCommandId = null
                isCurrentTrackReady = false
                isNextTrackReady = false
                // Disable isolated party session in MusicService and restore user playback
                if (partySessionActive) {
                    try {
                        playerConnection?.service?.setPartySession(
                            active = false,
                            hostId = null,
                            currentUserId = auth?.currentUser?.uid
                        )
                    } catch (_: Exception) {}
                    partySessionActive = false
                }

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to leave party", e)
                _events.value = PartyEvent.Error("Failed to leave party: ${e.message}")
            }
        }
    }

    /**
     * Add a track to the party queue
     */
    fun addTrackToQueue(metadata: MediaMetadata) {
        viewModelScope.launch {
            try {
                val a = auth ?: return@launch
                val db = database ?: return@launch
                val user = a.currentUser
                val partyCode = currentPartyCode

                if (user == null || partyCode == null) return@launch

                val track = mapOf(
                    "id" to metadata.id,
                    "title" to metadata.title,
                    "artist" to metadata.artists.joinToString(", ") { it.name },
                    "durationMs" to (metadata.duration ?: 0).toLong(),
                    "thumbnailUrl" to (metadata.thumbnailUrl ?: "")
                )

                val queueRef = db.reference.child("parties").child(partyCode).child("queue")
                queueRef.push().setValue(track).await()

                Log.d("PartyVM", "Added track: ${metadata.title}")

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to add track", e)
                _events.value = PartyEvent.Error("Failed to add track: ${e.message}")
            }
        }
    }

    /**
     * Update playback state (host only)
     */
    fun updatePlaybackState(isPlaying: Boolean, position: Long = 0) {
        viewModelScope.launch {
            try {
                val party = _partyState.value
                val partyCode = currentPartyCode

                if (party == null || partyCode == null) return@launch

                // Only host can control playback
                if (!isHost()) {
                    _events.value = PartyEvent.Error("Only host can control playback")
                    return@launch
                }

                // If trying to play but readiness is uncertain, still issue the command.
                // The player will catch up via elastic/hard sync.

                // Issue command
                issueCommand(if (isPlaying) "play" else "pause", position)

                // Host preemptive local alignment:
                // Immediately align the local player to the expected lagged target so the host
                // never perceives being ahead while we wait for RTDB to propagate.
                try {
                    val conn = playerConnection ?: return@launch
                    val state = _partyState.value ?: return@launch

                    // Project the base progress for this command
                    val serverNow = System.currentTimeMillis() + serverTimeOffsetMs
                    val age = if (state.lastUpdated > 0L && serverNow > state.lastUpdated) (serverNow - state.lastUpdated) else 0L
                    val baseProgress = when {
                        position > 0 -> position
                        isPlaying -> (state.progressMs + age)
                        else -> state.progressMs
                    }
                    val duration = state.currentTrack?.durationMs ?: 0L
                    val desired = (baseProgress - CLIENT_LAG_MS)
                        .coerceAtLeast(0L)
                        .let { if (duration > 0L) it.coerceAtMost(duration) else it }

                    conn.player.seekTo(desired)
                    conn.player.playbackParameters = PlaybackParameters(SPEED_NORMAL)
                    // Do not force immediate play; decree/heartbeat will start after 1s/5s gate
                    if (!isPlaying) conn.player.pause()
                } catch (_: Exception) { /* best-effort; RTDB will confirm */ }

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to update playback", e)
                _events.value = PartyEvent.Error("Failed to update playback: ${e.message}")
            }
        }
    }

    /**
     * Skip to next track (host only)
     */
    fun nextTrack() {
        viewModelScope.launch {
            try {
                if (!isHost()) {
                    _events.value = PartyEvent.Error("Only host can control playback")
                    return@launch
                }

                issueCommand("next")

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to skip track", e)
                _events.value = PartyEvent.Error("Failed to skip track: ${e.message}")
            }
        }
    }

    /**
     * Skip to previous track (host only)
     */
    fun prevTrack() {
        viewModelScope.launch {
            try {
                if (!isHost()) {
                    _events.value = PartyEvent.Error("Only host can control playback")
                    return@launch
                }

                // Service does not support 'prev'; seek to start of current track instead
                issueCommand("seek", position = 0)

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to skip to previous track", e)
                _events.value = PartyEvent.Error("Failed to skip to previous track: ${e.message}")
            }
        }
    }

    /**
     * Add multiple tracks to queue and optionally start playing
     */
    fun addTracksAndMaybePlay(tracks: List<MediaMetadata>) {
        viewModelScope.launch {
            try {
                val a = auth ?: return@launch
                val db = database ?: return@launch
                val user = a.currentUser
                val partyCode = currentPartyCode

                if (user == null || partyCode == null) return@launch

                val userId = user.displayName ?: "User ${user.uid.takeLast(4)}"
                
                tracks.forEach { metadata ->
                    val track = mapOf(
                        "id" to metadata.id,
                        "title" to metadata.title,
                        "artist" to metadata.artists.joinToString(", ") { it.name },
                        "durationMs" to (metadata.duration ?: 0).toLong(),
                        "thumbnailUrl" to (metadata.thumbnailUrl ?: ""),
                        "addedBy" to userId,
                        "addedAt" to ServerValue.TIMESTAMP
                    )

                    val queueRef = db.reference.child("parties").child(partyCode).child("queue")
                    queueRef.push().setValue(track).await()
                }

                Log.d("PartyVM", "Added ${tracks.size} tracks")

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to add tracks", e)
                _events.value = PartyEvent.Error("Failed to add tracks: ${e.message}")
            }
        }
    }

    /**
     * Add multiple tracks to queue
     */
    fun addTracksToQueue(tracks: List<MediaMetadata>) {
        addTracksAndMaybePlay(tracks)
    }

    /**
     * Remove a track at a specific index (host only)
     */
    fun removeTrackAt(index: Int) {
        viewModelScope.launch {
            try {
                if (!isHost()) {
                    _events.value = PartyEvent.Error("Only host can modify queue")
                    return@launch
                }

                val db = database ?: return@launch
                val partyCode = currentPartyCode ?: return@launch
                val state = _partyState.value ?: return@launch

                if (index < 0 || index >= state.queue.size) return@launch

                // Get the Firebase key for this index
                val queueRef = db.reference.child("parties").child(partyCode).child("queue")
                val snapshot = queueRef.get().await()
                val keys = snapshot.children.mapNotNull { it.key }
                
                if (index < keys.size) {
                    queueRef.child(keys[index]).removeValue().await()
                    Log.d("PartyVM", "Removed track at index $index")
                }

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to remove track", e)
                _events.value = PartyEvent.Error("Failed to remove track: ${e.message}")
            }
        }
    }

    /**
     * Move a track from one position to another (host only)
     */
    fun moveTrack(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            try {
                if (!isHost()) {
                    _events.value = PartyEvent.Error("Only host can modify queue")
                    return@launch
                }

                val db = database ?: return@launch
                val partyCode = currentPartyCode ?: return@launch
                val state = _partyState.value ?: return@launch

                if (fromIndex < 0 || fromIndex >= state.queue.size || toIndex < 0 || toIndex >= state.queue.size) {
                    return@launch
                }

                // Get all queue items
                val queueRef = db.reference.child("parties").child(partyCode).child("queue")
                val snapshot = queueRef.get().await()
                val items = snapshot.children.map { it to it.value }.toList()

                if (fromIndex >= items.size || toIndex >= items.size) return@launch

                // Reorder locally
                val mutableItems = items.toMutableList()
                val item = mutableItems.removeAt(fromIndex)
                mutableItems.add(toIndex, item)

                // Update Firebase (replace entire queue)
                queueRef.removeValue().await()
                mutableItems.forEach { (_, value) ->
                    queueRef.push().setValue(value).await()
                }

                Log.d("PartyVM", "Moved track from $fromIndex to $toIndex")

            } catch (e: Exception) {
                Log.e("PartyVM", "Failed to move track", e)
                _events.value = PartyEvent.Error("Failed to move track: ${e.message}")
            }
        }
    }

    /**
     * Set player connection and start sync
     */
    fun setPlayerConnection(connection: PlayerConnection?) {
        stopMusicServiceSync()
        playerConnection = connection

        if (connection != null) {
            // Add listener to detect when tracks are ready
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    evaluateReadiness()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    evaluateReadiness()
                }
            }
            connection.player.addListener(listener)
            playerEventListener = listener

            startMusicServiceSync()
        }
    }

    /**
     * Start syncing party state to player
     */
    private fun startMusicServiceSync() {
        val connection = playerConnection ?: return

        // State sync job - reacts to state changes
        stateSyncJob = viewModelScope.launch {
            _partyState.collect { party ->
                if (party != null) {
                    try {
                        syncPartyStateToPlayer(party, connection)
                        // Provide server-projected master progress to MusicService for prefetching
                        try {
                            val serverNow = System.currentTimeMillis() + serverTimeOffsetMs
                            val age = if (party.lastUpdated > 0L && serverNow > party.lastUpdated) (serverNow - party.lastUpdated) else 0L
                            val masterProgressNow = if (party.isPlaying) (party.progressMs + age) else party.progressMs
                            val durationMs = party.currentTrack?.durationMs ?: 0L
                            connection.service.setPartyPrefetchHint(masterProgressNow, party.isPlaying, durationMs)
                        } catch (_: Exception) { }
                    } catch (e: Exception) {
                        Log.e("PartyVM", "Error syncing state", e)
                    }
                }
            }
        }

        // Periodic seek-based sync loop
        periodicSeekSyncJob = viewModelScope.launch {
            while (true) {
                try {
                    val party = _partyState.value
                    if (party != null) {
                        applySeekSync(party, connection)
                    }
                } catch (e: Exception) {
                    Log.e("PartyVM", "Error in seek sync", e)
                } finally {
                    // Align checks with server 1s boundaries to keep all devices snapping together
                    val serverNow = System.currentTimeMillis() + serverTimeOffsetMs
                    val untilNext = PHASE_LOCK_INTERVAL_MS - (serverNow % PHASE_LOCK_INTERVAL_MS)
                    // Keep a minimum cadence in case of timing jitter
                    val sleep = untilNext.coerceIn(50L, 250L)
                    delay(sleep)
                }
            }
        }
    }

    /**
     * Sync party state to the music player
     */
    private suspend fun syncPartyStateToPlayer(party: PartyState, connection: PlayerConnection) {
        val incomingCommandId = party.lastCommandId
        val isNewDecree = incomingCommandId.isNotEmpty() && incomingCommandId != lastExecutedCommandId

        if (isNewDecree) {
            // NEW DECREE - Load and Ready
            lastExecutedCommandId = incomingCommandId
            isCurrentTrackReady = false
            isNextTrackReady = false
            // Nothing else; decree seek below will align us to buffered target

            Log.d("PartyVM", "New decree detected: $incomingCommandId")

            // Update queue
            updatePlayerQueue(party, connection)

            // Seek FIRST to desired lagged authoritative position
            try {
                val desired = computeLaggedAuthoritativePosition(party)
                connection.player.seekTo(desired)
            } catch (_: Exception) { }

            // Keep normal speed (seek-based sync)
            connection.player.playbackParameters = PlaybackParameters(SPEED_NORMAL)

            // Align play/pause for all clients: wait for 1s RTDB age and >=5s buffer
            if (party.isPlaying && canAudiblyPlay(party, connection)) connection.player.play() else connection.player.pause()

            return
        }

        // HEARTBEAT - Don't re-gate; only reflect play/pause state changes to avoid jitter
        if (party.isPlaying) {
            if (!connection.player.playWhenReady) connection.player.play()
        } else if (connection.player.playWhenReady) {
            connection.player.pause()
        }
    }

    /**
     * Apply seek-based sync: seek to the lagged authoritative position when drift exceeds a small tolerance.
     * Speed stays at 1.0x. Seeks are rate-limited.
     */
    private fun applySeekSync(party: PartyState, connection: PlayerConnection) {
        val player = connection.player
        // Keep speed at normal at all times
        if (player.playbackParameters.speed != SPEED_NORMAL) {
            player.playbackParameters = PlaybackParameters(SPEED_NORMAL)
        }

        // Only correct when player is ready
        if (player.playbackState != Player.STATE_READY) return

        val authoritativePos = computeLaggedAuthoritativePosition(party)
        val localPos = player.currentPosition
        val drift = authoritativePos - localPos

        val absDrift = kotlin.math.abs(drift)
        val nowLocal = System.currentTimeMillis()

        // 1) Phase-lock snap: once per server-second boundary, snap within a tight tolerance
        val serverNow = nowLocal + serverTimeOffsetMs
        val boundary = (serverNow / PHASE_LOCK_INTERVAL_MS) * PHASE_LOCK_INTERVAL_MS
        if (boundary > lastPhaseLockBoundaryMs && party.isPlaying) {
            lastPhaseLockBoundaryMs = boundary
            if (absDrift > PHASE_LOCK_TOLERANCE_MS) {
                try {
                    player.seekTo(authoritativePos)
                    lastSeekCorrectionAtMs = nowLocal
                    driftOverThresholdSinceMs = null
                    Log.d("PartyVM", "Phase-lock seek: drift=${drift}ms -> ${authoritativePos}")
                } catch (_: Exception) { }
                return
            } else {
                // Within tight tolerance; consider in-sync
                driftOverThresholdSinceMs = null
                return
            }
        }

        // 2) Hard drift fallback outside boundary: correct only when sustained and rate-limited
        if (absDrift <= IN_SYNC_TOLERANCE_MS) {
            driftOverThresholdSinceMs = null
            return
        }
        if (absDrift >= HARD_DRIFT_THRESHOLD_MS) {
            val startedAt = driftOverThresholdSinceMs ?: nowLocal.also { driftOverThresholdSinceMs = it }
            val sustained = nowLocal - startedAt >= HARD_DRIFT_SUSTAIN_MS
            val tooSoon = nowLocal - lastSeekCorrectionAtMs < MAX_SEEK_INTERVAL_MS
            if (sustained && !tooSoon) {
                try {
                    player.seekTo(authoritativePos)
                    lastSeekCorrectionAtMs = nowLocal
                    driftOverThresholdSinceMs = null
                    Log.d("PartyVM", "Seek sync (hard fallback): drift=${drift}ms, seekTo=${authoritativePos}")
                } catch (_: Exception) { }
            }
            return
        }
        // For mid-range drift, defer to the next phase-lock boundary to avoid jitter.
    }


    /**
     * Update player queue based on party state
     */
    private suspend fun updatePlayerQueue(party: PartyState, connection: PlayerConnection) {
        try {
            isCurrentTrackReady = false

            val items = buildList {
                // Add current track
                party.currentTrack?.let { track ->
                    add(
                        MediaMetadata(
                            id = track.id,
                            title = track.title,
                            artists = listOf(MediaMetadata.Artist(id = "", name = track.artist)),
                            duration = track.durationMs.toInt(),
                            thumbnailUrl = track.thumbnailUrl.takeIf { it.isNotEmpty() },
                            genre = null
                        )
                    )
                }

                // Add next track
                party.nextTrack?.let { track ->
                    add(
                        MediaMetadata(
                            id = track.id,
                            title = track.title,
                            artists = listOf(MediaMetadata.Artist(id = "", name = track.artist)),
                            duration = track.durationMs.toInt(),
                            thumbnailUrl = track.thumbnailUrl.takeIf { it.isNotEmpty() },
                            genre = null
                        )
                    )
                }

                // Add remaining queue
                addAll(
                    party.queue.map { track ->
                        MediaMetadata(
                            id = track.id,
                            title = track.title,
                            artists = listOf(MediaMetadata.Artist(id = "", name = track.artist)),
                            duration = track.durationMs.toInt(),
                            thumbnailUrl = track.thumbnailUrl.takeIf { it.isNotEmpty() },
                            genre = null
                        )
                    }
                )
            }

            val listQueue = ListQueue(
                title = "Party Queue",
                items = items,
                startIndex = 0,
                position = computeLaggedAuthoritativePosition(party)
            )

            connection.service.playQueue(
                listQueue,
                // Let syncing logic decide when to start playback (1s/5s gate)
                playWhenReady = false,
                shouldResume = false,
                replace = true,
                isRadio = false,
                title = "Party Queue",
                allowNonHostForSync = true
            )

            Log.d("PartyVM", "Queue updated with ${items.size} tracks")

        } catch (e: Exception) {
            Log.e("PartyVM", "Failed to update queue", e)
        }
    }

    /**
     * Evaluate if current and next tracks are ready
     */
    private fun evaluateReadiness() {
        val connection = playerConnection ?: return
        val party = _partyState.value ?: return

        val playerState = connection.player.playbackState
        val currentMediaId = connection.player.currentMediaItem?.mediaId
        val expectedCurrentId = party.currentTrack?.id

        // Check if current track is ready
        if (playerState == Player.STATE_READY && currentMediaId == expectedCurrentId) {
            if (!isCurrentTrackReady) {
                isCurrentTrackReady = true
                Log.d("PartyVM", "Current track ready: $currentMediaId")
            }
        } else {
            isCurrentTrackReady = false
        }

        // Check if next track is pre-buffered
        val windows = connection.queueWindows.value
        val nextMediaId = if (windows.size >= 2) windows[1].mediaItem.mediaId else null
        val expectedNextId = party.nextTrack?.id

        isNextTrackReady = nextMediaId == expectedNextId && playerState == Player.STATE_READY
    }

    /**
     * Stop music service sync
     */
    private fun stopMusicServiceSync() {
        stateSyncJob?.cancel()
        periodicSeekSyncJob?.cancel()
        stateSyncJob = null
        periodicSeekSyncJob = null

        playerConnection?.let { conn ->
            playerEventListener?.let { conn.player.removeListener(it) }
            playerEventListener = null
        }
    }

    /**
     * Start listening to party state changes
     */
    private fun startPartyListener(partyCode: String) {
        val db = database ?: return

        // Listen to the party ROOT so we can read name/hostId and nested state/queue
        val partyRef = db.reference.child("parties").child(partyCode)

        partyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.exists()) {
                        val state = snapshot.toPartyStateFromRoot(partyCode)
                        _partyState.value = state
                        // Record latency sample for smoothing
                        recordLatencySample(state)
                        // Keep MusicService informed of the current host/user so it can enforce
                        // isolation and host-only controls internally
                        if (!partySessionActive) {
                            try {
                                playerConnection?.service?.setPartySession(
                                    active = true,
                                    hostId = state.hostId,
                                    currentUserId = auth?.currentUser?.uid
                                )
                                partySessionActive = true
                            } catch (_: Exception) {}
                        } else {
                            try {
                                playerConnection?.service?.setPartySession(
                                    active = true,
                                    hostId = state.hostId,
                                    currentUserId = auth?.currentUser?.uid
                                )
                            } catch (_: Exception) {}
                        }
                        Log.d("PartyVM", "State updated: playing=${state.isPlaying}, pos=${state.progressMs}, host=${state.hostId}, name=${state.name}")
                    } else {
                        _partyState.value = null
                        _events.value = PartyEvent.PartyEnded
                        stopPartyListener()
                        // Party node removed – exit isolated session
                        if (partySessionActive) {
                            try {
                                playerConnection?.service?.setPartySession(
                                    active = false,
                                    hostId = null,
                                    currentUserId = auth?.currentUser?.uid
                                )
                            } catch (_: Exception) {}
                            partySessionActive = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PartyVM", "Error parsing state", e)
                    _events.value = PartyEvent.Error("Failed to update state")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PartyVM", "State listener cancelled", error.toException())
                _events.value = PartyEvent.Error("Connection lost: ${error.message}")
                _isConnected.value = false
            }
        }

        partyRef.addValueEventListener(partyListener!!)
    }

    private fun recordLatencySample(state: PartyState) {
        val serverNow = System.currentTimeMillis() + serverTimeOffsetMs
        val sample = if (state.lastUpdated > 0L && serverNow > state.lastUpdated) (serverNow - state.lastUpdated) else 0L
        synchronized(latencySamplesMs) {
            if (latencySamplesMs.size >= 3) latencySamplesMs.removeFirst()
            latencySamplesMs.addLast(sample)
        }
    }

    private fun startServerTimeOffsetListener() {
        val db = database ?: return
        // Avoid duplicate listener
        if (serverTimeOffsetListener != null) return
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val offset = snapshot.getValue(Long::class.java) ?: 0L
                    serverTimeOffsetMs = offset
                } catch (_: Exception) {}
            }
            override fun onCancelled(error: DatabaseError) {
                // no-op
            }
        }
        db.reference.child(".info/serverTimeOffset").addValueEventListener(listener)
        serverTimeOffsetListener = listener
    }

    private fun stopServerTimeOffsetListener() {
        val db = database ?: return
        serverTimeOffsetListener?.let { listener ->
            db.reference.child(".info/serverTimeOffset").removeEventListener(listener)
        }
        serverTimeOffsetListener = null
    }

    /**
     * Stop party listener
     */
    private fun stopPartyListener() {
        partyListener?.let { listener ->
            currentPartyCode?.let { code ->
                database?.reference?.child("parties")?.child(code)
                    ?.removeEventListener(listener)
            }
        }
        partyListener = null
    }

    /**
     * Issue a command to the web service (host only)
     */
    private suspend fun issueCommand(action: String, position: Long? = null) {
        val db = database ?: return
        val code = currentPartyCode ?: return

        val cmd = mutableMapOf<String, Any>(
            "action" to action,
            "commandId" to UUID.randomUUID().toString()
        )
        position?.let { cmd["position"] = it }

        db.reference.child("parties").child(code).child("command").setValue(cmd).await()
        Log.d("PartyVM", "Command issued: $action")
    }

    /**
     * Check if current user is host
     */
    private fun isHost(): Boolean {
        val party = _partyState.value ?: return false
        return party.hostId == auth?.currentUser?.uid
    }

    /**
     * Generate a random party code
     */
    private fun generatePartyCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun clearEvents() {
        _events.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPartyListener()
        stopMusicServiceSync()
    }
}

// Extension function for parsing state from RTDB
private fun DataSnapshot.toPartyState(partyCode: String): PartyState {
    val currentTrackSnap = child("currentTrack")
    val currentTrack = if (currentTrackSnap.exists()) {
        PartyTrack(
            id = currentTrackSnap.child("id").getValue(String::class.java) ?: "",
            title = currentTrackSnap.child("title").getValue(String::class.java) ?: "",
            artist = currentTrackSnap.child("artist").getValue(String::class.java) ?: "",
            durationMs = currentTrackSnap.child("durationMs").getValue(Long::class.java) ?: 0L,
            duration = currentTrackSnap.child("durationMs").getValue(Long::class.java) ?: 0L,
            thumbnailUrl = currentTrackSnap.child("thumbnailUrl").getValue(String::class.java) ?: "",
            addedBy = currentTrackSnap.child("addedBy").getValue(String::class.java) ?: "",
            addedAt = currentTrackSnap.child("addedAt").getValue(Long::class.java) ?: 0L
        )
    } else null

    val nextTrackSnap = child("nextTrack")
    val nextTrack = if (nextTrackSnap.exists()) {
        PartyTrack(
            id = nextTrackSnap.child("id").getValue(String::class.java) ?: "",
            title = nextTrackSnap.child("title").getValue(String::class.java) ?: "",
            artist = nextTrackSnap.child("artist").getValue(String::class.java) ?: "",
            durationMs = nextTrackSnap.child("durationMs").getValue(Long::class.java) ?: 0L,
            duration = nextTrackSnap.child("durationMs").getValue(Long::class.java) ?: 0L,
            thumbnailUrl = nextTrackSnap.child("thumbnailUrl").getValue(String::class.java) ?: "",
            addedBy = nextTrackSnap.child("addedBy").getValue(String::class.java) ?: "",
            addedAt = nextTrackSnap.child("addedAt").getValue(Long::class.java) ?: 0L
        )
    } else null

    val progressValue = child("progressMs").getValue(Long::class.java) ?: 0L

    return PartyState(
        code = partyCode,
        name = "", // Load from parent if needed
        hostId = "", // Load from parent if needed
        isPlaying = child("isPlaying").getValue(Boolean::class.java) ?: false,
        isPartyEnding = child("isPartyEnding").getValue(Boolean::class.java) ?: false,
        progressMs = progressValue,
        currentPositionMs = progressValue,
        currentTrack = currentTrack,
        nextTrack = nextTrack,
        queue = emptyList(), // Load from queue path if needed
        lastCommandId = child("lastCommandId").getValue(String::class.java) ?: "",
        lastUpdated = child("lastUpdated").getValue(Long::class.java) ?: 0L
    )
}

// Parse from party ROOT node: expects children 'name', 'hostId', 'state', and 'queue'
private fun DataSnapshot.toPartyStateFromRoot(partyCode: String): PartyState {
    val name = child("name").getValue(String::class.java) ?: ""
    val hostId = child("hostId").getValue(String::class.java) ?: ""

    val stateSnap = child("state")

    val currentTrackSnap = stateSnap.child("currentTrack")
    val currentTrack = if (currentTrackSnap.exists()) {
        PartyTrack(
            id = currentTrackSnap.child("id").getValue(String::class.java) ?: "",
            title = currentTrackSnap.child("title").getValue(String::class.java) ?: "",
            artist = currentTrackSnap.child("artist").getValue(String::class.java) ?: "",
            durationMs = currentTrackSnap.child("durationMs").getValue(Long::class.java) ?: 0L,
            duration = currentTrackSnap.child("durationMs").getValue(Long::class.java) ?: 0L,
            thumbnailUrl = currentTrackSnap.child("thumbnailUrl").getValue(String::class.java) ?: "",
            addedBy = currentTrackSnap.child("addedBy").getValue(String::class.java) ?: "",
            addedAt = currentTrackSnap.child("addedAt").getValue(Long::class.java) ?: 0L
        )
    } else null

    val nextTrackSnap = stateSnap.child("nextTrack")
    val nextTrack = if (nextTrackSnap.exists()) {
        PartyTrack(
            id = nextTrackSnap.child("id").getValue(String::class.java) ?: "",
            title = nextTrackSnap.child("title").getValue(String::class.java) ?: "",
            artist = nextTrackSnap.child("artist").getValue(String::class.java) ?: "",
            durationMs = nextTrackSnap.child("durationMs").getValue(Long::class.java) ?: 0L,
            duration = nextTrackSnap.child("durationMs").getValue(Long::class.java) ?: 0L,
            thumbnailUrl = nextTrackSnap.child("thumbnailUrl").getValue(String::class.java) ?: "",
            addedBy = nextTrackSnap.child("addedBy").getValue(String::class.java) ?: "",
            addedAt = nextTrackSnap.child("addedAt").getValue(Long::class.java) ?: 0L
        )
    } else null

    val progressValue = stateSnap.child("progressMs").getValue(Long::class.java) ?: 0L

    // Parse queue list (pushId -> trackObject)
    val queueSnap = child("queue")
    val queue = if (queueSnap.exists()) {
        queueSnap.children.mapNotNull { item ->
            val id = item.child("id").getValue(String::class.java) ?: return@mapNotNull null
            PartyTrack(
                id = id,
                title = item.child("title").getValue(String::class.java) ?: "",
                artist = item.child("artist").getValue(String::class.java) ?: "",
                durationMs = item.child("durationMs").getValue(Long::class.java) ?: 0L,
                duration = item.child("durationMs").getValue(Long::class.java) ?: 0L,
                thumbnailUrl = item.child("thumbnailUrl").getValue(String::class.java) ?: "",
                addedBy = item.child("addedBy").getValue(String::class.java) ?: "",
                addedAt = item.child("addedAt").getValue(Long::class.java) ?: 0L
            )
        }
    } else emptyList()

    return PartyState(
        code = partyCode,
        name = name,
        hostId = hostId,
        isPlaying = stateSnap.child("isPlaying").getValue(Boolean::class.java) ?: false,
        isPartyEnding = stateSnap.child("isPartyEnding").getValue(Boolean::class.java) ?: false,
        progressMs = progressValue,
        currentPositionMs = progressValue,
        currentTrack = currentTrack,
        nextTrack = nextTrack,
        queue = queue,
        lastCommandId = stateSnap.child("lastCommandId").getValue(String::class.java) ?: "",
        lastUpdated = stateSnap.child("lastUpdated").getValue(Long::class.java) ?: 0L
    )
}


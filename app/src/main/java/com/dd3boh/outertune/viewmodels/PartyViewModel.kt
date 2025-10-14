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
import com.dd3boh.outertune.extensions.isUserLoggedIn

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
    private var elasticSyncJob: Job? = null

    // Party listener
    private var partyListener: ValueEventListener? = null
    private var currentPartyCode: String? = null

    // DRIFT CORRECTION CONSTANTS
    private val DRIFT_THRESHOLD_MS = 250L // When to apply speed adjustment
    private val DRIFT_IN_SYNC_MS = 100L // When to return to normal speed
    private val SPEED_FAST = 1.05f // Speed up to catch up
    private val SPEED_SLOW = 0.95f // Slow down to fall back
    private val SPEED_NORMAL = 1.0f

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
                _isConnected.value = true
                _events.value = PartyEvent.PartyJoined(code)
                
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
                        // Host leaving - delete entire party
                        database?.reference?.child("parties")?.child(partyCode)?.removeValue()?.await()
                        _events.value = PartyEvent.PartyEnded
                    } else {
                        _events.value = PartyEvent.PartyLeft
                    }
                }

                // Clean up
                stopPartyListener()
                stopMusicServiceSync()
                currentPartyCode = null
                _partyState.value = null
                _isConnected.value = false
                lastExecutedCommandId = null
                isCurrentTrackReady = false
                isNextTrackReady = false

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
                    } catch (e: Exception) {
                        Log.e("PartyVM", "Error syncing state", e)
                    }
                }
            }
        }

        // Elastic sync job - drift correction loop
        elasticSyncJob = viewModelScope.launch {
            while (true) {
                try {
                    val party = _partyState.value
                    if (party != null && party.isPlaying) {
                        applyElasticSync(party, connection)
                    }
                } catch (e: Exception) {
                    Log.e("PartyVM", "Error in elastic sync", e)
                } finally {
                    delay(300) // Check every 300ms
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

            Log.d("PartyVM", "New decree detected: $incomingCommandId")

            // Update queue
            updatePlayerQueue(party, connection)

            // Align play/pause state
            if (party.isPlaying) {
                connection.player.play()
            } else {
                connection.player.pause()
            }

            // Ensure position aligns to authoritative state on decree
            try {
                connection.player.seekTo(party.progressMs)
            } catch (_: Exception) { }

            // Ensure normal speed
            connection.player.playbackParameters = PlaybackParameters(SPEED_NORMAL)

            return
        }

        // HEARTBEAT - Elastic drift correction applied separately in elasticSyncJob
        // Just ensure play/pause state is aligned
        if (party.isPlaying && !connection.player.playWhenReady) {
            connection.player.play()
        }
        if (!party.isPlaying && connection.player.playWhenReady) {
            connection.player.pause()
        }
    }

    /**
     * Apply elastic sync - smooth drift correction using speed adjustment
     */
    private fun applyElasticSync(party: PartyState, connection: PlayerConnection) {
        if (!party.isPlaying) return

        val authoritativePos = party.progressMs
        val localPos = connection.player.currentPosition
        val drift = authoritativePos - localPos

        // Hard-correct if drift is very large (e.g., join mid-playback or network hiccup)
        val LARGE_DRIFT_MS = 2000L
        if (kotlin.math.abs(drift) >= LARGE_DRIFT_MS) {
            try {
                connection.player.seekTo(authoritativePos)
                connection.player.playbackParameters = PlaybackParameters(SPEED_NORMAL)
                Log.d("PartyVM", "Hard sync: seek to ${authoritativePos}ms (drift ${drift}ms)")
            } catch (e: Exception) {
                Log.w("PartyVM", "Hard sync seek failed: ${e.message}")
            }
            return
        }

        val targetSpeed = when {
            drift > DRIFT_THRESHOLD_MS -> SPEED_FAST // Behind, speed up
            drift < -DRIFT_THRESHOLD_MS -> SPEED_SLOW // Ahead, slow down
            kotlin.math.abs(drift) < DRIFT_IN_SYNC_MS -> SPEED_NORMAL // In sync
            else -> connection.player.playbackParameters.speed // Keep current adjustment
        }

        if (connection.player.playbackParameters.speed != targetSpeed) {
            connection.player.playbackParameters = PlaybackParameters(targetSpeed)
            Log.d("PartyVM", "Drift: ${drift}ms, Speed: $targetSpeed")
        }
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
                position = party.progressMs
            )

            connection.service.playQueue(
                listQueue,
                playWhenReady = party.isPlaying,
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
        elasticSyncJob?.cancel()
        stateSyncJob = null
        elasticSyncJob = null

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
                        Log.d("PartyVM", "State updated: playing=${state.isPlaying}, pos=${state.progressMs}, host=${state.hostId}, name=${state.name}")
                    } else {
                        _partyState.value = null
                        _events.value = PartyEvent.PartyEnded
                        stopPartyListener()
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
        progressMs = progressValue,
        currentPositionMs = progressValue,
        currentTrack = currentTrack,
        nextTrack = nextTrack,
        queue = queue,
        lastCommandId = stateSnap.child("lastCommandId").getValue(String::class.java) ?: "",
        lastUpdated = stateSnap.child("lastUpdated").getValue(Long::class.java) ?: 0L
    )
}


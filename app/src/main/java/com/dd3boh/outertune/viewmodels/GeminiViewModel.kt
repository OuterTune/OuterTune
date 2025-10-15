package com.dd3boh.outertune.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import com.dd3boh.outertune.constants.GeminiApiKey
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.PlaylistSongMap
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.playback.MusicService
import com.dd3boh.outertune.playback.queues.ListQueue
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import javax.inject.Inject

data class GeneratedTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val year: Int? = null,
    val genre: String = "",
    val confidence: Float = 0.0f,
    // Optional hints for direct mapping
    val youtubeId: String? = null,
    val youtubeUrl: String? = null,
    val durationSec: Int? = null
)

data class GeneratedQueue(
    val tracks: List<GeneratedTrack>,
    val description: String,
    val totalCount: Int
)

data class GeneratedPlaylist(
    val name: String,
    val description: String,
    val tracks: List<GeneratedTrack>,
    val totalCount: Int,
    val syncWithYouTube: Boolean
)

sealed class GeminiState {
    object Idle : GeminiState()
    object Loading : GeminiState()
    data class QueueGenerated(val queue: GeneratedQueue) : GeminiState()
    data class PlaylistGenerated(
        val playlist: GeneratedPlaylist,
        val localPlaylistId: String?,
        val youtubePlaylistId: String?
    ) : GeminiState()
    data class Error(val message: String) : GeminiState()
}

@HiltViewModel
class GeminiViewModel @Inject constructor(
    private val database: MusicDatabase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _state = MutableStateFlow<GeminiState>(GeminiState.Idle)
    val state: StateFlow<GeminiState> = _state.asStateFlow()
    
    /**
     * Make HTTP request to Gemini API using REST API instead of SDK
     * Simple text request (legacy). Prefer JSON mode helpers below for new features.
     */
    private suspend fun makeGeminiApiRequest(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            val key = context.dataStore.data.firstOrNull()?.get(GeminiApiKey).orEmpty()
            if (key.isBlank()) throw Exception("Gemini API key not set. Add it in Settings.")
            connection.setRequestProperty("x-goog-api-key", key)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            // Allow very long generations (100 songs); 0 means no timeout
            connection.connectTimeout = 0
            connection.readTimeout = 0

            // Create request body
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            // Send request
            val outputWriter = OutputStreamWriter(connection.outputStream)
            outputWriter.write(requestBody.toString())
            outputWriter.flush()
            outputWriter.close()

            // Read response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                // Parse JSON response
                val jsonResponse = JSONObject(response)
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).getString("text")
                    }
                }
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.readText()
                errorReader.close()
                Log.e("GeminiViewModel", "HTTP Error $responseCode: $errorResponse")
                when (responseCode) {
                    401 -> throw Exception("Invalid API key. Please check your Gemini API key in Settings.")
                    403 -> throw Exception("API access forbidden. Please verify your API key has proper permissions.")
                    429 -> throw Exception("Rate limit exceeded. Please try again later.")
                    else -> throw Exception("API request failed with code $responseCode: $errorResponse")
                }
            }
            
            connection.disconnect()
            return@withContext null
        } catch (e: Exception) {
            Log.e("GeminiViewModel", "Error making API request", e)
            throw e
        }
    }

    /**
     * Make a JSON-mode Gemini request with persona (systemInstruction), safety settings,
     * and enforced JSON response via generationConfig.response_mime_type = application/json
     */
    private suspend fun makeGeminiJsonRequest(
        systemInstruction: String,
        userPrompt: String,
        responseMimeType: String = "application/json"
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            val key = context.dataStore.data.firstOrNull()?.get(GeminiApiKey).orEmpty()
            if (key.isBlank()) throw Exception("Gemini API key not set. Add it in Settings.")
            connection.setRequestProperty("x-goog-api-key", key)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            // Allow very long generations (100 songs); 0 means no timeout
            connection.connectTimeout = 0
            connection.readTimeout = 0

            val requestBody = JSONObject().apply {
                // Persona / system instruction
                put("systemInstruction", JSONObject().apply {
                    put("role", "system")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })

                // No safetySettings per request — responses are constrained by our strict JSON schema

                // Generation config: force JSON
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.9)
                    put("response_mime_type", responseMimeType)
                })

                // User content
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userPrompt) })
                        })
                    })
                })
            }

            val outputWriter = OutputStreamWriter(connection.outputStream)
            outputWriter.write(requestBody.toString())
            outputWriter.flush()
            outputWriter.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(response)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text")
                    }
                }
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.readText()
                errorReader.close()
                Log.e("GeminiViewModel", "HTTP Error $responseCode: $errorResponse")
                when (responseCode) {
                    401 -> throw Exception("Invalid API key. Please check your Gemini API key in Settings.")
                    403 -> throw Exception("API access forbidden. Please verify your API key has proper permissions.")
                    429 -> throw Exception("Rate limit exceeded. Please try again later.")
                    else -> throw Exception("API request failed with code $responseCode: $errorResponse")
                }
            }
            connection.disconnect()
            return@withContext null
        } catch (e: Exception) {
            Log.e("GeminiViewModel", "Error making JSON-mode API request", e)
            throw e
        }
    }
    
    /**
     * Process user prompt to handle artist names, genres, and moods better
     */
    private fun processUserPrompt(prompt: String): String {
        // Clean up the prompt and make it more structured for better parsing
        var processed = prompt.trim()
        
        // Handle common patterns for better AI understanding
        processed = processed
            // Normalize artist mentions
            .replace(Regex("songs? by ([^,]+(?:, [^,]+)*)", RegexOption.IGNORE_CASE)) { match ->
                "songs by ${match.groupValues[1]}"
            }
            // Normalize genre mentions
            .replace(Regex("(\\b(?:pop|rock|hip[- ]?hop|rap|country|jazz|blues|classical|electronic|indie|alternative|metal|punk|reggae|folk|r&b|soul)\\b)", RegexOption.IGNORE_CASE)) { match ->
                match.groupValues[1].lowercase()
            }
            // Normalize mood mentions
            .replace(Regex("(\\b(?:sad|happy|upbeat|chill|relaxing|energetic|melancholic|romantic|party|workout|study)\\b)", RegexOption.IGNORE_CASE)) { match ->
                match.groupValues[1].lowercase()
            }
            // Clean up multiple spaces and punctuation
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[,;]\\s*and\\s+"), ", ")
        
        return processed
    }
    
    fun generateQueue(
        prompt: String,
        songCount: Int
    ) {
        viewModelScope.launch {
            try {
                _state.value = GeminiState.Loading
                
                val processedPrompt = processUserPrompt(prompt)
                val systemInstruction = """
                    You are DJ Gemini, a meticulous music curator who only outputs strict JSON when asked.
                    Task: Suggest exactly $songCount real, popular songs available on YouTube Music that match the user's theme.
                    Requirements:
                    - Only real, widely known songs by real artists; avoid fictional or ultra-obscure tracks
                    - Favor tracks likely available on YouTube Music
                    - Provide high-confidence suggestions and include optional fields if confidently known
                    Output format must be JSON with:
                    {
                      "description": string,
                      "tracks": [
                        {
                          "title": string,
                          "artist": string,
                          "album": string?,
                          "year": number?,
                          "genre": string?,
                          "confidence": number (0-1),
                          "youtube_id": string?,
                          "youtube_url": string?,
                          "duration_sec": number?
                        }
                      ]
                    }
                """.trimIndent()

                val userPrompt = """
                    User request/theme: $processedPrompt
                    Return strict JSON only. No markdown, no commentary.
                """.trimIndent()

                Log.d("GeminiViewModel", "Sending JSON-mode request to Gemini for queue generation")
                val responseText = makeGeminiJsonRequest(systemInstruction, userPrompt)
                    ?: throw Exception("Empty response from AI")
                
                Log.d("GeminiViewModel", "Received response: ${responseText.take(200)}...")
                val generatedQueue = parseQueueResponse(responseText, songCount)
                
                _state.value = GeminiState.QueueGenerated(generatedQueue)
                
            } catch (e: Exception) {
                Log.e("GeminiViewModel", "Failed to generate queue: ${e.message}", e)
                _state.value = GeminiState.Error("Failed to generate queue: ${e.message}")
            }
        }
    }

    /**
     * Search for actual songs from the generated recommendations and return MediaMetadata list
     */
    suspend fun searchAndConvertTracks(tracks: List<GeneratedTrack>): List<MediaMetadata> {
        val mediaItems = mutableListOf<MediaMetadata>()
        
        tracks.forEach { track ->
            try {
                // 1) Try direct mapping by YouTube ID/URL if provided
                val directVideoId = track.youtubeId
                    ?: track.youtubeUrl?.substringAfter("v=")?.substringBefore('&')
                val directItem: SongItem? = if (!directVideoId.isNullOrBlank()) {
                    runCatching {
                        YouTube.queue(listOf(directVideoId)).getOrNull()?.firstOrNull()
                    }.getOrNull()
                } else null

                val bestMatch: SongItem? = directItem ?: run {
                    // 2) Fallback to search with improved matching
                    val searchQuery = buildString {
                        append(track.title)
                        append(' ')
                        append(track.artist)
                        if (track.album.isNotBlank()) {
                            append(' ')
                            append(track.album)
                        }
                        append(" official audio")
                    }

                    var candidate: SongItem? = null
                    YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).onSuccess { searchResult ->
                        val songItems = searchResult.items.filterIsInstance<SongItem>()

                        fun normalize(text: String): Set<String> {
                            return text
                                .lowercase()
                                .replace("\\(.*?\\)".toRegex(), " ")
                                .replace("\\[.*?\\]".toRegex(), " ")
                                .replace("feat\\.|ft\\.|official|audio|video|lyric(s)?".toRegex(), " ")
                                .replace("[^a-z0-9 ]".toRegex(), " ")
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .toSet()
                        }

                        val targetTitle = normalize(track.title)
                        val targetArtist = normalize(track.artist)

                        fun score(item: SongItem): Double {
                            val itemTitle = normalize(item.title)
                            val itemArtistsTokens = item.artists.flatMap { normalize(it.name) }.toSet()
                            val titleIntersect = itemTitle.intersect(targetTitle).size.toDouble()
                            val titleUnion = (itemTitle + targetTitle).toSet().size.toDouble().coerceAtLeast(1.0)
                            val titleScore = titleIntersect / titleUnion
                            val artistIntersect = itemArtistsTokens.intersect(targetArtist).size.toDouble()
                            val artistUnion = (itemArtistsTokens + targetArtist).toSet().size.toDouble().coerceAtLeast(1.0)
                            val artistScore = artistIntersect / artistUnion
                            val durScore = track.durationSec?.let { dur ->
                                item.duration?.let { idur ->
                                    val diff = kotlin.math.abs(idur - dur)
                                    if (diff <= 3) 1.0 else if (diff <= 7) 0.6 else if (diff <= 12) 0.3 else 0.0
                                }
                            } ?: 0.0
                            // Weighted score: title 0.5, artist 0.4, duration 0.1
                            return 0.5 * titleScore + 0.4 * artistScore + 0.1 * durScore
                        }

                        val aiConfidence = (track.confidence.takeIf { it in 0.0f..1.0f } ?: 0.0f).toDouble()
                        val baseThreshold = 0.30 // stricter than before
                        val boostedThreshold = if (aiConfidence < 0.6) 0.40 else baseThreshold

                        candidate = songItems
                            .asSequence()
                            .map { it to score(it) }
                            .filter { (_, sc) -> sc >= boostedThreshold }
                            .maxByOrNull { it.second }
                            ?.first
                    }.onFailure {
                        Log.w("GeminiViewModel", "Failed to search for track: ${track.title} - ${track.artist}")
                    }
                    candidate
                }

                bestMatch?.let { songItem ->
                    val mediaMetadata = songItem.toMediaMetadata()
                    mediaItems.add(mediaMetadata)
                    Log.d("GeminiViewModel", "Found track '${track.title}' -> '${songItem.title}' (${songItem.id})")
                } ?: run {
                    Log.w("GeminiViewModel", "Could not find track: ${track.title} - ${track.artist}")
                }

            } catch (e: Exception) {
                Log.e("GeminiViewModel", "Error searching for track ${track.title}", e)
            }
        }
        
        return mediaItems
    }
    
    /**
     * Generate a playlist based on user prompt and save it to database
     */
    fun generatePlaylist(
        playlistName: String,
        description: String,
        songCount: Int,
        syncWithYouTube: Boolean
    ) {
        viewModelScope.launch {
            try {
                _state.value = GeminiState.Loading
                
                val processedName = processUserPrompt(playlistName)
                val processedDescription = processUserPrompt(description)
                val systemInstruction = """
                    You are DJ Gemini, a helpful playlist-creating AI. You only output strict JSON.
                    Task: Create exactly $songCount real songs for a cohesive playlist that matches the user's theme.
                    Additionally, suggest 3 great, unique playlist names.
                    Requirements:
                    - Only real, popular, widely-available songs; avoid fictional content
                    - Include optional metadata if confidently known
                    Output format must be JSON with:
                    {
                      "playlist_suggestions": [
                        {
                          "playlist_names": [string, string, string],
                          "description": string,
                          "tracks": [
                            {
                              "title": string,
                              "artist": string,
                              "album": string?,
                              "year": number?,
                              "genre": string?,
                              "confidence": number (0-1),
                              "youtube_id": string?,
                              "youtube_url": string?,
                              "duration_sec": number?
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent()

                val userPrompt = """
                    Playlist Name (user): $processedName
                    Description (user): $processedDescription
                    Return strict JSON only. No markdown, no commentary.
                """.trimIndent()

                val responseText = makeGeminiJsonRequest(systemInstruction, userPrompt)
                    ?: throw Exception("Empty response from AI")
                
                val generatedPlaylist = parsePlaylistResponse(
                    responseText, 
                    playlistName, 
                    description, 
                    songCount, 
                    syncWithYouTube
                )
                
                // Search for actual songs on YouTube Music
                val mediaItems = withContext(Dispatchers.IO) {
                    searchAndConvertTracks(generatedPlaylist.tracks)
                }
                
                // Save playlist to database
                val browseId = if (syncWithYouTube) {
                    try {
                        // Create YouTube playlist and get its ID
                        YouTube.createPlaylist(generatedPlaylist.name)
                    } catch (e: Exception) {
                        Log.e("GeminiViewModel", "Failed to create YouTube playlist", e)
                        null
                    }
                } else {
                    null
                }
                
                val localPlaylistId = savePlaylistToDatabase(generatedPlaylist, mediaItems, browseId)
                _state.value = GeminiState.PlaylistGenerated(generatedPlaylist, localPlaylistId, browseId)
                
            } catch (e: Exception) {
                Log.e("GeminiViewModel", "Failed to generate playlist", e)
                _state.value = GeminiState.Error("Failed to generate playlist: ${e.message}")
            }
        }
    }
    
    private fun parseQueueResponse(responseText: String, songCount: Int): GeneratedQueue {
        try {
            // Extract JSON from response (in case there's extra text)
            val jsonStart = responseText.indexOf("{")
            val jsonEnd = responseText.lastIndexOf("}") + 1
            
            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                Log.e("GeminiViewModel", "No valid JSON found in response: $responseText")
                return createFallbackQueue(songCount)
            }
            
            val jsonString = responseText.substring(jsonStart, jsonEnd)
            Log.d("GeminiViewModel", "Extracted JSON: $jsonString")
            
            val jsonObject = JSONObject(jsonString)
            val description = jsonObject.optString("description", "AI-generated queue")
            val tracksArray = jsonObject.getJSONArray("tracks")
            
            val tracks = mutableListOf<GeneratedTrack>()
            for (i in 0 until tracksArray.length()) {
                val trackObject = tracksArray.getJSONObject(i)
                val title = trackObject.optString("title", "").trim()
                val artist = trackObject.optString("artist", "").trim()
                
                // Skip tracks with empty title or artist
                if (title.isBlank() || artist.isBlank()) {
                    Log.w("GeminiViewModel", "Skipping track with empty title or artist: $title - $artist")
                    continue
                }
                
                tracks.add(
                    GeneratedTrack(
                        title = title,
                        artist = artist,
                        album = trackObject.optString("album", "").trim(),
                        year = if (trackObject.has("year")) trackObject.getInt("year") else null,
                        genre = trackObject.optString("genre", "").trim(),
                        confidence = trackObject.optDouble("confidence", 0.8).toFloat(),
                        youtubeId = trackObject.optString("youtube_id").takeIf { it.isNotBlank() },
                        youtubeUrl = trackObject.optString("youtube_url").takeIf { it.isNotBlank() },
                        durationSec = trackObject.optInt("duration_sec").takeIf { trackObject.has("duration_sec") }
                    )
                )
            }
            
            // If we don't have enough valid tracks, use fallback
            if (tracks.isEmpty()) {
                Log.w("GeminiViewModel", "No valid tracks parsed from response, using fallback")
                return createFallbackQueue(songCount)
            }
            
            return GeneratedQueue(
                tracks = tracks,
                description = description,
                totalCount = tracks.size
            )
            
        } catch (e: Exception) {
            Log.e("GeminiViewModel", "Failed to parse queue response: $responseText", e)
            // Return fallback queue with real popular songs
            return createFallbackQueue(songCount)
        }
    }
    
    private fun parsePlaylistResponse(
        responseText: String, 
        playlistName: String, 
        userDescription: String, 
        songCount: Int,
        syncWithYouTube: Boolean
    ): GeneratedPlaylist {
        try {
            // Extract JSON from response (in case there's extra text)
            val jsonStart = responseText.indexOf("{")
            val jsonEnd = responseText.lastIndexOf("}") + 1
            val jsonString = responseText.substring(jsonStart, jsonEnd)
            
            val jsonObject = JSONObject(jsonString)

            // Try new advanced schema first
            val suggestions = jsonObject.optJSONArray("playlist_suggestions")
            val targetObj = if (suggestions != null && suggestions.length() > 0) {
                suggestions.getJSONObject(0)
            } else jsonObject

            val aiDescription = targetObj.optString("description", jsonObject.optString("description", ""))
            val tracksArray = targetObj.optJSONArray("tracks") ?: jsonObject.getJSONArray("tracks")
            val nameSuggestions = targetObj.optJSONArray("playlist_names")
            val suggestedName = when {
                nameSuggestions != null && nameSuggestions.length() > 0 -> nameSuggestions.getString(0)
                else -> null
            }
            
            val tracks = mutableListOf<GeneratedTrack>()
            for (i in 0 until tracksArray.length()) {
                val trackObject = tracksArray.getJSONObject(i)
                tracks.add(
                    GeneratedTrack(
                        title = trackObject.getString("title"),
                        artist = trackObject.getString("artist"),
                        album = trackObject.optString("album", ""),
                        year = if (trackObject.has("year")) trackObject.getInt("year") else null,
                        genre = trackObject.optString("genre", ""),
                        confidence = trackObject.optDouble("confidence", 0.0).toFloat(),
                        youtubeId = trackObject.optString("youtube_id").takeIf { it.isNotBlank() },
                        youtubeUrl = trackObject.optString("youtube_url").takeIf { it.isNotBlank() },
                        durationSec = trackObject.optInt("duration_sec").takeIf { trackObject.has("duration_sec") }
                    )
                )
            }
            
            return GeneratedPlaylist(
                name = if (playlistName.isNotBlank()) playlistName else (suggestedName ?: "AI Playlist"),
                description = userDescription,
                tracks = tracks,
                totalCount = tracks.size,
                syncWithYouTube = syncWithYouTube
            )
            
        } catch (e: Exception) {
            Log.e("GeminiViewModel", "Failed to parse playlist response", e)
            // Return fallback playlist
            return createFallbackPlaylist(playlistName, userDescription, songCount, syncWithYouTube)
        }
    }
    

    
    /**
     * CORE BRIDGE FUNCTIONALITY
     * 
     * This implements the bridge between AI suggestions and app functionality:
     * 1. Get creative song ideas from Gemini 🧠
     * 2. Find actual songs on YouTube Music 🔍
     * 3. Add to queue or playlist 🎶
     */
    
    /**
     * Bridge Function: Generate AI queue and add to app's QueueBoard
     * 
     * This is the comprehensive bridge that:
     * 1) Gets song ideas from Gemini based on user prompt
     * 2) Searches for real YouTube Music tracks
     * 3) Adds them to the app's queue system (QueueBoard)
     */
    fun generateAndAddToQueue(
        prompt: String,
        songCount: Int,
        queueName: String? = null,
        onProgress: (String) -> Unit = {},
        onSuccess: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                onProgress("Getting AI song suggestions...")
                _state.value = GeminiState.Loading
                
                // Step 1: Get creative song ideas from Gemini 🧠
                val systemPrompt = """
                    You are a music recommendation AI assistant. Based on the user's description, generate a list of exactly $songCount songs.
                    
                    Format your response as a JSON object with this structure:
                    {
                        "description": "Brief description of the generated queue",
                        "tracks": [
                            {
                                "title": "Song Title",
                                "artist": "Artist Name", 
                                "album": "Album Name",
                                "year": 2023,
                                "genre": "Genre",
                                "confidence": 0.95
                            }
                        ]
                    }
                    
                    Rules:
                    - Include exactly $songCount tracks
                    - Use real songs that exist on music platforms
                    - Prefer well-known songs that are likely to be found
                    - Confidence should be between 0.0 and 1.0 based on how well the song matches the prompt
                    - Vary the selection to create a cohesive but interesting queue
                    - Consider mood, genre, tempo, and theme based on the prompt
                    
                    User prompt: $prompt
                """.trimIndent()
                
                val responseText = makeGeminiApiRequest(systemPrompt)
                    ?: throw Exception("Empty response from AI")
                
                val generatedQueue = parseQueueResponse(responseText, songCount)
                
                onProgress("Searching for songs on YouTube Music...")
                
                // Step 2: Find actual songs on YouTube Music 🔍
                val mediaItems = withContext(Dispatchers.IO) {
                    searchAndConvertTracks(generatedQueue.tracks)
                }
                
                if (mediaItems.isEmpty()) {
                    onError("No songs found on YouTube Music. Try a different description.")
                    _state.value = GeminiState.Error("No songs found")
                    return@launch
                }
                
                onProgress("Adding songs to queue...")
                
                // Step 3: Prepare queue data for UI layer to handle 🎶
                val queueTitle = queueName ?: "AI Generated: ${generatedQueue.description}"
                val result = addTracksToNewQueue(mediaItems, queueTitle)
                
                if (result != null) {
                    onSuccess(mediaItems.size)
                    _state.value = GeminiState.QueueGenerated(generatedQueue)
                } else {
                    onError("Failed to prepare songs for queue")
                    _state.value = GeminiState.Error("Failed to prepare queue")
                }
                
            } catch (e: Exception) {
                Log.e("GeminiViewModel", "Failed to generate and add to queue", e)
                onError("Failed to generate queue: ${e.message}")
                _state.value = GeminiState.Error("Failed to generate queue: ${e.message}")
            }
        }
    }
    
    /**
     * Bridge Function: Generate AI playlist and save to database
     * 
     * This is the comprehensive bridge that:
     * 1) Gets song ideas from Gemini based on user description
     * 2) Searches for real YouTube Music tracks
     * 3) Creates new playlist in database and adds songs
     */
    fun generateAndCreatePlaylist(
        playlistName: String,
        description: String,
        songCount: Int,
        syncWithYouTube: Boolean = false,
        onProgress: (String) -> Unit = {},
        onSuccess: (String, Int) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                onProgress("Getting AI playlist suggestions...")
                _state.value = GeminiState.Loading
                
                // Step 1: Get creative song ideas from Gemini 🧠
                                val systemInstruction = """
                                        You are DJ Gemini, a helpful playlist-creating AI. You only output strict JSON.
                                        Task: Create exactly $songCount real songs for a cohesive playlist that matches the user's theme.
                                        Additionally, suggest 3 great, unique playlist names.
                                        Requirements:
                                        - Only real, popular, widely-available songs; avoid fictional content
                                        - Include optional metadata if confidently known
                                        Output format must be JSON with:
                                        {
                                            "playlist_suggestions": [
                                                {
                                                    "playlist_names": [string, string, string],
                                                    "description": string,
                                                    "tracks": [
                                                        {
                                                            "title": string,
                                                            "artist": string,
                                                            "album": string?,
                                                            "year": number?,
                                                            "genre": string?,
                                                            "confidence": number (0-1),
                                                            "youtube_id": string?,
                                                            "youtube_url": string?,
                                                            "duration_sec": number?
                                                        }
                                                    ]
                                                }
                                            ]
                                        }
                                """.trimIndent()

                                val userPrompt = """
                                        Playlist Name (user): $playlistName
                                        Description (user): $description
                                        Return strict JSON only. No markdown, no commentary.
                                """.trimIndent()
                
                                val responseText = makeGeminiJsonRequest(systemInstruction, userPrompt)
                    ?: throw Exception("Empty response from AI")
                
                val generatedPlaylist = parsePlaylistResponse(
                    responseText, 
                    playlistName, 
                    description, 
                    songCount, 
                    syncWithYouTube
                )
                
                onProgress("Searching for songs on YouTube Music...")
                
                // Step 2: Find actual songs on YouTube Music 🔍
                val mediaItems = withContext(Dispatchers.IO) {
                    searchAndConvertTracks(generatedPlaylist.tracks)
                }
                
                if (mediaItems.isEmpty()) {
                    onError("No songs found on YouTube Music. Try a different description.")
                    _state.value = GeminiState.Error("No songs found")
                    return@launch
                }
                
                onProgress("Creating playlist...")
                
                // Step 3: Create playlist and add songs 🎵
                val browseId = if (syncWithYouTube) {
                    onProgress("Creating YouTube playlist...")
                    try {
                        YouTube.createPlaylist(generatedPlaylist.name)
                    } catch (e: Exception) {
                        Log.e("GeminiViewModel", "Failed to create YouTube playlist", e)
                        null
                    }
                } else {
                    null
                }
                
                val localPlaylistId = savePlaylistToDatabase(generatedPlaylist, mediaItems, browseId)
                
                if (localPlaylistId != null) {
                    onSuccess(localPlaylistId, mediaItems.size)
                    _state.value = GeminiState.PlaylistGenerated(generatedPlaylist, localPlaylistId, browseId)
                } else {
                    onError("Failed to create playlist")
                    _state.value = GeminiState.Error("Failed to create playlist")
                }
                
            } catch (e: Exception) {
                Log.e("GeminiViewModel", "Failed to generate and create playlist", e)
                onError("Failed to create playlist: ${e.message}")
                _state.value = GeminiState.Error("Failed to create playlist: ${e.message}")
            }
        }
    }
    
    /**
     * Helper: Add tracks to a new queue (returns queue title for use in GemQ screen)
     */
    suspend fun addTracksToNewQueue(
        mediaItems: List<MediaMetadata>,
        queueTitle: String
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            // The GemQ screen will handle the actual queue creation through PlayerConnection
            // This just prepares the data and returns the queue title
            Log.d("GeminiViewModel", "Prepared ${mediaItems.size} tracks for queue: $queueTitle")
            queueTitle
        } catch (e: Exception) {
            Log.e("GeminiViewModel", "Failed to prepare tracks for queue", e)
            null
        }
    }
    
    /**
     * Enhanced database saving with actual MediaMetadata objects
     */
    private suspend fun savePlaylistToDatabase(
        generatedPlaylist: GeneratedPlaylist, 
        mediaItems: List<MediaMetadata>,
        browseId: String? = null
    ): String? {
        try {
            return withContext(Dispatchers.IO) {
                // Create the playlist entity synchronously
                val entity = PlaylistEntity(
                    name = generatedPlaylist.name,
                    browseId = browseId,
                    isEditable = browseId != null,
                    isLocal = browseId == null,
                    bookmarkedAt = LocalDateTime.now()
                )
                database.insert(entity)

                // Add the actual found songs to the playlist synchronously
                mediaItems.forEachIndexed { index, mediaMetadata ->
                    // Ensure the song exists
                    database.insert(mediaMetadata)
                    // Map song into playlist
                    database.insert(
                        PlaylistSongMap(
                            playlistId = entity.id,
                            songId = mediaMetadata.id,
                            position = index
                        )
                    )

                    // Also add to YouTube playlist if syncing
                    if (!browseId.isNullOrBlank()) {
                        runCatching {
                            YouTube.addToPlaylist(browseId, mediaMetadata.id)
                        }.onFailure { e ->
                            Log.w("GeminiViewModel", "Failed to add to YT playlist: ${mediaMetadata.id}", e)
                        }
                    }
                }

                Log.d("GeminiViewModel", "Playlist '${generatedPlaylist.name}' saved with ${mediaItems.size} songs")
                entity.id
            }
        } catch (e: Exception) {
            Log.e("GeminiViewModel", "Failed to save playlist to database", e)
            throw e
        }
    }
    
    private fun createFallbackQueue(songCount: Int): GeneratedQueue {
        val fallbackTracks = listOf(
            // Mix of classic and modern popular songs
            GeneratedTrack("Blinding Lights", "The Weeknd", "After Hours", 2020, "Pop", 0.95f),
            GeneratedTrack("Shape of You", "Ed Sheeran", "÷ (Divide)", 2017, "Pop", 0.95f),
            GeneratedTrack("Bohemian Rhapsody", "Queen", "A Night at the Opera", 1975, "Rock", 0.95f),
            GeneratedTrack("Bad Guy", "Billie Eilish", "When We All Fall Asleep, Where Do We Go?", 2019, "Pop", 0.95f),
            GeneratedTrack("Hotel California", "Eagles", "Hotel California", 1976, "Rock", 0.95f),
            GeneratedTrack("Anti-Hero", "Taylor Swift", "Midnights", 2022, "Pop", 0.95f),
            GeneratedTrack("As It Was", "Harry Styles", "Harry's House", 2022, "Pop", 0.95f),
            GeneratedTrack("Don't Stop Believin'", "Journey", "Escape", 1981, "Rock", 0.95f),
            GeneratedTrack("Heat Waves", "Glass Animals", "Dreamland", 2020, "Indie Pop", 0.95f),
            GeneratedTrack("Imagine", "John Lennon", "Imagine", 1971, "Pop", 0.95f),
            GeneratedTrack("Stay", "The Kid LAROI & Justin Bieber", "F*CK LOVE 3: OVER YOU", 2021, "Pop", 0.95f),
            GeneratedTrack("Smells Like Teen Spirit", "Nirvana", "Nevermind", 1991, "Grunge", 0.95f),
            GeneratedTrack("Good 4 U", "Olivia Rodrigo", "SOUR", 2021, "Pop Rock", 0.95f),
            GeneratedTrack("Billie Jean", "Michael Jackson", "Thriller", 1982, "Pop", 0.95f),
            GeneratedTrack("Levitating", "Dua Lipa", "Future Nostalgia", 2020, "Pop", 0.95f),
            GeneratedTrack("Sweet Child O' Mine", "Guns N' Roses", "Appetite for Destruction", 1987, "Rock", 0.95f),
            GeneratedTrack("Watermelon Sugar", "Harry Styles", "Fine Line", 2020, "Pop", 0.95f),
            GeneratedTrack("Rolling in the Deep", "Adele", "21", 2011, "Pop", 0.95f),
            GeneratedTrack("Bad Habits", "Ed Sheeran", "=", 2021, "Pop", 0.95f),
            GeneratedTrack("Industry Baby", "Lil Nas X & Jack Harlow", "MONTERO", 2021, "Hip Hop", 0.95f)
        ).take(songCount)
        
        return GeneratedQueue(
            tracks = fallbackTracks,
            description = "A classic mix of popular songs",
            totalCount = fallbackTracks.size
        )
    }
    
    private fun createFallbackPlaylist(
        name: String, 
        description: String, 
        songCount: Int,
        syncWithYouTube: Boolean
    ): GeneratedPlaylist {
        val fallbackQueue = createFallbackQueue(songCount)
        
        return GeneratedPlaylist(
            name = name,
            description = description,
            tracks = fallbackQueue.tracks,
            totalCount = fallbackQueue.totalCount,
            syncWithYouTube = syncWithYouTube
        )
    }
    
    fun clearState() {
        _state.value = GeminiState.Idle
    }
}
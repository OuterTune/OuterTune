package com.dd3boh.outertune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.ui.utils.appBarScrollBehavior
import com.dd3boh.outertune.viewmodels.GeminiViewModel
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.viewmodels.GeminiState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.constants.GeminiApiKey
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemListScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior = appBarScrollBehavior(),
    geminiViewModel: GeminiViewModel = hiltViewModel()
) {
    var prompt by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("") }
    var songCount by remember { mutableFloatStateOf(15f) }
    var syncWithYouTube by remember { mutableStateOf(false) }
    
    val geminiState by geminiViewModel.state.collectAsState()
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    var progress by remember { mutableFloatStateOf(0f) }
    val (geminiKey, _) = rememberPreference(GeminiApiKey, defaultValue = "")
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 450),
        label = "ai_playlist_progress"
    )
    LaunchedEffect(geminiState) {
        when (geminiState) {
            is GeminiState.Loading -> if (progress == 0f) progress = 0.1f
            is GeminiState.PlaylistGenerated -> progress = 1f
            is GeminiState.Error, GeminiState.Idle -> progress = 0f
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GemList - AI Playlist") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        // Apply only bottom inset so TopAppBar handles top padding
        modifier = Modifier.windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
        )
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "AI-Powered Playlist Generation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Create and save a new playlist with AI",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            val currentState = geminiState
            when (currentState) {
                is GeminiState.Idle, is GeminiState.Loading -> {
                // Input Form
                Text(
                    text = "Playlist Name",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    placeholder = { Text("e.g., \"My Chill Vibes\", \"Workout Energy\"") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )

                Text(
                    text = "Describe the music style:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Music description") },
                    placeholder = { Text("e.g., \"acoustic indie folk with female vocals\", \"energetic rock anthems from the 80s\"") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )

                // Song Count Selector
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Number of songs: ${songCount.roundToInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Slider(
                            value = songCount,
                            onValueChange = { songCount = it },
                            valueRange = 10f..100f,
                            steps = 17, // 10, 15, 20, 25, ..., 100
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "10 songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "100 songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // YouTube Sync Option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "Sync with YouTube Music",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Create playlist on your YouTube Music account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = syncWithYouTube,
                            onCheckedChange = { syncWithYouTube = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Enhanced Generate Button with Bridge Functionality
                Button(
                    onClick = {
                        // Use the new bridge functionality
                        progress = 0.05f
                        geminiViewModel.generateAndCreatePlaylist(
                            playlistName = playlistName,
                            description = prompt,
                            songCount = songCount.roundToInt(),
                            syncWithYouTube = syncWithYouTube,
                            onProgress = { message ->
                                coroutineScope.launch {
                                    // Map coarse progress messages to a visual fill
                                    val p = when {
                                        message.contains("Getting AI", ignoreCase = true) -> 0.25f
                                        message.contains("Searching", ignoreCase = true) -> 0.6f
                                        message.contains("Creating YouTube", ignoreCase = true) -> 0.9f
                                        message.contains("Creating playlist", ignoreCase = true) -> 0.8f
                                        else -> 0.5f
                                    }
                                    if (p > progress) progress = p
                                }
                            },
                            onSuccess = { localPlaylistId, foundCount ->
                                coroutineScope.launch {
                                    // Navigation will be handled in the result view
                                    progress = 1f
                                }
                            },
                            onError = { error ->
                                coroutineScope.launch {
                                    // Error handling will be shown in the Error state
                                    progress = 0f
                                }
                            }
                        )
                    },
                    enabled = prompt.isNotBlank() && playlistName.isNotBlank() && geminiState !is GeminiState.Loading && geminiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Fill overlay behind content to show progress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        val overlayColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .height(48.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(8.dp))
                                .background(overlayColor)
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (geminiState is GeminiState.Loading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Creating AI Playlist… ${(animatedProgress * 100).roundToInt()}%")
                            } else {
                                Text(if (geminiKey.isBlank()) "Add Gemini API key in Settings" else "Generate AI Playlist")
                            }
                        }
                    }
                }

                // Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = "💡 Tip: The more specific your description, the better the results! Include genres, time periods, moods, or specific artists for inspiration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                }
                is GeminiState.PlaylistGenerated -> {
                    // Enhanced Generation Result with Bridge Information
                    val playlist = currentState.playlist
                    EnhancedPlaylistResultView(
                        playlistName = playlist.name,
                        songs = playlist.tracks.map { "${it.title} - ${it.artist}" },
                        syncWithYouTube = playlist.syncWithYouTube,
                        localPlaylistId = currentState.localPlaylistId,
                        youtubePlaylistId = currentState.youtubePlaylistId,
                        onOpenPlaylist = {
                            // Open the generated playlist if we have its ID; otherwise fallback to library Playlists
                            val id = currentState.localPlaylistId
                            if (!id.isNullOrBlank()) {
                                navController.navigate("local_playlist/$id")
                            } else {
                                navController.navigate("playlists")
                            }
                        },
                        onPlayPlaylist = {
                            // Play the playlist immediately
                            coroutineScope.launch {
                                val mediaItems = withContext(Dispatchers.IO) {
                                    geminiViewModel.searchAndConvertTracks(playlist.tracks)
                                }
                                
                                if (mediaItems.isNotEmpty()) {
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = playlist.name,
                                            items = mediaItems
                                        )
                                    )
                                }
                            }
                        },
                        onTryAgain = {
                            geminiViewModel.clearState()
                        }
                    )
                }
                is GeminiState.Error -> {
                    // Error state
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "❌ Generation Failed",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = (geminiState as GeminiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = { geminiViewModel.clearState() }
                        ) {
                            Text("Try Again")
                        }
                    }
                }
                else -> {
                    // Fallback for other states
                }
            }
        }
    }
}

@Composable
private fun EnhancedPlaylistResultView(
    playlistName: String,
    songs: List<String>,
    syncWithYouTube: Boolean,
    localPlaylistId: String?,
    youtubePlaylistId: String?,
    onOpenPlaylist: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onTryAgain: () -> Unit
) {
    Column {
        Text(
            text = "✅ AI Playlist Created!",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "\"$playlistName\"",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Success status cards
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🧠", fontSize = 18.sp)
                    Text(
                        text = "Step 1: AI suggested ${songs.size} songs based on your description",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔍", fontSize = 18.sp)
                    Text(
                        text = "Step 2: Found actual songs on YouTube Music and matched them",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎶", fontSize = 18.sp)
                    Text(
                        text = "Step 3: Created playlist in your library${if (syncWithYouTube) " and YouTube Music" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Song preview (first few songs)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Songs in your playlist:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                songs.take(6).forEachIndexed { index, song ->  // Show first 6 songs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = song,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (songs.size > 6) {
                    Text(
                        text = "... and ${songs.size - 6} more songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // YouTube sync status
        if (syncWithYouTube) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (youtubePlaylistId != null) 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = if (youtubePlaylistId != null) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (youtubePlaylistId != null)
                            "✅ Successfully synced to YouTube Music"
                        else
                            "⚠️ Local playlist created, but YouTube sync failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Primary action: Open playlist
            Button(
                onClick = onOpenPlaylist,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Playlist")
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play playlist immediately
                Button(
                    onClick = onPlayPlaylist,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play Now", fontSize = 12.sp)
                }

                // Try again
                Button(
                    onClick = onTryAgain,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Try Again", fontSize = 12.sp)
                }
            }
        }

        // Success info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🎉 Your AI playlist is ready!",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "The AI successfully bridged creative suggestions with real YouTube Music tracks. Your playlist has been saved to your library and is ready to enjoy!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
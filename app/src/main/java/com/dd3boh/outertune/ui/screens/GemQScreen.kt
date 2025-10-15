package com.dd3boh.outertune.ui.screens

import android.util.Log
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.ui.utils.appBarScrollBehavior
import com.dd3boh.outertune.viewmodels.GeminiViewModel
import com.dd3boh.outertune.viewmodels.GeminiState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.constants.GeminiApiKey
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemQScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior = appBarScrollBehavior(),
    geminiViewModel: GeminiViewModel = hiltViewModel()
) {
    var prompt by remember { mutableStateOf("") }
    var songCount by remember { mutableFloatStateOf(10f) }
    
    val geminiState by geminiViewModel.state.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val (geminiKey, _) = rememberPreference(GeminiApiKey, defaultValue = "")
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 450),
        label = "ai_queue_progress"
    )
    LaunchedEffect(geminiState) {
        when (geminiState) {
            is GeminiState.Loading -> if (progress == 0f) progress = 0.1f
            is GeminiState.QueueGenerated -> progress = 1f
            is GeminiState.Error, GeminiState.Idle -> progress = 0f
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GemQ - AI Queue") },
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
        // Apply only bottom inset so the top bar doesn't get double spacing
        modifier = Modifier.windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
        ),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "AI-Powered Queue Generation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Create a temporary queue with AI suggestions",
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
                    text = "Describe the mood or type of music you want:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Music prompt") },
                    placeholder = { Text("e.g., \"rainy day indie folk\", \"upbeat workout music\", \"chill lo-fi for studying\"") },
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
                        .padding(bottom = 24.dp),
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
                            valueRange = 5f..50f,
                            steps = 44, // 5, 6, 7, ..., 50
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "5 songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "50 songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Generate Button with Enhanced Bridge Functionality
                Button(
                    onClick = {
                        // Use the new bridge functionality
                        progress = 0.05f
                        geminiViewModel.generateAndAddToQueue(
                            prompt = prompt,
                            songCount = songCount.roundToInt(),
                            queueName = "AI Queue: ${prompt.take(30)}${if (prompt.length > 30) "..." else ""}",
                            onProgress = { message ->
                                scope.launch {
                                    // Map progress message to fill
                                    val p = when {
                                        message.contains("Getting AI", ignoreCase = true) -> 0.25f
                                        message.contains("Searching", ignoreCase = true) -> 0.6f
                                        message.contains("Adding", ignoreCase = true) -> 0.85f
                                        else -> 0.5f
                                    }
                                    if (p > progress) progress = p
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onSuccess = { foundCount ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("✅ Found $foundCount songs! Ready to add to queue.")
                                    progress = 1f
                                }
                            },
                            onError = { error ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("❌ $error")
                                    progress = 0f
                                }
                            }
                        )
                    },
                    enabled = prompt.isNotBlank() && geminiState !is GeminiState.Loading && geminiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val overlayColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                        Box(modifier = Modifier.matchParentSize())
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .height(48.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(8.dp))
                                .background(overlayColor)
                        )
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (geminiState is GeminiState.Loading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Generating Queue… ${(animatedProgress * 100).roundToInt()}%")
                            } else {
                                Text(if (geminiKey.isBlank()) "Add Gemini API key in Settings" else "Generate AI Queue")
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
                        text = "💡 Tip: Be specific with your description. Include genres, moods, activities, or even specific time periods for better results!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                }
                is GeminiState.QueueGenerated -> {
                    // Enhanced Generation Result with Bridge Integration
                    val queue = currentState.queue
                    EnhancedGenerationResultView(
                        songs = queue.tracks.map { "${it.title} - ${it.artist}" },
                        queueTitle = "AI Generated: ${queue.description}",
                        onAddToNewQueue = {
                            // Add to a new queue (doesn't replace current playback)
                            scope.launch {
                                try {
                                    snackbarHostState.showSnackbar("Adding to new queue...")
                                    
                                    val mediaItems = withContext(Dispatchers.IO) {
                                        geminiViewModel.searchAndConvertTracks(queue.tracks)
                                    }
                                    
                                    if (mediaItems.isEmpty()) {
                                        snackbarHostState.showSnackbar("❌ No songs found. Try a different description.")
                                    } else if (playerConnection == null) {
                                        snackbarHostState.showSnackbar("Player not ready yet. Try again in a moment.")
                                    } else {
                                        // Create new queue without interrupting current playback
                                        val queueName = "AI: ${queue.description.take(30)}${if (queue.description.length > 30) "..." else ""}"
                                        val newQueue = playerConnection.service.queueBoard.addQueue(
                                            queueName, 
                                            mediaItems,
                                            forceInsert = true, 
                                            delta = false
                                        )
                                        newQueue?.let {
                                            playerConnection.service.queueBoard.setCurrQueue(it)
                                        }
                                        snackbarHostState.showSnackbar("✅ Created queue with ${mediaItems.size} songs and switched to it!")
                                    }
                                } catch (e: Exception) {
                                    Log.e("GemQScreen", "Failed to create queue", e)
                                    snackbarHostState.showSnackbar("❌ Failed to create queue: ${e.message}")
                                }
                            }
                        },
                        onAddToCurrentQueue = {
                            // Add songs to current queue (extends current playback)
                            scope.launch {
                                try {
                                    snackbarHostState.showSnackbar("Adding to current queue...")
                                    
                                    val mediaItems = withContext(Dispatchers.IO) {
                                        geminiViewModel.searchAndConvertTracks(queue.tracks)
                                    }
                                    
                                    if (mediaItems.isEmpty()) {
                                        snackbarHostState.showSnackbar("❌ No songs found. Try a different description.")
                                    } else if (playerConnection == null) {
                                        snackbarHostState.showSnackbar("Player not ready yet. Try again in a moment.")
                                    } else {
                                        // Add to end of current queue
                                        playerConnection.enqueueEnd(mediaItems.map { it.toMediaItem() })
                                        snackbarHostState.showSnackbar("✅ Added ${mediaItems.size} songs to current queue!")
                                    }
                                } catch (e: Exception) {
                                    Log.e("GemQScreen", "Failed to add to current queue", e)
                                    snackbarHostState.showSnackbar("❌ Failed to add to queue: ${e.message}")
                                }
                            }
                        },
                        onPlayNow = {
                            // Play immediately (replaces current playback)
                            scope.launch {
                                try {
                                    snackbarHostState.showSnackbar("Starting playback...")
                                    
                                    val mediaItems = withContext(Dispatchers.IO) {
                                        geminiViewModel.searchAndConvertTracks(queue.tracks)
                                    }
                                    
                                    if (mediaItems.isEmpty()) {
                                        snackbarHostState.showSnackbar("❌ No songs found. Try a different description.")
                                    } else if (playerConnection == null) {
                                        snackbarHostState.showSnackbar("Player not ready yet. Try again in a moment.")
                                    } else {
                                        // Play immediately
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = "AI Generated: ${queue.description}",
                                                items = mediaItems
                                            )
                                        )
                                        snackbarHostState.showSnackbar("🎵 Playing ${mediaItems.size} songs now!")
                                    }
                                } catch (e: Exception) {
                                    Log.e("GemQScreen", "Failed to start playback", e)
                                    snackbarHostState.showSnackbar("❌ Failed to start playback: ${e.message}")
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
private fun EnhancedGenerationResultView(
    songs: List<String>,
    queueTitle: String,
    onAddToNewQueue: () -> Unit,
    onAddToCurrentQueue: () -> Unit,
    onPlayNow: () -> Unit,
    onTryAgain: () -> Unit
) {
    Column {
        Text(
            text = "✨ AI Queue Generated!",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = queueTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Found ${songs.size} songs matching your description:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                songs.take(8).forEachIndexed { index, song ->  // Show first 8 songs
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
                if (songs.size > 8) {
                    Text(
                        text = "... and ${songs.size - 8} more songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Enhanced Action Buttons with Multiple Options
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Primary action: Play now
            Button(
                onClick = onPlayNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play Now")
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Create new queue (doesn't interrupt current playback)
                Button(
                    onClick = onAddToNewQueue,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Queue", fontSize = 12.sp)
                }

                // Add to current queue
                Button(
                    onClick = onAddToCurrentQueue,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to Queue", fontSize = 12.sp)
                }
            }

            // Try again button
            Button(
                onClick = onTryAgain,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try Different Prompt")
            }
        }

        // Enhanced info cards
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "🎵 Play Now: Immediately starts playing these songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "🔗 New Queue: Creates a separate queue without interrupting current music",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "➕ Add to Queue: Extends your current queue with these songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
package com.dd3boh.outertune.ui.screens.showermode
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.playback.PlayerConnection
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.viewmodels.LocalSearchViewModel
import com.dd3boh.outertune.models.toMediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowerModeScreen(
    navController: NavController,
    searchViewModel: LocalSearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val database = searchViewModel.database
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    var recognizedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    
    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    recognizedText = text
                    
                    if (playerConnection != null) {
                        handleVoiceCommand(text, database, playerConnection, coroutineScope, context)
                    }
                }
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shower Mode") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!hasPermission) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant Audio Permission")
                }
            } else {
                IconButton(
                    onClick = {
                        if (isListening) {
                            speechRecognizer.stopListening()
                            isListening = false
                        } else {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            }
                            speechRecognizer.startListening(intent)
                            isListening = true
                        }
                    },
                    modifier = Modifier.size(120.dp)
                ) {
                    Icon(
                        if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = "Mic",
                        modifier = Modifier.size(80.dp),
                        tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(if (isListening) "Listening..." else "Tap to speak")
                Spacer(modifier = Modifier.height(16.dp))
                Text("You said: $recognizedText", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

fun handleVoiceCommand(
    command: String,
    database: MusicDatabase,
    playerConnection: PlayerConnection,
    coroutineScope: CoroutineScope,
    context: android.content.Context
) {
    val lowerCommand = command.lowercase(Locale.getDefault())
    
    if (lowerCommand.startsWith("outer play ") || lowerCommand.startsWith("play ")) {
        val query = lowerCommand.removePrefix("outer play ").removePrefix("play ").trim()
        
        // Remove 'by' artist if present for broader search, or keep it. Let's just search the query directly.
        if (query.isEmpty()) return
        
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val searchResults = database.searchSongs(query).first()
                val firstTrack = searchResults.firstOrNull()
                
                withContext(Dispatchers.Main) {
                    if (firstTrack != null) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = "Voice Command: $query",
                                items = listOf(firstTrack.toMediaMetadata()),
                                startIndex = 0
                            )
                        )
                        Toast.makeText(context, "Playing: ${firstTrack.song.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No results found for '$query'", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

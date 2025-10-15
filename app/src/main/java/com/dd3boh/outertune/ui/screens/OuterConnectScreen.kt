package com.dd3boh.outertune.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.extensions.observeUserLoggedIn
import com.dd3boh.outertune.ui.utils.appBarScrollBehavior
import com.dd3boh.outertune.viewmodels.PartyViewModel
import com.dd3boh.outertune.viewmodels.PartyEvent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.dd3boh.outertune.ui.components.QrScannerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OuterConnectScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior = appBarScrollBehavior(),
    partyViewModel: PartyViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Create", "Join")
    val context = LocalContext.current
    
    // Reactive Authentication Status: Continuously observe login changes
    val isUserLoggedIn by context.observeUserLoggedIn().collectAsState(initial = false)
    
    val partyState by partyViewModel.partyState.collectAsState()
    val isLoading by partyViewModel.isLoading.collectAsState()
    val events by partyViewModel.events.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Handle party events
    LaunchedEffect(events) {
        events?.let { event ->
            when (event) {
                is PartyEvent.PartyCreated -> {
                    navController.navigate("party/${event.code}")
                }
                is PartyEvent.PartyJoined -> {
                    navController.navigate("party/${event.code}")
                }
                is PartyEvent.Error -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }
                else -> { /* Handle other events */ }
            }
            partyViewModel.clearEvents()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OuterConnect") },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // Apply only bottom inset (player) to avoid double top padding with TopAppBar
        modifier = Modifier.windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
        )
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTabIndex) {
                    0 -> CreatePartyTab(
                        onPartyCreated = { partyId ->
                            navController.navigate("party/$partyId")
                        },
                        partyViewModel = partyViewModel,
                        isLoading = isLoading,
                        isUserLoggedIn = isUserLoggedIn,
                        onCopy = { code -> scope.launch { snackbarHostState.showSnackbar("Copied $code") } },
                        onNavigateToAccount = { navController.navigate("settings/account") }
                    )
                    1 -> JoinPartyTab(
                        onPartyJoined = { partyId ->
                            navController.navigate("party/$partyId")
                        },
                        partyViewModel = partyViewModel,
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePartyTab(
    onPartyCreated: (String) -> Unit,
    partyViewModel: PartyViewModel,
    isLoading: Boolean,
    isUserLoggedIn: Boolean,
    onCopy: (String) -> Unit,
    onNavigateToAccount: () -> Unit
) {
    var partyName by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableFloatStateOf(6f) }
    var isCreating by remember { mutableStateOf(false) }
    var createdPartyCode by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        if (createdPartyCode == null) {
            // Party creation form
            Text(
                text = "Create a New Party",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = partyName,
                onValueChange = { partyName = it },
                label = { Text("Party Name") },
                placeholder = { Text("e.g., Road Trip Jams") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Removed privacy toggle per requirement: parties are join-by-code only

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Max Participants: ${maxParticipants.roundToInt()}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Slider(
                        value = maxParticipants,
                        onValueChange = { maxParticipants = it },
                        valueRange = 2f..12f,
                        steps = 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Conditional Create Party Button: Enabled only when user is logged in
            Button(
                onClick = {
                    if (isUserLoggedIn) {
                        partyViewModel.createPartyWithAuth(partyName)
                    }
                },
                enabled = partyName.isNotBlank() && !isLoading && isUserLoggedIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    isLoading -> Text("Creating Party...")
                    !isUserLoggedIn -> Text("Sign In Required")
                    else -> Text("Create Party")
                }
            }
            
            // Authentication Status Card: Shows different messages based on login status
            if (!isUserLoggedIn) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Please sign in to create a party",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "Creating parties requires a YouTube Music account. Go to Settings > Account to sign in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        FilledTonalButton(
                            onClick = onNavigateToAccount,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Go to Account Settings")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "✅ You're signed in! You can create and manage parties.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else {
            // Party created successfully
            PartyCreatedView(
                partyCode = createdPartyCode!!,
                partyName = partyName,
                onJoinParty = { onPartyCreated(createdPartyCode!!) },
                onCopy = onCopy
            )
        }
    }
}

@Composable
private fun JoinPartyTab(
    onPartyJoined: (String) -> Unit,
    partyViewModel: PartyViewModel,
    isLoading: Boolean
) {
    var partyCode by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Join a Party",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "Enter the party code to join an existing listening session:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = "🎵 No sign-in required! Anyone can join a party with just the code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }

        OutlinedTextField(
            value = partyCode,
            onValueChange = { partyCode = it.uppercase() },
            label = { Text("Party Code") },
            placeholder = { Text("Enter 6-character code") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Characters
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        FilledTonalButton(
            onClick = { showScanner = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text("Scan QR to Join")
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                partyViewModel.joinParty(partyCode)
            },
            enabled = partyCode.length == 6 && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Joining Party..." else "Join Party")
        }

        if (showScanner) {
            QrScannerDialog(
                onResult = { code ->
                    showScanner = false
                    partyViewModel.joinParty(code)
                },
                onDismiss = { showScanner = false }
            )
        }
    }
}

@Composable
private fun PartyCreatedView(
    partyCode: String,
    partyName: String,
    onJoinParty: () -> Unit,
    onCopy: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉 Party Created!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = partyName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Share this code:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = partyCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 32.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(partyCode))
                            onCopy(partyCode)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy party code"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onJoinParty,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enter Party")
        }
    }
}

// Utility function to generate a random 6-character party code
private fun generatePartyCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..6)
        .map { chars.random() }
        .joinToString("")
}
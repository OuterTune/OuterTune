package com.dd3boh.outertune.ui.menu

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ShowLyricsKey
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.playback.ExoDownloadService
import com.dd3boh.outertune.playback.queues.YouTubeQueue
import com.dd3boh.outertune.ui.component.BigSeekBar
import com.dd3boh.outertune.ui.component.BottomSheetState
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.dialog.AddToPlaylistDialog
import com.dd3boh.outertune.ui.dialog.AddToQueueDialog
import com.dd3boh.outertune.ui.dialog.ArtistDialog
import com.dd3boh.outertune.ui.dialog.DetailsDialog
import com.dd3boh.outertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val clipboardManager = LocalClipboard.current

    var showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    val playerConnection = LocalPlayerConnection.current ?: return
    val playerVolume = playerConnection.service.playerVolume.collectAsState()
    val currentFormatState = database.format(mediaMetadata.id).collectAsState(initial = null)
    val currentFormat = currentFormatState.value
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)

    val activityResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }


    var showPitchTempoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showPitchTempoDialog) {
        PitchTempoDialog(
            onDismiss = { showPitchTempoDialog = false }
        )
    }

    val sleepTimerEnabled = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.pauseWhenSongEnd
    ) {
        playerConnection.service.sleepTimer.isActive
    }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                val newSleepTimerTimeLeft = if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                    playerConnection.player.duration - playerConnection.player.currentPosition
                } else {
                    playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                }
                delay(1000L)

                withContext(Dispatchers.Main) {
                    sleepTimerTimeLeft = newSleepTimerTimeLeft
                }
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(30f)
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = { Icon(imageVector = Icons.Rounded.Timer, contentDescription = null) },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                val focusRequester = remember {
                    FocusRequester()
                }

                var showDialog by remember {
                    mutableStateOf(false)
                }

                LaunchedEffect(showDialog) {
                    if (showDialog) {
                        delay(300)
                        focusRequester.requestFocus()
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val pluralString = pluralStringResource(
                        R.plurals.minute,
                        sleepTimerValue.roundToInt(),
                        sleepTimerValue.roundToInt()
                    )

                    val endTime = System.currentTimeMillis() + (sleepTimerValue.roundToInt() * 60 * 1000).toLong()
                    val calendarNow = Calendar.getInstance()
                    val calendarEnd = Calendar.getInstance().apply { timeInMillis = endTime }

                    // show date if it will span to next day
                    val endTimeString =
                        if (calendarNow.get(Calendar.DAY_OF_YEAR) == calendarEnd.get(Calendar.DAY_OF_YEAR) &&
                            calendarNow.get(Calendar.YEAR) == calendarEnd.get(Calendar.YEAR)
                        ) {
                            SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT, Locale.getDefault())
                                .format(Date(endTime))
                        } else {
                            SimpleDateFormat.getDateTimeInstance(
                                SimpleDateFormat.SHORT,
                                SimpleDateFormat.SHORT,
                                Locale.getDefault()
                            ).format(Date(endTime))
                        }

                    Text(
                        text = "$pluralString\n$endTimeString",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
                            .clip(shape = RoundedCornerShape(8.dp))
                            .clickable {
                                showDialog = true
                            }
                    )

                    // manual input
                    if (showDialog) {
                        val initialText = TextFieldValue(
                            text = sleepTimerValue.roundToInt().toString(),
                            selection = TextRange(0, sleepTimerValue.roundToInt().toString().length),
                        )

                        val (textFieldValue, onTextFieldValueChange) = remember {
                            mutableStateOf(initialText)
                        }

                        TextField(
                            value = textFieldValue,
                            onValueChange = onTextFieldValueChange,
                            placeholder = { pluralString },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.MoreTime, null) },
                            colors = OutlinedTextFieldDefaults.colors(),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Number
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val text = textFieldValue.text.toFloatOrNull()
                                    if (text != null) {
                                        sleepTimerValue = textFieldValue.text.toFloatOrNull() ?: sleepTimerValue
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(weight = 1f, fill = false)
                                .focusRequester(focusRequester)
                        )
                    }

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 1f..120f,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Preset time options
                        val timeIntervals = listOf(15L, 30L, 45L, 60L)

                        // Create time chips for all intervals
                        val timeChips = timeIntervals.map { interval ->
                            val (timeString, duration) = getNextInterval(interval)
                            TimeChip(
                                duration = duration,
                                composable = {
                                    OutlinedButton(
                                        onClick = { sleepTimerValue = duration },
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text(timeString)
                                    }
                                }
                            )
                        }.sortedBy { it.duration } + remember {
                            TimeChip(
                                duration = Float.MAX_VALUE,
                                composable = {
                                    OutlinedButton(
                                        onClick = {
                                            showSleepTimerDialog = false
                                            playerConnection.service.sleepTimer.start(-1)
                                        },
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text(stringResource(R.string.end_of_song))
                                    }
                                }
                            )
                        }

                        timeChips.forEach { timeChip ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                            ) {
                                timeChip.composable()
                            }
                        }
                    }
                }
            }
        )
    }

    var showDetailsDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showDetailsDialog) {
        DetailsDialog(
            mediaMetadata = mediaMetadata,
            currentFormat = currentFormat,
            currentPlayCount = librarySong?.playCount?.fastSumBy { it.count } ?: 0,
            clipboardManager = clipboardManager,
            setVisibility = { showDetailsDialog = it }
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 6.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )

        BigSeekBar(
            progressProvider = playerVolume::value,
            onProgressChange = { playerConnection.service.playerVolume.value = it },
            modifier = Modifier.weight(1f)
        )
    }

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        if (!mediaMetadata.isLocal)
            GridMenuItem(
                icon = Icons.Rounded.Radio,
                title = R.string.start_radio
            ) {
                playerConnection.playQueue(YouTubeQueue.radio(mediaMetadata), isRadio = true)
                onDismiss()
            }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
        if (!mediaMetadata.isLocal)
            DownloadGridMenu(
                localDateTime = download,
                onDownload = {
                    database.transaction {
                        insert(mediaMetadata)
                    }
                    downloadUtil.download(mediaMetadata)
                },
                onRemoveDownload = {
                    DownloadService.sendRemoveDownload(
                        context,
                        ExoDownloadService::class.java,
                        mediaMetadata.id,
                        false
                    )
                }
            )
        if (librarySong?.song?.inLibrary != null && !librarySong!!.song.isLocal) {
            GridMenuItem(
                icon = Icons.Rounded.LibraryAddCheck,
                title = R.string.remove_from_library,
            ) {
                database.query {
                    toggleInLibrary(mediaMetadata.id, null)
                }
            }
        } else if (!mediaMetadata.isLocal) {
            GridMenuItem(
                icon = Icons.Rounded.LibraryAdd,
                title = R.string.add_to_library,
            ) {
                database.transaction {
                    insert(mediaMetadata)
                    toggleInLibrary(mediaMetadata.id, LocalDateTime.now())
                }
            }
        }
        GridMenuItem(
            icon = R.drawable.artist,
            title = R.string.view_artist
        ) {
            if (mediaMetadata.artists.size == 1) {
                navController.navigate("artist/${mediaMetadata.artists[0].id}")
                playerBottomSheetState.collapseSoft()
                onDismiss()
            } else {
                showSelectArtistDialog = true
            }
        }
        if (mediaMetadata.album != null && !mediaMetadata.isLocal) {
            GridMenuItem(
                icon = R.drawable.album,
                title = R.string.view_album
            ) {
                navController.navigate("album/${mediaMetadata.album.id}")
                playerBottomSheetState.collapseSoft()
                onDismiss()
            }
        }

        if (!mediaMetadata.isLocal)
            GridMenuItem(
                icon = Icons.Rounded.Share,
                title = R.string.share
            ) {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${mediaMetadata.id}")
                }
                context.startActivity(Intent.createChooser(intent, null))
                onDismiss()
            }
        GridMenuItem(
            icon = Icons.Rounded.Lyrics,
            title = R.string.toggle_lyrics
        ) {
            onDismiss()
            showLyrics = !showLyrics
        }
        GridMenuItem(
            icon = Icons.Rounded.Info,
            title = R.string.details
        ) {
            showDetailsDialog = true
        }
        SleepTimerGridMenu(
            sleepTimerTimeLeft = sleepTimerTimeLeft,
            enabled = sleepTimerEnabled
        ) {
            if (sleepTimerEnabled) playerConnection.service.sleepTimer.clear()
            else showSleepTimerDialog = true
        }
        GridMenuItem(
            icon = Icons.Rounded.Equalizer,
            title = R.string.equalizer
        ) {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playerConnection.player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                activityResultLauncher.launch(intent)
            }
            onDismiss()
        }
        GridMenuItem(
            icon = Icons.Rounded.Tune,
            title = R.string.advanced
        ) {
            showPitchTempoDialog = true
        }
    }

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */

    if (showChooseQueueDialog) {
        AddToQueueDialog(
            onAdd = { queueName ->
                val q = playerConnection.service.queueBoard.addQueue(
                    queueName,
                    listOf(mediaMetadata),
                    forceInsert = true,
                    delta = false
                )
                q?.let {
                    playerConnection.service.queueBoard.setCurrQueue(it)
                }
            },
            onDismiss = {
                showChooseQueueDialog = false
                onDismiss() // here we dismiss since we switch to the queue anyways
            }
        )
    }

    if (showChoosePlaylistDialog) {
        AddToPlaylistDialog(
            navController = navController,
            songIds = listOf(mediaMetadata.id),
            onPreAdd = { playlist ->
                database.transaction {
                    insert(mediaMetadata)
                }

                playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }

                listOf(mediaMetadata.id)
            },
            onDismiss = {
                showChoosePlaylistDialog = false
            }
        )
    }

    if (showSelectArtistDialog) {
        ArtistDialog(
            navController = navController,
            artists = mediaMetadata.artists,
            onDismiss = {
                playerBottomSheetState.collapseSoft()
                showSelectArtistDialog = false
            }
        )
    }

}

@Composable
fun PitchTempoDialog(
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var tempo by remember {
        mutableFloatStateOf(playerConnection.player.playbackParameters.speed)
    }
    var transposeValue by remember {
        mutableIntStateOf(round(12 * log2(playerConnection.player.playbackParameters.pitch)).toInt())
    }
    val updatePlaybackParameters = {
        playerConnection.player.playbackParameters = PlaybackParameters(tempo, 2f.pow(transposeValue.toFloat() / 12))
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(
                onClick = {
                    tempo = 1f
                    transposeValue = 0
                    updatePlaybackParameters()
                }
            ) {
                Text(stringResource(R.string.reset))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column {
                ValueAdjuster(
                    icon = Icons.Rounded.SlowMotionVideo,
                    currentValue = tempo,
                    values = (0..35).map { round((0.25f + it * 0.05f) * 100) / 100 },
                    onValueUpdate = {
                        tempo = it
                        updatePlaybackParameters()
                    },
                    valueText = { "x$it" },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ValueAdjuster(
                    icon = Icons.Rounded.Tune,
                    currentValue = transposeValue,
                    values = (-12..12).toList(),
                    onValueUpdate = {
                        transposeValue = it
                        updatePlaybackParameters()
                    },
                    valueText = { "${if (it > 0) "+" else ""}$it" }
                )
            }
        }
    )
}

@Composable
fun <T> ValueAdjuster(
    icon: ImageVector,
    currentValue: T,
    values: List<T>,
    onValueUpdate: (T) -> Unit,
    valueText: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )

        IconButton(
            enabled = currentValue != values.first(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) - 1])
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.RemoveCircleOutline,
                contentDescription = null
            )
        }

        Text(
            text = valueText(currentValue),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(80.dp)
        )

        IconButton(
            enabled = currentValue != values.last(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) + 1])
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.AddCircleOutline,
                contentDescription = null
            )
        }
    }
}

data class TimeChip(
    val duration: Float,
    val composable: @Composable () -> Unit
) : Comparable<TimeChip> {
    override fun compareTo(other: TimeChip): Int {
        return duration.compareTo(other.duration)
    }
}

fun getNextInterval(targetMin: Long): Pair<String, Float> {
    require(targetMin in 1..60) { "Interval must be between 1 and 60 minutes" }

    val now = LocalDateTime.now()
    val intervalMinutes = targetMin - now.minute

    val targetTime: LocalDateTime = if (intervalMinutes > 0) {
        // Within this hour
        now.plusMinutes(intervalMinutes)
    } else if (intervalMinutes < 0) {
        // Next hour
        now.plusHours(1).plusMinutes(targetMin - now.minute)
//        now.plusMinutes((60 - now.minute) + targetMin)        // other way to calculate targetTime
    } else {
        // Equal to 0
        now.plusHours(1)
    }

    // Format the time
    val timeString = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT, Locale.getDefault())
        .format(Date(targetTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))

    // Calculate minutes between now and target
    val minutesBetween = ChronoUnit.MINUTES.between(now, targetTime).toFloat()

    return Pair(timeString, minutesBetween)
}

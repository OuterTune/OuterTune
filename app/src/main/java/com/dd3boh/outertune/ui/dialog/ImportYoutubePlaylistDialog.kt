package com.dd3boh.outertune.ui.dialog

import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.R
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.PlaylistSongMap
import com.dd3boh.outertune.extensions.isInternetConnected
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.utils.extractYouTubePlaylistId
import com.dd3boh.outertune.utils.extractYouTubeWatchVideoId
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.utils.completed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

private const val IMPORT_YT_TAG = "OuterTune"

@Composable
fun ImportYoutubePlaylistDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    var urlText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    DefaultDialog(
        onDismiss = { if (!loading) onDismiss() },
        icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
        title = { Text(stringResource(R.string.import_youtube_playlist_title)) },
        buttons = {
            TextButton(onClick = { if (!loading) onDismiss() }, enabled = !loading) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    val playlistId = extractYouTubePlaylistId(urlText)
                    if (playlistId == null) {
                        Log.w(IMPORT_YT_TAG, "import: no playlist id in URL (input length=${urlText.length})")
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_youtube_playlist_no_list_param),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@TextButton
                    }
                    if (!context.isInternetConnected()) {
                        Log.w(IMPORT_YT_TAG, "import: offline, playlistId=$playlistId")
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_youtube_playlist_offline),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@TextButton
                    }
                    scope.launch {
                        try {
                            loading = true
                            Log.i(IMPORT_YT_TAG, "import: start playlistId=$playlistId")
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val seedVideoId = extractYouTubeWatchVideoId(urlText)
                                    val page = YouTube.playlist(playlistId, seedVideoId = seedVideoId).completed().getOrThrow()
                                    val songs = page.songs
                                    check(songs.isNotEmpty()) { "empty" }
                                    val first = songs.first()
                                    val playlistName = first.title.trim().ifEmpty { page.playlist.title }
                                    val thumb = first.toMediaMetadata().thumbnailUrl
                                    Log.i(
                                        IMPORT_YT_TAG,
                                        "import: fetched ${songs.size} songs, name=$playlistName",
                                    )
                                    val dbDone = CompletableDeferred<Result<Unit>>()
                                    database.query {
                                        dbDone.complete(
                                            runCatching {
                                                val entity = PlaylistEntity(
                                                    name = playlistName,
                                                    browseId = null,
                                                    bookmarkedAt = LocalDateTime.now(),
                                                    isEditable = true,
                                                    isLocal = true,
                                                    thumbnailUrl = thumb,
                                                )
                                                insert(entity)
                                                val metas = songs.map(SongItem::toMediaMetadata).onEach { insert(it) }
                                                metas.forEachIndexed { position, meta ->
                                                    insert(
                                                        PlaylistSongMap(
                                                            songId = meta.id,
                                                            playlistId = entity.id,
                                                            position = position,
                                                            setVideoId = meta.setVideoId,
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    dbDone.await().getOrThrow()
                                }
                            }
                            result.onSuccess {
                                Log.i(IMPORT_YT_TAG, "import: success")
                                onDismiss()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.import_youtube_playlist_done),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }.onFailure { e ->
                                Log.e(IMPORT_YT_TAG, "import: failed playlistId=$playlistId", e)
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.import_youtube_playlist_failed,
                                        (e.message ?: "").ifBlank { e.javaClass.simpleName },
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        } catch (e: Throwable) {
                            Log.e(IMPORT_YT_TAG, "import: unexpected error", e)
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.import_youtube_playlist_failed,
                                    (e.message ?: "").ifBlank { e.javaClass.simpleName },
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && urlText.isNotBlank(),
            ) {
                Text(stringResource(R.string.import_youtube_playlist_import))
            }
        },
    ) {
        val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
        val outlineColor = MaterialTheme.colorScheme.outline.toArgb()
        val padPx = with(LocalDensity.current) { 12.dp.roundToPx() }
        val hintString = stringResource(R.string.import_youtube_playlist_hint)

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Native EditText avoids MIUI/HyperOS bug: FloatingActionMode casts host to TextView;
                // Compose TextField uses AndroidComposeView and crashes on text selection toolbar.
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    factory = { ctx ->
                        EditText(ctx).apply {
                            hint = hintString
                            setHintTextColor(hintColor)
                            setTextColor(textColor)
                            setPadding(padPx, padPx, padPx, padPx)
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(android.graphics.Color.TRANSPARENT)
                                cornerRadius = 8f * resources.displayMetrics.density
                                setStroke(
                                    (1 * resources.displayMetrics.density).toInt(),
                                    outlineColor,
                                )
                            }
                            maxLines = 6
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            imeOptions = EditorInfo.IME_ACTION_DONE
                            isVerticalScrollBarEnabled = true
                            doAfterTextChanged { editable ->
                                urlText = editable?.toString().orEmpty()
                            }
                            suppressMiuiFloatingTextActions()
                            post { requestFocus() }
                        }
                    },
                    update = { edit ->
                        edit.isEnabled = !loading
                        edit.suppressMiuiFloatingTextActions()
                        edit.setHintTextColor(hintColor)
                        edit.setTextColor(textColor)
                        (edit.background as? android.graphics.drawable.GradientDrawable)?.apply {
                            setStroke(
                                (1 * edit.resources.displayMetrics.density).toInt(),
                                outlineColor,
                            )
                        }
                        if (edit.text.toString() != urlText) {
                            edit.setText(urlText)
                            edit.setSelection(urlText.length)
                        }
                    },
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.import_youtube_playlist_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * MIUI/HyperOS loads optional `miuix.textaction.*` classes for the floating insertion/selection
 * toolbar; when those classes are missing from the OEM split, logcat fills with ClassNotFoundException.
 * Returning false from [ActionMode.Callback.onCreateActionMode] avoids that code path while keeping
 * normal typing and IME paste.
 */
private fun EditText.suppressMiuiFloatingTextActions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val noop = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
        override fun onDestroyActionMode(mode: ActionMode?) {}
    }
    customInsertionActionModeCallback = noop
    customSelectionActionModeCallback = noop
}

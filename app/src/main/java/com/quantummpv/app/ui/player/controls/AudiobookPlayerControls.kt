package com.quantummpv.app.ui.player.controls

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantummpv.app.R
import com.quantummpv.app.database.dao.AudiobookDao
import com.quantummpv.app.database.entities.AudiobookBookmarkEntity
import com.quantummpv.app.database.entities.AudiobookChapterEntity
import com.quantummpv.app.ui.browser.audiobooks.AudiobookArtwork
import com.quantummpv.app.ui.browser.audiobooks.AudiobookDetails
import com.quantummpv.app.ui.browser.audiobooks.AudiobookIconButton
import com.quantummpv.app.ui.browser.audiobooks.bookTime
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.AudiobookPlayback
import com.quantummpv.app.ui.player.PlaybackPhase
import com.quantummpv.app.ui.player.PlaybackSession
import com.quantummpv.app.ui.player.PlayerViewModel
import com.quantummpv.app.ui.utils.rememberAppHaptics
import com.quantummpv.app.ui.utils.isMpvOptionOwnedByConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudiobookPlayerControls(viewModel: PlayerViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val state by PlaybackSession.state.collectAsStateWithLifecycle()
  val info = state.currentItem?.audiobook ?: return
  val book by AudiobookPlayback.book.collectAsStateWithLifecycle()
  val timer by AudiobookPlayback.timer.collectAsStateWithLifecycle()
  val saveFailed by AudiobookPlayback.saveFailed.collectAsStateWithLifecycle()
  val position by viewModel.precisePosition.collectAsStateWithLifecycle()
  val rawPosition by PlaybackSession.propDouble["time-pos"].collectAsStateWithLifecycle()
  val paused by PlaybackSession.propBoolean["pause"].collectAsStateWithLifecycle()
  val eof by PlaybackSession.propBoolean["eof-reached"].collectAsStateWithLifecycle()
  val actualSpeed by PlaybackSession.propFloat["speed"].collectAsStateWithLifecycle()
  val dao = koinInject<AudiobookDao>()
  val chaptersFlow = remember(info.bookId) { dao.observeChapters(info.bookId) }
  val bookmarksFlow = remember(info.bookId) { dao.observeBookmarks(info.bookId) }
  val storedChapters by chaptersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
  val bookmarks by bookmarksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val haptics = rememberAppHaptics()
  var sheet by rememberSaveable(info.bookId) { mutableStateOf<String?>(null) }
  var bookmarkEdit by remember(info.bookId) { mutableStateOf<AudiobookBookmarkEntity?>(null) }
  var error by remember(info.bookId) { mutableStateOf<String?>(null) }
  var opening by remember(info.bookId) { mutableStateOf(false) }
  var savingBookmark by remember(info.bookId) { mutableStateOf(false) }
  val currentBook = book?.takeIf { it.book.id == info.bookId }
  val tracks = currentBook?.orderedTracks.orEmpty()
  val track = tracks.firstOrNull { it.id == info.trackId }
  val positionMs = ((if (position > 0) position.toDouble() else rawPosition ?: 0.0) * 1000).toLong().coerceAtLeast(0)
  val chapters = remember(tracks, storedChapters) {
    tracks.flatMap { item ->
      storedChapters.filter { it.trackId == item.id }.sortedBy { it.startMs }.ifEmpty {
        listOf(AudiobookChapterEntity(item.id, 0, item.durationMs, item.title))
      }
    }
  }
  val currentChapterIndex = chapters.indexOfLast { it.trackId == info.trackId && it.startMs <= positionMs }
  val currentChapter = chapters.getOrNull(currentChapterIndex)
  val bookPosition = currentBook?.positionInBook(info.trackId, positionMs) ?: 0L
  val bookDuration = currentBook?.durationMs ?: 0L
  val speed = actualSpeed?.takeIf { it > 0 } ?: currentBook?.book?.playbackSpeed ?: 1f
  val ready = state.phase in setOf(PlaybackPhase.READY, PlaybackPhase.BACKGROUND) && currentBook != null
  val playing = paused == false && eof != true
  val title = currentBook?.book?.title ?: state.currentItem?.title.orEmpty()
  fun operation(action: suspend () -> Unit) {
    scope.launch {
      try { action() } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) {
        error = failure.localizedMessage ?: context.getString(R.string.audiobook_play_failed)
      }
    }
  }
  fun jump(trackId: Long, targetMs: Long) {
    if (opening) return
    sheet = null
    if (trackId == info.trackId) {
      AudiobookPlayback.seek(targetMs)
      haptics.confirm()
    } else {
      opening = true
      operation {
        try { AudiobookPlayback.launch(context, info.bookId, trackId, targetMs) } finally { opening = false }
      }
    }
  }
  fun addBookmark() {
    val progress = PlaybackSession.audiobookProgress() ?: return
    bookmarkEdit = AudiobookBookmarkEntity(bookId = info.bookId, trackId = info.trackId, positionMs = progress.positionMs,
      title = "${currentChapter?.title ?: track?.title.orEmpty()} ${bookTime(progress.positionMs)}".trim())
  }
  Scaffold(modifier = modifier.fillMaxSize(), topBar = {
    TopAppBar(title = { Text(stringResource(R.string.audiobooks_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
      navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.RoundedFilled.ArrowBack, stringResource(R.string.generic_cancel)) } },
      actions = {
        AudiobookIconButton(Icons.RoundedFilled.Info, stringResource(R.string.audiobook_details), currentBook != null) { sheet = "details" }
        AudiobookIconButton(Icons.RoundedFilled.Close, stringResource(R.string.audiobook_stop)) {
          PlaybackSession.stop(clearQueue = true)
          onBack()
        }
      })
  }) { padding ->
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
      val artwork: @Composable (Modifier) -> Unit = { layout ->
        Box(layout.padding(20.dp), contentAlignment = Alignment.Center) {
          AudiobookArtwork(currentBook?.book?.coverUri,
            Modifier.widthIn(max = 380.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
          if (!ready && state.error == null) CircularProgressIndicator()
        }
      }
      val controls: @Composable (Modifier) -> Unit = { layout ->
        Column(layout.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
          currentBook?.book?.author?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
          currentBook?.book?.narrator?.takeIf(String::isNotBlank)?.let {
            Text("${stringResource(R.string.audiobook_narrator)}: $it", style = MaterialTheme.typography.bodySmall,
              maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.secondary)
          }
          (state.error ?: error)?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { operation { AudiobookPlayback.launch(context, info.bookId) } }, enabled = !opening) {
              Text(stringResource(R.string.audiobook_retry))
            }
          }
          if (saveFailed) Text(stringResource(R.string.audiobook_save_failed), color = MaterialTheme.colorScheme.error)
          TextButton(onClick = { sheet = "chapters" }, enabled = chapters.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(currentChapter?.title ?: track?.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
          AudiobookSeekbar(info.trackId, positionMs, track?.durationMs ?: 0L, ready) { AudiobookPlayback.seek(it) }
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            AudiobookIconButton(Icons.RoundedFilled.SkipPrevious, stringResource(R.string.audiobook_previous_chapter), ready && currentChapterIndex >= 0) {
              val target = if (positionMs - (currentChapter?.startMs ?: 0) > 3000) currentChapter else chapters.getOrNull(currentChapterIndex - 1) ?: currentChapter
              target?.let { jump(it.trackId, it.startMs) }
            }
            AudiobookIconButton(Icons.RoundedFilled.FastRewind, stringResource(R.string.audiobook_rewind), ready) {
              AudiobookPlayback.seekBy(-15)
              haptics.confirm()
            }
            FilledIconButton(onClick = {
              if (eof == true) {
                operation { AudiobookPlayback.launch(context, info.bookId, fromBeginning = true) }
              } else PlaybackSession.setPropertyBoolean("pause", playing)
              haptics.selection(!playing)
            }, enabled = ready && !opening, modifier = Modifier.size(64.dp)) {
              Icon(if (playing) Icons.RoundedFilled.Pause else Icons.RoundedFilled.PlayArrow,
                stringResource(if (playing) R.string.audiobook_pause else R.string.audiobook_continue), modifier = Modifier.size(36.dp))
            }
            AudiobookIconButton(Icons.RoundedFilled.FastForward, stringResource(R.string.audiobook_forward), ready) {
              AudiobookPlayback.seekBy(30)
              haptics.confirm()
            }
            AudiobookIconButton(Icons.RoundedFilled.SkipNext, stringResource(R.string.audiobook_next_chapter),
              ready && currentChapterIndex >= 0 && currentChapterIndex < chapters.lastIndex) {
              chapters.getOrNull(currentChapterIndex + 1)?.let { jump(it.trackId, it.startMs) }
            }
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${stringResource(R.string.audiobook_book_time)} ${bookTime(bookPosition)}", modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.labelMedium)
            Text(bookTime(bookDuration), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
          }
          LinearProgressIndicator(progress = { if (bookDuration > 0) (bookPosition.toDouble() / bookDuration).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth())
          Text(stringResource(R.string.audiobook_remaining, bookTime(((bookDuration - bookPosition).coerceAtLeast(0) / speed).toLong())),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            AudiobookIconButton(Icons.RoundedFilled.MenuBook, stringResource(R.string.audiobook_chapters), chapters.isNotEmpty()) { sheet = "chapters" }
            AudiobookIconButton(Icons.RoundedFilled.Bookmarks, stringResource(R.string.audiobook_bookmarks), currentBook != null) { sheet = "bookmarks" }
            AudiobookIconButton(Icons.RoundedFilled.Add, stringResource(R.string.audiobook_add_bookmark), ready, ::addBookmark)
            AudiobookIconButton(Icons.RoundedFilled.Speed, "${stringResource(R.string.ui_playback_speed)} ${speed}x",
              ready && !isMpvOptionOwnedByConfig("speed")) { sheet = "speed" }
            AudiobookIconButton(Icons.RoundedFilled.Timer, stringResource(R.string.audiobook_sleep_timer), ready) { sheet = "timer" }
            AudiobookIconButton(Icons.RoundedFilled.Settings, stringResource(R.string.audiobook_smart_rewind), currentBook != null) { sheet = "rewind" }
          }
          timer?.let {
            TextButton(onClick = { sheet = "timer" }, modifier = Modifier.fillMaxWidth()) {
              Text(if (it.deadline == null) stringResource(R.string.audiobook_end_chapter)
                else "${stringResource(R.string.audiobook_sleep_timer)} ${bookTime(it.remainingSeconds * 1000L)}")
            }
          }
        }
      }
      if (maxWidth > maxHeight && maxHeight < 540.dp) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
          artwork(Modifier.weight(1f).fillMaxHeight())
          controls(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
        }
      } else {
        val controlsHeight = maxHeight * 0.7f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
          artwork(Modifier.weight(1f).fillMaxWidth())
          controls(Modifier.widthIn(max = 720.dp).fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()))
        }
      }
    }
  }
  if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
      item {
        val label = when (sheet) {
          "chapters" -> R.string.audiobook_chapters
          "bookmarks" -> R.string.audiobook_bookmarks
          "speed" -> R.string.ui_playback_speed
          "timer" -> R.string.audiobook_sleep_timer
          "rewind" -> R.string.audiobook_smart_rewind
          else -> R.string.audiobook_details
        }
        Text(stringResource(label), modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
      }
      when (sheet) {
        "chapters" -> items(chapters, key = { "${it.trackId}:${it.startMs}" }) { chapter ->
          Surface(color = if (chapter == currentChapter) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().selectable(chapter == currentChapter, enabled = !opening) { jump(chapter.trackId, chapter.startMs) }
              .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val file = tracks.firstOrNull { it.id == chapter.trackId }
                Text(file?.fileName.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              Text(bookTime(chapter.endMs - chapter.startMs), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
          }
        }
        "bookmarks" -> {
          item { TextButton(onClick = ::addBookmark, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.audiobook_add_bookmark)) } }
          if (bookmarks.isEmpty()) item { Text(stringResource(R.string.audiobook_no_bookmarks), modifier = Modifier.padding(20.dp)) }
          items(bookmarks, key = { it.id }) { bookmark ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
              TextButton(onClick = { jump(bookmark.trackId, bookmark.positionMs) }, modifier = Modifier.weight(1f), enabled = !opening) {
                Column(Modifier.fillMaxWidth()) {
                  Text(bookmark.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(bookTime(bookmark.positionMs), style = MaterialTheme.typography.labelSmall)
                }
              }
              AudiobookIconButton(Icons.RoundedFilled.Edit, stringResource(R.string.audiobook_bookmark_name)) { bookmarkEdit = bookmark }
              AudiobookIconButton(Icons.RoundedFilled.Delete, stringResource(R.string.audiobook_delete_bookmark)) { operation { dao.deleteBookmark(bookmark.id) } }
            }
          }
        }
        "speed" -> items(listOf(0.5f, 0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)) { option ->
          AudiobookOption("${option}x", speed == option) {
            if (speed != option) { AudiobookPlayback.setSpeed(option); haptics.selection(true) }
            sheet = null
          }
        }
        "timer" -> {
          item { AudiobookOption(stringResource(R.string.audiobook_timer_off), timer == null) { AudiobookPlayback.clearTimer(); sheet = null } }
          items(listOf(5, 10, 15, 30, 45, 60, 90)) { minutes ->
            AudiobookOption(stringResource(R.string.audiobook_minutes, minutes), false) { AudiobookPlayback.setTimer(minutes); sheet = null }
          }
          item { AudiobookOption(stringResource(R.string.audiobook_end_chapter), timer?.chapterEndMs != null,
            enabled = currentChapter != null && ready) { AudiobookPlayback.setTimer(null, currentChapter?.endMs); sheet = null } }
        }
        "rewind" -> items(listOf(0, 5, 10, 15, 30)) { seconds ->
          AudiobookOption(if (seconds == 0) stringResource(R.string.audiobook_timer_off) else stringResource(R.string.audiobook_seconds, seconds),
            currentBook?.book?.rewindSeconds == seconds) {
            if (currentBook?.book?.rewindSeconds != seconds) { AudiobookPlayback.setRewind(seconds); haptics.selection(true) }
            sheet = null
          }
        }
        "details" -> item { currentBook?.let { Column(Modifier.padding(horizontal = 20.dp)) { AudiobookDetails(it.book) } } }
      }
    }
  }
  bookmarkEdit?.let { bookmark ->
    var name by rememberSaveable(bookmark.id, bookmark.positionMs) { mutableStateOf(bookmark.title) }
    AlertDialog(onDismissRequest = { bookmarkEdit = null }, title = { Text(stringResource(R.string.audiobook_bookmark_name)) }, text = {
      OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
        label = { Text(stringResource(R.string.audiobook_bookmark_name)) })
    }, confirmButton = { TextButton(onClick = {
      if (savingBookmark) return@TextButton
      savingBookmark = true
      operation {
        try {
          if (bookmark.id == 0L) dao.addBookmark(bookmark.copy(title = name.trim())) else dao.renameBookmark(bookmark.id, name.trim())
          bookmarkEdit = null
          haptics.confirm()
        } finally {
          savingBookmark = false
        }
      }
    }, enabled = name.isNotBlank() && !savingBookmark) { Text(stringResource(R.string.audiobook_save)) } },
      dismissButton = { TextButton(onClick = { bookmarkEdit = null }) { Text(stringResource(R.string.generic_cancel)) } })
  }
}

@Composable
private fun AudiobookOption(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    RadioButton(selected, enabled = enabled, onClick = null)
    Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
  }
}

@Composable
private fun AudiobookSeekbar(trackId: Long, position: Long, duration: Long, enabled: Boolean, onSeek: (Long) -> Unit) {
  var seeking by remember(trackId) { mutableStateOf(false) }
  var target by remember(trackId) { mutableStateOf(0f) }
  val interaction = remember(trackId) { MutableInteractionSource() }
  val haptics = rememberAppHaptics()
  LaunchedEffect(interaction) {
    interaction.interactions.collect {
      if (it is DragInteraction.Cancel || it is PressInteraction.Cancel) seeking = false
    }
  }
  val maximum = duration.coerceAtLeast(1).toFloat()
  val value = (if (seeking) target else position.toFloat()).coerceIn(0f, maximum)
  Slider(value, onValueChange = { target = it; seeking = true }, enabled = enabled && duration > 0,
    valueRange = 0f..maximum, interactionSource = interaction, onValueChangeFinished = {
      if (seeking) {
        onSeek(target.toLong())
        seeking = false
        haptics.confirm()
      }
    })
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(bookTime(value.toLong()), style = MaterialTheme.typography.labelMedium)
    Text(bookTime(duration), style = MaterialTheme.typography.labelMedium)
  }
}
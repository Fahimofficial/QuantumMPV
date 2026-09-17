package com.quantummpv.app.ui.browser.audiobooks

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quantummpv.app.R
import com.quantummpv.app.database.entities.Audiobook
import com.quantummpv.app.database.entities.AudiobookEntity
import com.quantummpv.app.preferences.AudiobookSortType
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.MediaLayoutMode
import com.quantummpv.app.preferences.SortOrder
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.components.BrowserTopBar
import com.quantummpv.app.ui.browser.dialogs.AudiobookSortDialog
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.AudiobookPlayback
import com.quantummpv.app.ui.player.PlaybackSession
import com.quantummpv.app.ui.browser.LocalNavigationBarHeight
import com.quantummpv.app.ui.preferences.PreferencesScreen
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.navigateTo
import com.quantummpv.app.ui.utils.popSafely
import com.quantummpv.app.ui.utils.rememberAppHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object AudiobookLibraryScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val model: AudiobookLibraryViewModel = viewModel()
    val books by model.library.collectAsStateWithLifecycle()
    val importing by model.progress.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val browserPreferences = koinInject<BrowserPreferences>()
    val sortType by browserPreferences.audiobookSortType.collectAsState()
    val sortOrder by browserPreferences.audiobookSortOrder.collectAsState()
    val layoutMode by browserPreferences.audiobookLayoutMode.collectAsState()
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) }
    var search by rememberSaveable { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var detailsId by rememberSaveable { mutableStateOf<Long?>(null) }
    var removeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<AudiobookEntity?>(null) }
    var opening by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.importFiles(it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) model.importFiles(emptyList(), uri)
    }
    val visible = remember(books, query, filter, sortType, sortOrder) {
      val filtered = books.orEmpty().filter { book ->
        val matches = when (filter) {
          1 -> book.book.progressMs > 0 && !book.book.finished
          2 -> book.book.finished
          3 -> book.book.progressMs == 0L && !book.book.finished
          else -> true
        }
        matches && listOf(book.book.title, book.book.author, book.book.narrator, book.book.series)
          .any { it.contains(query, ignoreCase = true) }
      }
      val comparator = when (sortType) {
        AudiobookSortType.Title -> compareBy<Audiobook, String>(String.CASE_INSENSITIVE_ORDER) { it.book.title }
        AudiobookSortType.Author -> compareBy<Audiobook, String>(String.CASE_INSENSITIVE_ORDER) { it.book.author }
        AudiobookSortType.Duration -> compareBy { it.durationMs }
        AudiobookSortType.Progress -> compareBy { it.progress }
        AudiobookSortType.LastPlayed -> compareBy { it.book.lastPlayedAt }
        AudiobookSortType.DateAdded -> compareBy { it.book.addedAt }
      }
      if (sortOrder == SortOrder.Descending) {
        filtered.sortedWith(comparator.reversed())
      } else {
        filtered.sortedWith(comparator)
      }
    }
    fun play(book: Audiobook, restart: Boolean = false) {
      if (opening) return
      opening = true
      scope.launch {
        try {
          AudiobookPlayback.launch(context, book.book.id, fromBeginning = restart)
          detailsId = null
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (failure: Exception) {
          playbackError = context.getString(R.string.audiobook_play_failed)
        } finally {
          opening = false
        }
      }
    }
    BackHandler(search) { search = false; query = "" }
    val navBarHeight = LocalNavigationBarHeight.current.takeIf { it > 0.dp } ?: 88.dp
    Scaffold(
      containerColor = com.quantummpv.app.ui.theme.wallpaperAwareBackgroundColor(),
      topBar = {
        BrowserTopBar(
          title = stringResource(R.string.audiobooks_title),
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = visible.size,
          onBackClick = { backStack.popSafely() },
          onCancelSelection = { },
          onSortClick = { isSortMenuExpanded = true },
          onSearchClick = { search = !search },
          onSettingsClick = { backStack.navigateTo(PreferencesScreen) },
          additionalActions = {
            Box {
              AudiobookIconButton(Icons.RoundedFilled.Add, stringResource(R.string.audiobook_import_files), importing == null) { importMenu = true }
              DropdownMenu(importMenu, onDismissRequest = { importMenu = false }) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.audiobook_import_files)) },
                  leadingIcon = { Icon(Icons.RoundedFilled.Add, null) },
                  onClick = { importMenu = false; files.launch(arrayOf("*/*")) }
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.audiobook_import_folder)) },
                  leadingIcon = { Icon(Icons.RoundedFilled.FolderOpen, null) },
                  onClick = { importMenu = false; folder.launch(null) }
                )
              }
            }
          },
        )
      },
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        if (search) {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.audiobook_search)) },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(R.string.audiobook_all, R.string.audiobook_in_progress, R.string.audiobook_finished, R.string.audiobook_not_started)
            .forEachIndexed { index, label ->
              FilterChip(
                selected = filter == index,
                onClick = {
                  if (filter != index) {
                    filter = index
                    haptics.selection(true)
                  }
                },
                label = { Text(stringResource(label)) }
              )
            }
        }
        importing?.let { progress ->
          Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (progress.second > 0) LinearProgressIndicator(progress = { progress.first.toFloat() / progress.second }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (progress.second > 0) Text(stringResource(R.string.audiobook_importing, progress.first, progress.second), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall) else Spacer(Modifier.weight(1f))
              TextButton(onClick = model::cancelImport) { Text(stringResource(R.string.generic_cancel)) }
            }
          }
        }
        when {
          books == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
          visible.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.RoundedFilled.MenuBook, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.audiobook_empty), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            if (books.orEmpty().isEmpty()) {
              Button(onClick = { files.launch(arrayOf("*/*")) }, enabled = importing == null) {
                Text(stringResource(R.string.audiobook_import_files))
              }
              TextButton(onClick = { folder.launch(null) }, enabled = importing == null) { Text(stringResource(R.string.audiobook_import_folder)) }
            }
          }
          layoutMode == MediaLayoutMode.GRID -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 145.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBarHeight + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            items(visible, key = { it.book.id }) { book ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { detailsId = book.book.id }
              ) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                  Box(
                    Modifier
                      .fillMaxWidth()
                      .aspectRatio(1f)
                      .clip(RoundedCornerShape(8.dp))
                  ) {
                    AudiobookArtwork(book.book.coverUri, Modifier.fillMaxSize())
                    if (book.progress > 0f) {
                      LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier
                          .align(Alignment.BottomCenter)
                          .fillMaxWidth()
                          .height(4.dp)
                      )
                    }
                  }
                  Spacer(Modifier.height(8.dp))
                  Text(
                    book.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (book.book.author.isNotBlank()) {
                    Text(
                      book.book.author,
                      style = MaterialTheme.typography.bodySmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(Modifier.height(4.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      if (book.book.finished) stringResource(R.string.audiobook_finished)
                      else stringResource(
                        R.string.audiobook_remaining,
                        bookTime(((book.durationMs - book.book.progressMs).coerceAtLeast(0) / book.book.playbackSpeed).toLong())
                      ),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.secondary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                      onClick = { play(book) },
                      enabled = !opening,
                      modifier = Modifier.size(28.dp)
                    ) {
                      Icon(
                        Icons.RoundedFilled.PlayArrow,
                        contentDescription = stringResource(R.string.audiobook_continue),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                      )
                    }
                  }
                }
              }
            }
          }
          else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarHeight + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(visible, key = { it.book.id }) { book ->
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().clickable { detailsId = book.book.id }
              ) {
                Row(
                  Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                  AudiobookArtwork(book.book.coverUri, Modifier.size(76.dp).clip(RoundedCornerShape(6.dp)))
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (book.book.author.isNotBlank()) Text(book.book.author, style = MaterialTheme.typography.bodyMedium,
                      maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (book.book.finished) stringResource(R.string.audiobook_finished) else {
                      stringResource(R.string.audiobook_remaining, bookTime(((book.durationMs - book.book.progressMs).coerceAtLeast(0) / book.book.playbackSpeed).toLong()))
                    }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
                  }
                  AudiobookIconButton(Icons.RoundedFilled.PlayArrow, stringResource(R.string.audiobook_continue), !opening) { play(book) }
                }
              }
            }
          }
        }
      }
    }
    if (isSortMenuExpanded) {
      AudiobookSortDialog(
        isOpen = isSortMenuExpanded,
        onDismiss = { isSortMenuExpanded = false },
        sortType = sortType,
        sortOrder = sortOrder,
        layoutMode = layoutMode,
        onSortTypeChange = { browserPreferences.audiobookSortType.set(it) },
        onSortOrderChange = { browserPreferences.audiobookSortOrder.set(it) },
        onLayoutModeChange = { browserPreferences.audiobookLayoutMode.set(it) },
      )
    }
    books?.firstOrNull { it.book.id == detailsId }?.let { book ->
      ModalBottomSheet(onDismissRequest = { detailsId = null }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AudiobookArtwork(book.book.coverUri, Modifier.size(92.dp).clip(RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f)) {
              Text(book.book.title, style = MaterialTheme.typography.titleLarge)
              Text(book.book.author, style = MaterialTheme.typography.bodyLarge)
              Text(bookTime(book.durationMs), style = MaterialTheme.typography.labelMedium)
            }
          }
          Button(onClick = { play(book) }, enabled = !opening, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (book.book.finished) R.string.audiobook_start_over else R.string.audiobook_continue))
          }
          if (book.book.progressMs > 0 && !book.book.finished) TextButton(onClick = { play(book, true) }, enabled = !opening) {
            Text(stringResource(R.string.audiobook_start_over))
          }
          AudiobookDetails(book.book)
          HorizontalDivider()
          Text(stringResource(R.string.audiobook_files), style = MaterialTheme.typography.titleSmall)
          LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(book.orderedTracks, key = { it.id }) { track ->
              Text("${track.position + 1}. ${track.fileName}", style = MaterialTheme.typography.bodySmall)
            }
          }
          HorizontalDivider()
          TextButton(onClick = { editing = book.book; detailsId = null }) { Text(stringResource(R.string.audiobook_edit)) }
          TextButton(onClick = { model.setFinished(book.book.id, !book.book.finished); detailsId = null },
            enabled = PlaybackSession.state.value.currentItem?.audiobook?.bookId != book.book.id) {
            Text(stringResource(if (book.book.finished) R.string.audiobook_mark_unfinished else R.string.audiobook_mark_finished))
          }
          TextButton(onClick = { removeId = book.book.id; detailsId = null }) {
            Text(stringResource(R.string.audiobook_remove), color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }
    books?.firstOrNull { it.book.id == removeId }?.let { book ->
      AlertDialog(onDismissRequest = { removeId = null }, title = { Text(stringResource(R.string.audiobook_remove)) },
        text = { Text(stringResource(R.string.audiobook_remove_confirmation, book.book.title)) },
        confirmButton = { TextButton(onClick = { model.remove(book.book.id); removeId = null }) { Text(stringResource(R.string.audiobook_remove)) } },
        dismissButton = { TextButton(onClick = { removeId = null }) { Text(stringResource(R.string.generic_cancel)) } })
    }
    editing?.let { book -> AudiobookEditDialog(book, onDismiss = { editing = null }) { model.edit(it); editing = null } }
    (error ?: playbackError)?.let { message ->
      AlertDialog(onDismissRequest = { model.dismissError(); playbackError = null }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { model.dismissError(); playbackError = null }) { Text(stringResource(R.string.generic_ok)) } })
    }
  }
}

@Composable
internal fun AudiobookDetails(book: AudiobookEntity) {
  listOf(
    R.string.audiobook_subtitle to book.subtitle, R.string.audiobook_author to book.author,
    R.string.audiobook_narrator to book.narrator, R.string.audiobook_series to book.series,
    R.string.audiobook_series_part to book.seriesPart, R.string.audiobook_description to book.description,
    R.string.audiobook_genre to book.genre, R.string.audiobook_language to book.language,
    R.string.audiobook_publisher to book.publisher, R.string.audiobook_published to book.publishedYear,
    R.string.audiobook_isbn to book.isbn, R.string.audiobook_asin to book.asin,
  ).filter { it.second.isNotBlank() }.forEach { (label, value) ->
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
      Text(value, style = MaterialTheme.typography.bodyMedium)
    }
  }
  book.abridged?.let { Text(stringResource(if (it) R.string.audiobook_abridged else R.string.audiobook_unabridged)) }
}

@Composable
private fun AudiobookEditDialog(book: AudiobookEntity, onDismiss: () -> Unit, onSave: (AudiobookEntity) -> Unit) {
  var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
  var author by rememberSaveable(book.id) { mutableStateOf(book.author) }
  var narrator by rememberSaveable(book.id) { mutableStateOf(book.narrator) }
  var series by rememberSaveable(book.id) { mutableStateOf(book.series) }
  var part by rememberSaveable(book.id) { mutableStateOf(book.seriesPart) }
  AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.audiobook_edit)) }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.audiobook_title)) }, singleLine = true)
      OutlinedTextField(author, { author = it }, label = { Text(stringResource(R.string.audiobook_author)) }, singleLine = true)
      OutlinedTextField(narrator, { narrator = it }, label = { Text(stringResource(R.string.audiobook_narrator)) }, singleLine = true)
      OutlinedTextField(series, { series = it }, label = { Text(stringResource(R.string.audiobook_series)) }, singleLine = true)
      OutlinedTextField(part, { part = it }, label = { Text(stringResource(R.string.audiobook_series_part)) }, singleLine = true)
    }
  }, confirmButton = {
    TextButton(onClick = { onSave(book.copy(title = title, author = author, narrator = narrator, series = series, seriesPart = part)) },
      enabled = title.isNotBlank()) { Text(stringResource(R.string.audiobook_save)) }
  }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) } })
}

@Composable
internal fun AudiobookArtwork(uri: String?, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var image by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(uri) {
    image = withContext(Dispatchers.IO) {
      if (uri == null) return@withContext null
      runCatching {
        fun stream() = context.contentResolver.openInputStream(Uri.parse(uri))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
          inSampleSize = 1
          while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 800) inSampleSize *= 2
        }
        stream()?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
      }.getOrNull()
    }
  }
  Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
    val loaded = image
    if (loaded != null) Image(loaded, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    else Icon(Icons.RoundedFilled.MenuBook, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
  }
}

internal fun bookTime(milliseconds: Long): String = DateUtils.formatElapsedTime(milliseconds.coerceAtLeast(0) / 1000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudiobookIconButton(icon: AppIcon, label: String, enabled: Boolean = true, onClick: () -> Unit) {
  TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(icon, label) }
  }
}
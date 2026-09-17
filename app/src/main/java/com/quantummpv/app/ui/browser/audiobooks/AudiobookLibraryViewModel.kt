package com.quantummpv.app.ui.browser.audiobooks

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quantummpv.app.R
import com.quantummpv.app.database.dao.AudiobookDao
import com.quantummpv.app.database.entities.AudiobookEntity
import com.quantummpv.app.domain.audiobook.AudiobookImporter
import com.quantummpv.app.ui.player.AudiobookPlayback
import com.quantummpv.app.ui.player.PlaybackSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class AudiobookLibraryViewModel(application: Application) : AndroidViewModel(application) {
  private val dao = GlobalContext.get().get<AudiobookDao>()
  val library = dao.observeLibrary()
    .map { list -> list.filter { !it.book.sourceKey.startsWith("abs:") } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
  private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
  val progress = _progress.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  val error = _error.asStateFlow()
  private var importJob: Job? = null

  init {
    viewModelScope.launch(Dispatchers.IO) {
      com.quantummpv.app.domain.audiobook.AudiobookMarkerUtils.syncKnownAudiobooks(application, dao)
    }
  }

  fun importFiles(uris: List<Uri>, folder: Uri? = null) {
    if (importJob?.isActive == true || uris.isEmpty() && folder == null) return
    _error.value = null
    _progress.value = 0 to 0
    importJob = viewModelScope.launch(Dispatchers.IO) {
      val context = getApplication<Application>()
      try {
        (listOfNotNull(folder) + uris).forEach { uri ->
          runCatching {
            context.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
          }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
          }
        }
        AudiobookImporter(context, dao).importBook(uris, folder) { current, total -> _progress.value = current to total }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        _error.value = context.getString(R.string.audiobook_import_failed, failure.localizedMessage.orEmpty())
      } finally {
        _progress.value = null
      }
    }
  }

  fun cancelImport() { importJob?.cancel() }
  fun dismissError() { _error.value = null }

  fun remove(id: Long) = operation {
    if (PlaybackSession.state.value.currentItem?.audiobook?.bookId == id) {
      throw IllegalStateException(getApplication<Application>().getString(R.string.audiobook_stop_before_remove))
    }
    dao.deleteBook(id)
  }

  fun setFinished(id: Long, finished: Boolean) = operation {
    if (PlaybackSession.state.value.currentItem?.audiobook?.bookId == id) return@operation
    AudiobookPlayback.capture()
    AudiobookPlayback.flush()
    dao.setFinished(id, finished)
  }

  fun edit(book: AudiobookEntity) = operation {
    dao.updateDetails(book.id, book.title.trim(), book.author.trim(), book.narrator.trim(), book.series.trim(), book.seriesPart.trim())
  }

  private fun operation(action: suspend () -> Unit) = viewModelScope.launch {
    try {
      action()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      _error.value = failure.localizedMessage.orEmpty()
    }
  }
}
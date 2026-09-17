package com.quantummpv.app.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "audiobooks", indices = [Index(value = ["sourceKey"], unique = true)])
data class AudiobookEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sourceKey: String,
  val title: String,
  val subtitle: String = "",
  val author: String = "",
  val narrator: String = "",
  val series: String = "",
  val seriesPart: String = "",
  val description: String = "",
  val genre: String = "",
  val language: String = "",
  val publisher: String = "",
  val publishedYear: String = "",
  val isbn: String = "",
  val asin: String = "",
  val abridged: Boolean? = null,
  val coverUri: String? = null,
  val addedAt: Long = System.currentTimeMillis(),
  val lastPlayedAt: Long = 0,
  val currentTrackId: Long? = null,
  val positionMs: Long = 0,
  val progressMs: Long = 0,
  val finished: Boolean = false,
  val playbackSpeed: Float = 1f,
  val rewindSeconds: Int = 10,
)

@Entity(
  tableName = "audiobook_tracks",
  foreignKeys = [ForeignKey(entity = AudiobookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index(value = ["bookId"]), Index(value = ["bookId", "uri"], unique = true)],
)
data class AudiobookTrackEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val bookId: Long,
  val uri: String,
  val fileName: String,
  val title: String,
  val position: Int,
  val durationMs: Long,
  val size: Long = 0,
)

@Entity(
  tableName = "audiobook_chapters",
  primaryKeys = ["trackId", "startMs"],
  foreignKeys = [ForeignKey(entity = AudiobookTrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
)
data class AudiobookChapterEntity(
  val trackId: Long,
  val startMs: Long,
  val endMs: Long,
  val title: String,
)

@Entity(
  tableName = "audiobook_bookmarks",
  foreignKeys = [
    ForeignKey(entity = AudiobookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = AudiobookTrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
  ],
  indices = [Index(value = ["bookId"]), Index(value = ["trackId"])],
)
data class AudiobookBookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val bookId: Long,
  val trackId: Long,
  val positionMs: Long,
  val title: String,
  val createdAt: Long = System.currentTimeMillis(),
)

data class Audiobook(
  @Embedded val book: AudiobookEntity,
  @Relation(parentColumn = "id", entityColumn = "bookId") val tracks: List<AudiobookTrackEntity>,
) {
  @get:Ignore
  val orderedTracks: List<AudiobookTrackEntity> get() = tracks.sortedBy { it.position }
  @get:Ignore
  val durationMs: Long get() = tracks.sumOf { it.durationMs.coerceAtLeast(0) }
  @get:Ignore
  val progress: Float get() = if (book.finished) 1f else {
    if (durationMs == 0L) 0f else (book.progressMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
  }

  fun positionInBook(trackId: Long, positionMs: Long): Long {
    val track = tracks.firstOrNull { it.id == trackId } ?: return 0L
    return orderedTracks.takeWhile { it.id != trackId }.sumOf { it.durationMs.coerceAtLeast(0) } +
      positionMs.coerceIn(0, track.durationMs.coerceAtLeast(0))
  }
}
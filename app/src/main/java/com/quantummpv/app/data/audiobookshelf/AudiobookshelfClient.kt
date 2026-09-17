/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.data.audiobookshelf

import android.net.Uri
import android.util.Log
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfBook
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfChapter
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfLibrary
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfServer
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfTrack
import com.quantummpv.app.network.awaitResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AudiobookshelfClient(
  private val httpClient: OkHttpClient,
  private val json: Json,
) {
  companion object {
    private const val TAG = "AudiobookshelfClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }

  suspend fun login(
    serverUrl: String,
    username: String,
    password: String,
  ): Result<AudiobookshelfServer> = withContext(Dispatchers.IO) {
    val cleanUrl = serverUrl.trimEnd('/')
    try {
      val payload = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
          mapOf(
            "username" to kotlinx.serialization.json.JsonPrimitive(username),
            "password" to kotlinx.serialization.json.JsonPrimitive(password),
          )
        )
      )

      val request = Request.Builder()
        .url("$cleanUrl/login")
        .post(payload.toRequestBody(JSON_MEDIA_TYPE))
        .build()

      httpClient.newCall(request).awaitResponse().use { response ->
        if (!response.isSuccessful) {
          return@withContext Result.failure(Exception("Login failed (HTTP ${response.code})"))
        }

        val body = response.body.string() ?: return@withContext Result.failure(Exception("Empty response"))
        val root = json.parseToJsonElement(body).jsonObject
        val userObj = root["user"]?.jsonObject ?: return@withContext Result.failure(Exception("Missing user object in response"))

        val token = userObj["token"]?.jsonPrimitive?.content
          ?: root["token"]?.jsonPrimitive?.content
          ?: return@withContext Result.failure(Exception("Missing token in login response"))
        val userId = userObj["id"]?.jsonPrimitive?.content ?: ""
        val defaultLibId = userObj["userDefaultLibraryId"]?.jsonPrimitive?.content

        val server = AudiobookshelfServer(
          name = cleanUrl.substringAfter("://").substringBefore(":").substringBefore("/"),
          serverUrl = cleanUrl,
          username = username,
          token = token,
          userId = userId,
          activeLibraryId = defaultLibId,
          lastConnected = System.currentTimeMillis(),
        )
        Result.success(server)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Login exception", e)
      Result.failure(e)
    }
  }

  suspend fun verifyToken(
    serverUrl: String,
    token: String,
    name: String = "",
  ): Result<AudiobookshelfServer> = withContext(Dispatchers.IO) {
    val cleanUrl = serverUrl.trimEnd('/')
    try {
      val request = Request.Builder()
        .url("$cleanUrl/api/authorize")
        .header("Authorization", "Bearer $token")
        .post("{}".toRequestBody(JSON_MEDIA_TYPE))
        .build()

      httpClient.newCall(request).awaitResponse().use { response ->
        if (!response.isSuccessful) {
          // Fallback to GET /api/me
          val meRequest = Request.Builder()
            .url("$cleanUrl/api/me")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
          httpClient.newCall(meRequest).awaitResponse().use { meResponse ->
            if (!meResponse.isSuccessful) {
              return@withContext Result.failure(Exception("Token verification failed (HTTP ${meResponse.code})"))
            }
            val body = meResponse.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val userObj = json.parseToJsonElement(body).jsonObject
            val username = userObj["username"]?.jsonPrimitive?.content ?: "user"
            val userId = userObj["id"]?.jsonPrimitive?.content ?: ""
            val defaultLibId = userObj["userDefaultLibraryId"]?.jsonPrimitive?.content

            Result.success(
              AudiobookshelfServer(
                name = name.ifBlank { cleanUrl.substringAfter("://").substringBefore(":").substringBefore("/") },
                serverUrl = cleanUrl,
                username = username,
                token = token,
                userId = userId,
                activeLibraryId = defaultLibId,
                lastConnected = System.currentTimeMillis(),
              )
            )
          }
        } else {
          val body = response.body.string() ?: return@withContext Result.failure(Exception("Empty response"))
          val root = json.parseToJsonElement(body).jsonObject
          val userObj = root["user"]?.jsonObject ?: root
          val username = userObj["username"]?.jsonPrimitive?.content ?: "user"
          val userId = userObj["id"]?.jsonPrimitive?.content ?: ""
          val defaultLibId = userObj["userDefaultLibraryId"]?.jsonPrimitive?.content

          Result.success(
            AudiobookshelfServer(
              name = name.ifBlank { cleanUrl.substringAfter("://").substringBefore(":").substringBefore("/") },
              serverUrl = cleanUrl,
              username = username,
              token = token,
              userId = userId,
              activeLibraryId = defaultLibId,
              lastConnected = System.currentTimeMillis(),
            )
          )
        }
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Verify token exception", e)
      Result.failure(e)
    }
  }

  suspend fun getLibraries(server: AudiobookshelfServer): Result<List<AudiobookshelfLibrary>> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${server.serverUrl.trimEnd('/')}/api/libraries")
        .header("Authorization", "Bearer ${server.token}")
        .get()
        .build()

      httpClient.newCall(request).awaitResponse().use { response ->
        if (!response.isSuccessful) {
          return@withContext Result.failure(Exception("Failed to fetch libraries (HTTP ${response.code})"))
        }
        val body = response.body.string() ?: return@withContext Result.failure(Exception("Empty response"))
        val root = json.parseToJsonElement(body).jsonObject
        val librariesArray = root["libraries"]?.jsonArray ?: JsonArray(emptyList())

        val libraries = librariesArray.mapNotNull { element ->
          val obj = element.jsonObject
          val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
          val name = obj["name"]?.jsonPrimitive?.content ?: "Library"
          val mediaType = obj["mediaType"]?.jsonPrimitive?.content ?: "book"
          val icon = obj["icon"]?.jsonPrimitive?.content
          val displayOrder = obj["displayOrder"]?.jsonPrimitive?.intOrNull ?: 0
          AudiobookshelfLibrary(
            id = id,
            name = name,
            mediaType = mediaType,
            icon = icon,
            displayOrder = displayOrder,
          )
        }.filter { it.mediaType == "book" || it.mediaType == "podcast" }

        Result.success(libraries)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "getLibraries exception", e)
      Result.failure(e)
    }
  }

  suspend fun getItems(
    server: AudiobookshelfServer,
    libraryId: String,
    page: Int = 0,
    limit: Int = 200,
    sort: String? = null,
    desc: Boolean = false,
    filter: String? = null,
  ): Result<List<AudiobookshelfBook>> = withContext(Dispatchers.IO) {
    try {
      val builder = Uri.parse("${server.serverUrl.trimEnd('/')}/api/libraries/$libraryId/items").buildUpon()
        .appendQueryParameter("limit", limit.toString())
        .appendQueryParameter("page", page.toString())
        .appendQueryParameter("include", "progress,rssfeed,authors,downloads")

      if (!sort.isNullOrBlank()) {
        builder.appendQueryParameter("sort", sort)
      }
      if (desc) {
        builder.appendQueryParameter("desc", "1")
      }
      if (!filter.isNullOrBlank()) {
        builder.appendQueryParameter("filter", filter)
      }

      val request = Request.Builder()
        .url(builder.build().toString())
        .header("Authorization", "Bearer ${server.token}")
        .get()
        .build()

      httpClient.newCall(request).awaitResponse().use { response ->
        if (!response.isSuccessful) {
          return@withContext Result.failure(Exception("Failed to fetch library items (HTTP ${response.code})"))
        }
        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val resultsArray = root["results"]?.jsonArray ?: JsonArray(emptyList())

        val books = resultsArray.mapNotNull { elem ->
          parseBookItem(elem.jsonObject, server, libraryId)
        }

        Result.success(books)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "getItems exception", e)
      Result.failure(e)
    }
  }

  suspend fun getItemDetails(
    server: AudiobookshelfServer,
    itemId: String,
  ): Result<AudiobookshelfBook> = withContext(Dispatchers.IO) {
    try {
      val url = "${server.serverUrl.trimEnd('/')}/api/items/$itemId?expanded=1&include=progress,rssfeed,authors,downloads"
      val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${server.token}")
        .get()
        .build()

      httpClient.newCall(request).awaitResponse().use { response ->
        if (!response.isSuccessful) {
          return@withContext Result.failure(Exception("Failed to fetch item details (HTTP ${response.code})"))
        }
        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val book = parseBookItem(root, server, root["libraryId"]?.jsonPrimitive?.content ?: "")
          ?: return@withContext Result.failure(Exception("Failed to parse book item"))

        Result.success(book)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "getItemDetails exception", e)
      Result.failure(e)
    }
  }

  suspend fun syncProgress(
    server: AudiobookshelfServer,
    itemId: String,
    currentTimeSeconds: Double,
    durationSeconds: Double,
    isFinished: Boolean = false,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val progress = if (durationSeconds > 0) (currentTimeSeconds / durationSeconds).toFloat().coerceIn(0f, 1f) else 0f
      val payload = JsonObject(
        mapOf(
          "currentTime" to kotlinx.serialization.json.JsonPrimitive(currentTimeSeconds),
          "timeListened" to kotlinx.serialization.json.JsonPrimitive(5.0),
          "duration" to kotlinx.serialization.json.JsonPrimitive(durationSeconds),
          "progress" to kotlinx.serialization.json.JsonPrimitive(progress),
          "isFinished" to kotlinx.serialization.json.JsonPrimitive(isFinished),
        )
      ).toString()

      val cleanUrl = server.serverUrl.trimEnd('/')
      val url = "$cleanUrl/api/me/progress/$itemId"
      val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${server.token}")
        .patch(payload.toRequestBody(JSON_MEDIA_TYPE))
        .build()

      httpClient.newCall(request).awaitResponse().use { response ->
        if (response.isSuccessful) {
          Result.success(Unit)
        } else {
          // Fallback to POST /api/me/progress/$itemId
          val postRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${server.token}")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
          httpClient.newCall(postRequest).awaitResponse().use { postResponse ->
            if (postResponse.isSuccessful) {
              Result.success(Unit)
            } else {
              Log.w(TAG, "Progress sync failed (PATCH HTTP ${response.code}, POST HTTP ${postResponse.code})")
              Result.failure(Exception("Progress sync failed (HTTP ${response.code})"))
            }
          }
        }
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "syncProgress exception", e)
      Result.failure(e)
    }
  }

  fun getCoverUrl(server: AudiobookshelfServer, itemId: String): String {
    val cleanUrl = server.serverUrl.trimEnd('/')
    return "$cleanUrl/api/items/$itemId/cover?token=${server.token}"
  }

  fun getTrackStreamUrl(server: AudiobookshelfServer, track: AudiobookshelfTrack, bookId: String): String {
    val cleanUrl = server.serverUrl.trimEnd('/')
    return when {
      track.contentUrl.startsWith("http://") || track.contentUrl.startsWith("https://") -> {
        if (!track.contentUrl.contains("token=")) {
          val sep = if (track.contentUrl.contains("?")) "&" else "?"
          "${track.contentUrl}${sep}token=${server.token}"
        } else {
          track.contentUrl
        }
      }
      track.contentUrl.startsWith("/") -> "$cleanUrl${track.contentUrl}?token=${server.token}"
      track.ino.isNotBlank() -> "$cleanUrl/api/items/$bookId/file/${track.ino}?token=${server.token}"
      track.id.isNotBlank() -> "$cleanUrl/api/items/$bookId/file/${track.id}?token=${server.token}"
      else -> "$cleanUrl/api/items/$bookId/file?token=${server.token}"
    }
  }

  private fun parseBookItem(
    obj: JsonObject,
    server: AudiobookshelfServer,
    fallbackLibraryId: String,
  ): AudiobookshelfBook? {
    val id = obj["id"].asString() ?: return null
    val libraryId = obj["libraryId"].asString() ?: fallbackLibraryId
    val media = obj["media"]?.jsonObject ?: JsonObject(emptyMap())
    val metadata = media["metadata"]?.jsonObject ?: obj["metadata"]?.jsonObject ?: JsonObject(emptyMap())

    val title = metadata["title"].asString()
      ?: obj["title"].asString()
      ?: "Unknown Title"
    val subtitle = metadata["subtitle"].asString() ?: obj["subtitle"].asString() ?: ""

    // Author resolution: join all authors if available
    val authorsArray = metadata["authors"]?.jsonArray ?: obj["authors"]?.jsonArray
    val authors = authorsArray?.mapNotNull {
      if (it is JsonObject) it["name"].asString() else it.asString()
    }?.filter { it.isNotBlank() }
    val authorName = if (!authors.isNullOrEmpty()) {
      authors.joinToString(", ")
    } else {
      metadata["authorName"].asString()
        ?: metadata["author"].asString()
        ?: obj["author"].asString()
        ?: ""
    }

    // Narrator resolution: join all narrators if available
    val narratorsArray = metadata["narrators"]?.jsonArray ?: obj["narrators"]?.jsonArray
    val narrators = narratorsArray?.mapNotNull {
      if (it is JsonObject) it["name"].asString() else it.asString()
    }?.filter { it.isNotBlank() }
    val narrator = if (!narrators.isNullOrEmpty()) {
      narrators.joinToString(", ")
    } else {
      metadata["narratorName"].asString()
        ?: metadata["narrator"].asString()
        ?: obj["narrator"].asString()
        ?: ""
    }

    // Series resolution
    val seriesList = metadata["series"]?.jsonArray ?: obj["series"]?.jsonArray
    val firstSeries = seriesList?.firstOrNull()?.jsonObject
    val seriesName = firstSeries?.get("name").asString()
      ?: metadata["seriesName"].asString()
      ?: ""
    val seriesPart = firstSeries?.get("sequence").asString()
      ?: metadata["seriesSequence"].asString()
      ?: ""

    val description = metadata["description"].asString()
      ?: metadata["summary"].asString()
      ?: obj["description"].asString()
      ?: ""
    val rawGenres = (metadata["genres"]?.jsonArray ?: obj["genres"]?.jsonArray)?.mapNotNull { it.asString() } ?: emptyList()
    val rawTags = (metadata["tags"]?.jsonArray ?: obj["tags"]?.jsonArray)?.mapNotNull { it.asString() } ?: emptyList()
    val genres = (rawGenres + rawTags).distinct()
    val publishedYear = metadata["publishedYear"].asString()
      ?: metadata["publishedDate"].asString()
      ?: obj["publishedYear"].asString()
      ?: ""
    val publisher = metadata["publisher"].asString() ?: obj["publisher"].asString() ?: ""
    val language = metadata["language"].asString() ?: obj["language"].asString() ?: ""
    val isbn = metadata["isbn"].asString() ?: obj["isbn"].asString() ?: ""
    val asin = metadata["asin"].asString() ?: obj["asin"].asString() ?: ""

    // Tracks parsing
    val tracksArray = media["tracks"]?.jsonArray ?: media["audioFiles"]?.jsonArray ?: obj["tracks"]?.jsonArray ?: obj["audioFiles"]?.jsonArray
    val tracks = tracksArray?.mapIndexedNotNull { index, itemElem ->
      val trackObj = itemElem.jsonObject
      val trackId = trackObj["id"].asString() ?: trackObj["ino"].asString() ?: "$index"
      val trackIno = trackObj["ino"].asString() ?: ""
      val trackMeta = trackObj["metadata"]?.jsonObject
      val trackTitle = trackObj["title"].asString()
        ?: trackMeta?.get("filename").asString()
        ?: "Track ${index + 1}"
      val trackDurationSec = trackObj["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
      val trackSize = trackObj["size"]?.jsonPrimitive?.longOrNull ?: 0L
      val mimeType = trackObj["mimeType"]?.jsonPrimitive?.content ?: "audio/mp4"
      val contentUrl = trackObj["contentUrl"]?.jsonPrimitive?.content ?: ""

      AudiobookshelfTrack(
        id = trackId,
        index = index,
        ino = trackIno,
        title = trackTitle,
        durationMs = (trackDurationSec * 1000).toLong(),
        size = trackSize,
        mimeType = mimeType,
        contentUrl = contentUrl,
      )
    } ?: emptyList()

    val durationSec = media["duration"]?.jsonPrimitive?.doubleOrNull
      ?: media["duration"]?.jsonPrimitive?.longOrNull?.toDouble()
      ?: obj["duration"]?.jsonPrimitive?.doubleOrNull
      ?: (tracks.sumOf { it.durationMs } / 1000.0)
    val durationMs = (durationSec * 1000).toLong()

    // Progress resolution: check all possible keys
    val userProgress = obj["userMediaProgress"]?.jsonObject
      ?: obj["mediaProgress"]?.jsonObject
      ?: media["userMediaProgress"]?.jsonObject
      ?: media["progress"]?.jsonObject
      ?: obj["progress"]?.jsonObject
    val currentTimeSec = userProgress?.get("currentTime")?.jsonPrimitive?.doubleOrNull
      ?: userProgress?.get("currentTime")?.jsonPrimitive?.longOrNull?.toDouble()
      ?: 0.0
    val rawProgress = userProgress?.get("progress")?.jsonPrimitive?.floatOrNull
      ?: userProgress?.get("progress")?.jsonPrimitive?.doubleOrNull?.toFloat()
      ?: 0f
    val isFinished = userProgress?.get("isFinished")?.jsonPrimitive?.booleanOrNull
      ?: (userProgress?.get("isFinished")?.jsonPrimitive?.intOrNull?.let { it == 1 })
      ?: (currentTimeSec > 0 && durationSec > 0 && currentTimeSec >= durationSec - 5)
    val progressMs = (currentTimeSec * 1000).toLong()
    val progressPercent = if (rawProgress > 0f) rawProgress else if (durationSec > 0) (currentTimeSec / durationSec).toFloat().coerceIn(0f, 1f) else 0f

    val coverUrl = getCoverUrl(server, id)

    // Chapters parsing
    val rawChapters = media["chapters"]?.jsonArray
      ?: obj["chapters"]?.jsonArray
      ?: media["audioFiles"]?.jsonArray?.firstOrNull()?.jsonObject?.get("chapters")?.jsonArray
    var chapters = rawChapters?.mapIndexedNotNull { index, chapElem ->
      val chapObj = chapElem.jsonObject
      val chapId = chapObj["id"]?.jsonPrimitive?.longOrNull ?: index.toLong()
      val startSec = chapObj["start"]?.jsonPrimitive?.doubleOrNull ?: 0.0
      val endSec = chapObj["end"]?.jsonPrimitive?.doubleOrNull ?: 0.0
      val chapTitle = chapObj["title"].asString() ?: "Chapter ${index + 1}"

      AudiobookshelfChapter(
        id = chapId,
        startMs = (startSec * 1000).toLong(),
        endMs = (endSec * 1000).toLong(),
        title = chapTitle,
      )
    } ?: emptyList()

    if (chapters.isEmpty()) {
      var runningMs = 0L
      val fileChapters = media["audioFiles"]?.jsonArray?.flatMapIndexed { fileIndex, fileElem ->
        val fileObj = fileElem.jsonObject
        val fileDurSec = fileObj["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val fileDurMs = (fileDurSec * 1000).toLong()
        val fileChaps = fileObj["chapters"]?.jsonArray
        val mapped = fileChaps?.mapNotNull { chapElem ->
          val chapObj = chapElem.jsonObject
          val chapId = chapObj["id"]?.jsonPrimitive?.longOrNull ?: 0L
          val startSec = chapObj["start"]?.jsonPrimitive?.doubleOrNull ?: 0.0
          val endSec = chapObj["end"]?.jsonPrimitive?.doubleOrNull ?: 0.0
          val chapTitle = chapObj["title"].asString() ?: "Chapter"
          AudiobookshelfChapter(
            id = chapId,
            startMs = (startSec * 1000).toLong(),
            endMs = (endSec * 1000).toLong(),
            title = chapTitle,
          )
        }
        runningMs += fileDurMs
        mapped ?: emptyList()
      } ?: emptyList()
      if (fileChapters.isNotEmpty()) {
        chapters = fileChapters
      }
    }

    val addedAt = obj["addedAt"]?.jsonPrimitive?.longOrNull ?: 0L
    val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L

    return AudiobookshelfBook(
      id = id,
      libraryId = libraryId,
      title = title,
      subtitle = subtitle,
      author = authorName,
      narrator = narrator,
      series = seriesName,
      seriesPart = seriesPart,
      description = description,
      genres = genres,
      publisher = publisher,
      publishedYear = publishedYear,
      language = language,
      isbn = isbn,
      asin = asin,
      durationMs = durationMs,
      progressMs = progressMs,
      progressPercent = progressPercent,
      isFinished = isFinished,
      coverUrl = coverUrl,
      tracks = tracks,
      chapters = chapters,
      addedAt = addedAt,
      updatedAt = updatedAt,
    )
  }

  private fun JsonElement?.asString(): String? {
    if (this == null || this is kotlinx.serialization.json.JsonNull) return null
    val str = this.jsonPrimitive.contentOrNull ?: return null
    return if (str.equals("null", ignoreCase = true) || str.isBlank()) null else str
  }
}

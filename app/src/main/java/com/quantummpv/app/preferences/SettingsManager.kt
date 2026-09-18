/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Xml
import com.quantummpv.app.BuildConfig
import com.quantummpv.app.database.MpvRxDatabase
import com.quantummpv.app.domain.network.NetworkConnection
import com.quantummpv.app.domain.network.NetworkProtocol
import com.quantummpv.app.preferences.preference.PreferenceStore
import com.quantummpv.app.ui.theme.saveWallpaperCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsManager(
  private val context: Context,
  private val preferenceStore: PreferenceStore,
  private val database: MpvRxDatabase,
) {
  companion object {
    private const val TAG_ROOT = "mpvRxSettings"
    private const val TAG_PREFERENCES = "preferences"
    private const val TAG_PREFERENCE = "preference"
    private const val TAG_DATABASE = "database"
    private const val TAG_NETWORK_CONNECTIONS = "networkConnections"
    private const val TAG_NETWORK_CONNECTION = "networkConnection"

    private const val ATTR_KEY = "key"
    private const val ATTR_TYPE = "type"
    private const val ATTR_VALUE = "value"
    private const val ATTR_EXPORT_DATE = "exportDate"
    private const val ATTR_VERSION = "version"
    private const val ATTR_FORMAT_VERSION = "formatVersion"
    private const val ATTR_ENCODING = "encoding"
    private const val FORMAT_VERSION = 2
    private const val ENCODING_BASE64 = "base64"
    private const val ENCODING_JSON = "base64-json"

    private const val TYPE_STRING = "string"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_STRING_SET = "stringSet"
    private const val STRING_SET_SEPARATOR = "|||"
  }

  suspend fun exportSettings(outputUri: Uri): Result<ExportStats> =
    withContext(Dispatchers.IO) {
      var temporaryFile: File? = null
      try {
        val snapshot = File.createTempFile("settings_", ".xml", context.cacheDir).also { temporaryFile = it }
        val stats = snapshot.outputStream().use { writeSettingsToXml(it) }
        currentCoroutineContext().ensureActive()
        context.contentResolver.openOutputStream(outputUri, "wt")?.use { outputStream ->
          snapshot.inputStream().use { it.copyTo(outputStream) }
          Result.success(stats)
        } ?: Result.failure(Exception("Failed to open output stream"))
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (e: Exception) {
        Result.failure(e)
      } finally {
        temporaryFile?.delete()
      }
    }

  suspend fun importSettings(inputUri: Uri): Result<ImportStats> =
    withContext(Dispatchers.IO) {
      try {
        context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
          val stats = readSettingsFromXml(inputStream)
          Result.success(stats)
        } ?: Result.failure(Exception("Failed to open input stream"))
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private suspend fun writeSettingsToXml(outputStream: OutputStream): ExportStats {
    val allPreferences: MutableMap<String, Any?> = preferenceStore.getAll().toMutableMap()
    val wallpaper = allPreferences[AppearancePreferences.CUSTOM_WALLPAPER_URI_KEY] as? String
    if (!wallpaper.isNullOrBlank()) {
      allPreferences[AppearancePreferences.CUSTOM_WALLPAPER_URI_KEY] = saveWallpaperCopy(context, wallpaper)
    }
    val serializer: XmlSerializer = Xml.newSerializer()
    serializer.setOutput(outputStream, "UTF-8")
    serializer.startDocument("UTF-8", true)
    serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

    serializer.startTag(null, TAG_ROOT)
    serializer.attribute(null, ATTR_FORMAT_VERSION, FORMAT_VERSION.toString())
    serializer.attribute(
      null,
      ATTR_VERSION,
      BuildConfig.VERSION_NAME,
    )
    serializer.attribute(
      null,
      ATTR_EXPORT_DATE,
      SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    )

    var exportedCount = 0
    val exportedKeys = mutableListOf<String>()

    serializer.startTag(null, TAG_PREFERENCES)
    for ((key, value) in allPreferences) {
      currentCoroutineContext().ensureActive()
      if (key != BrowserPreferences.ONBOARDING_COMPLETED_KEY && value != null) {
        writePreference(serializer, key, value)
        exportedCount++
        exportedKeys.add("pref:$key")
      }
    }
    serializer.endTag(null, TAG_PREFERENCES)

    serializer.startTag(null, TAG_DATABASE)

    val networkConnections = database.networkConnectionDao().getAllConnectionsList()
    serializer.startTag(null, TAG_NETWORK_CONNECTIONS)
    networkConnections.forEach { connection ->
      writeNetworkConnection(serializer, connection)
      exportedCount++
      exportedKeys.add("network:${connection.name}")
    }
    serializer.endTag(null, TAG_NETWORK_CONNECTIONS)

    serializer.endTag(null, TAG_DATABASE)

    serializer.endTag(null, TAG_ROOT)
    serializer.endDocument()
    serializer.flush()

    return ExportStats(
      totalExported = exportedCount,
      exportedKeys = exportedKeys,
    )
  }

  private fun sanitizeXmlValue(value: String): String {
    if (value.all { it in ' '..'~' }) return value
    return buildString(value.length) {
      var index = 0
      while (index < value.length) {
        val codePoint = Character.codePointAt(value, index)
        if (codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD ||
          codePoint in 0x20..0xD7FF || codePoint in 0xE000..0xFFFD || codePoint in 0x10000..0x10FFFF
        ) {
          appendCodePoint(codePoint)
        }
        index += Character.charCount(codePoint)
      }
    }
  }

  private fun writePreference(
    serializer: XmlSerializer,
    key: String,
    value: Any,
  ) {
    serializer.startTag(null, TAG_PREFERENCE)
    serializer.attribute(null, ATTR_KEY, key)

    when (value) {
      is String -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_STRING)
        if (sanitizeXmlValue(value) == value) {
          serializer.attribute(null, ATTR_VALUE, value)
        } else {
          serializer.attribute(null, ATTR_ENCODING, ENCODING_BASE64)
          serializer.attribute(null, ATTR_VALUE, Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        }
      }
      is Int -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_INT)
        serializer.attribute(null, ATTR_VALUE, value.toString())
      }
      is Long -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_LONG)
        serializer.attribute(null, ATTR_VALUE, value.toString())
      }
      is Float -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_FLOAT)
        serializer.attribute(null, ATTR_VALUE, value.toString())
      }
      is Boolean -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_BOOLEAN)
        serializer.attribute(null, ATTR_VALUE, value.toString())
      }
      is Set<*> -> {
        require(value.all { it is String }) { "Invalid string set: $key" }
        serializer.attribute(null, ATTR_TYPE, TYPE_STRING_SET)
        serializer.attribute(null, ATTR_ENCODING, ENCODING_JSON)
        serializer.attribute(
          null,
          ATTR_VALUE,
          Base64.encodeToString(JSONArray(value.toList()).toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
        )
      }
      else -> error("Unsupported preference type: $key")
    }

    serializer.endTag(null, TAG_PREFERENCE)
  }

  private fun writeNetworkConnection(
    serializer: XmlSerializer,
    connection: NetworkConnection,
  ) {
    serializer.startTag(null, TAG_NETWORK_CONNECTION)
    serializer.attribute(null, "id", connection.id.toString())
    serializer.attribute(null, "name", sanitizeXmlValue(connection.name))
    serializer.attribute(null, "protocol", connection.protocol.name)
    serializer.attribute(null, "host", sanitizeXmlValue(connection.host))
    serializer.attribute(null, "port", connection.port.toString())
    serializer.attribute(null, "username", sanitizeXmlValue(connection.username))
    // Passwords are Android Keystore-bound ciphertext at rest. Exporting that envelope would both
    // expose credential material and create an unusable backup on another installation. Deliberately
    // omit it; non-anonymous imports stay disconnected until the user enters a password again.
    serializer.attribute(null, "path", sanitizeXmlValue(connection.path))
    serializer.attribute(null, "isAnonymous", connection.isAnonymous.toString())
    serializer.attribute(null, "lastConnected", connection.lastConnected.toString())
    serializer.attribute(null, "autoConnect", connection.autoConnect.toString())
    serializer.attribute(null, "useHttps", connection.useHttps.toString())
    serializer.endTag(null, TAG_NETWORK_CONNECTION)
  }

  private suspend fun readSettingsFromXml(inputStream: InputStream): ImportStats {
    val parser: XmlPullParser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    parser.setInput(inputStream, "UTF-8")
    parser.nextTag()
    parser.require(XmlPullParser.START_TAG, null, TAG_ROOT)
    val formatVersion = parser.getAttributeValue(null, ATTR_FORMAT_VERSION)?.toInt() ?: 1
    require(formatVersion in 1..FORMAT_VERSION) { "Unsupported settings format: $formatVersion" }

    val stats = ImportStats()
    var eventType = parser.eventType
    val editor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
    val parents = ArrayDeque<String>()
    val networkConnections = mutableListOf<NetworkConnection>()

    while (eventType != XmlPullParser.END_DOCUMENT) {
      currentCoroutineContext().ensureActive()
      when (eventType) {
        XmlPullParser.START_TAG -> {
          parents.addLast(parser.name)
          when (parser.name) {
            TAG_ROOT -> {
              val version = parser.getAttributeValue(null, ATTR_VERSION)
              stats.version = version ?: "unknown"
            }
            TAG_PREFERENCE -> {
              if (parents.size == 3 && parents[1] == TAG_PREFERENCES &&
                parser.getAttributeValue(null, ATTR_KEY) != BrowserPreferences.ONBOARDING_COMPLETED_KEY
              ) {
                try {
                  readPreference(parser, editor)
                  stats.imported++
                } catch (cancelled: CancellationException) {
                  throw cancelled
                } catch (e: Exception) {
                  stats.failed++
                  stats.errors.add("Failed to import preference: ${e.message}")
                }
              }
            }
            TAG_NETWORK_CONNECTION -> {
              if (parents.size == 4 && parents[1] == TAG_DATABASE && parents[2] == TAG_NETWORK_CONNECTIONS) {
                try {
                  networkConnections.add(readNetworkConnection(parser))
                } catch (e: Exception) {
                  stats.failed++
                  stats.errors.add("Failed to import network connection: ${e.message}")
                }
              }
            }
          }
        }
        XmlPullParser.END_TAG -> parents.removeLast()
      }
      eventType = parser.next()
    }

    currentCoroutineContext().ensureActive()
    check(stats.failed == 0) { stats.errors.joinToString("\n") }
    check(editor.commit()) { "Failed to save imported preferences" }

    // Insert all database data
    try {
      if (networkConnections.isNotEmpty()) {
        database.networkConnectionDao().insertAll(networkConnections)
        stats.imported += networkConnections.size
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (e: Exception) {
      stats.failed += networkConnections.size
      stats.errors.add("Failed to insert database data: ${e.message}")
    }

    return stats
  }

  private suspend fun readPreference(parser: XmlPullParser, editor: SharedPreferences.Editor) {
    val key = requireNotNull(parser.getAttributeValue(null, ATTR_KEY)) { "Missing preference key" }
    val type = requireNotNull(parser.getAttributeValue(null, ATTR_TYPE)) { "Missing preference type: $key" }
    val valueStr = requireNotNull(parser.getAttributeValue(null, ATTR_VALUE)) { "Missing preference value: $key" }
    val encoding = parser.getAttributeValue(null, ATTR_ENCODING)

    when (type) {
      TYPE_STRING -> {
        val value = when (encoding) {
          null -> valueStr
          ENCODING_BASE64 -> String(Base64.decode(valueStr, Base64.DEFAULT), Charsets.UTF_8)
          else -> error("Unsupported string encoding: $encoding")
        }
        val restored = if (key == AppearancePreferences.CUSTOM_WALLPAPER_URI_KEY && value.isNotBlank()) {
          saveWallpaperCopy(context, value)
        } else value
        editor.putString(key, restored)
      }
      TYPE_INT -> editor.putInt(key, valueStr.toInt())
      TYPE_LONG -> editor.putLong(key, valueStr.toLong())
      TYPE_FLOAT -> editor.putFloat(key, valueStr.toFloat().also { require(it.isFinite()) { "Invalid float: $key" } })
      TYPE_BOOLEAN -> editor.putBoolean(key, valueStr.toBooleanStrict())
      TYPE_STRING_SET -> {
        val stringSet = when (encoding) {
          ENCODING_JSON -> {
            val values = JSONArray(String(Base64.decode(valueStr, Base64.DEFAULT), Charsets.UTF_8))
            (0 until values.length()).map { index ->
              val value = values.get(index)
              require(value is String) { "Invalid string set: $key" }
              value
            }.toSet()
          }
          null -> if (valueStr.isEmpty()) emptySet() else valueStr.split(STRING_SET_SEPARATOR).toSet()
          else -> error("Unsupported string-set encoding: $encoding")
        }
        editor.putStringSet(key, stringSet)
      }
      else -> error("Unsupported preference type: $type")
    }
  }

  private fun readNetworkConnection(parser: XmlPullParser): NetworkConnection =
    NetworkConnection(
      id = 0, // Will be auto-generated
      name = parser.getAttributeValue(null, "name") ?: "",
      protocol =
        NetworkProtocol.valueOf(
          parser.getAttributeValue(null, "protocol") ?: "SMB",
        ),
      host = parser.getAttributeValue(null, "host") ?: "",
      port = parser.getAttributeValue(null, "port")?.toInt() ?: 445,
      username = parser.getAttributeValue(null, "username") ?: "",
      // Never restore credentials from settings XML, including legacy exports that contained
      // plaintext passwords or device-bound encrypted envelopes.
      password = "",
      path = parser.getAttributeValue(null, "path") ?: "/",
      isAnonymous = parser.getAttributeValue(null, "isAnonymous")?.toBooleanStrict() ?: false,
      lastConnected = parser.getAttributeValue(null, "lastConnected")?.toLong() ?: 0L,
      // Avoid repeated authentication attempts before credentials have been re-entered.
      autoConnect = false,
      useHttps = parser.getAttributeValue(null, "useHttps")?.toBooleanStrict() ?: false,
    )

  fun getDefaultExportFilename(): String {
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    return "mpvrx_settings_${dateFormat.format(Date())}.xml"
  }

  data class ImportStats(
    var imported: Int = 0,
    var failed: Int = 0,
    var version: String = "unknown",
    val errors: MutableList<String> = mutableListOf(),
  )

  data class ExportStats(
    val totalExported: Int,
    val exportedKeys: List<String>,
  )
}

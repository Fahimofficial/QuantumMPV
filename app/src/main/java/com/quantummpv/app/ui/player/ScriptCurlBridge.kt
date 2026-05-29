package com.quantummpv.app.ui.player

import android.util.Log
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Bridges HTTP requests from Lua/JS scripts to OkHttp.
 *
 * Scripts write a JSON request to `user-data/mpvrx/curl_request`.
 * The bridge executes the request asynchronously and writes the result
 * back to `user-data/mpvrx/curl_response`, which scripts observe.
 *
 * ## Request format (JSON string written by the script)
 * ```json
 * {
 *   "id":      "unique-callback-id",
 *   "url":     "https://example.com/api",
 *   "method":  "GET",            // optional, default "GET"
 *   "headers": {                 // optional
 *     "Authorization": "Bearer token",
 *     "Accept": "application/json"
 *   },
 *   "body":    "raw string body", // optional, used for POST/PUT/PATCH
 *   "content_type": "application/json", // optional, default "text/plain"
 *   "timeout": 30                // optional, seconds, default 30, max 120
 * }
 * ```
 *
 * ## Response format (JSON string written to `user-data/mpvrx/curl_response`)
 * ```json
 * {
 *   "id":      "unique-callback-id",
 *   "status":  200,
 *   "body":    "response body as string",
 *   "headers": {
 *     "Content-Type": "application/json"
 *   },
 *   "error":   null              // non-null string on network/timeout errors
 * }
 * ```
 *
 * ## Lua usage example
 * ```lua
 * local json = require("mp.utils")
 *
 * -- Observe the response property once
 * mp.observe_property("user-data/mpvrx/curl_response", "string", function(name, value)
 *     if value == nil or value == "" then return end
 *     local res = json.parse_json(value)
 *     if res and res.id == "my-request-1" then
 *         mp.set_property("user-data/mpvrx/show_text", "Status: " .. tostring(res.status))
 *     end
 * end)
 *
 * -- Fire a GET request
 * mp.set_property("user-data/mpvrx/curl_request", json.format_json({
 *     id     = "my-request-1",
 *     url    = "https://api.example.com/data",
 *     method = "GET",
 * }))
 * ```
 *
 * ## JS usage example
 * ```javascript
 * mp.observe_property("user-data/mpvrx/curl_response", "string", function(name, value) {
 *     if (!value) return;
 *     const res = JSON.parse(value);
 *     if (res.id === "req-1") {
 *         mp.set_property("user-data/mpvrx/show_text", "Got: " + res.status);
 *     }
 * });
 *
 * mp.set_property("user-data/mpvrx/curl_request", JSON.stringify({
 *     id: "req-1",
 *     url: "https://api.example.com/data",
 *     method: "GET",
 *     headers: { "Accept": "application/json" }
 * }));
 * ```
 */
class ScriptCurlBridge(
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "ScriptCurlBridge"
        private const val RESPONSE_PROPERTY = "user-data/mpvrx/curl_response"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val MAX_TIMEOUT_SECONDS = 120L
        private const val MAX_RESPONSE_BODY_BYTES = 2 * 1024 * 1024 // 2 MB
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Shared client; per-request timeouts are applied via a copy()
    private val baseClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Serializable
    private data class CurlRequest(
        val id: String,
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val content_type: String = "text/plain; charset=utf-8",
        val timeout: Int = DEFAULT_TIMEOUT_SECONDS.toInt(),
    )

    @Serializable
    private data class CurlResponse(
        val id: String,
        val status: Int,
        val body: String,
        val headers: Map<String, String>,
        val error: String?,
    )

    /**
     * Parses the JSON payload written by the script and dispatches the HTTP
     * request on the IO dispatcher. The result is written back to mpv's
     * `user-data/mpvrx/curl_response` property on the main thread.
     */
    fun handleRequest(rawJson: String) {
        val request = runCatching {
            json.decodeFromString<CurlRequest>(rawJson)
        }.getOrElse { e ->
            Log.w(TAG, "Failed to parse curl_request JSON: ${e.message}")
            writeErrorResponse(id = "unknown", error = "Invalid request JSON: ${e.message}")
            return
        }

        if (request.url.isBlank()) {
            writeErrorResponse(id = request.id, error = "URL must not be blank")
            return
        }

        val timeoutSec = request.timeout.toLong().coerceIn(1L, MAX_TIMEOUT_SECONDS)

        scope.launch(Dispatchers.IO) {
            val response = executeRequest(request, timeoutSec)
            // Write back on the calling thread — MPVLib.setPropertyString is thread-safe
            writeResponse(response)
        }
    }

    private fun executeRequest(request: CurlRequest, timeoutSec: Long): CurlResponse {
        val client = baseClient.newBuilder()
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .writeTimeout(timeoutSec, TimeUnit.SECONDS)
            .build()

        val okRequest = runCatching {
            buildOkHttpRequest(request)
        }.getOrElse { e ->
            return CurlResponse(
                id = request.id,
                status = 0,
                body = "",
                headers = emptyMap(),
                error = "Failed to build request: ${e.message}",
            )
        }

        return try {
            client.newCall(okRequest).execute().use { response ->
                val rawBody = response.body?.byteStream()?.let { stream ->
                    val buffer = ByteArray(MAX_RESPONSE_BODY_BYTES + 1)
                    var totalRead = 0
                    var bytesRead: Int
                    while (totalRead <= MAX_RESPONSE_BODY_BYTES &&
                        stream.read(buffer, totalRead, buffer.size - totalRead).also { bytesRead = it } != -1
                    ) {
                        totalRead += bytesRead
                    }
                    val truncated = totalRead > MAX_RESPONSE_BODY_BYTES
                    val limit = if (truncated) MAX_RESPONSE_BODY_BYTES else totalRead
                    if (truncated) {
                        Log.w(TAG, "Response body truncated (> $MAX_RESPONSE_BODY_BYTES bytes)")
                    }
                    buffer.toString(Charsets.UTF_8, 0, limit) +
                        if (truncated) "\n[truncated]" else ""
                } ?: ""

                val responseHeaders = response.headers.toMultimap()
                    .mapValues { (_, values) -> values.firstOrNull() ?: "" }

                CurlResponse(
                    id = request.id,
                    status = response.code,
                    body = rawBody,
                    headers = responseHeaders,
                    error = null,
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error for request ${request.id}: ${e.message}")
            CurlResponse(
                id = request.id,
                status = 0,
                body = "",
                headers = emptyMap(),
                error = e.message ?: "Network error",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error for request ${request.id}", e)
            CurlResponse(
                id = request.id,
                status = 0,
                body = "",
                headers = emptyMap(),
                error = e.message ?: "Unexpected error",
            )
        }
    }

    private fun buildOkHttpRequest(request: CurlRequest): Request {
        val builder = Request.Builder().url(request.url)

        // Apply custom headers
        request.headers.forEach { (name, value) ->
            builder.addHeader(name, value)
        }

        val method = request.method.uppercase()
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "HEAD" -> builder.head()
            "POST", "PUT", "PATCH" -> {
                val mediaType = request.content_type.toMediaTypeOrNull()
                val body = (request.body ?: "").toRequestBody(mediaType)
                when (method) {
                    "POST" -> builder.post(body)
                    "PUT" -> builder.put(body)
                    "PATCH" -> builder.patch(body)
                }
            }
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }

        return builder.build()
    }

    private fun writeResponse(response: CurlResponse) {
        val responseJson = runCatching {
            json.encodeToString(response)
        }.getOrElse { e ->
            Log.e(TAG, "Failed to serialize curl response", e)
            // Fallback minimal JSON
            """{"id":"${response.id}","status":0,"body":"","headers":{},"error":"Serialization error: ${e.message}"}"""
        }
        MPVLib.setPropertyString(RESPONSE_PROPERTY, responseJson)
    }

    private fun writeErrorResponse(id: String, error: String) {
        val responseJson = """{"id":"$id","status":0,"body":"","headers":{},"error":"$error"}"""
        MPVLib.setPropertyString(RESPONSE_PROPERTY, responseJson)
    }
}

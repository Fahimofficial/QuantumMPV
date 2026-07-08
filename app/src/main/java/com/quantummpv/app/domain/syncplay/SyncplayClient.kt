package com.quantummpv.app.domain.syncplay

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocketFactory

class SyncplayClient {
    private val TAG = "SyncplayClient"
    private val gson = Gson()
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val writeMutex = Mutex()

    private val _messages = MutableSharedFlow<SyncplayMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<SyncplayMessage> = _messages.asSharedFlow()

    suspend fun connect(host: String, port: Int, useTls: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = if (useTls) {
                SSLSocketFactory.getDefault().createSocket(host, port)
            } else {
                Socket(host, port)
            }
            
            socket?.let {
                it.soTimeout = 0 // Infinite timeout for readLine
                reader = BufferedReader(InputStreamReader(it.inputStream, StandardCharsets.UTF_8))
                writer = BufferedWriter(OutputStreamWriter(it.outputStream, StandardCharsets.UTF_8))
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            return@withContext false
        }
    }

    suspend fun listen() = withContext(Dispatchers.IO) {
        val listeningSocket = socket
        val listeningReader = reader
        try {
            while (true) {
                val line = listeningReader?.readLine() ?: break
                if (line.isBlank()) continue
                
                try {
                    val message = gson.fromJson(line, SyncplayMessage::class.java)
                    _messages.emit(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse message: $line", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Listen error", e)
        } finally {
            if (socket === listeningSocket) {
                disconnect()
            }
        }
    }

    suspend fun sendMessage(message: SyncplayMessage): Boolean {
        return sendRawMessage(gson.toJson(message))
    }

    suspend fun sendRawMessage(json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            writeMutex.withLock {
                val activeWriter = writer ?: return@withLock false
                activeWriter.write(json)
                activeWriter.write("\r\n")
                activeWriter.flush()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send error", e)
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Close error", e)
        }
        socket = null
        reader = null
        writer = null
    }
    
    fun isConnected(): Boolean {
        return socket?.isConnected == true && socket?.isClosed == false
    }
}

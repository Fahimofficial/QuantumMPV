package com.quantummpv.app.domain.syncplay

import com.google.gson.annotations.SerializedName

data class SyncplayMessage(
    @SerializedName("Hello") val hello: HelloMessage? = null,
    @SerializedName("Set") val set: SetMessage? = null,
    @SerializedName("State") val state: StateMessage? = null,
    @SerializedName("Error") val error: ErrorMessage? = null,
    @SerializedName("List") val list: Map<String, Map<String, SyncplayListUser>>? = null,
    @SerializedName("Chat") val chat: ChatMessage? = null
)

data class HelloMessage(
    val username: String? = null,
    val room: Room? = null,
    val version: String? = null,
    val password: String? = null,
    val motd: String? = null,
    val features: Any? = null
)

data class Room(
    val name: String
)

data class StateMessage(
    val ping: Ping? = null,
    val playstate: Playstate? = null,
    val ignoringOnTheFly: Map<String, Int>? = null
)

data class Playstate(
    val paused: Boolean? = null,
    val position: Double? = null,
    val doSeek: Boolean? = null,
    val setBy: String? = null
)

data class Ping(
    val clientLatencyCalculation: Double? = null,
    val clientRtt: Double? = null,
    val serverRtt: Double? = null,
    val latencyCalculation: Double? = null,
    val yourLatency: Double? = null,
    val senderLatency: Double? = null
)

data class SetMessage(
    val user: Map<String, UserEvent>? = null,
    val file: SyncplayFile? = null
)

data class UserEvent(
    val room: Room? = null,
    val event: Event? = null,
    val file: SyncplayFile? = null,
    val controller: Boolean? = null,
    val isReady: Boolean? = null,
    val features: Any? = null
)

data class Event(
    val joined: Boolean? = null,
    val left: Boolean? = null
)

data class SyncplayFile(
    val duration: Double? = null,
    val name: String? = null,
    val size: Any? = null
)

data class ErrorMessage(
    val message: String
)

data class SyncplayListUser(
    val position: Double? = null,
    val file: SyncplayFile? = null,
    val controller: Boolean? = null,
    val isReady: Boolean? = null,
    val features: Any? = null
)

data class ChatMessage(
    val message: String
)

data class SyncplayPlaybackState(
    val position: Double,
    val paused: Boolean
)

package io.github.isramorante.brava.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val J = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val password: String,
    val panelUrl: String = "",
    val panelServerId: String = "",
    val panelKey: String = "",
) {
    val hasPanel: Boolean get() = panelUrl.isNotBlank() && panelServerId.isNotBlank() && panelKey.isNotBlank()
}

@Serializable
data class QuickCmd(val label: String, val command: String)

@Serializable
data class ServerInfo(
    @SerialName("Hostname") val hostname: String = "",
    @SerialName("MaxPlayers") val maxPlayers: Int = 0,
    @SerialName("Players") val players: Int = 0,
    @SerialName("Queued") val queued: Int = 0,
    @SerialName("Joining") val joining: Int = 0,
    @SerialName("EntityCount") val entityCount: Long = 0,
    @SerialName("Uptime") val uptime: Long = 0,
    @SerialName("Map") val map: String = "",
    @SerialName("Framerate") val framerate: Double = 0.0,
    @SerialName("Memory") val memory: Long = 0,
    @SerialName("NetworkIn") val networkIn: Long = 0,
    @SerialName("NetworkOut") val networkOut: Long = 0,
)

@Serializable
data class Player(
    @SerialName("SteamID") val steamId: String = "",
    @SerialName("DisplayName") val name: String = "",
    @SerialName("Ping") val ping: Int = 0,
    @SerialName("Address") val address: String = "",
    @SerialName("ConnectedSeconds") val connectedSeconds: Long = 0,
    @SerialName("Health") val health: Double = 0.0,
)

@Serializable
data class ChatPayload(
    @SerialName("Channel") val channel: Int = 0,
    @SerialName("Message") val message: String = "",
    @SerialName("UserId") val userId: String = "",
    @SerialName("Username") val username: String = "",
    @SerialName("Time") val time: Long = 0,
)

data class Ban(val steamId: String, val name: String, val reason: String)

data class LogLine(val time: Long, val text: String, val type: String)

data class ChatMsg(val time: Long, val name: String, val text: String, val team: Boolean, val mine: Boolean = false)

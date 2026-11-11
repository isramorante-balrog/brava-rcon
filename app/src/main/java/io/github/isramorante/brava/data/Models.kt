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

@Serializable
data class ScheduleItem(
    @SerialName("Time") val time: String = "",
    @SerialName("EveryMinutes") val everyMinutes: Int = 0,
    @SerialName("Command") val command: String = "",
)

/** Settings of the BravaCompanion server plugin, as printed by the brava.status command. */
@Serializable
data class CompanionCfg(
    @SerialName("NtfyServer") val ntfyServer: String = "",
    @SerialName("Topic") val topic: String = "",
    @SerialName("Keywords") val keywords: List<String> = emptyList(),
    @SerialName("NotifyJoins") val notifyJoins: Boolean = false,
    @SerialName("NotifyWipe") val notifyWipe: Boolean = true,
    @SerialName("NotifyStart") val notifyStart: Boolean = true,
    @SerialName("UtcOffsetHours") val utcOffsetHours: Double = 0.0,
    @SerialName("Schedules") val schedules: List<ScheduleItem> = emptyList(),
    @SerialName("FreshHostname") val freshHostname: String = "",
    @SerialName("FreshHours") val freshHours: Double = 48.0,
    @SerialName("Language") val language: String = "en",
    @SerialName("NotifyReports") val notifyReports: Boolean = true,
)

data class MapDot(val steamId: String, val name: String, val x: Float, val z: Float)

data class Ban(val steamId: String, val name: String, val reason: String)

data class LogLine(val time: Long, val text: String, val type: String)

data class ChatMsg(val time: Long, val name: String, val text: String, val team: Boolean, val mine: Boolean = false)

/** An F7 report kept by the companion plugin. Times are Unix seconds. */
@Serializable
data class Report(
    @SerialName("Time") val time: Long = 0,
    @SerialName("ReporterId") val reporterId: String = "",
    @SerialName("ReporterName") val reporterName: String = "",
    @SerialName("TargetId") val targetId: String = "",
    @SerialName("TargetName") val targetName: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("Subject") val subject: String = "",
    @SerialName("Message") val message: String = "",
)

/** A player the companion plugin has seen on the server. */
@Serializable
data class SeenPlayer(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("First") val first: Long = 0,
    @SerialName("Last") val last: Long = 0,
    @SerialName("Visits") val visits: Int = 0,
    @SerialName("Online") val online: Boolean = false,
)

@Serializable
data class WipeEntry(
    @SerialName("Index") val index: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("Seed") val seed: Long = 0,
    @SerialName("Size") val size: Int = 0,
    @SerialName("Cron") val cron: String = "",
    @SerialName("Utc") val utc: Long = 0,
    @SerialName("Full") val full: Boolean = false,
) {
    /** The commands run 5 minutes before the wipe, so the new map starts at this time. */
    val wipeAt: Long get() = if (utc > 0) utc + 5 * 60 else 0
    val forced: Boolean get() = name.startsWith("Forzado", ignoreCase = true) || name.contains("forced", ignoreCase = true)
}

@Serializable
data class WipeCalendar(
    @SerialName("Enabled") val enabled: Boolean = false,
    @SerialName("Offset") val offset: Double = 0.0,
    @SerialName("CurrentSeed") val currentSeed: Long = 0,
    @SerialName("CurrentSize") val currentSize: Int = 0,
    @SerialName("Wipes") val wipes: List<WipeEntry> = emptyList(),
) {
    val next: WipeEntry? get() {
        val now = System.currentTimeMillis() / 1000
        return wipes.filter { it.wipeAt > now }.minByOrNull { it.wipeAt }
    }
}

@Serializable
data class KitItem(
    @SerialName("Container") val container: String = "main",
    @SerialName("Shortname") val shortname: String = "",
    @SerialName("Amount") val amount: Int = 0,
)

@Serializable
data class KitInfo(
    @SerialName("Name") val name: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Permission") val permission: String = "",
    @SerialName("Cooldown") val cooldown: Int = 0,
    @SerialName("MaxUses") val maxUses: Int = 0,
    @SerialName("Hidden") val hidden: Boolean = false,
    @SerialName("Items") val items: List<KitItem> = emptyList(),
)

@Serializable
data class MapChunk(
    @SerialName("Seed") val seed: Long = 0,
    @SerialName("Size") val size: Int = 0,
    @SerialName("Margin") val margin: Int = 0,
    @SerialName("Chunks") val chunks: Int = 0,
    @SerialName("Index") val index: Int = 0,
    @SerialName("Data") val data: String = "",
    @SerialName("Error") val error: String = "",
)

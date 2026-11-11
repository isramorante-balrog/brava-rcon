package io.github.isramorante.brava

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.isramorante.brava.data.Ban
import io.github.isramorante.brava.data.ChatMsg
import io.github.isramorante.brava.data.ChatPayload
import io.github.isramorante.brava.data.CompanionCfg
import io.github.isramorante.brava.data.MapDot
import io.github.isramorante.brava.data.J
import io.github.isramorante.brava.data.LogLine
import io.github.isramorante.brava.data.Player
import io.github.isramorante.brava.data.QuickCmd
import io.github.isramorante.brava.data.ServerInfo
import io.github.isramorante.brava.data.ServerProfile
import io.github.isramorante.brava.data.Store
import io.github.isramorante.brava.panel.PanelApi
import io.github.isramorante.brava.panel.PanelStatus
import io.github.isramorante.brava.rcon.ConnState
import io.github.isramorante.brava.rcon.RconClient
import io.github.isramorante.brava.rcon.RconMessage
import io.github.isramorante.brava.update.Release
import io.github.isramorante.brava.update.Updater
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val rcon = RconClient()
    private val panelApi = PanelApi()
    private val updater = Updater(app)
    private var updateFile: File? = null
    val installedBuild: Int = try { updater.installedBuild } catch (e: Exception) { 0 }

    var servers by mutableStateOf(store.loadServers())
        private set
    var active by mutableStateOf<ServerProfile?>(null)
        private set
    var info by mutableStateOf<ServerInfo?>(null)
        private set
    var players by mutableStateOf<List<Player>>(emptyList())
        private set
    var bans by mutableStateOf<List<Ban>?>(null)
        private set
    var bansRaw by mutableStateOf("")
        private set
    var pluginsText by mutableStateOf("")
        private set
    var quick by mutableStateOf(store.loadQuick())
        private set
    var notes by mutableStateOf(store.loadNotes())
        private set
    var history by mutableStateOf(store.loadHistory())
        private set
    var panel by mutableStateOf<PanelStatus?>(null)
        private set
    var panelError by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
    var lockEnabled by mutableStateOf(store.loadLock())
        private set
    var locked by mutableStateOf(store.loadLock())
        private set
    var permsText by mutableStateOf("")
        private set
    var mapDots by mutableStateOf<List<MapDot>>(emptyList())
        private set
    var worldSize by mutableStateOf(0)
        private set
    var mapError by mutableStateOf<String?>(null)
        private set
    var companion by mutableStateOf<CompanionCfg?>(null)
        private set
    var companionState by mutableStateOf("loading")
        private set

    var update by mutableStateOf<Release?>(null)
        private set
    // idle, checking, current, available, downloading, ready
    var updateState by mutableStateOf("idle")
        private set
    var updateProgress by mutableStateOf(0f)
        private set
    var updateError by mutableStateOf<String?>(null)
        private set
    var updatePrompt by mutableStateOf(false)
        private set

    val console = mutableStateListOf<LogLine>()
    val chat = mutableStateListOf<ChatMsg>()

    init {
        checkUpdate(manual = false)
        viewModelScope.launch { rcon.messages.collect { onMessage(it) } }
        viewModelScope.launch {
            rcon.state.collect { state ->
                if (state == ConnState.Connected) {
                    refresh()
                    loadChatTail()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(5000)
                if (active != null && rcon.state.value == ConnState.Connected) refresh()
            }
        }
        viewModelScope.launch {
            while (true) {
                if (active?.hasPanel == true) refreshPanel()
                delay(10000)
            }
        }
    }

    // ---- server profiles ----

    fun saveServer(profile: ServerProfile) {
        servers = if (servers.any { it.id == profile.id }) servers.map { if (it.id == profile.id) profile else it } else servers + profile
        store.saveServers(servers)
        if (active?.id == profile.id) active = profile
    }

    fun deleteServer(profile: ServerProfile) {
        servers = servers.filter { it.id != profile.id }
        store.saveServers(servers)
    }

    fun open(profile: ServerProfile) {
        active = profile
        info = null
        players = emptyList()
        bans = null
        bansRaw = ""
        pluginsText = ""
        permsText = ""
        mapDots = emptyList()
        worldSize = 0
        companion = null
        companionState = "loading"
        panel = null
        panelError = null
        console.clear()
        chat.clear()
        rcon.connect(profile)
        if (profile.hasPanel) viewModelScope.launch { refreshPanel() }
    }

    fun leave() {
        rcon.disconnect()
        active = null
    }

    // ---- rcon ----

    private fun onMessage(m: RconMessage) {
        if (m.text.isBlank()) return
        if (m.type == "Chat") {
            val payload = try {
                J.decodeFromString<ChatPayload>(m.text)
            } catch (e: Exception) {
                null
            }
            if (payload != null) {
                chat.add(ChatMsg(System.currentTimeMillis(), payload.username, payload.message, payload.channel == 1))
                trim(chat, 500)
                log("[CHAT] ${payload.username}: ${payload.message}", "Chat")
                return
            }
        }
        log(m.text, m.type)
    }

    private fun log(text: String, type: String) {
        console.add(LogLine(System.currentTimeMillis(), text.trimEnd(), type))
        trim(console, 1000)
    }

    private fun <T> trim(list: MutableList<T>, max: Int) {
        while (list.size > max) list.removeAt(0)
    }

    private suspend fun refresh() {
        try {
            info = J.decodeFromString<ServerInfo>(rcon.request("serverinfo"))
            players = J.decodeFromString<List<Player>>(rcon.request("playerlist")).sortedBy { it.ping }
        } catch (e: Exception) {
            // Keep the last known values; the connection state already tells the user.
        }
    }

    private suspend fun loadChatTail() {
        try {
            val items = J.decodeFromString<List<ChatPayload>>(rcon.request("chat.tail 50"))
            if (chat.isEmpty()) {
                chat.addAll(items.map { ChatMsg(if (it.time > 0) it.time * 1000 else System.currentTimeMillis(), it.username, it.message, it.channel == 1) })
            }
        } catch (e: Exception) {
            // Older servers may not answer chat.tail as JSON; live chat still works.
        }
    }

    /** Runs a command typed or tapped by the admin and prints the reply in the console. */
    fun run(command: String, remember: Boolean = false) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        if (remember) {
            history = (listOf(cmd) + history.filter { it != cmd }).take(30)
            store.saveHistory(history)
        }
        log("> $cmd", "Own")
        viewModelScope.launch {
            try {
                val reply = rcon.request(cmd)
                if (reply.isNotBlank()) log(reply, "Generic")
            } catch (e: Exception) {
                log(tr("No reply: ", "Sin respuesta: ") + (e.message ?: ""), "Error")
            }
        }
    }

    fun say(text: String) {
        val msg = text.trim()
        if (msg.isEmpty()) return
        chat.add(ChatMsg(System.currentTimeMillis(), "SERVER", msg, team = false, mine = true))
        run("say $msg")
    }

    private fun q(value: String) = "\"" + value.replace("\"", "'") + "\""

    fun kick(p: Player, reason: String) = run("kick ${p.steamId} ${q(reason.ifBlank { "Kicked by admin" })}")

    fun ban(p: Player, reason: String) {
        val why = reason.ifBlank { "Banned by admin" }
        run("banid ${p.steamId} ${q(p.name)} ${q(why)}")
        run("kick ${p.steamId} ${q(why)}")
        run("server.writecfg")
    }

    fun mute(p: Player, on: Boolean) = run((if (on) "mute " else "unmute ") + p.steamId)

    fun give(p: Player, item: String, amount: String) {
        val name = item.trim()
        if (name.isEmpty()) return
        run("inventory.giveto ${p.steamId} $name ${amount.trim().toIntOrNull() ?: 1}")
    }

    fun unban(b: Ban) {
        run("unban ${b.steamId}")
        run("server.writecfg")
        bans = bans?.filter { it.steamId != b.steamId }
    }

    fun loadBans() {
        viewModelScope.launch {
            try {
                val raw = rcon.request("bans")
                bansRaw = raw
                bans = try {
                    (J.parseToJsonElement(raw) as JsonArray).map {
                        val o = it.jsonObject
                        Ban(
                            steamId = o["steamid"]?.jsonPrimitive?.content ?: "",
                            name = o["username"]?.jsonPrimitive?.content ?: "",
                            reason = o["notes"]?.jsonPrimitive?.content ?: "",
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            } catch (e: Exception) {
                bansRaw = tr("Could not load the ban list: ", "No se pudo cargar la lista de bans: ") + (e.message ?: "")
            }
        }
    }

    fun loadPlugins() {
        viewModelScope.launch {
            pluginsText = try {
                val carbon = rcon.request("c.plugins")
                if (carbon.isNotBlank()) carbon else rcon.request("o.plugins")
            } catch (e: Exception) {
                tr("Could not load plugins: ", "No se pudieron cargar los plugins: ") + (e.message ?: "")
            }
        }
    }

    // ---- app lock ----

    fun changeLock(on: Boolean) {
        lockEnabled = on
        store.saveLock(on)
    }

    fun lockIfEnabled() {
        if (lockEnabled) locked = true
    }

    fun unlocked() {
        locked = false
    }

    // ---- permissions (Carbon) ----

    fun perms(command: String) {
        log("> $command", "Own")
        viewModelScope.launch {
            permsText = try {
                rcon.request(command).ifBlank { tr("Done (no reply).", "Hecho (sin respuesta).") }
            } catch (e: Exception) {
                tr("No reply: ", "Sin respuesta: ") + (e.message ?: "")
            }
        }
    }

    // ---- player map ----

    private val posRegex = Regex("""(7656\d{13})[^(]*\(\s*(-?[\d.]+),\s*(-?[\d.]+),\s*(-?[\d.]+)\s*\)""")

    suspend fun loadMap() {
        try {
            if (worldSize == 0) {
                worldSize = Regex("""(\d{3,5})""").find(rcon.request("worldsize"))?.value?.toIntOrNull() ?: 0
            }
            val raw = rcon.request("playerlistpos")
            mapDots = posRegex.findAll(raw).mapNotNull { m ->
                val id = m.groupValues[1]
                val x = m.groupValues[2].toFloatOrNull()
                val z = m.groupValues[4].toFloatOrNull()
                if (x == null || z == null) null else MapDot(id, players.firstOrNull { it.steamId == id }?.name ?: id.takeLast(5), x, z)
            }.toList()
            mapError = null
        } catch (e: Exception) {
            mapError = e.message ?: e.javaClass.simpleName
        }
    }

    // ---- companion plugin (alerts and schedules) ----

    suspend fun loadCompanion() {
        try {
            val raw = rcon.request("brava.status")
            val start = raw.indexOf('{')
            if (start < 0) {
                companion = null
                companionState = "missing"
            } else {
                companion = J.decodeFromString<CompanionCfg>(raw.substring(start))
                companionState = "ok"
            }
        } catch (e: Exception) {
            companion = null
            companionState = "missing"
        }
    }

    fun companionCmd(command: String) {
        log("> $command", "Own")
        viewModelScope.launch {
            try {
                val reply = rcon.request(command)
                if (reply.isNotBlank()) toast = reply.lines().first().take(80)
            } catch (e: Exception) {
                toast = tr("No reply", "Sin respuesta")
            }
            loadCompanion()
        }
    }

    // ---- app updates ----

    fun checkUpdate(manual: Boolean) {
        if (updateState == "checking" || updateState == "downloading") return
        updateState = "checking"
        updateError = null
        viewModelScope.launch {
            try {
                val newest = updater.latest()
                if (newest != null && newest.build > installedBuild) {
                    if (update?.build != newest.build) updateFile = null
                    update = newest
                    updateState = if (updateFile != null) "ready" else "available"
                    // Ask once per build on start-up; the button in the app always works.
                    if (!manual && store.loadSkippedBuild() != newest.build) updatePrompt = true
                } else {
                    update = null
                    updateState = "current"
                }
            } catch (e: Exception) {
                updateState = "idle"
                if (manual) updateError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun installUpdate() {
        val release = update ?: return
        if (updateState == "downloading") return
        updateError = null
        val ready = updateFile
        if (ready != null && ready.exists()) {
            launchInstaller(ready)
            return
        }
        updateState = "downloading"
        updateProgress = 0f
        viewModelScope.launch {
            try {
                val file = updater.download(release) { updateProgress = it }
                updateFile = file
                updateState = "ready"
                launchInstaller(file)
            } catch (e: Exception) {
                updateState = "available"
                updateError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    private fun launchInstaller(file: File) {
        try {
            updater.install(file)
        } catch (e: Exception) {
            updateError = e.message ?: e.javaClass.simpleName
        }
    }

    fun closeUpdatePrompt() {
        updatePrompt = false
        val release = update ?: return
        // "Cancel" means: do not ask again for this build.
        if (updateState == "available") store.saveSkippedBuild(release.build)
    }

    // ---- local data ----

    fun setNote(steamId: String, note: String) {
        notes = if (note.isBlank()) notes - steamId else notes + (steamId to note.trim())
        store.saveNotes(notes)
    }

    fun addQuick(label: String, command: String) {
        if (label.isBlank() || command.isBlank()) return
        quick = quick + QuickCmd(label.trim(), command.trim())
        store.saveQuick(quick)
    }

    fun removeQuick(item: QuickCmd) {
        quick = quick - item
        store.saveQuick(quick)
    }

    // ---- hosting panel ----

    suspend fun refreshPanel() {
        val profile = active ?: return
        if (!profile.hasPanel) return
        try {
            panel = panelApi.status(profile)
            panelError = null
        } catch (e: Exception) {
            panelError = e.message ?: e.javaClass.simpleName
        }
    }

    fun power(signal: String) {
        val profile = active ?: return
        viewModelScope.launch {
            try {
                panelApi.power(profile, signal)
                toast = tr("Sent: ", "Enviado: ") + signal
                delay(2000)
                refreshPanel()
            } catch (e: Exception) {
                panelError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    override fun onCleared() {
        rcon.disconnect()
    }
}

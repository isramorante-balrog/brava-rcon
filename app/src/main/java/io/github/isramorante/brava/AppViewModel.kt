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
import io.github.isramorante.brava.data.KitInfo
import io.github.isramorante.brava.data.MapChunk
import io.github.isramorante.brava.data.Report
import io.github.isramorante.brava.data.SeenPlayer
import io.github.isramorante.brava.data.WipeCalendar
import io.github.isramorante.brava.watch.ServerWatchWorker
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    // Navigation lives here so the dashboard can open a page under "More".
    var tab by mutableIntStateOf(0)
    var morePage by mutableStateOf("")

    var reports by mutableStateOf<List<Report>?>(null)
        private set
    var reportsSeen by mutableStateOf(0L)
        private set
    var seen by mutableStateOf<List<SeenPlayer>?>(null)
        private set
    var wipes by mutableStateOf<WipeCalendar?>(null)
        private set
    var wipesError by mutableStateOf<String?>(null)
        private set
    var kits by mutableStateOf<List<KitInfo>?>(null)
        private set
    var kitsError by mutableStateOf<String?>(null)
        private set
    var combat by mutableStateOf<Pair<String, String>?>(null)
    var mapImage by mutableStateOf<ImageBitmap?>(null)
        private set
    var mapMargin by mutableIntStateOf(0)
        private set
    var mapImageState by mutableStateOf("idle")
        private set
    var lastReply by mutableStateOf<Pair<String, String>?>(null)
        private set
    var watched by mutableStateOf(store.loadWatched())
        private set

    val unseenReports: Int get() = reports?.count { it.time > reportsSeen } ?: 0

    val console = mutableStateListOf<LogLine>()
    val chat = mutableStateListOf<ChatMsg>()

    init {
        checkUpdate(manual = false)
        ServerWatchWorker.schedule(app, store.loadWatched().isNotEmpty())
        viewModelScope.launch { rcon.messages.collect { onMessage(it) } }
        viewModelScope.launch {
            rcon.state.collect { state ->
                if (state == ConnState.Connected) {
                    refresh()
                    loadChatTail()
                    loadCompanion()
                    if (companion != null) {
                        loadReportsNow()
                        loadWipesNow()
                    }
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
        reports = null
        reportsSeen = store.loadReportsSeen(profile.id)
        seen = null
        wipes = null
        wipesError = null
        kits = null
        kitsError = null
        combat = null
        mapImage = null
        mapMargin = 0
        mapImageState = "idle"
        lastReply = null
        tab = 0
        morePage = ""
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

    /** Runs a dashboard button and keeps its reply to show under the buttons. */
    fun runQuick(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        log("> $cmd", "Own")
        lastReply = cmd to tr("Waiting…", "Esperando…")
        viewModelScope.launch {
            val reply = try {
                rcon.request(cmd).ifBlank { tr("Done (no reply).", "Hecho (sin respuesta).") }
            } catch (e: Exception) {
                tr("No reply: ", "Sin respuesta: ") + (e.message ?: "")
            }
            log(reply, "Generic")
            lastReply = cmd to reply
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

    // ---- moderation through the companion plugin ----

    private suspend fun companionJson(command: String, timeoutMs: Long = 10_000): String? {
        val raw = rcon.request(command, timeoutMs)
        val start = raw.indexOfFirst { it == '[' || it == '{' }
        return if (start < 0) null else raw.substring(start)
    }

    private suspend fun loadReportsNow() {
        try {
            reports = companionJson("brava.reports 200")?.let { J.decodeFromString<List<Report>>(it) }
        } catch (e: Exception) {
            reports = null
        }
    }

    fun loadReports() {
        viewModelScope.launch { loadReportsNow() }
    }

    fun markReportsSeen() {
        val server = active ?: return
        val newest = reports?.maxOfOrNull { it.time } ?: return
        if (newest > reportsSeen) {
            reportsSeen = newest
            store.saveReportsSeen(server.id, newest)
        }
    }

    fun clearReports() {
        viewModelScope.launch {
            try {
                rcon.request("brava.reports.clear")
            } catch (e: Exception) {
                toast = tr("No reply", "Sin respuesta")
            }
            loadReportsNow()
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            seen = try {
                companionJson("brava.history 300")?.let { J.decodeFromString<List<SeenPlayer>>(it) } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** Bans by SteamID, so it also works for players who already left. */
    fun banId(steamId: String, name: String, reason: String) {
        val id = steamId.trim()
        if (!Regex("""\d{17}""").matches(id)) {
            toast = tr("A SteamID has 17 digits", "Un SteamID tiene 17 dígitos")
            return
        }
        val why = reason.ifBlank { "Banned by admin" }
        run("banid $id ${q(name.ifBlank { id })} ${q(why)}")
        run("kick $id ${q(why)}")
        run("server.writecfg")
        bans = null
    }

    fun loadCombat(steamId: String, name: String) {
        combat = name to tr("Loading…", "Cargando…")
        viewModelScope.launch {
            val text = try {
                rcon.request("brava.combatlog $steamId 40")
            } catch (e: Exception) {
                tr("No reply: ", "Sin respuesta: ") + (e.message ?: "")
            }
            combat = name to text.ifBlank { tr("No reply. Is the BravaCompanion plugin installed?", "Sin respuesta. ¿Está instalado el plugin BravaCompanion?") }
        }
    }

    // ---- wipe calendar (Carbon AutoWipe, edited through the companion plugin) ----

    private suspend fun loadWipesNow() {
        try {
            val raw = companionJson("brava.wipes")
            if (raw == null || raw.contains("\"Error\"")) {
                wipes = null
                wipesError = raw ?: tr("No reply", "Sin respuesta")
            } else {
                wipes = J.decodeFromString<WipeCalendar>(raw)
                wipesError = null
            }
        } catch (e: Exception) {
            wipesError = e.message ?: e.javaClass.simpleName
        }
    }

    fun loadWipes() {
        viewModelScope.launch { loadWipesNow() }
    }

    private fun wipeCmd(command: String) {
        log("> $command", "Own")
        viewModelScope.launch {
            try {
                val reply = rcon.request(command, 15_000)
                if (reply.isNotBlank()) toast = reply.lines().first().take(120)
            } catch (e: Exception) {
                toast = tr("No reply", "Sin respuesta")
            }
            loadWipesNow()
        }
    }

    fun setWipeSeed(index: Int, seed: String) = wipeCmd("brava.wipes.seed $index ${seed.trim()}")

    fun setWipeTime(hhmm: String) = wipeCmd("brava.wipes.time ${hhmm.trim()}")

    // ---- kits (Kits plugin, edited through the companion plugin) ----

    private suspend fun loadKitsNow() {
        try {
            val raw = companionJson("brava.kits")
            if (raw == null || raw.startsWith("{")) {
                kits = null
                kitsError = raw ?: tr("No reply", "Sin respuesta")
            } else {
                kits = J.decodeFromString<List<KitInfo>>(raw)
                kitsError = null
            }
        } catch (e: Exception) {
            kitsError = e.message ?: e.javaClass.simpleName
        }
    }

    fun loadKits() {
        viewModelScope.launch { loadKitsNow() }
    }

    /** Each kit change briefly unloads and reloads the Kits plugin on the server. */
    fun kitCmd(command: String) {
        log("> $command", "Own")
        viewModelScope.launch {
            try {
                val reply = rcon.request(command, 20_000)
                if (reply.isNotBlank()) toast = reply.lines().first().take(120)
            } catch (e: Exception) {
                toast = tr("No reply", "Sin respuesta")
            }
            delay(1500)
            loadKitsNow()
        }
    }

    private fun q2(value: String) = "\"" + value.replace("\"", "'") + "\""

    fun setKitField(kit: String, field: String, value: String) = kitCmd("brava.kits.set ${q2(kit)} $field ${q2(value)}")

    fun setKitItem(kit: String, container: String, shortname: String, amount: Int) =
        kitCmd("brava.kits.item ${q2(kit)} $container ${shortname.trim().lowercase()} $amount")

    // ---- terrain image behind the player map ----

    fun loadMapImage() {
        if (mapImageState == "loading" || mapImage != null) return
        mapImageState = "loading"
        viewModelScope.launch {
            try {
                val seed = Regex("""(\d+)""").find(rcon.request("server.seed"))?.value ?: "0"
                val size = Regex("""(\d{3,5})""").find(rcon.request("worldsize"))?.value ?: "0"
                val dir = java.io.File(getApplication<Application>().cacheDir, "maps").apply { mkdirs() }
                val cached = dir.listFiles()?.firstOrNull { it.name.startsWith("map_${seed}_${size}_") }
                val bytes: ByteArray
                val margin: Int
                if (cached != null) {
                    bytes = withContext(Dispatchers.IO) { cached.readBytes() }
                    margin = cached.name.removePrefix("map_${seed}_${size}_").removeSuffix(".jpg").toIntOrNull() ?: 0
                } else {
                    // The first request renders the map on the server, which can take a while.
                    val first = J.decodeFromString<MapChunk>(companionJson("brava.mapimage 0", 90_000) ?: throw IllegalStateException("No reply"))
                    if (first.error.isNotBlank()) throw IllegalStateException(first.error)
                    val text = StringBuilder(first.data)
                    for (i in 1 until first.chunks) {
                        val part = J.decodeFromString<MapChunk>(companionJson("brava.mapimage $i", 30_000) ?: throw IllegalStateException("No reply"))
                        text.append(part.data)
                    }
                    bytes = Base64.decode(text.toString(), Base64.DEFAULT)
                    margin = first.margin
                    withContext(Dispatchers.IO) {
                        dir.listFiles()?.forEach { it.delete() }
                        java.io.File(dir, "map_${first.seed}_${first.size}_${first.margin}.jpg").writeBytes(bytes)
                    }
                }
                val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    ?: throw IllegalStateException("Bad image")
                mapMargin = margin
                mapImage = bitmap.asImageBitmap()
                mapImageState = "ok"
            } catch (e: Exception) {
                mapImageState = "error"
            }
        }
    }

    // ---- background watch ----

    fun setWatched(server: ServerProfile, on: Boolean) {
        watched = if (on) watched + server.id else watched - server.id
        store.saveWatched(watched)
        ServerWatchWorker.schedule(getApplication<Application>(), watched.isNotEmpty())
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

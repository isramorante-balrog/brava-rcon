package io.github.isramorante.brava.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.AppViewModel
import io.github.isramorante.brava.data.Ban
import io.github.isramorante.brava.fmtGb
import io.github.isramorante.brava.tr

@Composable
fun MoreScreen(vm: AppViewModel) {
    val page = vm.morePage
    val back = { vm.morePage = "" }
    BackHandler(enabled = page.isNotEmpty()) { back() }
    when (page) {
        "bans" -> BansPage(vm, back)
        "plugins" -> PluginsPage(vm, back)
        "panel" -> PanelPage(vm, back)
        "perms" -> PermsPage(vm, back)
        "alerts" -> AlertsPage(vm, back)
        "reports" -> ReportsPage(vm, back)
        "wipes" -> WipesPage(vm, back)
        "kits" -> KitsPage(vm, back)
        "edit" -> ServerForm(vm.active, onSave = { vm.saveServer(it); back() }, onCancel = back)
        else -> Column(Modifier.fillMaxSize()) {
            Header(tr("More", "Más"))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val go = { name: String -> vm.morePage = name }
                Label(tr("Moderation", "Moderación"))
                val unseen = vm.unseenReports
                BravaButton(
                    tr("F7 reports", "Reportes F7") + if (unseen > 0) " · $unseen " + tr("new", "nuevos") else "",
                    { go("reports") },
                    Modifier.fillMaxWidth(),
                    if (unseen > 0) Kind.DangerSolid else Kind.Neutral,
                )
                BravaButton(tr("Ban list", "Lista de bans"), { go("bans") }, Modifier.fillMaxWidth())
                BravaButton(tr("Permissions", "Permisos"), { go("perms") }, Modifier.fillMaxWidth())
                Label(tr("Server", "Servidor"), Modifier.padding(top = 8.dp))
                BravaButton(tr("Wipe calendar", "Calendario de wipes"), { go("wipes") }, Modifier.fillMaxWidth())
                BravaButton("Kits", { go("kits") }, Modifier.fillMaxWidth())
                BravaButton("Plugins", { go("plugins") }, Modifier.fillMaxWidth())
                BravaButton(tr("Hosting panel", "Panel del hosting"), { go("panel") }, Modifier.fillMaxWidth())
                Label(tr("Automation", "Automatización"), Modifier.padding(top = 8.dp))
                BravaButton(tr("Alerts and schedules", "Alertas y programados"), { go("alerts") }, Modifier.fillMaxWidth())
                Label(tr("Connection", "Conexión"), Modifier.padding(top = 8.dp))
                BravaButton(tr("Edit this server", "Editar este servidor"), { go("edit") }, Modifier.fillMaxWidth())
                BravaButton(tr("Switch server", "Cambiar de servidor"), { vm.leave() }, Modifier.fillMaxWidth(), Kind.Danger)
                Label(tr("App version", "Versión de la app"), Modifier.padding(top = 8.dp))
                UpdateRow(vm)
                Text(
                    "Brava - Complete Rust RCON · GPL-3.0\n" + tr(
                        "Not affiliated with Facepunch Studios.",
                        "Sin relación con Facepunch Studios.",
                    ),
                    color = C.mute,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
fun SubHeader(title: String, onBack: () -> Unit) {
    Header(title) { BravaButton(tr("Back", "Volver"), onBack, height = 44.dp) }
}

@Composable
private fun BansPage(vm: AppViewModel, onBack: () -> Unit) {
    var removing by remember { mutableStateOf<Ban?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(vm.bans == null) { if (vm.bans == null) vm.loadBans() }
    Column(Modifier.fillMaxSize()) {
        SubHeader(tr("Ban list", "Lista de bans"), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BravaButton(tr("Ban by SteamID", "Banear por SteamID"), { adding = true }, Modifier.fillMaxWidth(), Kind.DangerSolid)
            Text(
                tr("Works for players who already left. Find SteamIDs in Players → History.", "Sirve para jugadores que ya se fueron. Los SteamID están en Jugadores → Historial."),
                color = C.mute,
                fontSize = 13.sp,
            )
            val bans = vm.bans
            when {
                bans == null && vm.bansRaw.isBlank() -> Text(tr("Loading…", "Cargando…"), color = C.mute)
                bans == null -> Text(vm.bansRaw, color = C.soft, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                bans.isEmpty() -> Text(tr("Nobody is banned.", "No hay nadie baneado."), color = C.mute)
                else -> bans.forEach { ban ->
                    Panel(Modifier.fillMaxWidth()) {
                        Text(ban.name.ifBlank { ban.steamId }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(ban.steamId, color = C.mute, fontSize = 14.sp)
                        if (ban.reason.isNotBlank()) Text(ban.reason, color = C.soft, fontSize = 15.sp)
                        BravaButton(tr("Unban", "Quitar ban"), { removing = ban }, kind = Kind.Danger, height = 46.dp)
                    }
                }
            }
        }
    }
    if (adding) {
        PromptDialog(
            title = tr("Ban by SteamID", "Banear por SteamID"),
            labels = listOf("SteamID (17 " + tr("digits", "dígitos") + ")", tr("Name (optional)", "Nombre (opcional)"), tr("Reason", "Motivo")),
            confirm = "Ban",
            onConfirm = { vm.banId(it[0], it[1], it[2]) },
            onDismiss = { adding = false },
        )
    }
    removing?.let { ban ->
        ConfirmDialog(
            title = tr("Unban", "Quitar ban"),
            text = ban.name.ifBlank { ban.steamId },
            confirm = tr("Unban", "Quitar ban"),
            onConfirm = { vm.unban(ban) },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun PluginsPage(vm: AppViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadPlugins() }
    Column(Modifier.fillMaxSize()) {
        SubHeader("Plugins", onBack)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(name, { name = it }, tr("Plugin name", "Nombre del plugin"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BravaButton(tr("Reload", "Recargar"), { vm.run("c.reload $name"); vm.loadPlugins() }, Modifier.weight(1f), height = 46.dp, enabled = name.isNotBlank())
                BravaButton(tr("Load", "Cargar"), { vm.run("c.load $name"); vm.loadPlugins() }, Modifier.weight(1f), height = 46.dp, enabled = name.isNotBlank())
                BravaButton(tr("Unload", "Descargar"), { vm.run("c.unload $name"); vm.loadPlugins() }, Modifier.weight(1f), Kind.Danger, 46.dp, name.isNotBlank())
            }
            Text(
                tr("These buttons use Carbon commands (c.reload). Replies appear in the console.", "Estos botones usan comandos de Carbon (c.reload). Las respuestas salen en la consola."),
                color = C.mute,
                fontSize = 13.sp,
            )
        }
        SelectionContainer(Modifier.weight(1f).fillMaxWidth().background(C.black)) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    vm.pluginsText.ifBlank { tr("Loading…", "Cargando…") },
                    color = C.ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun PanelPage(vm: AppViewModel, onBack: () -> Unit) {
    val server = vm.active ?: return
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshPanel() }
    Column(Modifier.fillMaxSize()) {
        SubHeader(tr("Hosting panel", "Panel del hosting"), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!server.hasPanel) {
                Text(
                    tr(
                        "Add the panel address, the server ID and an API key in \"Edit this server\" to control power from here.",
                        "Agrega la dirección del panel, el ID del servidor y una API key en \"Editar este servidor\" para controlar el encendido desde aquí.",
                    ),
                    color = C.soft,
                )
                return@Column
            }
            val error = vm.panelError
            if (error != null) Text(tr("Panel error: ", "Error del panel: ") + error, color = C.rustText, fontSize = 14.sp)
            Label(tr("Power · works even if RCON is down", "Encendido · funciona aunque el RCON esté caído"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BravaButton(tr("Start", "Encender"), { vm.power("start") }, Modifier.weight(1f), Kind.Go)
                BravaButton(tr("Restart", "Reiniciar"), { confirm = "restart" }, Modifier.weight(1f))
                BravaButton(tr("Stop", "Apagar"), { confirm = "stop" }, Modifier.weight(1f), Kind.Danger)
            }
            val status = vm.panel
            if (status != null) {
                Panel(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("STATE", "ESTADO"), fontWeight = FontWeight.Bold)
                        Text(status.state.uppercase(), color = if (status.state == "running") C.oliveText else C.yellow, fontWeight = FontWeight.Bold)
                    }
                    Meter("RAM", "${fmtGb(status.memoryBytes)} / ${fmtGb(status.memoryLimitBytes)} GB", status.memoryBytes, status.memoryLimitBytes)
                    Meter("CPU", "${status.cpuPercent.toInt()}%", status.cpuPercent.toLong(), 100)
                    Meter(tr("DISK", "DISCO"), "${fmtGb(status.diskBytes)} / ${fmtGb(status.diskLimitBytes)} GB", status.diskBytes, status.diskLimitBytes)
                }
            } else if (error == null) {
                Text(tr("Loading…", "Cargando…"), color = C.mute)
            }
        }
    }
    confirm?.let { signal ->
        ConfirmDialog(
            title = if (signal == "stop") tr("Stop server", "Apagar servidor") else tr("Restart server", "Reiniciar servidor"),
            text = tr(
                "This acts immediately, with no warning to players.",
                "Esto actúa de inmediato, sin aviso a los jugadores.",
            ),
            confirm = if (signal == "stop") tr("Stop", "Apagar") else tr("Restart", "Reiniciar"),
            onConfirm = { vm.power(signal) },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun Meter(label: String, value: String, used: Long, limit: Long) {
    val fraction = if (limit > 0) used.toFloat() / limit.toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(value, color = C.soft)
        }
        Bar(fraction, if (fraction > 0.85f) C.rust else C.olive)
    }
}

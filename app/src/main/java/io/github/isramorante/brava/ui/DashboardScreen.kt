package io.github.isramorante.brava.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.AppViewModel
import io.github.isramorante.brava.data.QuickCmd
import io.github.isramorante.brava.fmtDuration
import io.github.isramorante.brava.fmtGb
import io.github.isramorante.brava.rcon.ConnState
import io.github.isramorante.brava.tr

@Composable
fun ConnBadge(vm: AppViewModel) {
    val state by vm.rcon.state.collectAsState()
    val (text, color, bg) = when (state) {
        ConnState.Connected -> Triple(tr("ONLINE", "EN LÍNEA"), C.oliveText, C.olive)
        ConnState.Connecting -> Triple(tr("CONNECTING", "CONECTANDO"), C.yellow, C.yellow)
        ConnState.Disconnected -> Triple(tr("OFFLINE", "SIN CONEXIÓN"), C.rustText, C.rust)
    }
    Row(
        Modifier.border(1.dp, bg).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(bg))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Label(label)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sub, color = C.soft, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val server = vm.active ?: return
    val info = vm.info
    val error by vm.rcon.error.collectAsState()
    val state by vm.rcon.state.collectAsState()
    var sayOpen by remember { mutableStateOf(false) }
    var restartOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<QuickCmd?>(null) }
    var editQuick by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Header(server.name) { ConnBadge(vm) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state != ConnState.Connected && error != null) {
                Text(tr("Cannot connect: ", "No se pudo conectar: ") + error, color = C.rustText, fontSize = 14.sp)
            }
            if (info != null && info.hostname.isNotBlank()) {
                Text(info.hostname, color = C.soft, fontSize = 14.sp, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(
                    tr("Players", "Jugadores"),
                    if (info == null) "–" else "${info.players} / ${info.maxPlayers}",
                    if (info == null) "" else tr("${info.queued} queued · ${info.joining} joining", "${info.queued} en cola · ${info.joining} entrando"),
                    Modifier.weight(1f),
                )
                Stat("FPS", if (info == null) "–" else info.framerate.toInt().toString(), tr("Server frame rate", "FPS del servidor"), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val host = vm.panel
                if (host != null && host.memoryLimitBytes > 0) {
                    Stat(
                        "RAM",
                        "${fmtGb(host.memoryBytes)} / ${fmtGb(host.memoryLimitBytes)} GB",
                        tr("Whole server, from the hosting panel", "Todo el servidor, según el panel"),
                        Modifier.weight(1f),
                    )
                } else {
                    Stat(
                        tr("Game memory", "Memoria del juego"),
                        if (info == null) "–" else "${info.memory} MB",
                        tr("Rust only, not the whole server", "Solo Rust, no todo el servidor"),
                        Modifier.weight(1f),
                    )
                }
                Stat(
                    tr("Uptime", "Encendido"),
                    if (info == null) "–" else fmtDuration(info.uptime),
                    if (info == null) "" else tr("${info.entityCount} entities", "${info.entityCount} entidades"),
                    Modifier.weight(1f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(tr("Quick commands", "Comandos rápidos"), Modifier.weight(1f))
                BravaButton(if (editQuick) tr("Done", "Listo") else tr("Edit", "Editar"), { editQuick = !editQuick }, height = 44.dp)
            }
            vm.quick.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { item ->
                        BravaButton(
                            if (editQuick) "✕ ${item.label}" else item.label,
                            { if (editQuick) removing = item else vm.run(item.command) },
                            Modifier.weight(1f),
                            if (editQuick) Kind.Danger else Kind.Neutral,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (editQuick) BravaButton(tr("Add command", "Agregar comando"), { addOpen = true }, Modifier.fillMaxWidth())

            Label(tr("Server", "Servidor"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BravaButton(tr("Save", "Guardar"), { vm.run("server.save") }, Modifier.weight(1f), Kind.Go)
                BravaButton(tr("Say", "Decir"), { sayOpen = true }, Modifier.weight(1f))
                BravaButton(tr("Restart", "Reiniciar"), { restartOpen = true }, Modifier.weight(1f), Kind.Danger)
            }

            val last = vm.console.lastOrNull()
            if (last != null) {
                Panel(Modifier.fillMaxWidth()) {
                    Label(tr("Latest", "Lo último"))
                    Text(last.text, color = lineColor(last.type), fontSize = 14.sp, maxLines = 3)
                }
            }
        }
    }

    if (sayOpen) {
        PromptDialog(
            title = tr("Message to everyone", "Mensaje para todos"),
            labels = listOf(tr("Message", "Mensaje")),
            confirm = tr("Send", "Enviar"),
            onConfirm = { vm.say(it[0]) },
            onDismiss = { sayOpen = false },
        )
    }
    if (restartOpen) {
        ConfirmDialog(
            title = tr("Restart server", "Reiniciar servidor"),
            text = tr(
                "Players get a 60 second warning, then everyone is disconnected.",
                "Los jugadores reciben un aviso de 60 segundos y luego se desconecta a todos.",
            ),
            confirm = tr("Restart", "Reiniciar"),
            onConfirm = { vm.run("restart 60") },
            onDismiss = { restartOpen = false },
        )
    }
    if (addOpen) {
        PromptDialog(
            title = tr("New quick command", "Nuevo comando rápido"),
            labels = listOf(tr("Button label", "Texto del botón"), tr("Command", "Comando")),
            confirm = tr("Add", "Agregar"),
            onConfirm = { vm.addQuick(it[0], it[1]) },
            onDismiss = { addOpen = false },
        )
    }
    removing?.let { item ->
        ConfirmDialog(
            title = tr("Remove button", "Quitar botón"),
            text = "${item.label}: ${item.command}",
            confirm = tr("Remove", "Quitar"),
            onConfirm = { vm.removeQuick(item) },
            onDismiss = { removing = null },
        )
    }
}

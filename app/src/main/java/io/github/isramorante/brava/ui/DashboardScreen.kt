package io.github.isramorante.brava.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
private fun Stat(label: String, value: String, sub: String, modifier: Modifier = Modifier, fraction: Float? = null) {
    Panel(modifier.fillMaxHeight()) {
        Label(label)
        // Long values get a smaller size so they never wrap or get cut in a half-width card.
        Text(value, fontSize = if (value.length > 7) 24.sp else 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (fraction != null) Bar(fraction, if (fraction > 0.85f) C.rust else C.olive)
        Text(sub, color = C.soft, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(
                    tr("Players", "Jugadores"),
                    if (info == null) "–" else "${info.players} / ${info.maxPlayers}",
                    if (info == null) "" else tr("${info.queued} queued · ${info.joining} joining", "${info.queued} en cola · ${info.joining} entrando"),
                    Modifier.weight(1f),
                )
                Stat("FPS", if (info == null) "–" else info.framerate.toInt().toString(), tr("Server frame rate", "FPS del servidor"), Modifier.weight(1f))
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val host = vm.panel
                if (host != null && host.memoryLimitBytes > 0) {
                    Stat(
                        "RAM",
                        "${fmtGb(host.memoryBytes)} GB",
                        tr("of ${fmtGb(host.memoryLimitBytes)} GB", "de ${fmtGb(host.memoryLimitBytes)} GB"),
                        Modifier.weight(1f),
                        host.memoryBytes.toFloat() / host.memoryLimitBytes,
                    )
                } else {
                    Stat(
                        tr("Game memory", "Memoria del juego"),
                        if (info == null) "–" else "${info.memory} MB",
                        tr("Rust only", "Solo Rust"),
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

            val next = vm.wipes?.next
            val unseen = vm.unseenReports
            if (unseen > 0) {
                BravaButton(
                    tr("$unseen new F7 reports", "$unseen reportes F7 nuevos"),
                    { vm.tab = 5; vm.morePage = "reports" },
                    Modifier.fillMaxWidth(),
                    Kind.DangerSolid,
                )
            }
            if (next != null) {
                Row(
                    Modifier.fillMaxWidth().background(C.panel).border(1.dp, C.line)
                        .clickable { vm.tab = 5; vm.morePage = "wipes" }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label(if (next.forced) tr("Next wipe · forced", "Próximo wipe · forzado") else tr("Next wipe", "Próximo wipe"))
                        Text(fmtDate(next.wipeAt), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(tr("Seed ${next.seed} · size ${next.size}", "Semilla ${next.seed} · tamaño ${next.size}"), color = C.soft, fontSize = 14.sp)
                    }
                    Text("›", color = C.mute, fontSize = 28.sp)
                }
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
                            { if (editQuick) removing = item else vm.runQuick(item.command) },
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

            val reply = vm.lastReply
            if (reply != null) {
                Column(
                    Modifier.fillMaxWidth().background(C.black).border(1.dp, C.line).clickable { vm.tab = 3 }.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(tr("Reply to ", "Respuesta a ") + reply.first, Modifier.weight(1f))
                        Text(tr("Console ›", "Consola ›"), color = C.mute, fontSize = 13.sp)
                    }
                    Text(reply.second, color = C.ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 14, overflow = TextOverflow.Ellipsis)
                }
            } else {
                val last = vm.console.lastOrNull()
                if (last != null) {
                    Panel(Modifier.fillMaxWidth()) {
                        Label(tr("Latest", "Lo último"))
                        Text(last.text, color = lineColor(last.type), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
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

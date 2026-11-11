package io.github.isramorante.brava.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.github.isramorante.brava.data.ServerProfile
import io.github.isramorante.brava.tr
import java.util.UUID

@Composable
fun ServersScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ServerProfile?>(null) }

    val form = editing
    if (form != null || creating) {
        ServerForm(
            initial = form,
            onSave = { vm.saveServer(it); editing = null; creating = false },
            onCancel = { editing = null; creating = false },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Header("Brava RCON")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label(tr("Your servers", "Tus servidores"))
            if (vm.servers.isEmpty()) {
                Text(
                    tr(
                        "No servers yet. Add one with its IP, RCON port and RCON password.",
                        "Aún no hay servidores. Agrega uno con su IP, puerto RCON y contraseña RCON.",
                    ),
                    color = C.soft,
                )
            }
            vm.servers.forEach { server ->
                Column(
                    Modifier.fillMaxWidth().background(C.panel).border(1.dp, C.line).clickable { vm.open(server) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(server.name.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${server.host}:${server.port}", color = C.mute, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BravaButton(tr("Connect", "Conectar"), { vm.open(server) }, Modifier.weight(1f), Kind.Go, 48.dp)
                        BravaButton(tr("Edit", "Editar"), { editing = server }, Modifier.weight(1f), height = 48.dp)
                        BravaButton(tr("Delete", "Borrar"), { deleting = server }, Modifier.weight(1f), Kind.Danger, 48.dp)
                    }
                }
            }
            BravaButton(tr("Add server", "Agregar servidor"), { creating = true }, Modifier.fillMaxWidth())
        }
    }

    deleting?.let { server ->
        ConfirmDialog(
            title = tr("Delete server", "Borrar servidor"),
            text = tr("Remove \"${server.name}\" from this phone?", "¿Quitar \"${server.name}\" de este teléfono?"),
            confirm = tr("Delete", "Borrar"),
            onConfirm = { vm.deleteServer(server) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun ServerForm(initial: ServerProfile?, onSave: (ServerProfile) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var panelUrl by remember { mutableStateOf(initial?.panelUrl ?: "") }
    var panelId by remember { mutableStateOf(initial?.panelServerId ?: "") }
    var panelKey by remember { mutableStateOf(initial?.panelKey ?: "") }
    val portNumber = port.trim().toIntOrNull()
    val valid = name.isNotBlank() && host.isNotBlank() && portNumber != null && portNumber in 1..65535 && password.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Header(if (initial == null) tr("New server", "Nuevo servidor") else tr("Edit server", "Editar servidor"))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label("RCON")
            Field(name, { name = it }, tr("Name", "Nombre"))
            Field(host, { host = it }, tr("IP or host", "IP o host"))
            Field(port, { port = it }, tr("RCON port", "Puerto RCON"), number = true)
            Field(password, { password = it }, tr("RCON password", "Contraseña RCON"), password = true)
            Text(
                tr(
                    "Rust's RCON is not encrypted. Avoid using it on public Wi-Fi.",
                    "El RCON de Rust no va cifrado. Evita usarlo en Wi-Fi públicas.",
                ),
                color = C.mute,
                fontSize = 14.sp,
            )
            Label(tr("Hosting panel (optional)", "Panel del hosting (opcional)"), Modifier.padding(top = 8.dp))
            Field(panelUrl, { panelUrl = it }, tr("Panel address", "Dirección del panel"))
            Field(panelId, { panelId = it }, tr("Server ID in the panel", "ID del servidor en el panel"))
            Field(panelKey, { panelKey = it }, tr("Panel API key", "API key del panel"), password = true)
            Text(
                tr(
                    "For Pterodactyl panels. Lets you start, stop and restart the server even when RCON is down.",
                    "Para paneles Pterodactyl. Permite encender, apagar y reiniciar aunque el RCON esté caído.",
                ),
                color = C.mute,
                fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BravaButton(tr("Cancel", "Cancelar"), onCancel, Modifier.weight(1f))
                BravaButton(
                    tr("Save", "Guardar"),
                    {
                        onSave(
                            ServerProfile(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                host = host.trim(),
                                port = portNumber ?: 0,
                                password = password,
                                panelUrl = panelUrl.trim(),
                                panelServerId = panelId.trim(),
                                panelKey = panelKey.trim(),
                            )
                        )
                    },
                    Modifier.weight(1f),
                    Kind.Go,
                    enabled = valid,
                )
            }
        }
    }
}

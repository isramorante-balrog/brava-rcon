package io.github.isramorante.brava.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.AppViewModel
import io.github.isramorante.brava.data.Player
import io.github.isramorante.brava.fmtDuration
import io.github.isramorante.brava.tr

@Composable
fun PlayersScreen(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<Pair<String, Player>?>(null) }
    // For players found in the history, who may be offline: kind, SteamID, name.
    var idDialog by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val list = vm.players.filter { query.isBlank() || it.name.contains(query, true) || it.steamId.contains(query) }

    Column(Modifier.fillMaxSize()) {
        Header(tr("Players", "Jugadores")) {
            Text(tr("${vm.players.size} online", "${vm.players.size} conectados"), color = C.soft, fontSize = 15.sp)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BravaButton(tr("Online", "Conectados"), { showHistory = false }, Modifier.weight(1f), if (!showHistory) Kind.DangerSolid else Kind.Neutral, 44.dp)
                BravaButton(tr("History", "Historial"), { showHistory = true }, Modifier.weight(1f), if (showHistory) Kind.DangerSolid else Kind.Neutral, 44.dp)
            }
            Field(query, { query = it }, tr("Name or SteamID", "Nombre o SteamID"))
        }
        if (showHistory) {
            Box(Modifier.weight(1f)) {
                HistoryList(vm, query, onBan = { id, name -> idDialog = Triple("ban", id, name) }, onNote = { id, name -> idDialog = Triple("note", id, name) })
            }
        } else if (list.isEmpty()) {
            Text(
                if (vm.players.isEmpty()) tr("Nobody is connected.", "No hay nadie conectado.") else tr("No matches.", "Sin resultados."),
                Modifier.padding(20.dp),
                color = C.mute,
            )
        }
        if (!showHistory) LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.steamId }) { p ->
                val open = openId == p.steamId
                Column(Modifier.fillMaxWidth().background(if (open) C.panel else C.bg)) {
                    if (open) Box(Modifier.fillMaxWidth().height(2.dp).background(C.rust))
                    Row(
                        Modifier.fillMaxWidth().clickable { openId = if (open) null else p.steamId }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(40.dp).background(if (open) C.rust else C.line), contentAlignment = Alignment.Center) {
                            Text(p.name.take(2).uppercase(), color = if (open) C.black else C.ink, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                fmtDuration(p.connectedSeconds) + " · " + tr("HP ", "Vida ") + p.health.toInt(),
                                color = C.mute,
                                fontSize = 14.sp,
                            )
                        }
                        Text(
                            "${p.ping} ms",
                            color = if (p.ping < 80) C.oliveText else if (p.ping < 150) C.yellow else C.rustText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (open) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("SteamID ${p.steamId} · IP ${p.address.substringBefore(':')}", color = C.soft, fontSize = 14.sp)
                            val note = vm.notes[p.steamId]
                            if (note != null) Text(tr("Note: ", "Nota: ") + note, color = C.soft, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BravaButton(tr("Give item", "Dar item"), { dialog = "give" to p }, Modifier.weight(1f), height = 48.dp)
                                BravaButton(tr("Note", "Nota"), { dialog = "note" to p }, Modifier.weight(1f), height = 48.dp)
                                BravaButton(
                                    "Steam",
                                    {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/profiles/${p.steamId}")))
                                        } catch (e: Exception) {
                                            vm.toast = tr("No browser available", "No hay navegador")
                                        }
                                    },
                                    Modifier.weight(1f),
                                    height = 48.dp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BravaButton(tr("Mute", "Silenciar"), { vm.mute(p, true) }, Modifier.weight(1f), height = 48.dp)
                                BravaButton(tr("Unmute", "Desilenciar"), { vm.mute(p, false) }, Modifier.weight(1f), height = 48.dp)
                                BravaButton("Combat log", { vm.loadCombat(p.steamId, p.name) }, Modifier.weight(1f), height = 48.dp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BravaButton(tr("Kick", "Expulsar"), { dialog = "kick" to p }, Modifier.weight(1f), Kind.Danger, 48.dp)
                                BravaButton("Ban", { dialog = "ban" to p }, Modifier.weight(1f), Kind.DangerSolid, 48.dp)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                }
            }
        }
    }

    CombatDialog(vm)
    idDialog?.let { (kind, id, name) ->
        val close = { idDialog = null }
        if (kind == "ban") {
            PromptDialog(
                title = tr("Ban $name", "Banear a $name"),
                labels = listOf(tr("Reason (saved with the ban)", "Motivo (se guarda con el ban)")),
                confirm = "Ban",
                onConfirm = { vm.banId(id, name, it[0]) },
                onDismiss = close,
            )
        } else {
            PromptDialog(
                title = tr("Note on $name", "Nota sobre $name"),
                labels = listOf(tr("Only stored on this phone", "Solo se guarda en este teléfono")),
                defaults = listOf(vm.notes[id] ?: ""),
                confirm = tr("Save", "Guardar"),
                singleLine = false,
                onConfirm = { vm.setNote(id, it[0]) },
                onDismiss = close,
            )
        }
    }
    dialog?.let { (kind, p) ->
        val close = { dialog = null }
        when (kind) {
            "kick" -> PromptDialog(
                title = tr("Kick ${p.name}", "Expulsar a ${p.name}"),
                labels = listOf(tr("Reason", "Motivo")),
                confirm = tr("Kick", "Expulsar"),
                onConfirm = { vm.kick(p, it[0]) },
                onDismiss = close,
            )
            "ban" -> PromptDialog(
                title = tr("Ban ${p.name}", "Banear a ${p.name}"),
                labels = listOf(tr("Reason (saved with the ban)", "Motivo (se guarda con el ban)")),
                confirm = "Ban",
                onConfirm = { vm.ban(p, it[0]) },
                onDismiss = close,
            )
            "give" -> PromptDialog(
                title = tr("Give item to ${p.name}", "Dar item a ${p.name}"),
                labels = listOf(tr("Item short name (e.g. scrap)", "Nombre corto del item (ej. scrap)"), tr("Amount", "Cantidad")),
                defaults = listOf("", "1"),
                confirm = tr("Give", "Dar"),
                onConfirm = { vm.give(p, it[0], it[1]) },
                onDismiss = close,
            )
            else -> PromptDialog(
                title = tr("Note on ${p.name}", "Nota sobre ${p.name}"),
                labels = listOf(tr("Only stored on this phone", "Solo se guarda en este teléfono")),
                defaults = listOf(vm.notes[p.steamId] ?: ""),
                confirm = tr("Save", "Guardar"),
                singleLine = false,
                onConfirm = { vm.setNote(p.steamId, it[0]) },
                onDismiss = close,
            )
        }
    }
}

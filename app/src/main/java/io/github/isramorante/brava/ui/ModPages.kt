package io.github.isramorante.brava.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.AppViewModel
import io.github.isramorante.brava.data.KitInfo
import io.github.isramorante.brava.data.WipeEntry
import io.github.isramorante.brava.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- shared helpers

fun fmtAgo(unixSeconds: Long): String {
    if (unixSeconds <= 0) return "–"
    val s = (System.currentTimeMillis() / 1000 - unixSeconds).coerceAtLeast(0)
    return when {
        s < 90 -> tr("just now", "hace un momento")
        s < 3600 -> tr("${s / 60} min ago", "hace ${s / 60} min")
        s < 86400 -> tr("${s / 3600} h ago", "hace ${s / 3600} h")
        else -> tr("${s / 86400} d ago", "hace ${s / 86400} d")
    }
}

fun fmtDate(unixSeconds: Long): String {
    if (unixSeconds <= 0) return "–"
    return SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(Date(unixSeconds * 1000))
}

fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: Exception) {
    false
}

/** Shown on pages that need the BravaCompanion plugin when the server does not have it. */
@Composable
fun CompanionMissing(vm: AppViewModel) {
    Text(
        if (vm.companionState == "loading") tr("Checking the server…", "Revisando el servidor…")
        else tr(
            "This needs the BravaCompanion plugin on the server. Copy BravaCompanion.cs from the server-plugin folder of the Brava repository into carbon/plugins (or oxide/plugins), then come back.",
            "Esto necesita el plugin BravaCompanion en el servidor. Copia BravaCompanion.cs de la carpeta server-plugin del repositorio de Brava a carbon/plugins (u oxide/plugins) y vuelve aquí.",
        ),
        color = C.soft,
    )
    BravaButton(tr("Check again", "Revisar de nuevo"), { vm.companionCmd("brava.status") }, Modifier.fillMaxWidth())
}

/** A read-only dialog for long text such as a combat log. */
@Composable
fun TextDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panel,
        shape = Square,
        title = { Text(title.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, color = C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            SelectionContainer {
                Box(Modifier.heightIn(max = 460.dp).background(C.black).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(10.dp)) {
                    Text(text, color = C.ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, softWrap = false)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("CLOSE", "CERRAR"), color = C.rustText, fontFamily = Condensed, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun CombatDialog(vm: AppViewModel) {
    val (name, text) = vm.combat ?: return
    TextDialog(tr("Combat log · ", "Combat log · ") + name, text) { vm.combat = null }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text.uppercase(),
        Modifier.border(1.dp, color).padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

// ---------------------------------------------------------------- F7 reports

private fun reportType(type: String): String = when (type.lowercase()) {
    "cheat" -> tr("Cheating", "Trampa")
    "abusive" -> tr("Abusive", "Abuso")
    "name" -> tr("Name", "Nombre")
    "spam" -> "Spam"
    else -> type.ifBlank { tr("Other", "Otro") }
}

@Composable
fun ReportsPage(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val seenAtOpen = remember { vm.reportsSeen }
    var banning by remember { mutableStateOf<Pair<String, String>?>(null) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadReports() }
    LaunchedEffect(vm.reports) { if (vm.reports != null) vm.markReportsSeen() }

    Column(Modifier.fillMaxSize()) {
        SubHeader(tr("F7 reports", "Reportes F7"), onBack)
        val list = vm.reports
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                vm.companion == null -> CompanionMissing(vm)
                list == null -> Text(tr("Loading…", "Cargando…"), color = C.mute)
                list.isEmpty() -> Text(
                    tr(
                        "No reports yet. When a player presses F7 in game, the report shows up here and, if alerts are on, on your phone.",
                        "Aún no hay reportes. Cuando un jugador presiona F7 en el juego, el reporte aparece aquí y, si tienes las alertas activas, en tu teléfono.",
                    ),
                    color = C.soft,
                )
                else -> {
                    list.forEach { r ->
                        val fresh = r.time > seenAtOpen
                        Column(
                            Modifier.fillMaxWidth().background(C.panel).border(if (fresh) 2.dp else 1.dp, if (fresh) C.rust else C.line).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(r.targetName.ifBlank { r.targetId }, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (fresh) Badge(tr("New", "Nuevo"), C.rustText)
                                Badge(reportType(r.type), C.yellow)
                            }
                            Text(tr("By ", "Por ") + r.reporterName + " · " + fmtAgo(r.time), color = C.mute, fontSize = 14.sp)
                            if (r.subject.isNotBlank()) Text(r.subject, fontWeight = FontWeight.Bold)
                            if (r.message.isNotBlank()) Text(r.message, color = C.soft, fontSize = 15.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BravaButton("Combat log", { vm.loadCombat(r.targetId, r.targetName) }, Modifier.weight(1f), height = 46.dp)
                                BravaButton("Steam", {
                                    if (!openUrl(context, "https://steamcommunity.com/profiles/${r.targetId}")) vm.toast = tr("No browser available", "No hay navegador")
                                }, Modifier.weight(1f), height = 46.dp)
                                BravaButton("Ban", { banning = r.targetId to r.targetName }, Modifier.weight(1f), Kind.DangerSolid, 46.dp)
                            }
                        }
                    }
                    BravaButton(tr("Clear all reports", "Borrar todos los reportes"), { clearing = true }, Modifier.fillMaxWidth(), Kind.Danger)
                }
            }
        }
    }
    CombatDialog(vm)
    banning?.let { (id, name) ->
        PromptDialog(
            title = tr("Ban $name", "Banear a $name"),
            labels = listOf(tr("Reason (saved with the ban)", "Motivo (se guarda con el ban)")),
            confirm = "Ban",
            onConfirm = { vm.banId(id, name, it[0]) },
            onDismiss = { banning = null },
        )
    }
    if (clearing) {
        ConfirmDialog(
            title = tr("Clear reports", "Borrar reportes"),
            text = tr("Removes every stored F7 report from the server. This cannot be undone.", "Quita del servidor todos los reportes F7 guardados. No se puede deshacer."),
            confirm = tr("Clear", "Borrar"),
            onConfirm = { vm.clearReports() },
            onDismiss = { clearing = false },
        )
    }
}

// ---------------------------------------------------------------- player history (Players tab)

@Composable
fun HistoryList(vm: AppViewModel, query: String, onBan: (String, String) -> Unit, onNote: (String, String) -> Unit) {
    val context = LocalContext.current
    var openId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.loadHistory() }
    val all = vm.seen
    if (vm.companion == null) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { CompanionMissing(vm) }
        return
    }
    if (all == null) {
        Text(tr("Loading…", "Cargando…"), Modifier.padding(20.dp), color = C.mute)
        return
    }
    val list = all.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query) }
    if (list.isEmpty()) {
        Text(
            if (all.isEmpty()) tr("Nobody recorded yet. Players are added when they join.", "Aún no hay nadie registrado. Los jugadores se agregan al entrar.")
            else tr("No matches.", "Sin resultados."),
            Modifier.padding(20.dp),
            color = C.mute,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.id }) { p ->
            val open = openId == p.id
            Column(Modifier.fillMaxWidth().background(if (open) C.panel else C.bg)) {
                if (open) Box(Modifier.fillMaxWidth().height(2.dp).background(C.rust))
                Row(
                    Modifier.fillMaxWidth().clickable { openId = if (open) null else p.id }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name.ifBlank { p.id }, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (p.online) tr("Online now", "Conectado ahora") else tr("Last seen ", "Visto ") + fmtAgo(p.last)) +
                                " · " + tr("${p.visits} visits", "${p.visits} visitas"),
                            color = C.mute,
                            fontSize = 14.sp,
                        )
                    }
                    if (p.online) Box(Modifier.size(10.dp).background(C.olive))
                }
                if (open) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SteamID ${p.id} · " + tr("first seen ", "primera vez ") + fmtAgo(p.first), color = C.soft, fontSize = 14.sp)
                        val note = vm.notes[p.id]
                        if (note != null) Text(tr("Note: ", "Nota: ") + note, color = C.soft, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BravaButton("Combat log", { vm.loadCombat(p.id, p.name) }, Modifier.weight(1f), height = 48.dp)
                            BravaButton(tr("Note", "Nota"), { onNote(p.id, p.name) }, Modifier.weight(1f), height = 48.dp)
                            BravaButton("Steam", {
                                if (!openUrl(context, "https://steamcommunity.com/profiles/${p.id}")) vm.toast = tr("No browser available", "No hay navegador")
                            }, Modifier.weight(1f), height = 48.dp)
                        }
                        BravaButton(tr("Ban by SteamID", "Banear por SteamID"), { onBan(p.id, p.name) }, Modifier.fillMaxWidth(), Kind.DangerSolid, 48.dp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            }
        }
    }
}

// ---------------------------------------------------------------- wipe calendar

private fun rustMapsUrl(size: Int, seed: Long) = "https://rustmaps.com/map/${size}_$seed"

@Composable
fun WipesPage(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var seedFor by remember { mutableStateOf<WipeEntry?>(null) }
    var timeOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadWipes() }
    val cal = vm.wipes
    val now = System.currentTimeMillis() / 1000

    Column(Modifier.fillMaxSize()) {
        SubHeader(tr("Wipe calendar", "Calendario de wipes"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val error = vm.wipesError
            when {
                vm.companion == null -> CompanionMissing(vm)
                cal == null && error != null -> Text(
                    tr("Could not read Carbon's AutoWipe module: ", "No se pudo leer el módulo AutoWipe de Carbon: ") + error,
                    color = C.rustText,
                )
                cal == null -> Text(tr("Loading…", "Cargando…"), color = C.mute)
                else -> {
                    if (!cal.enabled) Text(tr("The AutoWipe module is turned off in Carbon.", "El módulo AutoWipe está apagado en Carbon."), color = C.rustText)
                    Panel(Modifier.fillMaxWidth()) {
                        Label(tr("Map now", "Mapa actual"))
                        Text(tr("Seed ${cal.currentSeed} · size ${cal.currentSize}", "Semilla ${cal.currentSeed} · tamaño ${cal.currentSize}"), fontWeight = FontWeight.Bold)
                        BravaButton(tr("View on RustMaps", "Ver en RustMaps"), {
                            if (!openUrl(context, rustMapsUrl(cal.currentSize, cal.currentSeed))) vm.toast = tr("No browser available", "No hay navegador")
                        }, Modifier.fillMaxWidth(), height = 46.dp)
                    }
                    BravaButton(tr("Change the time of every wipe", "Cambiar la hora de todos los wipes"), { timeOpen = true }, Modifier.fillMaxWidth())
                    Text(
                        tr(
                            "The server description in the hosting panel still says the old time; update it there too.",
                            "La descripción del servidor en el panel del hosting sigue diciendo la hora anterior; cámbiala también ahí.",
                        ),
                        color = C.mute,
                        fontSize = 13.sp,
                    )
                    val next = cal.next
                    val upcoming = cal.wipes.filter { it.wipeAt > now }.sortedBy { it.wipeAt }
                    if (upcoming.isEmpty()) Text(tr("No upcoming wipes in the calendar.", "No hay wipes próximos en el calendario."), color = C.mute)
                    upcoming.forEach { w ->
                        val isNext = w == next
                        Column(
                            Modifier.fillMaxWidth().background(C.panel).border(if (isNext) 2.dp else 1.dp, if (isNext) C.rust else C.line).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(fmtDate(w.wipeAt), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                if (isNext) Badge(tr("Next", "Siguiente"), C.rustText)
                                if (w.forced) Badge(tr("Forced", "Forzado"), C.yellow)
                            }
                            Text(
                                tr("Seed ${w.seed} · size ${w.size}", "Semilla ${w.seed} · tamaño ${w.size}") +
                                    if (w.full) tr(" · full wipe", " · wipe completo") else tr(" · map only", " · solo mapa"),
                                color = C.soft,
                                fontSize = 14.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BravaButton(tr("View map", "Ver mapa"), {
                                    if (!openUrl(context, rustMapsUrl(w.size, w.seed))) vm.toast = tr("No browser available", "No hay navegador")
                                }, Modifier.weight(1f), height = 46.dp)
                                BravaButton(tr("Change seed", "Cambiar semilla"), { seedFor = w }, Modifier.weight(1f), height = 46.dp)
                            }
                        }
                    }
                    Text(
                        tr(
                            "A seed can give a different map after Rust's monthly update. Check it on RustMaps after the forced wipe.",
                            "Una semilla puede dar otro mapa después de la actualización mensual de Rust. Revísala en RustMaps después del wipe forzado.",
                        ),
                        color = C.mute,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
    seedFor?.let { w ->
        PromptDialog(
            title = tr("Seed for ", "Semilla para el ") + fmtDate(w.wipeAt),
            labels = listOf(tr("Seed (1 to 2147483647)", "Semilla (1 a 2147483647)")),
            defaults = listOf(w.seed.toString()),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.setWipeSeed(w.index, it[0]) },
            onDismiss = { seedFor = null },
        )
    }
    if (timeOpen) {
        val current = cal?.next?.let { SimpleDateFormat("HH:mm", Locale.US).format(Date(it.wipeAt * 1000)) } ?: "20:00"
        PromptDialog(
            title = tr("Wipe time", "Hora del wipe"),
            labels = listOf(tr("New map at, 24 h (e.g. 20:00)", "Mapa nuevo a las, 24 h (ej. 20:00)")),
            defaults = listOf(current),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.setWipeTime(it[0]) },
            onDismiss = { timeOpen = false },
        )
    }
}

// ---------------------------------------------------------------- kits editor

private val containerNames = listOf("main", "wear", "belt")

private fun containerLabel(c: String) = when (c) {
    "wear" -> tr("Clothing", "Ropa")
    "belt" -> tr("Belt", "Cinturón")
    else -> tr("Backpack", "Mochila")
}

private fun fmtCooldown(seconds: Int): String = when {
    seconds <= 0 -> tr("no wait", "sin espera")
    seconds % 3600 == 0 -> "${seconds / 3600} h"
    seconds >= 60 -> "${seconds / 60} min"
    else -> "$seconds s"
}

@Composable
fun KitsPage(vm: AppViewModel, onBack: () -> Unit) {
    // Dialog key: "<action>|<kit>|<extra>"
    var dialog by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadKits() }
    val list = vm.kits

    Column(Modifier.fillMaxSize()) {
        SubHeader("Kits", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val error = vm.kitsError
            when {
                vm.companion == null -> CompanionMissing(vm)
                list == null && error != null -> Text(tr("Could not read the Kits plugin: ", "No se pudo leer el plugin Kits: ") + error, color = C.rustText)
                list == null -> Text(tr("Loading…", "Cargando…"), color = C.mute)
                else -> {
                    Text(
                        tr(
                            "Each change reloads the Kits plugin for a second. Item names are short names, for example rifle.ak or scrap.",
                            "Cada cambio recarga el plugin Kits por un segundo. Los items van con su nombre corto, por ejemplo rifle.ak o scrap.",
                        ),
                        color = C.mute,
                        fontSize = 13.sp,
                    )
                    list.forEach { kit -> KitCard(kit) { dialog = it } }
                    BravaButton(tr("New kit", "Nuevo kit"), { dialog = "new||" }, Modifier.fillMaxWidth(), Kind.Go)
                }
            }
        }
    }

    if (dialog.isEmpty()) return
    val (action, kit, extra) = dialog.split("|", limit = 3).let { Triple(it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
    val current = list?.firstOrNull { it.name == kit }
    val close = { dialog = "" }
    when (action) {
        "new" -> PromptDialog(
            title = tr("New kit", "Nuevo kit"),
            labels = listOf(tr("Name (letters, numbers, _)", "Nombre (letras, números, _)")),
            confirm = tr("Create", "Crear"),
            onConfirm = { vm.kitCmd("brava.kits.add ${it[0].trim().lowercase()}") },
            onDismiss = close,
        )
        "add" -> PromptDialog(
            title = tr("Add to ", "Agregar a ") + containerLabel(extra) + " · " + kit,
            labels = listOf(tr("Item short name", "Nombre corto del item"), tr("Amount", "Cantidad")),
            defaults = listOf("", "1"),
            confirm = tr("Add", "Agregar"),
            onConfirm = { vm.setKitItem(kit, extra, it[0], it[1].trim().toIntOrNull() ?: 1) },
            onDismiss = close,
        )
        "amount" -> {
            val (container, shortname) = extra.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val have = current?.items?.firstOrNull { it.container == container && it.shortname == shortname }?.amount ?: 1
            PromptDialog(
                title = shortname,
                labels = listOf(tr("Amount (0 removes it)", "Cantidad (0 lo quita)")),
                defaults = listOf(have.toString()),
                confirm = tr("Save", "Guardar"),
                onConfirm = { vm.setKitItem(kit, container, shortname, it[0].trim().toIntOrNull() ?: have) },
                onDismiss = close,
            )
        }
        "description" -> PromptDialog(
            title = tr("Description", "Descripción"),
            labels = listOf(tr("Shown to players", "La ven los jugadores")),
            defaults = listOf(current?.description ?: ""),
            confirm = tr("Save", "Guardar"),
            singleLine = false,
            onConfirm = { vm.setKitField(kit, "description", it[0].replace("\n", " ")) },
            onDismiss = close,
        )
        "cooldown" -> PromptDialog(
            title = tr("Wait between uses", "Espera entre usos"),
            labels = listOf(tr("Minutes (0 = no wait)", "Minutos (0 = sin espera)")),
            defaults = listOf(((current?.cooldown ?: 0) / 60).toString()),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.setKitField(kit, "cooldown", ((it[0].trim().toIntOrNull() ?: 0) * 60).toString()) },
            onDismiss = close,
        )
        "uses" -> PromptDialog(
            title = tr("Uses per wipe", "Usos por wipe"),
            labels = listOf(tr("Number (0 = unlimited)", "Número (0 = ilimitado)")),
            defaults = listOf((current?.maxUses ?: 0).toString()),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.setKitField(kit, "uses", it[0].trim()) },
            onDismiss = close,
        )
        "permission" -> PromptDialog(
            title = tr("Required permission", "Permiso necesario"),
            labels = listOf(tr("For example kits.vip (empty = everyone)", "Por ejemplo kits.vip (vacío = todos)")),
            defaults = listOf(current?.permission ?: ""),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.setKitField(kit, "permission", it[0].trim()) },
            onDismiss = close,
        )
        "delete" -> ConfirmDialog(
            title = tr("Delete kit", "Borrar kit"),
            text = tr("Delete the kit \"$kit\"? Players lose it right away.", "¿Borrar el kit \"$kit\"? Los jugadores lo pierden de inmediato."),
            confirm = tr("Delete", "Borrar"),
            onConfirm = { vm.kitCmd("brava.kits.delete $kit") },
            onDismiss = close,
        )
        else -> Unit
    }
}

@Composable
private fun KitCard(kit: KitInfo, open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.panel).border(1.dp, C.line).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("/kit ${kit.name}", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (kit.hidden) Badge(tr("Hidden", "Oculto"), C.mute)
        }
        if (kit.description.isNotBlank()) Text(kit.description, color = C.soft, fontSize = 15.sp)
        Text(
            tr("Wait: ", "Espera: ") + fmtCooldown(kit.cooldown) + " · " +
                tr("Uses: ", "Usos: ") + (if (kit.maxUses <= 0) tr("unlimited", "ilimitados") else kit.maxUses.toString()) + " · " +
                tr("Permission: ", "Permiso: ") + kit.permission.ifBlank { tr("everyone", "todos") },
            color = C.mute,
            fontSize = 14.sp,
        )
        containerNames.forEach { c ->
            val items = kit.items.filter { it.container == c }
            Label(containerLabel(c) + " · " + items.size)
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().background(C.black).clickable { open("amount|${kit.name}|$c:${item.shortname}") }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${item.amount} ×", Modifier.padding(end = 10.dp), color = C.oliveText, fontWeight = FontWeight.Bold)
                    Text(item.shortname, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("Edit", "Editar"), color = C.mute, fontSize = 13.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            containerNames.forEach { c ->
                BravaButton("+ " + containerLabel(c), { open("add|${kit.name}|$c") }, Modifier.weight(1f), height = 44.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BravaButton(tr("Text", "Texto"), { open("description|${kit.name}|") }, Modifier.weight(1f), height = 44.dp)
            BravaButton(tr("Wait", "Espera"), { open("cooldown|${kit.name}|") }, Modifier.weight(1f), height = 44.dp)
            BravaButton(tr("Uses", "Usos"), { open("uses|${kit.name}|") }, Modifier.weight(1f), height = 44.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BravaButton(tr("Permission", "Permiso"), { open("permission|${kit.name}|") }, Modifier.weight(1f), height = 44.dp)
            BravaButton(tr("Delete", "Borrar"), { open("delete|${kit.name}|") }, Modifier.weight(1f), Kind.Danger, 44.dp)
        }
    }
}

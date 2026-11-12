package io.github.isramorante.brava.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.AppViewModel
import io.github.isramorante.brava.data.MapDot
import io.github.isramorante.brava.tr
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- permissions

@Composable
fun PermsPage(vm: AppViewModel, onBack: () -> Unit) {
    var target by remember { mutableStateOf("default") }
    var perm by remember { mutableStateOf("") }
    var isUser by remember { mutableStateOf(false) }
    val kind = if (isUser) "user" else "group"
    LaunchedEffect(Unit) { vm.perms("c.show groups") }

    Column(Modifier.fillMaxSize()) {
        SubHeader(tr("Permissions", "Permisos"), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BravaButton(tr("Group", "Grupo"), { isUser = false }, Modifier.weight(1f), if (!isUser) Kind.DangerSolid else Kind.Neutral, 46.dp)
                BravaButton(tr("Player", "Jugador"), { isUser = true }, Modifier.weight(1f), if (isUser) Kind.DangerSolid else Kind.Neutral, 46.dp)
            }
            Field(target, { target = it }, if (isUser) tr("SteamID or name", "SteamID o nombre") else tr("Group name", "Nombre del grupo"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BravaButton(tr("Show", "Ver"), { vm.perms("c.show $kind $target") }, Modifier.weight(1f), height = 46.dp, enabled = target.isNotBlank())
                BravaButton(tr("Groups", "Grupos"), { vm.perms("c.show groups") }, Modifier.weight(1f), height = 46.dp)
                BravaButton(tr("All perms", "Todos"), { vm.perms("c.show perms") }, Modifier.weight(1f), height = 46.dp)
            }
            Field(perm, { perm = it }, tr("Permission (e.g. kits.compa)", "Permiso (ej. kits.compa)"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val ready = target.isNotBlank() && perm.isNotBlank()
                BravaButton(tr("Grant", "Dar"), { vm.perms("c.grant $kind $target ${perm.trim()}") }, Modifier.weight(1f), Kind.Go, 46.dp, ready)
                BravaButton(tr("Revoke", "Quitar"), { vm.perms("c.revoke $kind $target ${perm.trim()}") }, Modifier.weight(1f), Kind.Danger, 46.dp, ready)
            }
            Text(
                tr("Uses Carbon commands (c.grant, c.revoke, c.show).", "Usa comandos de Carbon (c.grant, c.revoke, c.show)."),
                color = C.mute,
                fontSize = 13.sp,
            )
            SelectionContainer {
                Text(
                    vm.permsText.replace(", ", "\n"),
                    Modifier.fillMaxWidth().background(C.black).padding(14.dp),
                    color = C.ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- player map

private const val CELL = 146.3f

private fun gridRef(dot: MapDot, world: Int): String {
    if (world <= 0) return ""
    val col = ((dot.x + world / 2f) / CELL).toInt().coerceAtLeast(0)
    val row = ((world / 2f - dot.z) / CELL).toInt().coerceAtLeast(0)
    val letters = if (col < 26) ('A' + col).toString() else "A" + ('A' + (col - 26))
    return "$letters$row"
}

@Composable
fun MapPage(vm: AppViewModel, onBack: (() -> Unit)?) {
    LaunchedEffect(Unit) {
        vm.loadMapImage()
        while (true) {
            vm.loadMap()
            delay(10000)
        }
    }
    val world = vm.worldSize
    val image = vm.mapImage
    // The terrain image has an ocean border around the playable square, measured in image pixels.
    val mf = if (image != null && image.width > 0) (vm.mapMargin.toFloat() / image.width).coerceIn(0f, 0.45f) else 0f
    val inner = 1f - 2f * mf
    fun fracX(x: Float) = mf + (x + world / 2f) / world * inner
    fun fracY(z: Float) = mf + (world / 2f - z) / world * inner
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize()) {
        if (onBack != null) SubHeader(tr("Player map", "Mapa de jugadores"), onBack)
        else Header(tr("Map", "Mapa")) { ConnBadge(vm) }
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(1f).background(C.black).clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 6f)
                        val limit = size.width * (nextZoom - 1f) / 2f
                        zoom = nextZoom
                        pan = Offset(
                            (pan.x + panChange.x).coerceIn(-limit, limit),
                            (pan.y + panChange.y).coerceIn(-limit, limit),
                        )
                    }
                },
        ) {
            val side = maxWidth
            val sidePx = constraints.maxWidth.toFloat()
            Box(Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y)) {
                if (image != null) {
                    Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                Canvas(Modifier.fillMaxSize()) {
                    if (world > 0) {
                        val start = size.width * mf
                        val end = size.width * (1f - mf)
                        val step = size.width * inner * CELL / world
                        val gridColor = if (image != null) C.black.copy(alpha = 0.35f) else C.line
                        var p = start
                        while (p <= end + 0.5f) {
                            drawLine(gridColor, Offset(p, start), Offset(p, end), 1f)
                            drawLine(gridColor, Offset(start, p), Offset(end, p), 1f)
                            p += step
                        }
                    }
                }
            }
            if (world > 0) {
                // Dots stay the same size at any zoom, so they are drawn outside the zoomed layer.
                vm.mapDots.forEach { dot ->
                    val fx = fracX(dot.x).coerceIn(0f, 1f)
                    val fy = fracY(dot.z).coerceIn(0f, 1f)
                    val x = (fx * sidePx - sidePx / 2f) * zoom + sidePx / 2f + pan.x
                    val y = (fy * sidePx - sidePx / 2f) * zoom + sidePx / 2f + pan.y
                    if (x in 0f..sidePx && y in 0f..sidePx) {
                        val xd = side * (x / sidePx)
                        val yd = side * (y / sidePx)
                        Box(Modifier.offset(x = xd - 6.dp, y = yd - 6.dp).size(12.dp).background(C.rust).border(2.dp, C.black))
                        Text(
                            dot.name.take(14),
                            Modifier.offset(x = xd + 9.dp, y = yd - 10.dp).background(C.black.copy(alpha = 0.6f)).padding(horizontal = 3.dp),
                            color = C.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val error = vm.mapError
            if (error != null) Text(tr("Could not load positions: ", "No se pudieron cargar las posiciones: ") + error, color = C.rustText, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    when (vm.mapImageState) {
                        "loading" -> tr("Drawing the terrain on the server… the first time takes about 20 seconds.", "Dibujando el terreno en el servidor… la primera vez tarda unos 20 segundos.")
                        "busy" -> tr(
                            "The terrain image is drawn when the server starts, so players never freeze. It will show after the next restart. Showing the grid for now.",
                            "La imagen del terreno se dibuja al arrancar el servidor, para no congelar a los jugadores. Aparecerá después del próximo reinicio; por ahora solo se ve la cuadrícula.",
                        )
                        "error" -> tr("No terrain image (needs the BravaCompanion plugin). Showing the grid only.", "Sin imagen del terreno (necesita el plugin BravaCompanion). Solo se ve la cuadrícula.")
                        else -> tr("Pinch to zoom. Positions update every 10 seconds.", "Pellizca para hacer zoom. Las posiciones se actualizan cada 10 segundos.")
                    },
                    Modifier.weight(1f),
                    color = C.mute,
                    fontSize = 13.sp,
                )
                if (zoom > 1f) BravaButton(tr("Reset", "Ajustar"), { zoom = 1f; pan = Offset.Zero }, height = 40.dp)
            }
            if (vm.mapDots.isEmpty()) Text(tr("Nobody is connected.", "No hay nadie conectado."), color = C.mute)
            vm.mapDots.forEach { dot ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dot.name, fontWeight = FontWeight.Bold)
                    Text(gridRef(dot, world), color = C.oliveText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- alerts and schedules

@Composable
fun AlertsPage(vm: AppViewModel, onBack: () -> Unit) {
    var dialog by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadCompanion() }
    val cfg = vm.companion
    val context = LocalContext.current
    val server = vm.active
    val watching = server != null && server.id in vm.watched
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (server != null) {
            vm.setWatched(server, granted)
            if (!granted) vm.toast = tr("Without notification permission the phone cannot warn you.", "Sin permiso de notificaciones el teléfono no puede avisarte.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubHeader(tr("Alerts", "Alertas"), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label(tr("This phone", "Este teléfono"))
            Toggle(
                tr("Warn me if the server goes down", "Avisarme si el servidor se cae"),
                tr(
                    "The phone checks the server every 15 minutes, even with Brava closed, and warns after two failed checks. Works without the plugin.",
                    "El teléfono revisa el servidor cada 15 minutos, aunque Brava esté cerrada, y avisa tras dos revisiones fallidas. Funciona sin el plugin.",
                ),
                watching,
            ) {
                if (server == null) return@Toggle
                if (watching) {
                    vm.setWatched(server, false)
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.setWatched(server, true)
                }
            }

            Label(tr("Server plugin", "Plugin del servidor"), Modifier.padding(top = 8.dp))
            if (cfg == null) {
                Text(
                    if (vm.companionState == "loading") tr("Checking the server…", "Revisando el servidor…")
                    else tr(
                        "The BravaCompanion plugin is not installed on this server. Copy BravaCompanion.cs from the server-plugin folder of the Brava repository into carbon/plugins (or oxide/plugins), then come back here.",
                        "El plugin BravaCompanion no está instalado en este servidor. Copia BravaCompanion.cs de la carpeta server-plugin del repositorio de Brava a carbon/plugins (u oxide/plugins) y vuelve aquí.",
                    ),
                    color = C.soft,
                )
                BravaButton(tr("Check again", "Revisar de nuevo"), { vm.companionCmd("brava.status") }, Modifier.fillMaxWidth())
                return@Column
            }

            Label(tr("Alerts through ntfy", "Alertas por ntfy"))
            Text(
                tr(
                    "Alerts arrive through the free ntfy app. Install ntfy on your phone and subscribe to the same topic you set here. Use a long, hard-to-guess topic: anyone who knows it can read the alerts.",
                    "Las alertas llegan por la app gratuita ntfy. Instala ntfy en tu teléfono y suscríbete al mismo tema que pongas aquí. Usa un tema largo y difícil de adivinar: quien lo conozca puede leer las alertas.",
                ),
                color = C.soft,
                fontSize = 14.sp,
            )
            Panel(Modifier.fillMaxWidth()) {
                Text(tr("Topic: ", "Tema: ") + cfg.topic.ifBlank { tr("not set (alerts are off)", "sin definir (alertas apagadas)") }, fontWeight = FontWeight.Bold)
                Text(tr("Server: ", "Servidor: ") + cfg.ntfyServer, color = C.mute, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BravaButton(tr("Set topic", "Cambiar tema"), { dialog = "topic" }, Modifier.weight(1f), height = 46.dp)
                    BravaButton(tr("Send test", "Enviar prueba"), { vm.companionCmd("brava.test") }, Modifier.weight(1f), Kind.Go, 46.dp, cfg.topic.isNotBlank())
                }
            }
            Toggle(tr("Chat keywords", "Palabras en el chat"), cfg.keywords.joinToString(", ").ifBlank { "–" }, null) { dialog = "keywords" }
            Toggle(tr("Every player join", "Cada jugador que entra"), "", cfg.notifyJoins) { vm.companionCmd("brava.joins ${!cfg.notifyJoins}") }
            Toggle(tr("Wipe finished (new map)", "Wipe terminado (mapa nuevo)"), "", cfg.notifyWipe) { vm.companionCmd("brava.wipe ${!cfg.notifyWipe}") }
            Toggle(tr("Server started", "Servidor encendido"), "", cfg.notifyStart) { vm.companionCmd("brava.start ${!cfg.notifyStart}") }
            Toggle(tr("F7 reports", "Reportes F7"), "", cfg.notifyReports) { vm.companionCmd("brava.reportalerts ${!cfg.notifyReports}") }
            Toggle(
                tr("Alert language", "Idioma de las alertas"),
                if (cfg.language == "es") "Español" else "English",
                null,
            ) { vm.companionCmd("brava.language " + if (cfg.language == "es") "en" else "es") }

            Label(tr("Fresh-wipe server name", "Nombre del servidor recién wipeado"), Modifier.padding(top = 8.dp))
            Toggle(
                if (cfg.freshHostname.isBlank()) tr("Off", "Apagado") else cfg.freshHostname,
                if (cfg.freshHostname.isBlank()) tr("Shows a different server name for some hours after each wipe.", "Muestra otro nombre del servidor unas horas después de cada wipe.")
                else tr("Shown for ${cfg.freshHours} h after each wipe, then the normal name returns.", "Se muestra ${cfg.freshHours} h después de cada wipe; luego vuelve el nombre normal."),
                null,
            ) { dialog = "wipetag" }

            Label(tr("Scheduled commands", "Comandos programados"), Modifier.padding(top = 8.dp))
            Text(
                tr("Server clock offset from UTC: ${cfg.utcOffsetHours} h", "Diferencia del reloj con UTC: ${cfg.utcOffsetHours} h"),
                color = C.mute,
                fontSize = 14.sp,
            )
            if (cfg.schedules.isEmpty()) Text(tr("Nothing scheduled.", "Nada programado."), color = C.mute)
            cfg.schedules.forEachIndexed { i, item ->
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        if (item.everyMinutes > 0) tr("Every ${item.everyMinutes} min", "Cada ${item.everyMinutes} min") else tr("Daily at ${item.time}", "Diario a las ${item.time}"),
                        color = C.oliveText,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(item.command, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    BravaButton(tr("Remove", "Quitar"), { vm.companionCmd("brava.schedule.remove $i") }, kind = Kind.Danger, height = 44.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BravaButton(tr("Add daily", "Agregar diario"), { dialog = "daily" }, Modifier.weight(1f), height = 46.dp)
                BravaButton(tr("Add repeating", "Agregar repetido"), { dialog = "every" }, Modifier.weight(1f), height = 46.dp)
            }
            BravaButton(tr("Set clock offset", "Ajustar reloj"), { dialog = "offset" }, Modifier.fillMaxWidth(), height = 46.dp)
        }
    }

    val close = { dialog = "" }
    when (dialog) {
        "topic" -> PromptDialog(
            title = tr("ntfy topic", "Tema de ntfy"),
            labels = listOf(tr("Topic (letters, numbers, - and _)", "Tema (letras, números, - y _)")),
            defaults = listOf(cfg?.topic ?: ""),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.companionCmd("brava.topic ${it[0].trim()}") },
            onDismiss = close,
        )
        "keywords" -> PromptDialog(
            title = tr("Chat keywords", "Palabras en el chat"),
            labels = listOf(tr("Separated by commas", "Separadas por comas")),
            defaults = listOf(cfg?.keywords?.joinToString(",") ?: ""),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.companionCmd("brava.keywords \"${it[0].replace("\"", "")}\"") },
            onDismiss = close,
        )
        "daily" -> PromptDialog(
            title = tr("Daily command", "Comando diario"),
            labels = listOf(tr("Time, 24 h (e.g. 13:55)", "Hora, 24 h (ej. 13:55)"), tr("Command", "Comando")),
            confirm = tr("Add", "Agregar"),
            onConfirm = { vm.companionCmd("brava.schedule.add ${it[0].trim()} \"${it[1].replace("\"", "'")}\"") },
            onDismiss = close,
        )
        "every" -> PromptDialog(
            title = tr("Repeating command", "Comando repetido"),
            labels = listOf(tr("Every how many minutes", "Cada cuántos minutos"), tr("Command", "Comando")),
            confirm = tr("Add", "Agregar"),
            onConfirm = { vm.companionCmd("brava.schedule.add every:${it[0].trim()} \"${it[1].replace("\"", "'")}\"") },
            onDismiss = close,
        )
        "wipetag" -> PromptDialog(
            title = tr("Fresh-wipe name", "Nombre recién wipeado"),
            labels = listOf(
                tr("Server name ({date} = wipe day). Empty = off", "Nombre ({date} = día del wipe). Vacío = apagado"),
                tr("Hours to show it", "Horas que se muestra"),
            ),
            defaults = listOf(cfg?.freshHostname ?: "", cfg?.freshHours?.toString() ?: "48"),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.companionCmd("brava.wipetag \"${it[0].replace("\"", "'")}\" ${it[1].trim()}") },
            onDismiss = close,
        )
        "offset" -> PromptDialog(
            title = tr("Clock offset from UTC", "Diferencia con UTC"),
            labels = listOf(tr("Hours (Mexico City is -6)", "Horas (CDMX es -6)")),
            defaults = listOf(cfg?.utcOffsetHours?.toString() ?: "0"),
            confirm = tr("Save", "Guardar"),
            onConfirm = { vm.companionCmd("brava.offset ${it[0].trim()}") },
            onDismiss = close,
        )
    }
}

@Composable
private fun Toggle(title: String, detail: String, on: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.panel).border(1.dp, C.line).padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (detail.isNotBlank()) Text(detail, color = C.mute, fontSize = 13.sp)
        }
        BravaButton(
            when (on) {
                null -> tr("Edit", "Editar")
                true -> tr("On", "Sí")
                false -> tr("Off", "No")
            },
            onClick,
            kind = if (on == true) Kind.Go else Kind.Neutral,
            height = 44.dp,
        )
    }
}

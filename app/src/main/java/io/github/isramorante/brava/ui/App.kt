package io.github.isramorante.brava.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.AppViewModel
import io.github.isramorante.brava.tr

@Composable
fun BravaApp(vm: AppViewModel, unlock: () -> Unit, canLock: () -> Boolean) {
    val context = LocalContext.current
    val toast = vm.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }
    Box(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding().imePadding()) {
        if (vm.locked) {
            LaunchedEffect(Unit) { unlock() }
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Title("Brava RCON")
                Text(tr("Locked", "Bloqueado"), color = C.mute)
                BravaButton(tr("Unlock", "Desbloquear"), unlock, kind = Kind.Go)
            }
        } else if (vm.active == null) {
            UpdatePrompt(vm)
            ServersScreen(vm, canLock)
        } else {
            UpdatePrompt(vm)
            var tab by rememberSaveable { mutableIntStateOf(0) }
            BackHandler { if (tab != 0) tab = 0 else vm.leave() }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> DashboardScreen(vm)
                        1 -> PlayersScreen(vm)
                        2 -> ConsoleScreen(vm)
                        3 -> ChatScreen(vm)
                        else -> MoreScreen(vm)
                    }
                }
                TabBar(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(tr("HOME", "INICIO"), tr("PLAYERS", "JUGADORES"), tr("CONSOLE", "CONSOLA"), "CHAT", tr("MORE", "MÁS"))
    Column(Modifier.fillMaxWidth().background(C.black)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val color = if (on) C.rustText else C.mute
                Column(
                    Modifier.weight(1f).clickable(role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) C.rust else C.black))
                    Column(
                        Modifier.height(60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        TabIcon(i, color)
                        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

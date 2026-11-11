package io.github.isramorante.brava.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.isramorante.brava.tr

@Composable
fun ChatScreen(vm: AppViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val count = vm.chat.size
    LaunchedEffect(count) {
        if (count > 0) listState.scrollToItem(count - 1)
    }
    val send = {
        vm.say(input)
        input = ""
    }

    Column(Modifier.fillMaxSize()) {
        Header("Chat") { ConnBadge(vm) }
        if (count == 0) {
            Text(tr("No chat messages yet.", "Todavía no hay mensajes."), Modifier.padding(20.dp), color = C.mute)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            items(vm.chat.toList()) { m ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, color = if (m.mine) C.rustText else C.blue, fontWeight = FontWeight.Bold)
                        if (m.team) Text(tr("TEAM", "EQUIPO"), color = C.oliveText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(fmtClock(m.time), color = C.mute, fontSize = 12.sp)
                    }
                    Text(m.text, fontSize = 17.sp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Field(input, { input = it }, tr("Message as SERVER", "Mensaje como SERVER"), Modifier.weight(1f), onSend = send)
            BravaButton(tr("Send", "Enviar"), send, kind = Kind.Go, height = 56.dp)
        }
    }
}

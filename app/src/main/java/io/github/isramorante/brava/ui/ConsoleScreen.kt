package io.github.isramorante.brava.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.AppViewModel
import io.github.isramorante.brava.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun lineColor(type: String): Color = when (type) {
    "Chat" -> C.blue
    "Warning" -> C.yellow
    "Error" -> C.red
    else -> C.ink
}

private val clock = SimpleDateFormat("HH:mm", Locale.US)

fun fmtClock(time: Long): String = clock.format(Date(time))

@Composable
fun ConsoleScreen(vm: AppViewModel) {
    var filter by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lines = when (filter) {
        1 -> vm.console.filter { it.type == "Chat" }
        2 -> vm.console.filter { it.type == "Warning" }
        3 -> vm.console.filter { it.type == "Error" }
        else -> vm.console.toList()
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }
    val send = {
        vm.run(input, remember = true)
        input = ""
    }

    Column(Modifier.fillMaxSize()) {
        Header(tr("Console", "Consola")) { ConnBadge(vm) }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(tr("All", "Todo"), "Chat", tr("Warnings", "Avisos"), tr("Errors", "Errores")).forEachIndexed { i, label ->
                BravaButton(label, { filter = i }, kind = if (filter == i) Kind.DangerSolid else Kind.Neutral, height = 44.dp)
            }
        }
        SelectionContainer(Modifier.weight(1f).fillMaxWidth().background(C.black)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                items(lines) { line ->
                    Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(fmtClock(line.time), color = C.mute, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text(
                            line.text,
                            color = lineColor(line.type),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontWeight = if (line.type == "Own") FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        if (vm.history.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.history.take(10).forEach { cmd ->
                    BravaButton(cmd, { input = cmd }, height = 44.dp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Field(input, { input = it }, tr("Command", "Comando"), Modifier.weight(1f), onSend = send)
            BravaButton(tr("Send", "Enviar"), send, kind = Kind.Go, height = 56.dp)
        }
    }
}

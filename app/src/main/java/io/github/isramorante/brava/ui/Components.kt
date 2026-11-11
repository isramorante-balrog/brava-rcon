package io.github.isramorante.brava.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.tr

val Square = RoundedCornerShape(0.dp)

enum class Kind { Neutral, Go, Danger, DangerSolid }

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, color = C.mute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun BravaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: Kind = Kind.Neutral,
    height: Dp = 52.dp,
    enabled: Boolean = true,
) {
    val bg = when (kind) {
        Kind.Neutral -> C.line
        Kind.Go -> C.olive
        Kind.Danger -> C.bg
        Kind.DangerSolid -> C.rust
    }
    val fg = when (kind) {
        Kind.Neutral -> C.ink
        Kind.Go, Kind.DangerSolid -> C.black
        Kind.Danger -> C.rustText
    }
    val border = when (kind) {
        Kind.Neutral -> C.line2
        Kind.Danger -> C.rust
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(height)
            .background(if (enabled) bg else C.panel)
            .border(if (kind == Kind.Danger) 2.dp else 1.dp, if (enabled) border else C.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = if (enabled) fg else C.mute,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.background(C.panel).border(1.dp, C.line).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    number: Boolean = false,
    singleLine: Boolean = true,
    onSend: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontFamily = Condensed) },
        singleLine = singleLine,
        shape = Square,
        textStyle = TextStyle(color = C.ink, fontFamily = Condensed, fontSize = 16.sp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
            autoCorrectEnabled = false,
            imeAction = if (onSend != null) ImeAction.Send else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = C.black,
            unfocusedContainerColor = C.black,
            focusedBorderColor = C.rust,
            unfocusedBorderColor = C.line2,
            focusedLabelColor = C.rustText,
            unfocusedLabelColor = C.mute,
            cursorColor = C.rust,
        ),
    )
}

@Composable
fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(C.black)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).background(color))
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panel,
        shape = Square,
        title = { Text(title.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, color = C.ink) },
        text = { Text(text, fontFamily = Condensed, color = C.soft, fontSize = 16.sp) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm.uppercase(), color = C.rustText, fontFamily = Condensed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("CANCEL", "CANCELAR"), color = C.soft, fontFamily = Condensed, fontWeight = FontWeight.Bold)
            }
        },
    )
}

/** A dialog with one text field per label. Returns the typed values in the same order. */
@Composable
fun PromptDialog(
    title: String,
    labels: List<String>,
    defaults: List<String> = emptyList(),
    confirm: String = "OK",
    singleLine: Boolean = true,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember { mutableStateListOf<String>().apply { labels.indices.forEach { add(defaults.getOrElse(it) { "" }) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panel,
        shape = Square,
        title = { Text(title.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, color = C.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEachIndexed { i, label ->
                    Field(values[i], { values[i] = it }, label, singleLine = singleLine, modifier = Modifier.heightIn(min = 56.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(values.toList()); onDismiss() }) {
                Text(confirm.uppercase(), color = C.rustText, fontFamily = Condensed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("CANCEL", "CANCELAR"), color = C.soft, fontFamily = Condensed, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun Header(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Title(title, Modifier.weight(1f))
        trailing()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
}

/** Hand-drawn tab icons, so the app needs no icon library. */
@Composable
fun TabIcon(index: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val s = Stroke(width = w * 0.09f)
        when (index) {
            0 -> {
                val c = w * 0.34f
                val g = w * 0.14f
                for (x in listOf(g, w - g - c)) for (y in listOf(g, w - g - c)) drawRect(color, Offset(x, y), Size(c, c), style = s)
            }
            1 -> {
                drawCircle(color, w * 0.17f, Offset(w / 2, w * 0.33f), style = s)
                drawArc(color, 180f, 180f, false, Offset(w * 0.18f, w * 0.58f), Size(w * 0.64f, w * 0.56f), style = s)
            }
            2 -> {
                drawLine(color, Offset(w * 0.2f, w * 0.28f), Offset(w * 0.42f, w * 0.5f), s.width)
                drawLine(color, Offset(w * 0.42f, w * 0.5f), Offset(w * 0.2f, w * 0.72f), s.width)
                drawLine(color, Offset(w * 0.5f, w * 0.76f), Offset(w * 0.8f, w * 0.76f), s.width)
            }
            3 -> {
                drawRect(color, Offset(w * 0.16f, w * 0.2f), Size(w * 0.68f, w * 0.46f), style = s)
                drawLine(color, Offset(w * 0.3f, w * 0.66f), Offset(w * 0.2f, w * 0.84f), s.width)
            }
            else -> for (y in listOf(0.28f, 0.5f, 0.72f)) drawLine(color, Offset(w * 0.16f, w * y), Offset(w * 0.84f, w * y), s.width)
        }
    }
}

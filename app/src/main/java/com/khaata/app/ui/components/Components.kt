package com.khaata.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.GoalStatus
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.GreenSoft
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Overdue
import com.khaata.app.ui.theme.OverdueSoft
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.ui.theme.RustSoft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color = Ink,
    sub: String? = null
) {
    Column(
        modifier
            .background(PaperCard, RoundedCornerShape(10.dp))
            .border(1.dp, PaperLine, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = accent, fontFamily = FontFamily.Monospace)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = Muted)
        }
    }
}

/** A small "stamped" progress ring, like a passbook seal, showing % complete. */
@Composable
fun ProgressStamp(pct: Float, color: Color, size: Dp = 64.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val diameter = kotlin.math.min(this.size.width, this.size.height) - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(color = PaperLine, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * (pct.coerceIn(0f, 100f) / 100f), useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${pct.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = Ink)
    }
}

private data class BadgeStyle(val bg: Color, val fg: Color, val text: String, val icon: ImageVector)

@Composable
fun StatusBadge(status: GoalStatus) {
    val style = when (status) {
        GoalStatus.ACHIEVED -> BadgeStyle(GreenSoft, Green, "Goal achieved", Icons.Filled.CheckCircle)
        GoalStatus.ON_TRACK -> BadgeStyle(GreenSoft, Green, "On track", Icons.Filled.CheckCircle)
        GoalStatus.BEHIND -> BadgeStyle(RustSoft, Rust, "Behind pace", Icons.Filled.Warning)
        GoalStatus.OVERDUE -> BadgeStyle(OverdueSoft, Overdue, "Past due date", Icons.Filled.Schedule)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(style.bg, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Icon(style.icon, contentDescription = null, tint = style.fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(style.text, color = style.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A ledger-style row: a colored dot, a label, a fill bar, and a right-aligned amount. */
@Composable
fun CategoryBarRow(label: String, color: Color, amount: String, pct: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, modifier = Modifier.width(140.dp))
        Box(
            Modifier
                .weight(1f)
                .height(7.dp)
                .background(PaperLine, RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f))
                    .background(color, RoundedCornerShape(50))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(amount, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(90.dp), textAlign = TextAlign.End)
    }
}

/** A read-only text field that opens a Material3 date picker dialog when tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
            }
        },
        modifier = modifier
    )

    if (showPicker) {
        val initialMillis = runCatching {
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onValueChange(date.toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

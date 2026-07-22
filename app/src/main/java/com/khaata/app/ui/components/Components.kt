package com.khaata.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.khaata.app.util.formatINR
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color = Ink,
    sub: String? = null,
    /**
     * When set, the figure counts up/down to this amount (re-formatting through
     * [format] each frame) instead of snapping — the signature "modern finance app"
     * move. Callers with a raw number pass it here; [value] is the fallback text for
     * everything else (percentages, non-money figures).
     */
    animatedValue: Double? = null,
    format: (Double) -> String = ::formatINR,
) {
    Column(
        modifier
            .background(PaperCard, RoundedCornerShape(10.dp))
            .border(1.dp, PaperLine, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(6.dp))
        val shown = if (animatedValue != null) {
            val animated by animateFloatAsState(
                targetValue = animatedValue.toFloat(),
                animationSpec = valueReveal(),
                label = "summaryAmount"
            )
            format(animated.toDouble())
        } else value
        Text(shown, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = accent, fontFamily = FontFamily.Monospace)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = Muted)
        }
    }
}

/** A small "stamped" progress ring, like a passbook seal, showing % complete. */
@Composable
fun ProgressStamp(pct: Float, color: Color, size: Dp = 64.dp) {
    // Sweep the ring and count the label up to the real percentage instead of
    // drawing it at its final angle instantly — the single most "alive" touch on
    // the Goals and Dashboard cards.
    val animatedPct by animateFloatAsState(
        targetValue = pct.coerceIn(0f, 100f),
        animationSpec = valueReveal(),
        label = "ringSweep"
    )
    val animatedColor by androidx.compose.animation.animateColorAsState(color, animationSpec = valueReveal(), label = "ringColor")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val diameter = kotlin.math.min(this.size.width, this.size.height) - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(color = PaperLine, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color = animatedColor, startAngle = -90f, sweepAngle = 360f * (animatedPct / 100f), useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${animatedPct.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = Ink)
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
    // One-shot celebratory pop the moment a goal flips to ACHIEVED. Keyed on the
    // status so it fires on the transition, not on every recomposition/scroll.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(status) {
        if (status == GoalStatus.ACHIEVED) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .scale(pop.value)
            .background(style.bg, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Icon(style.icon, contentDescription = null, tint = style.fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(style.text, color = style.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A ledger-style row: a colored dot, a label, a fill bar, and a right-aligned amount.
 * The bar grows from empty to its fraction on appear, staggered by [index] so a list
 * of them reads as a dashboard coming to life rather than a static chart.
 */
@Composable
fun CategoryBarRow(label: String, color: Color, amount: String, pct: Float, index: Int = 0) {
    val target = (pct / 100f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = target,
        animationSpec = valueReveal(delayMillis = staggerDelay(index)),
        label = "barFill"
    )
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
                    .fillMaxWidth(fraction = animatedFraction)
                    .background(color, RoundedCornerShape(50))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(amount, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(90.dp), textAlign = TextAlign.End)
    }
}

/**
 * A read-only text field that opens a Material3 date picker dialog when tapped.
 *
 * [allowFuture] defaults to true (goal target dates, etc. legitimately live in the
 * future). Pass false for things that can only have happened already — like an
 * expense date — to gray out and block every day after today in the calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowFuture: Boolean = true,
) {
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
        // When future dates are disallowed, cap selection at the end of today (UTC,
        // matching the picker's own UTC-based millis) so today itself stays pickable.
        val selectableDates = remember(allowFuture) {
            if (allowFuture) {
                object : SelectableDates {}
            } else {
                val todayEndMillis = LocalDate.now(ZoneOffset.UTC)
                    .plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1
                val todayYear = LocalDate.now(ZoneOffset.UTC).year
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayEndMillis
                    override fun isSelectableYear(year: Int) = year <= todayYear
                }
            }
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = selectableDates
        )

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

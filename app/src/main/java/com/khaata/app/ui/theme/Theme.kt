package com.khaata.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- Khaata palette: a ledger / passbook feel, not a generic Material default ---
val Ink = Color(0xFF1B2A38)
val NavySoft = Color(0xFF2E4258)
val Paper = Color(0xFFFBF8F1)
val PaperCard = Color(0xFFFFFDF8)
val PaperLine = Color(0xFFE2DCC8)
val Green = Color(0xFF2F6F4E)
val GreenSoft = Color(0xFFE2EEE6)
val Gold = Color(0xFFC18A2D)
val GoldSoft = Color(0xFFF1E9D2)
val Rust = Color(0xFFB5482F)
val RustSoft = Color(0xFFF3E2D6)
val Overdue = Color(0xFF8A2E1B)
val OverdueSoft = Color(0xFFEAD6D2)
val Muted = Color(0xFF6B6357)
val MutedOnInk = Color(0xFF9FB0C0)

private val KhaataColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Green,
    onSecondary = Color.White,
    tertiary = Gold,
    background = Paper,
    onBackground = Ink,
    surface = PaperCard,
    onSurface = Ink,
    surfaceVariant = PaperCard,
    error = Rust,
    outline = PaperLine,
)

/**
 * Khaata is deliberately a light-only, paper-and-ink theme — like an actual
 * passbook, it doesn't have a dark mode.
 */
@Composable
fun KhaataTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KhaataColorScheme,
        content = content
    )
}

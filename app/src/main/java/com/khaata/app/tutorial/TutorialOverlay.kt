package com.khaata.app.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.GoldSoft
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard

/** One card in a contextual walkthrough for a single screen. */
data class TutorialStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

/**
 * A focused, dismiss-anywhere walkthrough: a dimmed scrim + one card at a
 * time describing what matters on the current screen.
 *
 * Why not pixel-perfect "spotlight" coach marks? Because in a fast-moving
 * Compose codebase, anchoring tooltips to exact widget positions is brittle
 * (breaks on every layout tweak) and most users just want the gist of what
 * a screen does, not a guided finger-poke at every button. This pattern is
 * the same one used by Google Pay / Walnut style finance apps for first-run
 * help: short, skippable, and never blocks real use of the app for long.
 */
@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    onDismiss: () -> Unit,
) {
    if (steps.isEmpty()) {
        onDismiss()
        return
    }
    var index by remember { mutableStateOf(0) }
    val step = steps[index]
    val isLast = index == steps.lastIndex

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.62f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = PaperCard,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.size(40.dp).background(GoldSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(step.icon, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Tip ${index + 1} of ${steps.size}",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Skip", color = Muted) }
                }

                Spacer(Modifier.height(14.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "tutorial-step"
                ) { s ->
                    Column {
                        Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(s.description, fontSize = 13.5.sp, color = Muted, lineHeight = 19.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .height(4.dp)
                                .weight(1f)
                                .background(
                                    if (i <= index) Gold else Muted.copy(alpha = 0.25f),
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { if (isLast) onDismiss() else index++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLast) "Got it" else "Next")
                }
            }
        }
    }
}

/**
 * Per-screen tutorial content, kept in one place so copy can be reviewed
 * and updated without touching screen layout code. Each list maps to a
 * screenId used with [com.khaata.app.onboarding.hasTutorialBeenSeen].
 */
object TutorialContent {
    const val DASHBOARD = "dashboard"
    const val BUDGETS = "budgets"
    const val GOALS = "goals"
    const val ADD_ENTRY = "add_entry"
    const val ANALYTICS = "analytics"
    const val HISTORY = "history"

    fun stepsFor(screenId: String): List<TutorialStep> = when (screenId) {
        DASHBOARD -> listOf(
            TutorialStep(
                Icons.Filled.Home,
                "This is your Dashboard",
                "A live snapshot of this month: income, kharcha (spend), net savings, and how your budgets are tracking \u2014 all in one glance."
            ),
            TutorialStep(
                Icons.Filled.ShowChart,
                "Color tells you the status",
                "Green means on track, gold means watch closely, rust/red means you're over a budget or behind a goal \u2014 no need to read every number."
            ),
            TutorialStep(
                Icons.Filled.Flag,
                "Goals show up here too",
                "Once you add a savings goal, this screen tells you exactly how much more you need to save this month to stay on pace."
            ),
        )
        BUDGETS -> listOf(
            TutorialStep(
                Icons.Filled.ListAlt,
                "Cap what matters",
                "Pick a category and set a monthly limit. Khaata tracks every expense you log against it automatically \u2014 no manual math."
            ),
            TutorialStep(
                Icons.Filled.ShowChart,
                "Runway warnings",
                "If you're spending fast enough that a budget will run out before month-end, Khaata flags it early \u2014 not after it's already happened."
            ),
        )
        GOALS -> listOf(
            TutorialStep(
                Icons.Filled.Flag,
                "Saving toward something?",
                "Add a goal with a target amount and date. Khaata works out how much you need to save each month to get there on time."
            ),
            TutorialStep(
                Icons.Filled.ShowChart,
                "Log contributions anytime",
                "Every time you set money aside for a goal, log it here \u2014 your pace and status update instantly."
            ),
        )
        ADD_ENTRY -> listOf(
            TutorialStep(
                Icons.Filled.Add,
                "Logging an expense takes seconds",
                "Pick a category, type the amount, add an optional note, and save. The date defaults to today but you can backdate it."
            ),
        )
        ANALYTICS -> listOf(
            TutorialStep(
                Icons.Filled.ShowChart,
                "Spot the trend",
                "See how your spending and savings have moved over past months, and which categories are creeping up."
            ),
        )
        HISTORY -> listOf(
            TutorialStep(
                Icons.Filled.History,
                "Every month, on record",
                "Tap any past month to reopen its full ledger on the Dashboard \u2014 useful for double-checking or comparing months."
            ),
        )
        else -> emptyList()
    }
}
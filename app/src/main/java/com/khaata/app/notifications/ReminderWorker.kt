package com.khaata.app.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.khaata.app.data.model.BudgetStatus
import com.khaata.app.data.repository.FinanceRepository
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.GoalStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return Result.success()
        val repository = FinanceRepository(uid)

        // The worker runs every 24h in the background, so it's also our safety net
        // for materializing recurring expenses: if the app isn't opened for a month
        // (or several), this back-fills the missed occurrences that the on-open pass
        // in FinanceViewModel would otherwise never revisit. Idempotent, so running
        // it here as well as on app-open never double-posts.
        runCatching { repository.postDueRecurring(currentMonthKey()) }

        // From Android 13 on, notify() silently no-ops without POST_NOTIFICATIONS.
        // Bail out explicitly rather than doing all the reads only to drop the
        // notifications on the floor. (Inline check so Lint's MissingPermission
        // detector sees the guard.)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val monthKey = repository.loadLatestMonthKey() ?: currentMonthKey()
        val settings = loadReminderSettings(applicationContext)

        val budgetProgress = repository.latestBudgetProgress(monthKey)
        val riskyBudgets = budgetProgress.filter { (it.projectedRunout || it.status == BudgetStatus.OVER) && settings.budgetWarningsEnabled }
        if (riskyBudgets.isNotEmpty()) {
            val top = riskyBudgets.first()
            val message = if (top.projectedRunout) {
                "${top.category} is likely to run out in ${top.projectedRunoutDays} day${if (top.projectedRunoutDays == 1) "" else "s"}."
            } else {
                "${top.category} is over its monthly budget."
            }
            // A budget warning is informational — there's nothing to log, so no
            // "Quick add" action; tapping it lands on the Budgets tab instead.
            showAlertNotification(applicationContext, BUDGET_WARNING_NOTIFICATION_ID, "Budget warning", message, openTab = "budgets")
        }

        // Only nag about inactivity once there's a real logging history to be
        // inactive *from*. A brand-new user has no latest-expense date, and treating
        // that as "999 days inactive" would greet them with an absurd first
        // notification ("you haven't added anything in 999 days"). No history → no nag.
        val lastLoggedDate = repository.loadLatestExpenseDate(monthKey)
        val inactiveDays = lastLoggedDate?.let {
            val last = LocalDate.parse(it, DateTimeFormatter.ISO_DATE)
            java.time.temporal.ChronoUnit.DAYS.between(last, LocalDate.now()).toInt()
        }

        if (settings.inactivityRemindersEnabled && inactiveDays != null && inactiveDays >= 3) {
            showReminderNotification(
                applicationContext,
                INACTIVITY_NOTIFICATION_ID,
                "Log your spending",
                "You haven’t added any entries in the last $inactiveDays days."
            )
        }

        if (settings.goalMilestonesEnabled) {
            repository.currentGoalStats()
                .filter { (_, stats) -> !stats.achieved }
                .forEach { (goal, stats) ->
                    val currentPct = stats.pct.toInt()
                    val milestone = stats.nextMilestonePct
                    val alreadyHit = loadMilestoneState(applicationContext, goal.id)
                    if (currentPct >= milestone && alreadyHit < milestone) {
                        saveMilestoneState(applicationContext, goal.id, milestone)
                        showAlertNotification(
                            applicationContext,
                            2000 + milestone + goal.id.hashCode().absoluteValue % 1000,
                            "Goal unlocked",
                            "${goal.name} just hit ${milestoneLabel(milestone)}. Keep going.",
                            openTab = "goals",
                            highPriority = true
                        )
                    }
                }
        }

        // ── Udhaar nudges ─────────────────────────────────────────────────
        // A gentle "this has been sitting for a while" for long-outstanding
        // balances (either direction). Nudges when the last ledger movement is
        // ≥ UDHAAR_STALE_DAYS old, then at most once every UDHAAR_RENUDGE_DAYS
        // per person after that — a nudge, not a daily nag.
        if (settings.udhaarRemindersEnabled) {
            val today = LocalDate.now()
            repository.loadPeopleOnce()
                .filter { it.balance.absoluteValue >= 1.0 }
                .forEach { person ->
                    val lastMovement = repository.lastLedgerDate(person.id) ?: return@forEach
                    val staleDays = runCatching {
                        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(lastMovement), today).toInt()
                    }.getOrNull() ?: return@forEach
                    if (staleDays < UDHAAR_STALE_DAYS) return@forEach

                    val lastNudge = loadUdhaarNudgeDate(applicationContext, person.id)
                    val daysSinceNudge = runCatching {
                        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(lastNudge), today).toInt()
                    }.getOrDefault(Int.MAX_VALUE)
                    if (daysSinceNudge < UDHAAR_RENUDGE_DAYS) return@forEach

                    val amountStr = "₹${"%,.0f".format(person.balance.absoluteValue)}"
                    val message = if (person.balance > 0) {
                        "${person.name} has owed you $amountStr for $staleDays days."
                    } else {
                        "You've owed ${person.name} $amountStr for $staleDays days."
                    }
                    saveUdhaarNudgeDate(applicationContext, person.id, today.toString())
                    showAlertNotification(
                        applicationContext,
                        3000 + person.id.hashCode().absoluteValue % 1000,
                        "Udhaar reminder",
                        message,
                        openTab = "people"
                    )
                }
        }

        return Result.success()
    }

    companion object {
        /** Days a balance must sit unchanged before the first nudge. */
        const val UDHAAR_STALE_DAYS = 14
        /** Minimum days between nudges for the same person. */
        const val UDHAAR_RENUDGE_DAYS = 7
    }
}
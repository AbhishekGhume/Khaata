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
            showReminderNotification(applicationContext, BUDGET_WARNING_NOTIFICATION_ID, "Budget warning", message)
        }

        val lastLoggedDate = repository.loadLatestExpenseDate(monthKey)
        val inactiveDays = lastLoggedDate?.let {
            val last = LocalDate.parse(it, DateTimeFormatter.ISO_DATE)
            java.time.temporal.ChronoUnit.DAYS.between(last, LocalDate.now()).toInt()
        } ?: 999

        if (settings.inactivityRemindersEnabled && inactiveDays >= 3) {
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
                        showMilestoneNotification(
                            applicationContext,
                            2000 + milestone + goal.id.hashCode().absoluteValue % 1000,
                            "Goal unlocked",
                            "${goal.name} just hit ${milestoneLabel(milestone)}. Keep going."
                        )
                    }
                }
        }

        return Result.success()
    }
}
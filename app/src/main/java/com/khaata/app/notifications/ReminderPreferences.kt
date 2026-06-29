package com.khaata.app.notifications

import android.content.Context

private const val PREFS_NAME = "khaata_reminders"
private const val KEY_BUDGET_WARNINGS = "budget_warnings"
private const val KEY_INACTIVITY_REMINDERS = "inactivity_reminders"
private const val KEY_GOAL_MILESTONES = "goal_milestones"
private const val KEY_DAILY_REMINDER_ENABLED = "daily_reminder_enabled"
private const val KEY_DAILY_REMINDER_HOUR = "daily_reminder_hour"
private const val KEY_DAILY_REMINDER_MINUTE = "daily_reminder_minute"
private const val KEY_GOAL_MILESTONE_PREFIX = "goal_milestone_"

data class ReminderSettings(
    val budgetWarningsEnabled: Boolean = true,
    val inactivityRemindersEnabled: Boolean = true,
    val goalMilestonesEnabled: Boolean = true
    ,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderHour: Int = 20,
    val dailyReminderMinute: Int = 0
)

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun loadReminderSettings(context: Context): ReminderSettings {
    val sharedPrefs = prefs(context)
    return ReminderSettings(
        budgetWarningsEnabled = sharedPrefs.getBoolean(KEY_BUDGET_WARNINGS, true),
        inactivityRemindersEnabled = sharedPrefs.getBoolean(KEY_INACTIVITY_REMINDERS, true),
        goalMilestonesEnabled = sharedPrefs.getBoolean(KEY_GOAL_MILESTONES, true)
        ,
        dailyReminderEnabled = sharedPrefs.getBoolean(KEY_DAILY_REMINDER_ENABLED, false),
        dailyReminderHour = sharedPrefs.getInt(KEY_DAILY_REMINDER_HOUR, 20),
        dailyReminderMinute = sharedPrefs.getInt(KEY_DAILY_REMINDER_MINUTE, 0)
    )
}

fun saveReminderSettings(context: Context, settings: ReminderSettings) {
    prefs(context).edit()
        .putBoolean(KEY_BUDGET_WARNINGS, settings.budgetWarningsEnabled)
        .putBoolean(KEY_INACTIVITY_REMINDERS, settings.inactivityRemindersEnabled)
        .putBoolean(KEY_GOAL_MILESTONES, settings.goalMilestonesEnabled)
    .putBoolean(KEY_DAILY_REMINDER_ENABLED, settings.dailyReminderEnabled)
    .putInt(KEY_DAILY_REMINDER_HOUR, settings.dailyReminderHour)
    .putInt(KEY_DAILY_REMINDER_MINUTE, settings.dailyReminderMinute)
        .apply()
}

fun milestoneLabel(pct: Int): String = when (pct) {
    25 -> "25% milestone reached"
    50 -> "50% milestone reached"
    75 -> "75% milestone reached"
    100 -> "Goal completed"
    else -> "$pct% milestone reached"
}

fun loadMilestoneState(context: Context, goalId: String): Int {
    return prefs(context).getInt(KEY_GOAL_MILESTONE_PREFIX + goalId, 0)
}

fun saveMilestoneState(context: Context, goalId: String, pct: Int) {
    prefs(context).edit().putInt(KEY_GOAL_MILESTONE_PREFIX + goalId, pct).apply()
}
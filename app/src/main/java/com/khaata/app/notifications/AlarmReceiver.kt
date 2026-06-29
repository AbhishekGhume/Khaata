package com.khaata.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule if user has enabled daily reminder
            val settings = loadReminderSettings(context)
            if (settings.dailyReminderEnabled) {
                scheduleDailyReminder(context, settings.dailyReminderHour, settings.dailyReminderMinute)
            }
            return
        }

        // Regular alarm: show the daily logging reminder
        showReminderNotification(context, 3001, "Add today's entries", "Tap to add today's entries.")
    }
}

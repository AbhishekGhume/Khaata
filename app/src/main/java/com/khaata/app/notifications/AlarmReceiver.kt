package com.khaata.app.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat


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

        // Regular alarm: show the daily logging reminder — but only if we're allowed
        // to post. From Android 13 on, notify() silently no-ops without the runtime
        // permission; guard inline so Lint's MissingPermission detector sees it.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        showReminderNotification(context, 3001, "Add today's entries", "Tap to add today's entries.")
    }
}

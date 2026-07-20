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
        // Both a reboot and an app update wipe scheduled alarms, so re-arm on either.
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val settings = loadReminderSettings(context)
            if (settings.dailyReminderEnabled) {
                scheduleDailyReminder(context, settings.dailyReminderHour, settings.dailyReminderMinute)
            }
            return
        }

        // Regular daily alarm. The exact alarm is one-shot (see scheduleDailyReminder),
        // so re-arm tomorrow's before anything else — even if the notification is later
        // suppressed for lack of permission, the schedule must survive so it keeps
        // trying once the user grants it.
        val settings = loadReminderSettings(context)
        if (settings.dailyReminderEnabled) {
            scheduleDailyReminder(context, settings.dailyReminderHour, settings.dailyReminderMinute)
        }

        // Show the daily logging reminder — but only if we're allowed to post. From
        // Android 13 on, notify() silently no-ops without the runtime permission;
        // guard inline so Lint's MissingPermission detector sees it.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        showReminderNotification(context, DAILY_REMINDER_NOTIFICATION_ID, "Add today's entries", "Tap to add today's entries.")
    }
}

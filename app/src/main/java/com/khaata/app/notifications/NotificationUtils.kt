package com.khaata.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khaata.app.R
import com.khaata.app.MainActivity
import com.khaata.app.widget.QuickAddActivity
import androidx.annotation.RequiresPermission
import java.util.Calendar

const val KHAATA_NOTIFICATION_CHANNEL_ID = "khaata_reminders"
const val BUDGET_WARNING_NOTIFICATION_ID = 1001
const val INACTIVITY_NOTIFICATION_ID = 1002
const val EXTRA_OPEN_ADD_ENTRY = "com.khaata.app.notifications.EXTRA_OPEN_ADD_ENTRY"
// Which tab an alert notification should land on when tapped ("budgets" | "goals").
const val EXTRA_OPEN_TAB = "com.khaata.app.notifications.EXTRA_OPEN_TAB"
// Optional preselected category key for the quick-add popup; shared by the widget
// shortcuts and the notification "Quick add" action.
const val EXTRA_QUICK_ADD_CATEGORY = "com.khaata.app.notifications.EXTRA_QUICK_ADD_CATEGORY"

/**
 * True when we're allowed to post notifications. Below Android 13 the runtime
 * permission doesn't exist and notifications are always allowed; from Tiramisu on
 * it must be granted or `notify()` silently no-ops. Callers of the
 * `@RequiresPermission(POST_NOTIFICATIONS)` helpers must gate on this so denied
 * reminders are skipped explicitly rather than vanishing without a trace.
 */
fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

fun ensureNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        KHAATA_NOTIFICATION_CHANNEL_ID,
        "Khaata reminders",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Budget and logging reminders for Khaata"
    }
    manager.createNotificationChannel(channel)
}

/**
 * Reminder to *log something* (daily reminder, inactivity nag). These are the only
 * notifications that carry the "＋ Quick add" action — the whole point of the
 * notification is to get an entry added, so we offer the one-tap popup. Tapping
 * the body opens the app on the Add Entry tab.
 */
@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
fun showReminderNotification(context: Context, id: Int, title: String, body: String) {
    ensureNotificationChannel(context)
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(EXTRA_OPEN_ADD_ENTRY, true)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val contentIntent = PendingIntent.getActivity(
        context,
        id,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    // One-tap "Quick add" action: opens the lightweight popup that writes straight to
    // Firestore, so the user can log an entry without loading the whole app.
    val quickAddIntent = Intent(context, QuickAddActivity::class.java)
    val quickAddPending = PendingIntent.getActivity(
        context,
        id + 40000,
        quickAddIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, KHAATA_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentIntent)
        .addAction(R.mipmap.ic_launcher, "＋ Quick add", quickAddPending)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(id, notification)
}

/**
 * Informational alert (budget warning, goal milestone) — something to *look at*,
 * not something to log, so deliberately no "Quick add" action. Tapping it opens
 * the app on [openTab] ("budgets" / "goals"), or just the Dashboard when null.
 */
@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
fun showAlertNotification(
    context: Context,
    id: Int,
    title: String,
    body: String,
    openTab: String? = null,
    highPriority: Boolean = false,
) {
    ensureNotificationChannel(context)
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        if (openTab != null) putExtra(EXTRA_OPEN_TAB, openTab)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val contentIntent = PendingIntent.getActivity(
        context,
        id,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, KHAATA_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(id, notification)
}

fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pending = PendingIntent.getBroadcast(
        context,
        6001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val now = Calendar.getInstance()
    val trigger = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
    }

    // Prefer an exact alarm so "remind me at 20:00" actually lands near 20:00.
    // setInexactRepeating batches with other alarms and, under Doze, can slip by
    // hours or skip a day — which defeats a user-chosen reminder time. Exact alarms
    // are one-shot, so AlarmReceiver re-arms the next day after it fires. On Android
    // 12+ exact scheduling needs a permission we don't hold by default; if it's not
    // granted we fall back to a daily inexact repeat rather than crash or go silent.
    val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    if (canExact) {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
    } else {
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
    }
}

fun cancelDailyReminder(context: Context) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pending = PendingIntent.getBroadcast(
        context,
        6001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    am.cancel(pending)
}
package com.khaata.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.khaata.app.R
import com.khaata.app.MainActivity
import androidx.annotation.RequiresPermission
import java.util.Calendar

const val KHAATA_NOTIFICATION_CHANNEL_ID = "khaata_reminders"
const val BUDGET_WARNING_NOTIFICATION_ID = 1001
const val INACTIVITY_NOTIFICATION_ID = 1002
const val TEST_NOTIFICATION_ID = 1099
const val EXTRA_OPEN_ADD_ENTRY = "com.khaata.app.notifications.EXTRA_OPEN_ADD_ENTRY"

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
    val notification = NotificationCompat.Builder(context, KHAATA_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(id, notification)
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
fun showMilestoneNotification(context: Context, id: Int, title: String, body: String) {
    ensureNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, KHAATA_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
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

    // Use inexact repeating for battery friendliness
    am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
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
package com.khaata.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.khaata.app.R
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import java.util.Calendar

const val KHAATA_NOTIFICATION_CHANNEL_ID = "khaata_reminders"
const val BUDGET_WARNING_NOTIFICATION_ID = 1001
const val INACTIVITY_NOTIFICATION_ID = 1002
const val TEST_NOTIFICATION_ID = 1099

fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

fun showReminderNotification(context: Context, id: Int, title: String, body: String) {
    ensureNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, KHAATA_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(id, notification)
}

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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
    )
    am.cancel(pending)
}
package com.khaata.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.khaata.app.R

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
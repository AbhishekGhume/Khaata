package com.khaata.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.app.TimePickerDialog
import java.util.Calendar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import com.khaata.app.notifications.TEST_NOTIFICATION_ID
import com.khaata.app.notifications.loadReminderSettings
import com.khaata.app.notifications.saveReminderSettings
import com.khaata.app.notifications.scheduleDailyReminder
import com.khaata.app.notifications.cancelDailyReminder
import com.khaata.app.notifications.showReminderNotification
import com.khaata.app.onboarding.resetAllTutorials
import com.khaata.app.security.isAppLockEnabled
import com.khaata.app.security.setAppLockEnabled
import com.khaata.app.ui.theme.GoldSoft
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.viewmodel.FinanceViewModel

@Composable
fun NotificationSettingsScreen(viewModel: FinanceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadReminderSettings(context)) }
    var hour by remember { mutableStateOf(settings.dailyReminderHour) }
    var minute by remember { mutableStateOf(settings.dailyReminderMinute) }

    // Whether the OS will actually let notifications through. Below Android 13 the
    // permission doesn't exist, so it's always granted. This is the missing feedback
    // loop: without it a user flips every reminder switch ON, the permission was
    // denied once at first launch, and nothing ever fires — with no hint why.
    fun notificationsGranted(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    var notificationsAllowed by remember { mutableStateOf(notificationsGranted()) }

    // Re-check on resume so returning from system settings (where the user may have
    // toggled the permission) refreshes the banner without needing a screen reload.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = notificationsGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val anyNotificationOn = settings.budgetWarningsEnabled || settings.inactivityRemindersEnabled ||
        settings.goalMilestonesEnabled || settings.dailyReminderEnabled

    LaunchedEffect(settings) {
        saveReminderSettings(context, settings)
        if (settings.dailyReminderEnabled) {
            scheduleDailyReminder(context, settings.dailyReminderHour, settings.dailyReminderMinute)
        } else {
            cancelDailyReminder(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Text("Privacy", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        var appLockEnabled by remember { mutableStateOf(isAppLockEnabled(context)) }
        SettingCard(
            title = "Require unlock to open Khaata",
            subtitle = "Ask for your phone PIN, pattern, or biometrics each time you open the app. Applies from the next time you open Khaata.",
            checked = appLockEnabled,
            onCheckedChange = { appLockEnabled = it; setAppLockEnabled(context, it) }
        )

        Text("Notifications", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text("Choose which local reminders you want Khaata to send.", color = Muted, fontSize = 13.sp)

        // Reminders are worthless if the OS is dropping them. Surface that plainly the
        // moment a reminder is on without permission, with a one-tap way to fix it,
        // instead of letting the switches sit ON while nothing is ever delivered.
        if (anyNotificationOn && !notificationsAllowed) {
            Surface(
                color = com.khaata.app.ui.theme.Rust.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, com.khaata.app.ui.theme.Rust.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(
                        "Notifications are blocked",
                        color = com.khaata.app.ui.theme.Rust,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Khaata can't deliver these reminders until you allow notifications. " +
                            "Your choices above are saved and will start working once you do.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            // Opening the app's own notification settings page works on
                            // every Android version and can never silently no-op the way
                            // a permanently-denied runtime permission request does — so the
                            // user always has a real, non-dead-end way to turn them back on.
                            // The ON_RESUME re-check clears this banner when they return.
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.khaata.app.ui.theme.Rust, contentColor = Paper)
                    ) {
                        Text("Allow notifications")
                    }
                }
            }
        }

        SettingCard(
            title = "Budget warnings",
            subtitle = "Alert when a budget is likely to run out or goes over cap",
            checked = settings.budgetWarningsEnabled,
            onCheckedChange = { settings = settings.copy(budgetWarningsEnabled = it) }
        )
        SettingCard(
            title = "Inactivity reminders",
            subtitle = "Remind you when you have not logged spending for a few days",
            checked = settings.inactivityRemindersEnabled,
            onCheckedChange = { settings = settings.copy(inactivityRemindersEnabled = it) }
        )
        SettingCard(
            title = "Goal milestones",
            subtitle = "Celebrate 25%, 50%, 75% and 100% progress toward a goal",
            checked = settings.goalMilestonesEnabled,
            onCheckedChange = { settings = settings.copy(goalMilestonesEnabled = it) }
        )

        // Daily logging reminder
        Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily logging reminder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Remind me to add today's entries at ${String.format("%02d:%02d", hour, minute)}",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = settings.dailyReminderEnabled,
                        onCheckedChange = { enabled ->
                            settings = settings.copy(dailyReminderEnabled = enabled, dailyReminderHour = hour, dailyReminderMinute = minute)
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        val c = Calendar.getInstance()
                        val picker = TimePickerDialog(
                            context,
                            { _, h, m ->
                                hour = h
                                minute = m
                                settings = settings.copy(dailyReminderHour = hour, dailyReminderMinute = minute)
                            },
                            settings.dailyReminderHour,
                            settings.dailyReminderMinute,
                            true
                        )
                        picker.show()
                    }, colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)) {
                        Text("Change time")
                    }
                }
            }
        }

        // ── In-app tutorial controls ──────────────────────────────────────
        var tutorialsReset by remember { mutableStateOf(false) }
        Surface(color = GoldSoft, border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)), shape = RoundedCornerShape(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("In-app tips & tutorials", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (tutorialsReset) "Tips will show again when you visit each screen."
                        else "Each screen shows a quick tip the first time you open it. Tap to see them again.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        resetAllTutorials(context)
                        tutorialsReset = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Paper),
                    enabled = !tutorialsReset
                ) {
                    Text(if (tutorialsReset) "Done!" else "Replay")
                }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
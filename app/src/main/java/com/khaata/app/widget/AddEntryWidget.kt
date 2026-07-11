package com.khaata.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.khaata.app.MainActivity
import com.khaata.app.notifications.EXTRA_OPEN_ADD_ENTRY

/**
 * A tiny home-screen widget: the Khaata mark over a "＋ Add entry" tap target.
 * Tapping it opens MainActivity with the same [EXTRA_OPEN_ADD_ENTRY] extra the
 * reminder notifications use, so the existing intent handling routes straight to
 * the Add Entry tab (and correctly waits behind the lock gate first).
 *
 * Drawn in Compose via Glance — no XML UI layout, in keeping with the app.
 */
class AddEntryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current
        val openAddEntry = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_ADD_ENTRY, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF23201A))
                .padding(14.dp)
                .clickable(actionStartActivity(openAddEntry)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Khaata",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFF3E9D2)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "＋ Add entry",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFD9A741)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

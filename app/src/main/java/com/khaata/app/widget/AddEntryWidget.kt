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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.khaata.app.notifications.EXTRA_QUICK_ADD_CATEGORY

/**
 * Home-screen quick-add widget: a "Khaata ／ ＋ Add" header over a row of category
 * shortcut chips. Every tap opens [QuickAddActivity] — the general "＋ Add" with no
 * category, each chip with its category preselected — so an expense is logged in a
 * couple of taps without loading the full app.
 *
 * Drawn in Compose via Glance — no XML UI layout, in keeping with the app.
 */
class AddEntryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    /** Short chip labels + category key routed into the quick-add popup. */
    private val shortcuts = listOf(
        "Food" to "food",
        "Transport" to "transport",
        "Bills" to "bills",
        "Shopping" to "shopping",
    )

    private fun quickAddIntent(context: Context, category: String?): Intent =
        Intent(context, QuickAddActivity::class.java).apply {
            if (category != null) putExtra(EXTRA_QUICK_ADD_CATEGORY, category)
        }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF23201A))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Header: brand mark on the left, general "＋ Add" tap target on the right.
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Khaata",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFF3E9D2)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "＋ Add",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF23201A)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier
                        .background(Color(0xFFD9A741))
                        .cornerRadius(14.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(actionStartActivity(quickAddIntent(context, null)))
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            // Category shortcut chips — each preselects its category in the popup.
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                shortcuts.forEachIndexed { index, (label, key) ->
                    if (index > 0) Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = label,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFF3E9D2)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        modifier = GlanceModifier
                            .defaultWeight()
                            .background(Color(0x33FFFFFF))
                            .cornerRadius(12.dp)
                            .padding(vertical = 8.dp)
                            .clickable(actionStartActivity(quickAddIntent(context, key)))
                    )
                }
            }
        }
    }
}

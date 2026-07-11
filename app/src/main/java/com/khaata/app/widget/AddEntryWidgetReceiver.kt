package com.khaata.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Binds [AddEntryWidget] to the AppWidget framework. Registered in the manifest. */
class AddEntryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AddEntryWidget()
}

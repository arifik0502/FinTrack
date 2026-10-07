package com.example.fintrack.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.fintrack.MainActivity

class ScanWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Row(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.primaryContainer).cornerRadius(24.dp)
                        .padding(12.dp).clickable(actionStartActivity<MainActivity>(actionParametersOf(ActionParameters.Key<Boolean>("scan") to true))),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(ImageProvider(android.R.drawable.ic_menu_camera), "Scan receipt", GlanceModifier.size(28.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text("Scan receipt", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

class ScanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScanWidget()
}

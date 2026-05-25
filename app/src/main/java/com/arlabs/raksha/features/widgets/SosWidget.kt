package com.arlabs.raksha.features.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.action.ActionParameters
import com.arlabs.raksha.services.SosManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SosWidgetEntryPoint {
    fun sosManager(): SosManager
}

class SosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SosWidget()
}

class SosWidget : GlanceAppWidget() {
    companion object {
        val STATE_KEY = stringPreferencesKey("sos_widget_state") // idle, count_5..count_1
        val CANCEL_FLAG_KEY = booleanPreferencesKey("sos_cancel_flag")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                SosWidgetContent()
            }
        }
    }
}

@Composable
fun SosWidgetContent() {
    val prefs = currentState<Preferences>()
    val state = prefs[SosWidget.STATE_KEY] ?: "idle"

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E)),
        contentAlignment = Alignment.Center
    ) {
        if (state.startsWith("count_")) {
            val count = state.split("_")[1]
            // Countdown State
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFFFF9800)) // Warning Orange during countdown
                    .clickable(onClick = actionRunCallback<CancelSosAction>()),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = count,
                        style = TextStyle(
                            color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Tap to Cancel",
                        style = TextStyle(
                            color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        } else {
            // Idle State (Red Button)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .background(Color.Red)
                    .clickable(onClick = actionRunCallback<TriggerSosAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SOS",
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

class TriggerSosAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Reset cancel flag at the start
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SosWidget.CANCEL_FLAG_KEY] = false
        }

        // Countdown from 5 to 1
        for (i in 5 downTo 1) {
            // Check cancel flag BEFORE updating countdown
            if (isCancelled(context, glanceId)) return

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[SosWidget.STATE_KEY] = "count_$i"
            }
            SosWidget().update(context, glanceId)
            delay(1000)
        }

        // Final cancel check after last second
        if (isCancelled(context, glanceId)) return

        // Fire! Reset to idle first
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SosWidget.STATE_KEY] = "idle"
            prefs[SosWidget.CANCEL_FLAG_KEY] = false
        }
        SosWidget().update(context, glanceId)

        // Trigger SMS and Cloud
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            SosWidgetEntryPoint::class.java
        )
        val sosManager = entryPoint.sosManager()
        sosManager.sendSosToAllContacts(triggerCloudSos = true)
    }

    private suspend fun isCancelled(context: Context, glanceId: GlanceId): Boolean {
        var cancelled = false
        updateAppWidgetState(context, glanceId) { prefs ->
            if (prefs[SosWidget.CANCEL_FLAG_KEY] == true) {
                cancelled = true
                // Reset state back to idle
                prefs[SosWidget.STATE_KEY] = "idle"
                prefs[SosWidget.CANCEL_FLAG_KEY] = false
            }
        }
        if (cancelled) {
            SosWidget().update(context, glanceId)
        }
        return cancelled
    }
}

class CancelSosAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Set the cancel flag — the TriggerSosAction coroutine will read this
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SosWidget.CANCEL_FLAG_KEY] = true
            prefs[SosWidget.STATE_KEY] = "idle"
        }
        SosWidget().update(context, glanceId)
    }
}

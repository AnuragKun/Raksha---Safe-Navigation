package com.arlabs.raksha.features.widgets

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.arlabs.raksha.domain.repository.TimerRepository
import com.arlabs.raksha.services.TimerService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimerWidgetEntryPoint {
    fun timerRepository(): TimerRepository
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerWidget()
}

class TimerWidget : GlanceAppWidget() {
    companion object {
        val DURATION_KEY = intPreferencesKey("timer_widget_duration_mins")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                TimerWidgetContent()
            }
        }
    }
}

@Composable
fun TimerWidgetContent() {
    val prefs = currentState<Preferences>()
    val durationMins = prefs[TimerWidget.DURATION_KEY] ?: 20

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .cornerRadius(16.dp)
            .background(Color(0xFF1E1E2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Safety Timer",
                style = TextStyle(
                    color = androidx.glance.color.ColorProvider(day = Color.LightGray, night = Color.LightGray),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            
            Spacer(modifier = GlanceModifier.height(10.dp))
            
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Minus Button
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                        .cornerRadius(20.dp)
                        .clickable(onClick = actionRunCallback<DecreaseTimerAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", style = TextStyle(color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White), fontSize = 20.sp, fontWeight = FontWeight.Bold))
                }
                
                Spacer(modifier = GlanceModifier.width(12.dp))
                
                // Duration Display
                Text(
                    text = "$durationMins min",
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = GlanceModifier.width(12.dp))
                
                // Plus Button
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                        .cornerRadius(20.dp)
                        .clickable(onClick = actionRunCallback<IncreaseTimerAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", style = TextStyle(color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White), fontSize = 20.sp, fontWeight = FontWeight.Bold))
                }
            }
            
            Spacer(modifier = GlanceModifier.height(10.dp))
            
            // Start Button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFF00C853))
                    .cornerRadius(10.dp)
                    .clickable(onClick = actionRunCallback<StartTimerAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "START TIMER",
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.Black, night = Color.Black),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

class IncreaseTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[TimerWidget.DURATION_KEY] ?: 20
            if (current < 120) prefs[TimerWidget.DURATION_KEY] = current + 5
        }
        TimerWidget().update(context, glanceId)
    }
}

class DecreaseTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[TimerWidget.DURATION_KEY] ?: 20
            if (current > 5) prefs[TimerWidget.DURATION_KEY] = current - 5
        }
        TimerWidget().update(context, glanceId)
    }
}

class StartTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        var durationMins = 20
        updateAppWidgetState(context, glanceId) { prefs ->
            durationMins = prefs[TimerWidget.DURATION_KEY] ?: 20
        }

        // Step 1: Write timer data to Firestore (this is what was missing!)
        try {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                TimerWidgetEntryPoint::class.java
            )
            val timerRepository = entryPoint.timerRepository()
            val result = timerRepository.startTimer(durationMins)
            Log.d("TimerWidget", "Timer started in Firestore: $result")
        } catch (e: Exception) {
            Log.e("TimerWidget", "Failed to start timer in Firestore", e)
            return // Don't start service if Firestore write failed
        }

        // Step 2: Start the foreground service
        val intent = Intent(context, TimerService::class.java).apply {
            action = "START_TIMER"
            putExtra("duration_minutes", durationMins)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

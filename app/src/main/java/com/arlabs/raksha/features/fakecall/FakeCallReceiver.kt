package com.arlabs.raksha.features.fakecall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.arlabs.raksha.R

/**
 * BroadcastReceiver triggered by AlarmManager to show a fake incoming call.
 * Uses a full-screen intent to display FakeCallActivity even when the app
 * is in the background or the screen is locked.
 */
class FakeCallReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "fake_call_channel"
        const val NOTIFICATION_ID = 888
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val callerName = intent?.getStringExtra("CALLER_NAME") ?: "Unknown"
        val callerPhone = intent?.getStringExtra("CALLER_PHONE") ?: ""

        createNotificationChannel(context)

        // Build intent for the full-screen call activity
        val callIntent = Intent(context, FakeCallActivity::class.java).apply {
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_PHONE", callerPhone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // PRIMARY: Directly start the Activity — this is the most reliable path
        try {
            context.startActivity(callIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // BACKUP: Also fire a full-screen notification for lock-screen scenarios
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fake incoming call notifications"
                setSound(null, null) // We handle sound ourselves in FakeCallActivity
                enableVibration(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

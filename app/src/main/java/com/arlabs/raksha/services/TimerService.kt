package com.arlabs.raksha.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arlabs.raksha.MainActivity
import com.arlabs.raksha.R
import com.arlabs.raksha.domain.repository.TimerRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimerService : Service() {

    @Inject
    lateinit var timerRepository: TimerRepository

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var sosManager: SosManager

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Cancellation flag — set by Firestore observer when user taps "I'm Safe"
    @Volatile
    private var isCancelledByUser = false

    private var localCountdownJob: Job? = null
    private var reminderJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.lastLocation?.let { location ->
                updateLocationInFirestore(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        observeTimerCancellation()
        startLocalCountdown()
        return START_STICKY
    }

    /**
     * Observes Firestore for USER-INITIATED cancellations ONLY.
     * When user taps "I'm Safe", isCanceled flips to true → we set the flag.
     * This does NOT check time expiry — let the local countdown handle that.
     */
    private fun observeTimerCancellation() {
        serviceScope.launch {
            timerRepository.isTimerActive().collect { isActive ->
                if (!isActive && !isCancelledByUser) {
                    // Check if it was actually cancelled (not just expired)
                    try {
                        val (_, _, isCanceled) = timerRepository.getTimerDataOnce()
                        if (isCanceled) {
                            isCancelledByUser = true
                            Log.d(TAG, "User cancelled timer (I'm Safe)")
                            stopLocationUpdates()
                            localCountdownJob?.cancel()
                            reminderJob?.cancel()
                            stopSelf()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking cancellation: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Local countdown using one-shot Firestore read (getTimerDataOnce).
     * This does NOT use getExpiryTime() which filters by currentTime < expiryTime.
     * After delay, checks ONLY the cancellation flag — NOT the time.
     */
    private fun startLocalCountdown() {
        localCountdownJob = serviceScope.launch {
            try {
                // One-shot read: get expiryTime + durationMinutes regardless of time
                val (expiryTime, durationMinutes, isCanceled) = timerRepository.getTimerDataOnce()

                if (expiryTime <= 0L || isCanceled) {
                    Log.d(TAG, "No active timer found (expiry=$expiryTime, canceled=$isCanceled)")
                    stopSelf()
                    return@launch
                }

                val now = System.currentTimeMillis()
                val delayMs = expiryTime - now

                if (delayMs <= 0) {
                    // Timer already expired — check if cancelled
                    if (!isCancelledByUser) {
                        Log.d(TAG, "Timer already expired, triggering SOS now")
                        triggerSosAndNotify()
                    }
                    stopSelf()
                    return@launch
                }

                Log.d(TAG, "Local countdown: ${delayMs / 1000}s remaining, duration=${durationMinutes}min")

                // Start reminder notifications
                scheduleReminders(expiryTime, durationMinutes)

                // Wait for expiry
                delay(delayMs)

                // Timer expired — only check the cancellation flag, NOT isTimerActive()
                if (!isCancelledByUser) {
                    Log.d(TAG, "Timer expired! isCancelledByUser=$isCancelledByUser → Triggering SOS")
                    triggerSosAndNotify()
                } else {
                    Log.d(TAG, "Timer was cancelled before expiry, no SOS needed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Local countdown error: ${e.message}")
            } finally {
                stopLocationUpdates()
                stopSelf()
            }
        }
    }

    /**
     * Send SOS and show the expiry notification.
     * Also saves a flag so the app shows a dialog when reopened.
     */
    private suspend fun triggerSosAndNotify() {
        try {
            // Prevent double Twilio SMS: The Cloud Task handles the "timer expired" Twilio SMS.
            // We only want the native Android SMS here.
            val result = sosManager.sendSosToAllContacts(triggerCloudSos = false)
            Log.d(TAG, "SOS result: success=${result.first}, message=${result.second}")
            showTimerExpiredNotification(sosFailed = !result.first)

            // Save flag for in-app dialog when user reopens the app
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_TIMER_EXPIRED_SOS_SENT, true)
                .putLong(KEY_TIMER_EXPIRED_TIME, System.currentTimeMillis())
                .putBoolean(KEY_TIMER_SOS_FAILED, !result.first)
                .apply()

        } catch (e: Exception) {
            Log.e(TAG, "SOS exception: ${e.message}")
            showTimerExpiredNotification(sosFailed = true)

            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_TIMER_EXPIRED_SOS_SENT, true)
                .putLong(KEY_TIMER_EXPIRED_TIME, System.currentTimeMillis())
                .putBoolean(KEY_TIMER_SOS_FAILED, true)
                .apply()
        }
    }

    /**
     * Schedule reminder notifications before timer expiry.
     * - Timer ≤ 5min: only "1 minute left" reminder
     * - Timer > 5min: every 5 minutes + "1 minute left"
     * Example for 20min: reminders at 15min, 10min, 5min, 1min left
     */
    private fun scheduleReminders(expiryTime: Long, durationMinutes: Int) {
        reminderJob = serviceScope.launch {
            try {
                val reminderTimesMs = mutableListOf<Long>()

                if (durationMinutes > 5) {
                    // Add reminders at every 5-minute mark
                    var minutesLeft = durationMinutes - 5
                    while (minutesLeft >= 5) {
                        // Time when this reminder fires = expiryTime - minutesLeft * 60_000
                        reminderTimesMs.add(expiryTime - minutesLeft * 60_000L)
                        minutesLeft -= 5
                    }
                }

                // Always add "1 minute left" reminder (if timer ≥ 2 min)
                if (durationMinutes >= 2) {
                    reminderTimesMs.add(expiryTime - 60_000L)
                }

                // Sort and fire them
                reminderTimesMs.sort()
                for (fireTime in reminderTimesMs) {
                    val waitMs = fireTime - System.currentTimeMillis()
                    if (waitMs > 0) {
                        delay(waitMs)
                    }
                    if (isCancelledByUser) break

                    val secsLeft = ((expiryTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    val minsLeft = secsLeft / 60
                    val displayTime = if (minsLeft >= 1) "$minsLeft minute${if (minsLeft > 1) "s" else ""}"
                    else "$secsLeft seconds"

                    showReminderNotification(displayTime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reminder scheduling error: ${e.message}")
            }
        }
    }

    /**
     * Shows a reminder notification before timer expiry.
     */
    private fun showReminderNotification(timeLeft: String) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "safety_timer")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 2, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, EXPIRY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏳ $timeLeft left!")
            .setContentText("Mark yourself safe before the timer expires.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    /**
     * Shows a high-priority heads-up notification when the timer expires,
     * confirming to the user that their emergency contacts have been notified.
     */
    private fun showTimerExpiredNotification(sosFailed: Boolean = false) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "safety_timer")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val text: String
        if (sosFailed) {
            title = "⚠️ Safety Timer Expired"
            text = "Timer expired but SOS could not be sent. Please check your emergency contacts and SMS permissions."
        } else {
            title = "🚨 Safety Timer Expired"
            text = "Your emergency contacts have been notified with your last known location. Stay safe!"
        }

        val notification = NotificationCompat.Builder(this, EXPIRY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(EXPIRY_NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000)
            .setMinUpdateIntervalMillis(15_000)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateLocationInFirestore(location: Location) {
        serviceScope.launch {
            timerRepository.updateLiveLocation(location.latitude, location.longitude)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackingChannel = NotificationChannel(
                CHANNEL_ID,
                "Timer Tracking Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Safety timer is active — tap to check in."
            }

            val alertChannel = NotificationChannel(
                EXPIRY_CHANNEL_ID,
                "Timer Expiry & Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when the safety timer is about to expire or has expired."
                enableVibration(true)
                enableLights(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(trackingChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "safety_timer")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏱ Safety Timer Active")
            .setContentText("Tap to check in and mark yourself safe.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        localCountdownJob?.cancel()
        reminderJob?.cancel()
        serviceJob.cancel()
        stopLocationUpdates()
    }

    companion object {
        private const val TAG = "TimerService"
        private const val CHANNEL_ID = "safety_timer_channel"
        private const val EXPIRY_CHANNEL_ID = "timer_expiry_channel"
        private const val NOTIFICATION_ID = 101
        private const val EXPIRY_NOTIFICATION_ID = 102
        private const val REMINDER_NOTIFICATION_ID = 103

        // SharedPreferences keys for in-app dialog
        const val PREFS_NAME = "raksha_timer_prefs"
        const val KEY_TIMER_EXPIRED_SOS_SENT = "timer_expired_sos_sent"
        const val KEY_TIMER_EXPIRED_TIME = "timer_expired_time"
        const val KEY_TIMER_SOS_FAILED = "timer_sos_failed"

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Call this from the UI after showing the expiry dialog to clear the flag.
         */
        fun clearExpiredTimerFlag(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TIMER_EXPIRED_SOS_SENT)
                .remove(KEY_TIMER_EXPIRED_TIME)
                .remove(KEY_TIMER_SOS_FAILED)
                .apply()
        }
    }
}

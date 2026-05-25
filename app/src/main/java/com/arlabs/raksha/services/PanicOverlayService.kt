package com.arlabs.raksha.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import com.arlabs.raksha.features.medicalid.MedicalIdActivity
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.arlabs.raksha.MainActivity
import com.arlabs.raksha.R
import com.arlabs.raksha.data.local.UserPreferencesDataStore
import com.arlabs.raksha.features.fakecall.CallerEntry
import com.arlabs.raksha.features.fakecall.FakeCallViewModel
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Floating panic overlay that sits on top of all apps.
 * - Tap: Expand/collapse action panel (SOS / Fake Call / Share Location / Live Location)
 * - Long press (2 seconds): Immediately trigger SOS
 * - Draggable to any position
 */
@AndroidEntryPoint
class PanicOverlayService : Service() {

    @Inject
    lateinit var sosManager: SosManager

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var panelView: View? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val handler = Handler(Looper.getMainLooper())
    private var isPanelOpen = false

    companion object {
        private const val TAG = "PanicOverlay"
        private const val CHANNEL_ID = "panic_overlay_channel"
        private const val NOTIFICATION_ID = 500
        private const val LONG_PRESS_DURATION = 2000L
        private const val BUTTON_SIZE = 140 // dp-ish in px
        private const val PANEL_WIDTH = 420

        fun start(context: Context) {
            val intent = Intent(context, PanicOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PanicOverlayService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addOverlayButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        serviceJob.cancel()
    }

    // --- Notification ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Panic Overlay", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Keeps the panic button overlay active & provides Medical ID access"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("🛡️ Panic Button Active")
        .setContentText("Tap for SOS • Medical ID available below")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setOngoing(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            R.drawable.ic_launcher_foreground,
            "🏥 Medical ID",
            PendingIntent.getActivity(
                this, 1,
                Intent(this, MedicalIdActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).build()

    // --- Overlay Button ---
    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlayButton() {
        val params = WindowManager.LayoutParams(
            BUTTON_SIZE, BUTTON_SIZE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 400
        }

        // Create the button
        val button = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        val innerCircle = View(this).apply {
            val size = (BUTTON_SIZE * 0.85).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            background = createCircleDrawable()
            alpha = 0.85f
        }
        val shieldText = TextView(this).apply {
            text = "🛡️"
            textSize = 28f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        button.addView(innerCircle)
        button.addView(shieldText)

        // Touch handling: drag + tap + long press
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isLongPress = false
        var hasMoved = false

        val longPressRunnable = Runnable {
            isLongPress = true
            // Vibrate feedback
            vibrate()
            // Immediately trigger SOS
            innerCircle.setBackgroundColor(Color.RED)
            shieldText.text = "🆘"
            serviceScope.launch {
                Log.d(TAG, "Long press SOS triggered!")
                sosManager.sendSosToAllContacts()
            }
            handler.postDelayed({
                innerCircle.background = createCircleDrawable()
                shieldText.text = "🛡️"
            }, 2000)
        }

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isLongPress = false
                    hasMoved = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_DURATION)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(button, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!isLongPress && !hasMoved) {
                        togglePanel(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }

        overlayView = button
        windowManager.addView(button, params)
    }

    // --- Expandable Panel ---
    private fun togglePanel(anchorX: Int, anchorY: Int) {
        if (isPanelOpen) {
            removePanel()
        } else {
            showPanel(anchorX, anchorY)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    private fun showPanel(anchorX: Int, anchorY: Int) {
        val panelParams = WindowManager.LayoutParams(
            PANEL_WIDTH, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX + BUTTON_SIZE + 10
            y = anchorY
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = createPanelBackground()
        }

        // 1. SOS Button
        val sosBtn = createActionButton("🆘 SOS", Color.parseColor("#E53935"))
        sosBtn.setOnClickListener {
            vibrate()
            serviceScope.launch {
                sosManager.sendSosToAllContacts()
            }
            removePanel()
        }

        // 2. Fake Call Button — instant trigger using saved defaults
        val fakeCallBtn = createActionButton("📞 Fake Call", Color.parseColor("#1E88E5"))
        fakeCallBtn.setOnClickListener {
            vibrate()
            removePanel()
            serviceScope.launch {
                try {
                    val userData = userPreferencesDataStore.userData.first()
                    val callerEntry = CallerEntry.decode(userData.defaultFakeCallCallerEncoded)
                    val delay = userData.defaultFakeCallDelaySeconds
                    FakeCallViewModel.scheduleFakeCallFromContext(
                        this@PanicOverlayService,
                        callerEntry,
                        delay
                    )
                    handler.post {
                        Toast.makeText(
                            this@PanicOverlayService,
                            "📞 Fake call from ${callerEntry.name} in ${delay}s",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to trigger default fake call", e)
                }
            }
        }

        // 3. Share Location (Static) Button
        val shareLocBtn = createActionButton("📍 Share Location", Color.parseColor("#00897B"))
        shareLocBtn.setOnClickListener {
            vibrate()
            removePanel()
            shareCurrentLocation()
        }

        // 4. Live Location Button
        val liveLocBtn = createActionButton("📡 Live Location", Color.parseColor("#F57C00"))
        liveLocBtn.setOnClickListener {
            vibrate()
            removePanel()
            // Uses the same navigate_to pattern as safety_timer deep links
            val intent = Intent(this@PanicOverlayService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "live_location_share")
            }
            startActivity(intent)
        }

        // 5. Medical ID Button
        val medicalIdBtn = createActionButton("\uD83C\uDFE5 Medical ID", Color.parseColor("#7B1FA2"))
        medicalIdBtn.setOnClickListener {
            vibrate()
            removePanel()
            val intent = Intent(this@PanicOverlayService, MedicalIdActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }

        // 6. Close Button
        val closeBtn = createActionButton("✕ Close", Color.parseColor("#616161"))
        closeBtn.setOnClickListener { removePanel() }

        panel.addView(sosBtn)
        panel.addView(createSpacer())
        panel.addView(fakeCallBtn)
        panel.addView(createSpacer())
        panel.addView(shareLocBtn)
        panel.addView(createSpacer())
        panel.addView(liveLocBtn)
        panel.addView(createSpacer())
        panel.addView(medicalIdBtn)
        panel.addView(createSpacer())
        panel.addView(closeBtn)

        panelView = panel
        windowManager.addView(panel, panelParams)
        isPanelOpen = true
    }

    @SuppressLint("MissingPermission")
    private fun shareCurrentLocation() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)

            // Request a FRESH location — lastLocation is often null from a service context
            val locationRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(10_000) // Accept cached if less than 10s old
                .setDurationMillis(5_000) // Wait up to 5s for a fix
                .build()

            fusedClient.getCurrentLocation(locationRequest, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        launchShareIntent(location)
                    } else {
                        // Fallback to lastLocation
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                launchShareIntent(lastLoc)
                            } else {
                                handler.post {
                                    Toast.makeText(this, "Unable to get location. Make sure GPS is on.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    // Fallback to lastLocation on failure too
                    fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                        if (lastLoc != null) {
                            launchShareIntent(lastLoc)
                        } else {
                            handler.post {
                                Toast.makeText(this, "Location access failed. Turn on GPS.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share location", e)
            handler.post {
                Toast.makeText(this, "Could not access location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchShareIntent(location: Location) {
        val mapLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My Current Location — Raksha 🛡️")
            putExtra(
                Intent.EXTRA_TEXT,
                "🆘 I am sharing my current location with you via Raksha:\n$mapLink"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Share Location via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            // Fallback: launch without chooser
            try {
                startActivity(shareIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to launch share intent", e2)
            }
        }
    }

    private fun removePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        isPanelOpen = false
    }

    private fun removeOverlay() {
        removePanel()
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    // --- UI Helpers ---
    private fun createCircleDrawable(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor("#CC1A0A2E"))
            setStroke(3, Color.parseColor("#AAf40c5c"))
        }
    }

    private fun createPanelBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 28f
            setColor(Color.parseColor("#E61A0A2E"))
            setStroke(2, Color.parseColor("#66f40c5c"))
        }
    }

    private fun createActionButton(text: String, bgColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20f
                setColor(bgColor)
            }
        }
    }

    private fun createSpacer(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 12
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(150)
            }
        }
    }
}

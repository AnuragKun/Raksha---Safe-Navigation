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
import com.arlabs.raksha.domain.model.Report
import com.arlabs.raksha.domain.repository.ReportRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Foreground service that tracks user location during navigation
 * and sends distance-based proximity alerts for nearby danger zones.
 */
@AndroidEntryPoint
class ProximityAlertService : Service() {

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var reportRepository: ReportRepository

    @Inject
    lateinit var timerRepository: com.arlabs.raksha.domain.repository.TimerRepository

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var dangerZones: List<Report> = emptyList()
    private var routePoints: List<LatLng> = emptyList()
    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0

    // Track which zones have already alerted at which threshold (meters)
    private val alertedZones = mutableMapOf<String, Int>()

    // Alert thresholds in meters
    private val THRESHOLD_FAR = 500
    private val THRESHOLD_NEAR = 200
    private val THRESHOLD_CLOSE = 100

    private var alertNotificationId = 200

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.lastLocation?.let { location ->
                checkProximity(location)
                // Check if user reached destination (~100m)
                if (destinationLat != 0.0 && destinationLng != 0.0) {
                    val distToDest = distanceBetween(
                        location.latitude, location.longitude,
                        destinationLat, destinationLng
                    )
                    if (distToDest < 100.0) {
                        Log.d(TAG, "User reached destination, stopping service")
                        // Auto-cancel journey timer on arrival
                        serviceScope.launch {
                            try {
                                timerRepository.cancelTimer()
                                val timerIntent = Intent(this@ProximityAlertService, com.arlabs.raksha.services.TimerService::class.java)
                                stopService(timerIntent)
                                Log.d(TAG, "Journey timer cancelled — arrived safely")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to cancel journey timer", e)
                            }
                        }
                        showArrivalNotification()
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Extract route data from intent
        intent?.let {
            destinationLat = it.getDoubleExtra(EXTRA_DEST_LAT, 0.0)
            destinationLng = it.getDoubleExtra(EXTRA_DEST_LNG, 0.0)

            val routeLats = it.getDoubleArrayExtra(EXTRA_ROUTE_LATS)
            val routeLngs = it.getDoubleArrayExtra(EXTRA_ROUTE_LNGS)
            if (routeLats != null && routeLngs != null) {
                routePoints = routeLats.zip(routeLngs).map { (lat, lng) ->
                    LatLng(lat, lng)
                }
            }
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startLocationUpdates()
        loadDangerZones()

        return START_STICKY
    }

    private fun loadDangerZones() {
        // Load reports near the route
        if (routePoints.isNotEmpty()) {
            serviceScope.launch {
                try {
                    // Use the midpoint of the route as the center
                    val midIndex = routePoints.size / 2
                    val center = routePoints[midIndex]
                    reportRepository.getReportsInArea(
                        centerLat = center.latitude,
                        centerLng = center.longitude,
                        radiusDegrees = 0.1 // ~10km radius
                    ).collectLatest { reports ->
                        dangerZones = reports
                        Log.d(TAG, "Loaded ${reports.size} danger zones along route")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load danger zones", e)
                }
            }
        }
    }

    private fun checkProximity(location: Location) {
        dangerZones.forEach { zone ->
            val distance = distanceBetween(
                location.latitude, location.longitude,
                zone.latitude, zone.longitude
            )

            val zoneKey = "${zone.latitude}_${zone.longitude}"
            val lastAlertThreshold = alertedZones[zoneKey] ?: Int.MAX_VALUE

            when {
                distance < THRESHOLD_CLOSE && lastAlertThreshold > THRESHOLD_CLOSE -> {
                    sendProximityAlert(zone, distance.toInt(), "⚠️ VERY CLOSE")
                    alertedZones[zoneKey] = THRESHOLD_CLOSE
                }
                distance < THRESHOLD_NEAR && lastAlertThreshold > THRESHOLD_NEAR -> {
                    sendProximityAlert(zone, distance.toInt(), "🔶 Approaching")
                    alertedZones[zoneKey] = THRESHOLD_NEAR
                }
                distance < THRESHOLD_FAR && lastAlertThreshold > THRESHOLD_FAR -> {
                    sendProximityAlert(zone, distance.toInt(), "📍 Ahead")
                    alertedZones[zoneKey] = THRESHOLD_FAR
                }
            }
        }
    }

    private fun sendProximityAlert(zone: Report, distanceMeters: Int, urgency: String) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, alertNotificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$urgency — ${zone.incidentType}")
            .setContentText("${distanceMeters}m away: ${zone.description.take(100)}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${distanceMeters}m away: ${zone.description}\nSeverity: ${zone.severity}/10"))
            .setPriority(
                if (distanceMeters < THRESHOLD_CLOSE) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(alertNotificationId++, notification)
    }

    private fun showArrivalNotification() {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎯 You've arrived!")
            .setContentText("You've reached your destination safely. Proximity alerts stopped.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ARRIVAL_NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
            .setMinUpdateIntervalMillis(5_000)
            .setMinUpdateDistanceMeters(20f)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackingChannel = NotificationChannel(
                CHANNEL_ID,
                "Proximity Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracking your location for danger zone alerts."
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Danger Zone Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when you approach a reported danger zone."
                enableVibration(true)
                enableLights(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(trackingChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildForegroundNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop button
        val stopIntent = Intent(this, ProximityAlertService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🛡 Route Protection Active")
            .setContentText("Monitoring for danger zones along your route.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceJob.cancel()
    }

    companion object {
        private const val TAG = "ProximityAlertService"
        private const val CHANNEL_ID = "proximity_tracking_channel"
        private const val ALERT_CHANNEL_ID = "danger_zone_alerts_channel"
        private const val NOTIFICATION_ID = 201
        private const val ARRIVAL_NOTIFICATION_ID = 299
        private const val ACTION_STOP = "STOP_PROXIMITY_ALERT"

        const val EXTRA_DEST_LAT = "dest_lat"
        const val EXTRA_DEST_LNG = "dest_lng"
        const val EXTRA_ROUTE_LATS = "route_lats"
        const val EXTRA_ROUTE_LNGS = "route_lngs"

        fun start(
            context: Context,
            destLat: Double,
            destLng: Double,
            routePoints: List<LatLng>
        ) {
            val intent = Intent(context, ProximityAlertService::class.java).apply {
                putExtra(EXTRA_DEST_LAT, destLat)
                putExtra(EXTRA_DEST_LNG, destLng)
                putExtra(EXTRA_ROUTE_LATS, routePoints.map { it.latitude }.toDoubleArray())
                putExtra(EXTRA_ROUTE_LNGS, routePoints.map { it.longitude }.toDoubleArray())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProximityAlertService::class.java))
        }

        /**
         * Calculate distance in meters between two lat/lng points using Haversine formula.
         */
        fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val earthRadius = 6371000.0 // meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return earthRadius * c
        }
    }
}

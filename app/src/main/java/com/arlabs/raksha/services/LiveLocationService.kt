package com.arlabs.raksha.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arlabs.raksha.R
import com.arlabs.raksha.features.livelocation.data.LiveLocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LiveLocationService : Service() {

    @Inject
    lateinit var repository: LiveLocationRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentSessionId: String? = null
    private var expiresAt: Long = 0L

    companion object {
        const val ACTION_START = "ACTION_START_LIVE_LOCATION"
        const val ACTION_STOP = "ACTION_STOP_LIVE_LOCATION"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_EXPIRES_AT = "EXTRA_EXPIRES_AT"
        const val NOTIFICATION_ID = 2005
        const val CHANNEL_ID = "LiveLocationChannel"

        fun start(context: Context, sessionId: String, expiresAt: Long) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_EXPIRES_AT, expiresAt)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                currentSessionId?.let { sessionId ->
                    serviceScope.launch {
                        repository.updateLocation(
                            sessionId = sessionId,
                            lat = location.latitude,
                            lng = location.longitude,
                            speed = location.speed
                        )
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val expiry = intent.getLongExtra(EXTRA_EXPIRES_AT, 0L)
                if (sessionId != null) {
                    currentSessionId = sessionId
                    expiresAt = expiry
                    startForegroundService()
                    startLocationUpdates()
                    checkExpiryContinuously()
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Location Active")
            .setContentText("Sharing your location in real-time...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LiveLocService", "Location permission missing", e)
            stopTracking()
        }
    }

    private fun checkExpiryContinuously() {
        serviceScope.launch {
            while (isActive) {
                if (System.currentTimeMillis() >= expiresAt) {
                    stopTracking()
                    break
                }
                delay(10000) // check every 10s
            }
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        currentSessionId?.let {
            serviceScope.launch {
                repository.endSession(it)
                stopForeground(true)
                stopSelf()
            }
        } ?: run {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }
}

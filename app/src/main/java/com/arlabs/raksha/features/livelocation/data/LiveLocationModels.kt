package com.arlabs.raksha.features.livelocation.data

data class LiveLocationSession(
    val sessionId: String = "",
    val hostUserId: String = "",
    val hostName: String = "",
    val isActive: Boolean = true,
    val expiresAt: Long = 0L,
    val startTime: Long = 0L
)

data class LocationPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = 0L,
    val speed: Float = 0f
)

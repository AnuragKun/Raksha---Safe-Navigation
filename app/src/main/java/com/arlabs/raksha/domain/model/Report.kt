package com.arlabs.raksha.domain.model

data class Report(
    val id: String = "",
    val reporterUid: String = "",
    val incidentType: String = "",
    val category: String = "",
    val severity: Float = 0f,
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val status: String = ""
)

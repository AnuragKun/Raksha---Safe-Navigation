package com.arlabs.raksha.domain.repository

import com.arlabs.raksha.domain.model.Report
import com.arlabs.raksha.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    suspend fun submitReport(
        type: String,
        category: String,
        description: String,
        severity: Float,
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ): Result<Unit>

    /**
     * Returns a real-time flow of reports within a bounding box defined by
     * the center point and radius in degrees (~1 degree ≈ 111km).
     */
    fun getReportsInArea(
        centerLat: Double,
        centerLng: Double,
        radiusDegrees: Double
    ): Flow<List<Report>>
}

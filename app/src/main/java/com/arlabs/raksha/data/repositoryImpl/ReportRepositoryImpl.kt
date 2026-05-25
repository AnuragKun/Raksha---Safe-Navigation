package com.arlabs.raksha.data.repositoryImpl

import com.arlabs.raksha.domain.model.Report
import com.arlabs.raksha.domain.repository.ReportRepository
import com.arlabs.raksha.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ReportRepository {

    override suspend fun submitReport(
        type: String,
        category: String,
        description: String,
        severity: Float,
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ): Result<Unit> {
        return try {
            val report = hashMapOf(
                "reporterUid" to (auth.currentUser?.uid ?: "anonymous"),
                "incidentType" to type,
                "category" to category,
                "description" to description,
                "severity" to severity,
                "location" to GeoPoint(latitude, longitude),
                "timestamp" to timestamp,
                "status" to "PENDING"
            )

            firestore.collection("reports").add(report).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Failure(e.message ?: "Unknown Error")
        }
    }

    /**
     * Queries reports within a bounding box. Firestore doesn't support native
     * geo-queries, so we use a latitude-range filter and then filter longitude
     * in-memory. The snapshot listener provides real-time updates.
     */
    override fun getReportsInArea(
        centerLat: Double,
        centerLng: Double,
        radiusDegrees: Double
    ): Flow<List<Report>> = callbackFlow {
        // Firestore can only range-query on one field, so we filter latitude
        // in the query and longitude in-memory
        val minLat = centerLat - radiusDegrees
        val maxLat = centerLat + radiusDegrees
        val minLng = centerLng - radiusDegrees
        val maxLng = centerLng + radiusDegrees

        val listener = firestore.collection("reports")
            .whereGreaterThanOrEqualTo("location", GeoPoint(minLat, minLng))
            .whereLessThanOrEqualTo("location", GeoPoint(maxLat, maxLng))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val reports = snapshot?.documents?.mapNotNull { doc ->
                    val geoPoint = doc.getGeoPoint("location") ?: return@mapNotNull null
                    // Additional longitude filter in-memory
                    if (geoPoint.longitude < minLng || geoPoint.longitude > maxLng) {
                        return@mapNotNull null
                    }
                    Report(
                        id = doc.id,
                        reporterUid = doc.getString("reporterUid") ?: "",
                        incidentType = doc.getString("incidentType") ?: "",
                        category = doc.getString("category") ?: "",
                        severity = doc.getDouble("severity")?.toFloat() ?: 0f,
                        description = doc.getString("description") ?: "",
                        latitude = geoPoint.latitude,
                        longitude = geoPoint.longitude,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        status = doc.getString("status") ?: ""
                    )
                } ?: emptyList()

                trySend(reports)
            }

        awaitClose { listener.remove() }
    }
}

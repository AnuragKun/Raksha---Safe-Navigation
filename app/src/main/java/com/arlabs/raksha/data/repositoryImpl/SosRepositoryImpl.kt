package com.arlabs.raksha.data.repositoryImpl

import com.arlabs.raksha.domain.repository.SosRepository
import com.arlabs.raksha.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SosRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SosRepository {

    override suspend fun triggerSos(
        latitude: Double?,
        longitude: Double?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.Failure("User not authenticated")

        return try {
            val sosData = hashMapOf(
                "userId" to uid,
                "timestamp" to System.currentTimeMillis(),
                "status" to "ACTIVE",
                "lastLocation" to if (latitude != null && longitude != null) {
                    GeoPoint(latitude, longitude)
                } else {
                    null
                }
            )

            firestore.collection("sos_alerts").add(sosData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to trigger SOS")
        }
    }
}

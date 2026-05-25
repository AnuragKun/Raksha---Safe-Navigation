package com.arlabs.raksha.features.livelocation.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveLocationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val SESSIONS_COLLECTION = "live_location_sessions"
    private val POINTS_COLLECTION = "locations"

    suspend fun createSession(durationMinutes: Int): String {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + (durationMinutes * 60 * 1000L)

        // Try getting user name from Firestore profile to be robust, or fall back to auth display name
        var hostName = user.displayName ?: "A Raksha User"
        try {
            val profileDoc = firestore.collection("users").document(user.uid).get().await()
            if (profileDoc.exists()) {
                hostName = profileDoc.getString("name") ?: hostName
            }
        } catch (e: Exception) {
            Log.e("LiveLocRepo", "Failed to fetch user profile", e)
        }

        val session = LiveLocationSession(
            sessionId = sessionId,
            hostUserId = user.uid,
            hostName = hostName,
            isActive = true,
            expiresAt = expiresAt,
            startTime = now
        )

        firestore.collection(SESSIONS_COLLECTION).document(sessionId).set(session).await()
        return sessionId
    }

    suspend fun endSession(sessionId: String) {
        try {
            firestore.collection(SESSIONS_COLLECTION).document(sessionId)
                .update("isActive", false).await()
        } catch (e: Exception) {
            Log.e("LiveLocRepo", "Failed to end session", e)
        }
    }

    suspend fun updateLocation(sessionId: String, lat: Double, lng: Double, speed: Float) {
        val point = LocationPoint(
            lat = lat,
            lng = lng,
            timestamp = System.currentTimeMillis(),
            speed = speed
        )
        
        try {
             // Add to subcollection for a polyline
            firestore.collection(SESSIONS_COLLECTION).document(sessionId)
                .collection(POINTS_COLLECTION).add(point).await()
        } catch(e: Exception) {
            Log.e("LiveLocRepo", "Failed to update location point", e)
        }
    }

    // For the UI Viewer Screen (Recipient)
    fun observeSession(sessionId: String): Flow<LiveLocationSession?> = callbackFlow {
        val listener = firestore.collection(SESSIONS_COLLECTION).document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val session = snapshot?.toObject(LiveLocationSession::class.java)
                trySend(session)
            }
        awaitClose { listener.remove() }
    }

    fun observeLocationPoints(sessionId: String): Flow<List<LocationPoint>> = callbackFlow {
        val listener = firestore.collection(SESSIONS_COLLECTION).document(sessionId)
            .collection(POINTS_COLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val points = snapshot?.documents?.mapNotNull { it.toObject(LocationPoint::class.java) } ?: emptyList()
                trySend(points)
            }
        awaitClose { listener.remove() }
    }
}

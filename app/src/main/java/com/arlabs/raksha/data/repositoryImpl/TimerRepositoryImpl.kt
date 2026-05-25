package com.arlabs.raksha.data.repositoryImpl

import com.arlabs.raksha.domain.repository.TimerRepository
import com.arlabs.raksha.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class TimerRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : TimerRepository {

    private val db get() = firestore
    private val currentUser get() = auth.currentUser

    private fun getTimerDocRef() = currentUser?.uid?.let { uid ->
        db.collection("users").document(uid).collection("active_timers").document("current")
    }

    override suspend fun startTimer(durationMinutes: Int): Result<Unit> {
        val uid = currentUser?.uid ?: return Result.Failure("User not authenticated")
        val docRef = db.collection("users").document(uid).collection("active_timers").document("current")

        val startTime = System.currentTimeMillis()
        val expiryTime = startTime + (durationMinutes * 60 * 1000)

        val timerData = hashMapOf(
            "startTime" to startTime,
            "expiryTime" to expiryTime,
            "isCanceled" to false,
            "lastLocation" to null, // Initialized as null, updated by service
            "durationMinutes" to durationMinutes
        )

        return try {
            docRef.set(timerData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to start timer")
        }
    }

    override suspend fun cancelTimer(): Result<Unit> {
        val docRef = getTimerDocRef() ?: return Result.Failure("User not authenticated")

        return try {
            docRef.update("isCanceled", true).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to cancel timer")
        }
    }

    override suspend fun updateLiveLocation(lat: Double, lng: Double): Result<Unit> {
        val docRef = getTimerDocRef() ?: return Result.Failure("User not authenticated")

        return try {
            // Only update if the timer is still active (isCanceled == false)
            val snapshot = docRef.get().await()
            val isCanceled = snapshot.getBoolean("isCanceled") ?: true
            
            if (!isCanceled) {
                docRef.update("lastLocation", com.google.firebase.firestore.GeoPoint(lat, lng)).await()
                Result.Success(Unit)
            } else {
                Result.Failure("Timer is already canceled")
            }
        } catch (e: Exception) {
             Result.Failure(e.message ?: "Failed to update location")
        }
    }

    override fun isTimerActive(): Flow<Boolean> = callbackFlow {
        val docRef = getTimerDocRef()
        if (docRef == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(false)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val isCanceled = snapshot.getBoolean("isCanceled") ?: true
                val expiryTime = snapshot.getLong("expiryTime") ?: 0L
                
                // Active if it's not canceled and the time hasn't expired yet
                val isActive = !isCanceled && System.currentTimeMillis() < expiryTime
                trySend(isActive)
            } else {
                trySend(false)
            }
        }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun getExpiryTime(): Flow<Long> = callbackFlow {
        val docRef = getTimerDocRef()
        if (docRef == null) {
            trySend(0L)
            close()
            return@callbackFlow
        }

        val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(0L)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val isCanceled = snapshot.getBoolean("isCanceled") ?: true
                val expiryTime = snapshot.getLong("expiryTime") ?: 0L
                if (!isCanceled && System.currentTimeMillis() < expiryTime) {
                    trySend(expiryTime)
                } else {
                    trySend(0L)
                }
            } else {
                trySend(0L)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }
    override suspend fun getTimerDataOnce(): Triple<Long, Int, Boolean> {
        val docRef = getTimerDocRef() ?: return Triple(0L, 0, true)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val expiryTime = snapshot.getLong("expiryTime") ?: 0L
                val durationMinutes = (snapshot.getLong("durationMinutes") ?: 0L).toInt()
                val isCanceled = snapshot.getBoolean("isCanceled") ?: true
                Triple(expiryTime, durationMinutes, isCanceled)
            } else {
                Triple(0L, 0, true)
            }
        } catch (e: Exception) {
            Triple(0L, 0, true)
        }
    }
}

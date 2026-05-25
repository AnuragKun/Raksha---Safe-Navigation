package com.arlabs.raksha.domain.repository

import com.arlabs.raksha.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface TimerRepository {
    suspend fun startTimer(durationMinutes: Int): Result<Unit>
    suspend fun cancelTimer(): Result<Unit>
    suspend fun updateLiveLocation(lat: Double, lng: Double): Result<Unit>
    fun isTimerActive(): Flow<Boolean>

    /**
     * Returns a flow of the timer's expiry time in milliseconds.
     * Emits 0L if no active timer exists.
     */
    fun getExpiryTime(): Flow<Long>

    /**
     * One-shot read: returns (expiryTime, durationMinutes, isCanceled) from Firestore.
     * Unlike getExpiryTime(), does NOT filter by currentTime < expiryTime.
     */
    suspend fun getTimerDataOnce(): Triple<Long, Int, Boolean>
}

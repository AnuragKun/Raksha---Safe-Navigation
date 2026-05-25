package com.arlabs.raksha.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arlabs.raksha.domain.repository.SosRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that retries a Firestore SOS write when network is available.
 * Enqueued by SosManager when the initial write fails due to no connectivity.
 */
@HiltWorker
class SosWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sosRepository: SosRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val TAG = "SosWorker"
    }

    override suspend fun doWork(): Result {
        val lat = inputData.getDouble(KEY_LATITUDE, Double.NaN)
        val lng = inputData.getDouble(KEY_LONGITUDE, Double.NaN)

        val latitude = if (lat.isNaN()) null else lat
        val longitude = if (lng.isNaN()) null else lng

        Log.d(TAG, "Retrying offline SOS write (lat=$latitude, lng=$longitude)")

        return when (val result = sosRepository.triggerSos(latitude, longitude)) {
            is com.arlabs.raksha.domain.util.Result.Success -> {
                Log.d(TAG, "Offline SOS successfully written to Firestore")
                Result.success()
            }
            is com.arlabs.raksha.domain.util.Result.Failure -> {
                Log.e(TAG, "SOS retry failed: ${result.message}")
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
            else -> Result.retry()
        }
    }
}

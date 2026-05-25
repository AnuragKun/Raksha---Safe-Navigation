package com.arlabs.raksha.services

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.arlabs.raksha.domain.repository.EmergencyContactRepository
import com.arlabs.raksha.domain.repository.SosRepository
import com.arlabs.raksha.domain.repository.UserPreferencesRepository
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SosManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sosRepository: SosRepository,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    companion object {
        private const val TAG = "SosManager"
    }

    /**
     * Sends SOS alerts:
     * 1. Gets the user's current GPS location
     * 2. Fetches all emergency contacts from Firestore
     * 3. Composes an SMS with the user's SOS template + Google Maps link
     * 4. Sends SMS to each contact via Android SmsManager
     * 5. Writes an SOS alert document to Firestore (triggers Cloud Function fallback via Twilio)
     *    — If Firestore write fails (offline), enqueues a WorkManager job for retry when connected.
     *
     * @return Pair<Boolean, String> — (success, message)
     */
    @SuppressLint("MissingPermission")
    suspend fun sendSosToAllContacts(triggerCloudSos: Boolean = true): Pair<Boolean, String> {
        return try {
            // 1. Get emergency contacts
            val contacts = emergencyContactRepository.getContacts().first()
            if (contacts.isEmpty()) {
                return Pair(false, "No emergency contacts found. Add contacts in Profile.")
            }

            // 2. Get current location
            var latitude: Double? = null
            var longitude: Double? = null
            try {
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get location for SOS: ${e.message}")
            }

            // 3. Build message
            val userData = userPreferencesRepository.userData.first()
            val mapLink = if (latitude != null && longitude != null) {
                "\nhttps://maps.google.com/?q=$latitude,$longitude"
            } else {
                "\n(Location unavailable)"
            }
            
            val senderName = userData.name.takeIf { it.isNotBlank() } ?: "A friend"
            var baseMessage = userData.sosMessageTemplate
            
            // If it's the default message or doesn't mention their name, let's make it clear
            if (baseMessage == "I'm using Raksha and my timer just expired. Please check my last location.") {
                baseMessage = "I'm $senderName, using the Raksha app. I need help or my safety timer just expired! Please check my location."
            } else if (!baseMessage.contains(senderName, ignoreCase = true)) {
                // If they wrote a custom message but forgot their name
                baseMessage = "[$senderName using Raksha] - $baseMessage"
            }
            
            val message = "$baseMessage$mapLink"

            // 4. Send SMS to each contact via Android SmsManager
            var smsSentCount = 0
            try {
                @Suppress("DEPRECATION")
                val smsManager = SmsManager.getDefault()
                contacts.forEach { contact ->
                    try {
                        val parts = smsManager.divideMessage(message)
                        if (parts.size > 1) {
                            smsManager.sendMultipartTextMessage(
                                contact.phoneNumber, null, parts, null, null
                            )
                        } else {
                            smsManager.sendTextMessage(
                                contact.phoneNumber, null, message, null, null
                            )
                        }
                        smsSentCount++
                        Log.d(TAG, "SMS sent to ${contact.name} (${contact.phoneNumber})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send SMS to ${contact.name}: ${e.message}")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SMS permission denied: ${e.message}")
                // Continue to Firestore fallback even if device SMS fails
            }

            // 5. Write to Firestore sos_alerts ONLY if requested (prevents double Twilio SMS for Timers)
            if (triggerCloudSos) {
                val firestoreResult = sosRepository.triggerSos(latitude, longitude)
                if (firestoreResult is com.arlabs.raksha.domain.util.Result.Failure) {
                    // Offline — enqueue WorkManager for retry when connected
                    Log.w(TAG, "Firestore SOS write failed (likely offline), queuing WorkManager retry")
                    enqueueOfflineSos(latitude, longitude)
                }
            }

            val resultMsg = if (smsSentCount > 0) {
                "SOS sent to $smsSentCount contact(s)!"
            } else {
                "SOS alert saved. Twilio will send SMS via cloud."
            }
            Pair(true, resultMsg)

        } catch (e: Exception) {
            Log.e(TAG, "SOS failed: ${e.message}", e)
            Pair(false, "SOS failed: ${e.message}")
        }
    }

    /**
     * Enqueue a WorkManager job to retry the Firestore SOS write when network is available.
     */
    private fun enqueueOfflineSos(latitude: Double?, longitude: Double?) {
        val inputData = Data.Builder().apply {
            latitude?.let { putDouble(SosWorker.KEY_LATITUDE, it) }
            longitude?.let { putDouble(SosWorker.KEY_LONGITUDE, it) }
        }.build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SosWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "Offline SOS enqueued — will send when network is available")
    }
}


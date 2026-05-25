package com.arlabs.raksha.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service that:
 * 1. Handles new/refreshed FCM tokens and saves them to Firestore
 * 2. Receives emergency alert push notifications
 */
class RakshaMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "RakshaMessaging"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        // Handle data payload (for emergency alerts)
        message.data.let { data ->
            val alertType = data["type"]
            if (alertType == "manual_sos" || alertType == "timer_expired") {
                Log.d(TAG, "Emergency alert received: $alertType")
                // The notification payload is handled automatically by the system
                // when the app is in the background. For foreground, we could
                // create a local notification channel here.
            }
        }
    }

    private fun saveTokenToFirestore(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved to Firestore for user $uid")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token", e)
                // If document doesn't exist yet, create with set+merge
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            }
    }
}

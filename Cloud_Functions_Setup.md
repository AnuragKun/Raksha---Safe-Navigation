# Firebase Cloud Functions Setup Guide: Timed Safety Check

This guide will walk you through setting up the Firebase Cloud Functions and Cloud Tasks required for the Timed Safety Check feature. 

## Prerequisites
1. Node.js installed on your machine.
2. Firebase CLI installed (`npm install -g firebase-tools`).
3. You must be on the **Blaze (Pay as you go)** pricing plan in Firebase, as Cloud Tasks and Node.js 16+ require it. 

---

## Step 1: Initialize Firebase Functions

Open a terminal in your project's root folder (`c:\Users\Rana\AndroidStudioProjects\Raksha`) or any preferred directory, and run:

```bash
firebase login
firebase init functions
```

1. Select your Firebase project for Raksha.
2. Choose **TypeScript** or **JavaScript** (the code below is for JavaScript/Node.js).
3. Choose **Yes** to use ESLint.
4. Choose **Yes** to install dependencies via npm.

This creates a `functions` folder with `index.js` inside it.

---

## Step 2: Install Required Packages

Navigate into the `functions` directory and install the necessary specific packages:

```bash
cd functions
npm install firebase-admin firebase-functions @google-cloud/tasks
```

---

## Step 3: Enable Google Cloud Tasks

Since the timer needs to trigger exactly at the expiry time even if the app drops connection, we use Cloud Tasks.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Select your Firebase/Google Cloud Project.
3. Search for **Cloud Tasks API** in the top search bar and click **Enable**.
4. Open your terminal (ensure you have `gcloud` CLI installed, or use the Cloud Shell in the console) and create the queue:
   ```bash
   gcloud tasks queues create safety-timer-queue --location=us-central1
   ```
   *(Change the location to match your Firebase project's default region if it's not us-central1).*

---

## Step 4: Write the Cloud Functions

Open `functions/index.js` and paste the following code. It defines two functions:
1. `onTimerStart`: Triggered when a timer document is created. It schedules a task.
2. `sendEmergencyAlert`: The endpoint hit by the Cloud Task when the timer expires.

```javascript
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { CloudTasksClient } = require("@google-cloud/tasks");

admin.initializeApp();
const db = admin.firestore();

// IMPORTANT: Update these with your Project ID and Queue Region
const PROJECT_ID = process.env.GCLOUD_PROJECT || "YOUR-PROJECT-ID"; // e.g. "raksha-app-123"
const LOCATION = "us-central1"; // Must match your queue location
const QUEUE = "safety-timer-queue";

const tasksClient = new CloudTasksClient();

/**
 * 1. Triggered when a new timer is created by the Android App.
 */
exports.onTimerStart = functions.firestore
    .document("users/{userId}/active_timers/current")
    .onCreate(async (snap, context) => {
        const timerData = snap.data();
        const userId = context.params.userId;
        const expiryTimeMs = timerData.expiryTime; // Unix timestamp in MS

        // Create the task expiration payload
        const payload = {
            userId: userId,
            timerId: "current"
        };

        // Construct the webhook URL for the second function
        // Note: You can find your exact URL in the Firebase Console after deploying sendEmergencyAlert
        const url = `https://${LOCATION}-${PROJECT_ID}.cloudfunctions.net/sendEmergencyAlert`;

        const parent = tasksClient.queuePath(PROJECT_ID, LOCATION, QUEUE);

        // Schedule the Cloud Task
        const task = {
            httpRequest: {
                httpMethod: "POST",
                url: url,
                body: Buffer.from(JSON.stringify(payload)).toString("base64"),
                headers: {
                    "Content-Type": "application/json",
                },
            },
            // Convert expiry time (ms) to seconds for Cloud Tasks
            scheduleTime: {
                seconds: Math.floor(expiryTimeMs / 1000)
            }
        };

        try {
            const [response] = await tasksClient.createTask({ parent, task });
            console.log(`Successfully scheduled task ${response.name} for user ${userId}`);
            
            // Optionally, save the task name to Firestore so you can cancel the task
            // if the user clicks "I'm Safe" early.
            await snap.ref.update({ cloudTaskName: response.name });
            
        } catch (error) {
            console.error(`Failed to schedule task for user ${userId}`, error);
        }
    });

/**
 * 2. Triggered by Google Cloud Tasks when the timer expires.
 */
exports.sendEmergencyAlert = functions.https.onRequest(async (req, res) => {
    const { userId, timerId } = req.body;

    if (!userId) {
        return res.status(400).send("User ID missing.");
    }

    try {
        const timerRef = db.doc(`users/${userId}/active_timers/${timerId}`);
        const timerSnap = await timerRef.get();

        if (!timerSnap.exists) {
            console.log(`Timer ${timerId} for ${userId} does not exist. Skipping.`);
            return res.status(200).send("No active timer found.");
        }

        const timerData = timerSnap.data();

        // FAIL-SAFE CHECK: If it was canceled, do not send the alert
        if (timerData.isCanceled === true) {
            console.log(`Timer for ${userId} was marked as Safe. Alert Canceled.`);
            return res.status(200).send("User is safe.");
        }

        // TRIGGER EMERGENCY SOS!
        // User did not cancel the timer. Fetch emergency contacts and notify.
        const lastLocation = timerData.lastLocation; 
        console.error(`🚨 SOS INITIATED FOR USER: ${userId} 🚨`);
        console.error(`Last known location: ${lastLocation ? `${lastLocation.latitude}, ${lastLocation.longitude}` : "Unknown"}`);
        
        // ----------------------------------------------------
        // TODO: ADD YOUR SMS/NOTIFICATION LOGIC HERE
        // 1. Fetch `db.collection('users').doc(userId).collection('emergency_contacts')`
        // 2. Iterate through contacts
        // 3. Send SMS via Twilio or Firebase Cloud Messaging (FCM)
        // ----------------------------------------------------

        // Once alert is sent, mark the timer as completed or delete it
        await timerRef.update({ 
            alertTriggered: true, 
            isCanceled: true // Prevent duplicate triggers
        });

        res.status(200).send("Emergency alert dispatched successfully.");

    } catch (error) {
        console.error("Error dispatching emergency alert:", error);
        res.status(500).send("Internal Server Error");
    }
});
```

> **Note on Task Cancellation:** 
> If a user taps "I'm Safe" before the timer runs out, the timer document gets `isCanceled: true`. When `sendEmergencyAlert` ultimately runs, it checks this flag and exits peacefully without triggering the SMS.

---

## Step 5: Setup Firestore Security Rules

Ensure your Android app has permission to write the timer document. Go to **Firebase Console > Firestore Database > Rules** and add:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/active_timers/{timerId} {
      // Only the authenticated user can read/write their own timers
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // ... Any other rules you have
  }
}
```

---

## Step 6: Deploy Everything

Run the following command from the `functions` directory:

```bash
firebase deploy --only functions,firestore:rules
```

**Troubleshooting:**
- If the deployment fails complaining about permissions, ensure the Firebase service account has the **Cloud Tasks Enqueuer** role in Google Cloud IAM.
- Replace `"YOUR-PROJECT-ID"` in `index.js` with your actual project ID (found in Firebase Console > Project Settings).

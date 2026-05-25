/**
 * Raksha - Emergency SOS & Safety Timer
 * Updated for Firebase Gen 2 (2026)
 */

const { setGlobalOptions } = require("firebase-functions");
// Restrict instances to manage costs during development
setGlobalOptions({ maxInstances: 10 });

const { onRequest } = require("firebase-functions/v2/https");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const { CloudTasksClient } = require("@google-cloud/tasks");
const twilio = require("twilio");

admin.initializeApp();
console.log("Raksha Cloud Functions initialized - v2.1");
const db = admin.firestore();

// CONFIGURATION
const PROJECT_ID = process.env.GCLOUD_PROJECT || "raksha-97818";
const LOCATION = "asia-south1";
const QUEUE = "safety-timer-queue";

// Twilio credentials loaded from .env file
const twilioClient = twilio(
    process.env.TWILIO_ACCOUNT_SID,
    process.env.TWILIO_AUTH_TOKEN
);
const TWILIO_PHONE = process.env.TWILIO_PHONE_NUMBER;

const tasksClient = new CloudTasksClient();

/**
 * 1. Triggered when a timer document is created or updated.
 * Schedules a Cloud Task to fire at the timer's expiry time.
 */
exports.onTimerStart = onDocumentWritten(
    { document: "users/{userId}/active_timers/current", region: "asia-south1" },
    async (event) => {
        const afterData = event.data?.after?.data();

        // Skip if document was deleted or timer was marked as safe
        if (!afterData || afterData.isCanceled === true) {
            return;
        }

        const userId = event.params.userId;
        const expiryTimeMs = afterData.expiryTime;

        const payload = {
            userId: userId,
            timerId: "current"
        };

        // UPDATED: Using the exact URL from your successful deployment
        const url = `https://asia-south1-raksha-97818.cloudfunctions.net/sendEmergencyAlert`;

        const parent = tasksClient.queuePath(PROJECT_ID, LOCATION, QUEUE);

        const task = {
            httpRequest: {
                httpMethod: "POST",
                url: url,
                body: Buffer.from(JSON.stringify(payload)).toString("base64"),
                headers: {
                    "Content-Type": "application/json",
                },
            },
            scheduleTime: {
                seconds: Math.floor(expiryTimeMs / 1000)
            }
        };

        try {
            // Cancel any previously scheduled task for this specific timer
            if (afterData.cloudTaskName) {
                try {
                    await tasksClient.deleteTask({ name: afterData.cloudTaskName });
                    console.log(`Deleted previous task: ${afterData.cloudTaskName}`);
                } catch (deleteErr) {
                    console.log(`Old task already ran or deleted: ${deleteErr.message}`);
                }
            }

            const [response] = await tasksClient.createTask({ parent, task });
            console.log(`Successfully scheduled task ${response.name} for user ${userId}`);

            // Store the task name so we can cancel it if the user clicks "I'm Safe"
            await event.data.after.ref.update({ cloudTaskName: response.name });
        } catch (error) {
            console.error(`Failed to schedule task for user ${userId}`, error);
        }
    }
);

/**
 * 2. Triggered by Google Cloud Tasks when the timer expires.
 */
exports.sendEmergencyAlert = onRequest({ region: "asia-south1" }, async (req, res) => {
    const { userId, timerId } = req.body;

    if (!userId) return res.status(400).send("User ID missing.");

    try {
        const timerRef = db.doc(`users/${userId}/active_timers/${timerId}`);
        const timerSnap = await timerRef.get();

        if (!timerSnap.exists) return res.status(200).send("No active timer found.");

        const timerData = timerSnap.data();

        // FAIL-SAFE: If user canceled before the task ran
        if (timerData.isCanceled === true) {
            console.log(`User ${userId} is safe. Alert canceled.`);
            return res.status(200).send("User is safe.");
        }

        const lastLocation = timerData.lastLocation;
        console.error(`🚨 SOS INITIATED: ${userId} 🚨`);

        await sendNotificationsToContacts(userId, lastLocation, "timer_expired");

        await timerRef.update({
            alertTriggered: true,
            isCanceled: true
        });

        res.status(200).send("Alert dispatched.");
    } catch (error) {
        console.error("Error dispatching alert:", error);
        res.status(500).send("Internal Error");
    }
});

/**
 * 3. Triggered by manual SOS button in the app.
 */
exports.onSosCreated = onDocumentWritten(
    { document: "sos_alerts/{alertId}", region: "asia-south1" },
    async (event) => {
        const afterData = event.data?.after?.data();
        if (!afterData || afterData.alertDispatched) return;

        const userId = afterData.userId;
        const lastLocation = afterData.lastLocation;

        try {
            await sendNotificationsToContacts(userId, lastLocation, "manual_sos");
            await event.data.after.ref.update({
                alertDispatched: true,
                dispatchedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        } catch (error) {
            console.error(`Failed to dispatch manual SOS:`, error);
        }
    }
);

/**
 * Helper: Sends FCM Push and Twilio SMS
 */
async function sendNotificationsToContacts(userId, lastLocation, alertType) {
    const userDoc = await db.doc(`users/${userId}`).get();
    const userName = userDoc.exists ? (userDoc.data().displayName || userDoc.data().name || "A Raksha user") : "A Raksha user";

    const contactsSnap = await db.collection(`users/${userId}/emergency_contacts`).get();
    if (contactsSnap.empty) return;

    // FIXED: Added missing '$' for the latitude variable
    const locationString = lastLocation
        ? `https://www.google.com/maps?q=${lastLocation.latitude},${lastLocation.longitude}`
        : "Location unavailable";

    const alertTitle = alertType === "manual_sos" ? "🚨 EMERGENCY SOS" : "⚠️ Safety Timer Expired";
    const alertBody = alertType === "manual_sos"
        ? `[Raksha Alert] ${userName} triggered an EMERGENCY SOS! Please check their location immediately: ${locationString}`
        : `[Raksha Alert] ${userName}'s safety timer just expired! Please check their last known location: ${locationString}`;

    const notifications = [];

    for (const contactDoc of contactsSnap.docs) {
        const contact = contactDoc.data();

        // 1. Send Push Notification (if they have the app)
        if (contact.fcmToken) {
            notifications.push(
                admin.messaging().send({
                    token: contact.fcmToken,
                    notification: { title: alertTitle, body: alertBody },
                    data: { type: alertType, userId: userId, locationUrl: locationString },
                    android: { priority: "high", notification: { channelId: "emergency_alerts" } }
                }).catch(e => console.error("FCM Failed:", e.message))
            );
        }

        // 2. Send Twilio SMS (The main fallback)
        if (contact.phoneNumber && TWILIO_PHONE) {
            notifications.push(
                twilioClient.messages.create({
                    body: alertBody,
                    from: TWILIO_PHONE,
                    to: contact.phoneNumber
                }).then(msg => console.log(`✅ SMS sent to ${contact.name}`))
                  .catch(e => console.error(`❌ SMS Failed for ${contact.name}:`, e.message))
            );
        }
    }

    if (notifications.length > 0) {
        await Promise.all(notifications);
    }
}
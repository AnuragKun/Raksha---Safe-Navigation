<h1 align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher.webp" alt="Raksha Logo" width="120" onerror="this.src='https://via.placeholder.com/120?text=Raksha'">
  <br>
  Raksha — Safe Navigation
</h1>

<p align="center">
  <strong>A comprehensive, real-time personal safety Android application built with modern Android development standards.</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#tech-stack">Tech Stack</a> •
  <a href="#architecture--services">Architecture</a> •
  <a href="#getting-started">Getting Started</a> •
  <a href="#firebase--twilio-setup">Backend Setup</a>
</p>

---

## 🛡️ About Raksha

Raksha (meaning "Protection" in Sanskrit) is an advanced Android safety application designed to provide users with immediate assistance in emergency situations. It utilizes real-time location tracking, background services, Twilio SMS integration, and Firebase Cloud Functions to ensure reliable, instantaneous alerts even when the app is minimized or closed.

## ✨ Features

- 🚨 **Panic Button Overlay**: A persistent, floating widget (via `PanicOverlayService`) that allows users to trigger an SOS from any screen without opening the app.
- ⏱️ **Timed Safety Check**: Set a timer before entering a risky situation. If you don't mark yourself "Safe" before the timer expires, an SOS is automatically triggered via Firebase Cloud Tasks (even if your phone dies).
- 📍 **Live Location Sharing**: Real-time continuous location broadcasting to selected emergency contacts using foreground services (`LiveLocationService`).
- 🗺️ **Safe Zones & Proximity Alerts**: Geofencing capabilities that notify loved ones when you arrive or leave designated safe zones (`ProximityAlertService`).
- 📞 **Fake Call**: Generates a realistic simulated incoming call to help users discreetly escape uncomfortable situations.
- 🏥 **Medical ID**: Stores critical health information (blood type, allergies, medications) accessible instantly during emergencies.
- 📡 **Offline SOS Failsafe**: Utilizes Android's `WorkManager` (`SosWorker`) to queue SOS requests if the network is unavailable, dispatching them immediately upon reconnection.

## 🛠 Tech Stack

### Android (Frontend & Core)
- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: [Dagger Hilt](https://dagger.dev/hilt/) (via KSP)
- **Navigation**: Jetpack Navigation Compose
- **Maps & Location**: Google Maps SDK, Google Places API, Fused Location Provider
- **Background Processing**: Foreground Services, WorkManager
- **Widgets**: Jetpack Glance (App Widgets)
- **Local Storage**: DataStore Preferences

### Backend & Cloud
- **Authentication**: Firebase Auth (Google Sign-In integration)
- **Database**: Firebase Firestore (Real-time sync)
- **Push Notifications**: Firebase Cloud Messaging (FCM)
- **Serverless Compute**: Firebase Cloud Functions (Node.js)
- **Task Scheduling**: Google Cloud Tasks
- **SMS Gateway**: [Twilio API](https://www.twilio.com/)

---

## 🏗 Architecture & Services

The application relies heavily on Android Background Services to ensure continuous safety monitoring:

1. **`PanicOverlayService`**: Draws a system-alert window over other apps for instant SOS access.
2. **`LiveLocationService`**: A foreground service tracking high-accuracy GPS coordinates and pushing them to Firestore.
3. **`TimerService`**: Manages the local UI state for the Safety Timer while Firebase Cloud Tasks handles the backend failsafe.
4. **`ProximityAlertService`**: Computes distance to Safe Zone perimeters and triggers notifications.
5. **`SosManager` / `SosWorker`**: Handles the robust delivery of SOS payloads to Twilio via Cloud Functions.

*(Note: Data Flow Diagrams, Entity-Relationship Diagrams, and Use Case Diagrams are available in the root folder as `.html` files).*

---

## 🚀 Getting Started

### Prerequisites
- Android Studio (Jellyfish or newer recommended)
- Java 11 / 17
- A Firebase Project
- A Twilio Account
- Google Maps API Key

### 1. Clone & Open
```bash
git clone https://github.com/AnuragKun/Raksha---Safe-Navigation.git
```
Open the project in Android Studio.

### 2. Configure Google Maps API
Create a `local.properties` file in the root directory and add your Google Maps API Key:
```properties
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

### 3. Setup Firebase App Config
1. Create a Firebase project.
2. Register your Android app with the package name `com.arlabs.raksha`.
3. Download `google-services.json` and place it in the `app/` directory.

### 4. Build and Run
Sync Gradle and run the app on an emulator or physical device (physical device recommended for GPS and Overlay testing).

---

## ☁️ Firebase & Twilio Setup (Cloud Functions)

To enable SMS alerts and failsafe timers, you must deploy the included Cloud Functions.

1. Ensure you have the [Firebase CLI](https://firebase.google.com/docs/cli) installed.
2. Navigate to the `functions` directory:
   ```bash
   cd functions
   npm install
   ```
3. Create a `.env` file inside the `functions` directory with your Twilio credentials:
   ```env
   TWILIO_ACCOUNT_SID=your_account_sid
   TWILIO_AUTH_TOKEN=your_auth_token
   TWILIO_PHONE_NUMBER=+1234567890
   ```
4. Enable **Google Cloud Tasks** in your Google Cloud Console and create a queue named `safety-timer-queue`.
5. Deploy the functions:
   ```bash
   firebase deploy --only functions
   ```
*(For a more detailed walkthrough, see [Cloud_Functions_Setup.md](./Cloud_Functions_Setup.md)).*

---

## 🔐 Security & Privacy

- **No Secrets in Repo**: API keys and Twilio credentials are deliberately ignored via `.gitignore`. You must provide your own `.env` and `google-services.json` files.
- **Data Ownership**: Location data is only shared with explicitly chosen emergency contacts and only during active emergencies or live sessions.

---

> **Note**: This project was developed as a Major Project. See `major project file final.docx` for extensive academic documentation and research regarding this application.

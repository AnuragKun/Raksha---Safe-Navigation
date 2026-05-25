package com.arlabs.raksha.features.fakecall

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class FakeCallActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure the activity shows even when the screen is locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        enableEdgeToEdge()

        val callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        val callerPhone = intent.getStringExtra("CALLER_PHONE") ?: ""

        // Dismiss the notification from FakeCallReceiver
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(FakeCallReceiver.NOTIFICATION_ID)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FakeCallScreen(
                    callerName = callerName,
                    callerPhone = callerPhone,
                    onAccept = {
                        stopRinging()
                    },
                    onDecline = {
                        stopRinging()
                        finish()
                    }
                )
            }
        }

        startRinging()
    }

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }
}

@Composable
fun FakeCallScreen(
    callerName: String,
    callerPhone: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var isCallActive by remember { mutableStateOf(false) }
    var callDurationSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            while (true) {
                delay(1000)
                callDurationSeconds++
            }
        }
    }

    // A pure dark background feels much closer to standard Material dialers
    val bgColor = Color(0xFF121212)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        if (!isCallActive) {
            IncomingCallScreen(
                callerName = callerName,
                callerPhone = callerPhone,
                onAccept = {
                    onAccept()
                    isCallActive = true
                },
                onDecline = onDecline
            )
        } else {
            ActiveCallScreen(
                callerName = callerName,
                callerPhone = callerPhone,
                callDurationSeconds = callDurationSeconds,
                onEndCall = onDecline
            )
        }
    }
}

@Composable
private fun IncomingCallScreen(
    callerName: String,
    callerPhone: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Pulse animation to simulate "ringing" state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 80.dp, bottom = 60.dp), // Adjusted padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: Avatar + Caller Info
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Animated Profile Avatar
            Surface(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale), // Applies the breathing animation
                shape = CircleShape,
                color = Color(0xFF303030)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Contact",
                    tint = Color(0xFF9AA0A6), // Google's typical subtle icon tint
                    modifier = Modifier.padding(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = callerName,
                color = Color.White,
                fontSize = 36.sp, // Larger, more prominent
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (callerPhone.isNotBlank()) {
                Text(
                    text = "Mobile • $callerPhone",
                    color = Color(0xFF9AA0A6),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom Section: Decline + Accept
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 64.dp), // Pushed slightly closer to center
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Decline Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onDecline,
                    containerColor = Color(0xFFEA4335), // Google Red
                    contentColor = Color.White,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Decline",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Accept Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onAccept,
                    containerColor = Color(0xFF34A853), // Google Green
                    contentColor = Color.White,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Accept",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveCallScreen(
    callerName: String,
    callerPhone: String,
    callDurationSeconds: Int,
    onEndCall: () -> Unit
) {
    val minutes = callDurationSeconds / 60
    val seconds = callDurationSeconds % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Pure dark mode background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 60.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Name + Phone + Timer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = timeString,
                    color = Color(0xFF9AA0A6),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Middle: Avatar
            Surface(
                modifier = Modifier.size(180.dp),
                shape = CircleShape,
                color = Color(0xFF303030)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Contact",
                    tint = Color(0xFF9AA0A6),
                    modifier = Modifier.padding(40.dp)
                )
            }

            // Bottom: Actions + End Call
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Separated Action buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallActionButton(icon = Icons.Default.Dialpad, label = "Keypad")
                    CallActionButton(icon = Icons.Default.Mic, label = "Mute")
                    CallActionButton(icon = Icons.Default.VolumeUp, label = "Speaker")
                    CallActionButton(icon = Icons.Default.MoreVert, label = "More")
                }

                Spacer(modifier = Modifier.height(48.dp))

                // End Call Button — Prominent bottom red pill
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = Color(0xFFEA4335), // Google Red
                    contentColor = Color.White,
                    modifier = Modifier
                        .width(180.dp)
                        .height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = Color(0xFF303030) // Individual dark button backgrounds
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color(0xFF9AA0A6),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
package com.arlabs.raksha.features.emergencyhub

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyHubScreen(
    navController: NavController,
    viewModel: EmergencyHubViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Alarm management
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Start alarm when SOS is active
    LaunchedEffect(state.isSosActive) {
        if (state.isSosActive) {
            mediaPlayer = startAlarm(context)
        } else {
            mediaPlayer?.let {
                it.stop()
                it.release()
            }
            mediaPlayer = null
        }
    }

    // Clean up alarm on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let {
                it.stop()
                it.release()
            }
        }
    }

    // Error handling
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Confirmation dialog
    if (state.isConfirmDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmDialog() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("Trigger Emergency SOS?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will immediately alert all your emergency contacts with your current location. " +
                    "A loud alarm will sound on your device.\n\nOnly use this in a real emergency.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.triggerSos() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("TRIGGER SOS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Emergency background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (state.isSosActive) {
                        listOf(Color(0xFFB71C1C), Color(0xFF880E4F))
                    } else {
                        listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                    }
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Emergency Hub",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (state.isSosActive) {
                    // --- SOS Active State ---
                    SosActiveView(
                        onStopAlarm = {
                            mediaPlayer?.let {
                                it.stop()
                                it.release()
                            }
                            mediaPlayer = null
                            viewModel.resetSos()
                        }
                    )
                } else {
                    // --- Normal State with SOS Button ---
                    SosButtonView(
                        isTriggering = state.isTriggering,
                        onSosClick = { viewModel.showConfirmDialog() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SosButtonView(
    isTriggering: Boolean,
    onSosClick: () -> Unit
) {
    // Pulsing animation
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Emergency SOS",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Press the button below to alert your emergency contacts",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Outer pulse ring
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = 0.1f))
                .border(2.dp, Color.Red.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Inner SOS button
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFF1744), Color(0xFFD50000))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isTriggering
                    ) { onSosClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isTriggering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SOS",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Quick info cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(
                icon = Icons.Default.LocationOn,
                text = "Location will be shared",
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                icon = Icons.Default.Notifications,
                text = "Contacts will be alerted",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SosActiveView(
    onStopAlarm: () -> Unit
) {
    // Flashing animation
    val infiniteTransition = rememberInfiniteTransition(label = "flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "SOS Active",
            tint = Color.White.copy(alpha = flashAlpha),
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SOS ACTIVATED",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = flashAlpha),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Emergency contacts have been notified.\nYour location is being shared.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Stop Alarm button
        OutlinedButton(
            onClick = onStopAlarm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
        ) {
            Icon(Icons.Default.VolumeOff, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("STOP ALARM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun startAlarm(context: Context): MediaPlayer? {
    return try {
        // Set volume to max
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // Play alarm sound
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context, alarmUri)
            isLooping = true
            prepare()
            start()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

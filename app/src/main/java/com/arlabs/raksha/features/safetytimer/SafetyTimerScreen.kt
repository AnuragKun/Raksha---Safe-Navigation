package com.arlabs.raksha.features.safetytimer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arlabs.raksha.Common.GradientBox
import com.arlabs.raksha.services.TimerService

@Composable
fun SafetyTimerScreen(
    navController: NavController,
    viewModel: SafetyTimerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    GradientBox(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Symbol
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Safety Timer",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Timed Safety Check",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Set a timer. If you don't check in before it expires, your emergency contacts will be alerted with your last known location.",
                    fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Emergency contact reminder note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Make sure you've added your emergency contacts in the Profile screen before starting the timer.",
                        fontSize = 13.sp,
                        color = Color(0xFFFFCC80),
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (uiState.isActive) {
                    ActiveTimerView(
                        remainingTimeMs = uiState.remainingTimeMs,
                        onCancelClick = { viewModel.cancelTimer() },
                        isLoading = uiState.isLoading
                    )
                } else {
                    SetupTimerView(
                        selectedMinutes = uiState.selectedMinutes,
                        onDurationSelect = { viewModel.setDuration(it) },
                        onStartClick = {
                            viewModel.startTimer()
                            TimerService.start(context)
                        },
                        isLoading = uiState.isLoading
                    )
                }
            }
        }
    }
}



@Composable
private fun SetupTimerView(
    selectedMinutes: Int,
    onDurationSelect: (Int) -> Unit,
    onStartClick: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select Duration", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))

            // Duration Selector (Multiples of 5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (selectedMinutes > 5) onDurationSelect(selectedMinutes - 5) },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape).size(48.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                }

                Spacer(modifier = Modifier.width(32.dp))

                Text(
                    text = "$selectedMinutes min",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(
                    onClick = { onDurationSelect(selectedMinutes + 5) },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape).size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // *** HIGHLY VISIBLE Start Button ***
            val baseColor = Color(0xFFf40c5c)
            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth().height(64.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = baseColor,
                    contentColor = Color.White,
                    disabledContainerColor = baseColor.copy(alpha = 0.5f),
                    disabledContentColor = Color.White
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("⏱  START TIMER", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun ActiveTimerView(
    remainingTimeMs: Long,
    onCancelClick: () -> Unit,
    isLoading: Boolean
) {
    val totalSeconds = (remainingTimeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "timerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Red "TIMER ACTIVE" badge
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Color.Red.copy(alpha = 0.1f))
                    .border(1.dp, Color.Red, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("⚠  TIMER ACTIVE", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.Red, letterSpacing = 2.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large countdown display
            Text(
                text = timeString,
                fontSize = 72.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF1A237E),
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("remaining", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Your location is being tracked and updated periodically.",
                fontSize = 14.sp, textAlign = TextAlign.Center, color = Color.Gray
            )
            Spacer(modifier = Modifier.height(32.dp))

            // *** PROMINENT "I'M SAFE" button ***
            val baseColor = Color(0xFF2E7D32)
            Button(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth().height(80.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = baseColor, // Forest green
                    contentColor = Color.White,
                    disabledContainerColor = baseColor.copy(alpha = 0.5f),
                    disabledContentColor = Color.White
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                } else {
                    Text("✅  I'M SAFE — CHECK IN", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

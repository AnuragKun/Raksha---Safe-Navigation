package com.arlabs.raksha.features.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arlabs.raksha.features.home.HomeScreen
import com.arlabs.raksha.features.profile.ProfileScreen
import com.arlabs.raksha.features.report.ReportIncidentScreen
import com.arlabs.raksha.features.safezone.SafeZoneScreen
import com.arlabs.raksha.R
import com.arlabs.raksha.navigation.NavItem

import androidx.navigation.NavController
import com.arlabs.raksha.Common.AestheticCornerRadius
import com.arlabs.raksha.Common.AestheticHorizontalPadding
import com.arlabs.raksha.Common.GradientBox

import com.arlabs.raksha.services.TimerService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(modifier: Modifier = Modifier, navController: NavController) {

    val navItemList = listOf(
        NavItem("Home", painterResource(id = R.drawable.ic_home)),
        NavItem("Safe Zone", painterResource(id = R.drawable.ic_safe_zone)),
        NavItem("Report", painterResource(id = R.drawable.ic_report)),
        NavItem("Profile", painterResource(id = R.drawable.ic_profile))
    )
    var selectedIndex by remember { mutableIntStateOf(0) }

    // Double-back-to-exit logic
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            // Exit the app
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    // Timer expiry dialog: check SharedPreferences for pending SOS notification
    var showTimerExpiredDialog by remember { mutableStateOf(false) }
    var timerSosFailed by remember { mutableStateOf(false) }
    var timerExpiredTimeStr by remember { mutableStateOf("") }

    // Check on first composition
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(TimerService.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val sosSent = prefs.getBoolean(TimerService.KEY_TIMER_EXPIRED_SOS_SENT, false)
        if (sosSent) {
            timerSosFailed = prefs.getBoolean(TimerService.KEY_TIMER_SOS_FAILED, false)
            val expiredTime = prefs.getLong(TimerService.KEY_TIMER_EXPIRED_TIME, 0L)
            timerExpiredTimeStr = if (expiredTime > 0L) {
                SimpleDateFormat("hh:mm a, MMM dd", Locale.getDefault()).format(Date(expiredTime))
            } else ""
            showTimerExpiredDialog = true
        }
    }

    GradientBox(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                RakshaBottomNavBar(
                    items = navItemList,
                    selectedIndex = selectedIndex,
                    onItemSelected = { selectedIndex = it }
                )
            }
        ) { innerPadding ->
            ContentScreen(
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                selectedIndex = selectedIndex,
                navController = navController,
                onNavigateHome = { selectedIndex = 0 }
            )
        }
    }

    // Timer expired pop-up dialog
    if (showTimerExpiredDialog) {
        AlertDialog(
            onDismissRequest = {
                showTimerExpiredDialog = false
                TimerService.clearExpiredTimerFlag(context)
            },
            containerColor = Color(0xFF1A1A2E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.9f),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (timerSosFailed) "⚠️" else "🚨",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (timerSosFailed) "Timer Expired" else "Timer Expired — Contacts Notified",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column {
                    if (timerSosFailed) {
                        Text(
                            "Your safety timer expired${if (timerExpiredTimeStr.isNotEmpty()) " at $timerExpiredTimeStr" else ""}, " +
                            "but the SOS message could not be sent.\n\n" +
                            "Please check your emergency contacts and SMS permissions.",
                            fontSize = 15.sp
                        )
                    } else {
                        Text(
                            "Your safety timer expired${if (timerExpiredTimeStr.isNotEmpty()) " at $timerExpiredTimeStr" else ""}.\n\n" +
                            "✅ Your emergency contacts have been notified with your last known location.\n\n" +
                            "If you are safe, no further action is needed.",
                            fontSize = 15.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimerExpiredDialog = false
                        TimerService.clearExpiredTimerFlag(context)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (timerSosFailed) Color(0xFFFF9800) else Color(0xFF4CAF50)
                    )
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        )
    }
}

@Composable
fun RakshaBottomNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val PinkAccent = Color(0xFFE91E63)
    val PinkLight = Color(0xFFFCE4EC)
    val NavBackground = Color(0xFFFFFBFE)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = AestheticHorizontalPadding, end = AestheticHorizontalPadding, bottom = 16.dp, top = 8.dp)
    ) {
        // Floating Nav bar container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = AestheticCornerRadius,
                    clip = false,
                    ambientColor = PinkAccent.copy(alpha = 0.2f),
                    spotColor = PinkAccent.copy(alpha = 0.2f)
                )
                .clip(AestheticCornerRadius)
                .background(Color.White.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, navItem ->
                    val isSelected = selectedIndex == index

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) PinkAccent else Color(0xFF9E9E9E),
                        animationSpec = tween(300),
                        label = "iconTint"
                    )

                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) PinkAccent else Color(0xFF9E9E9E),
                        animationSpec = tween(300),
                        label = "labelColor"
                    )

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onItemSelected(index) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = navItem.icon,
                            contentDescription = navItem.label,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = navItem.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = labelColor
                        )
                        // Soft line indicator
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (isSelected) 16.dp else 0.dp,
                                    height = 3.dp
                                )
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) PinkAccent else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContentScreen(modifier: Modifier = Modifier, selectedIndex: Int, navController: NavController, onNavigateHome: () -> Unit) {
    when (selectedIndex) {
        0 -> HomeScreen(navController = navController, modifier = modifier)
        1 -> SafeZoneScreen(modifier = modifier, onNavigateHome = onNavigateHome)
        2 -> ReportIncidentScreen(navController = navController, modifier = modifier)
        3 -> ProfileScreen(navController = navController, modifier = modifier)
    }
}
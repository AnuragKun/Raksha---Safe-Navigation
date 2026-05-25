package com.arlabs.raksha.features.onboarding.Components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sms
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.arlabs.raksha.Common.GradientBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingPageItem(
    page: Page,
    pagerState: PagerState,
    onGetStartedClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1
    val context = LocalContext.current

    // Track permission states for the permissions page
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var contactsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val allPermissionsGranted = locationGranted && notificationGranted && smsGranted && contactsGranted
    val isPermissionsPage = page.type == PageType.SETUP_PERMISSIONS
    // Disable forward navigation on permissions page until all granted
    val canProceed = !isPermissionsPage || allPermissionsGranted

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        GradientBox(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Top content — adaptive based on page type
                when (page.type) {
                    PageType.FEATURE_TOUR -> {
                        Spacer(modifier = Modifier.weight(1f))
                        Image(
                            painter = painterResource(id = page.imageRes),
                            contentDescription = page.title,
                            modifier = Modifier
                                .size(200.dp)
                                .padding(16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    PageType.SETUP_CONTACTS, PageType.SETUP_PERMISSIONS -> {
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(page.emoji, fontSize = 48.sp)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Bottom card with text + action
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = page.title,
                            color = Color.Black,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = page.description,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Interactive content per page type
                        when (page.type) {
                            PageType.FEATURE_TOUR -> { /* No extra content */ }
                            PageType.SETUP_CONTACTS -> SetupContactsContent()
                            PageType.SETUP_PERMISSIONS -> SetupPermissionsContent(
                                locationGranted = locationGranted,
                                onLocationGranted = { locationGranted = it },
                                notificationGranted = notificationGranted,
                                onNotificationGranted = { notificationGranted = it },
                                smsGranted = smsGranted,
                                onSmsGranted = { smsGranted = it },
                                contactsGranted = contactsGranted,
                                onContactsGranted = { contactsGranted = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        PagerIndicator(pagerState = pagerState)

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (isLastPage) {
                                    onGetStartedClicked()
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            pagerState.currentPage + 1,
                                            animationSpec = tween(500)
                                        )
                                    }
                                }
                            },
                            enabled = canProceed,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White,
                                disabledContainerColor = Color.Gray.copy(alpha = 0.4f),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 2.dp
                            )
                        ) {
                            Text(
                                text = when {
                                    isPermissionsPage && !canProceed -> "Grant All Permissions to Continue"
                                    isLastPage -> "Get Started"
                                    page.type != PageType.FEATURE_TOUR -> "Continue"
                                    else -> "Next"
                                },
                                fontSize = if (isPermissionsPage && !canProceed) 14.sp else 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isLastPage && page.type == PageType.SETUP_CONTACTS) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You can set this up later",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ─── Setup Contacts Section ───────────────────────────────────────────

@Composable
private fun SetupContactsContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SetupCheckItem(
            icon = Icons.Default.People,
            label = "Add at least 1 emergency contact",
            hint = "You'll be able to add contacts from your Profile after onboarding",
            isComplete = false
        )
    }
}

// ─── Setup Permissions Section ────────────────────────────────────────

@Composable
private fun SetupPermissionsContent(
    locationGranted: Boolean,
    onLocationGranted: (Boolean) -> Unit,
    notificationGranted: Boolean,
    onNotificationGranted: (Boolean) -> Unit,
    smsGranted: Boolean,
    onSmsGranted: (Boolean) -> Unit,
    contactsGranted: Boolean,
    onContactsGranted: (Boolean) -> Unit
) {
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onLocationGranted(granted) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onNotificationGranted(granted) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onSmsGranted(granted) }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onContactsGranted(granted) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PermissionRow(
            icon = Icons.Default.LocationOn,
            label = "Precise Location",
            hint = "Required for safe routing & SOS alerts",
            granted = locationGranted,
            onRequest = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        )

        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(
                icon = Icons.Default.Notifications,
                label = "Notifications",
                hint = "Required for timer reminders & alerts",
                granted = notificationGranted,
                onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }

        PermissionRow(
            icon = Icons.Default.Sms,
            label = "SMS",
            hint = "Required to send SOS messages to contacts",
            granted = smsGranted,
            onRequest = { smsLauncher.launch(Manifest.permission.SEND_SMS) }
        )

        PermissionRow(
            icon = Icons.Default.Contacts,
            label = "Contacts",
            hint = "Required to pick emergency contacts",
            granted = contactsGranted,
            onRequest = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    hint: String = "",
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (granted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (granted) Color(0xFF4CAF50) else Color(0xFFFF9800))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (hint.isNotEmpty()) {
                Text(hint, fontSize = 11.sp, color = Color.Gray, lineHeight = 14.sp)
            }
        }
        if (granted) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = Color(0xFF4CAF50))
        } else {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Allow", fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun SetupCheckItem(
    icon: ImageVector,
    label: String,
    hint: String,
    isComplete: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isComplete) Color(0xFF4CAF50) else Color(0xFF9C27B0),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.Black)
            Text(hint, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
        }
    }
}

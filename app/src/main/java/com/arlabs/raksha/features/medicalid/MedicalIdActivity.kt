package com.arlabs.raksha.features.medicalid

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arlabs.raksha.data.local.UserPreferencesDataStore
import com.arlabs.raksha.domain.model.EmergencyContact
import com.arlabs.raksha.domain.model.UserData
import com.arlabs.raksha.domain.repository.EmergencyContactRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Emergency Medical ID Activity — displays critical medical information.
 *
 * Designed to be shown over the lock screen so a bystander or first responder
 * can access the user's medical info and emergency contacts without unlocking.
 */
@AndroidEntryPoint
class MedicalIdActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var emergencyContactRepository: EmergencyContactRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
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

        setContent {
            val userData by userPreferencesDataStore.userData.collectAsState(initial = UserData())
            val contacts by emergencyContactRepository.getContacts().collectAsState(initial = emptyList())

            MaterialTheme(colorScheme = darkColorScheme()) {
                MedicalIdScreen(
                    userData = userData,
                    contacts = contacts,
                    onClose = { finish() }
                )
            }
        }
    }
}

// ==========================================
// MEDICAL ID SCREEN
// ==========================================

@Composable
fun MedicalIdScreen(
    userData: UserData,
    contacts: List<EmergencyContact>,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val headerGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFB71C1C),
            Color(0xFFD32F2F)
        )
    )

    val hasMedicalData = userData.allergies.isNotEmpty() ||
            userData.medicalConditions.isNotEmpty() ||
            userData.emergencyNote.isNotEmpty() ||
            userData.bloodGroup.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ─── Red Emergency Header ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerGradient)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Medical cross icon
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MEDICAL ID",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Emergency Information",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // ─── Content ───
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Personal Info Card
                MedicalInfoCard(
                    title = "PERSONAL INFO",
                    icon = Icons.Default.Person,
                    accentColor = Color(0xFF42A5F5)
                ) {
                    if (userData.name.isNotEmpty()) {
                        InfoField(label = "Full Name", value = userData.name)
                    }
                    if (userData.phone.isNotEmpty()) {
                        InfoField(label = "Phone", value = userData.phone)
                    }
                    if (userData.bloodGroup.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFB71C1C).copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Bloodtype,
                                contentDescription = null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Blood Group",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    userData.bloodGroup,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFEF5350)
                                )
                            }
                        }
                    }

                    if (userData.name.isEmpty() && userData.phone.isEmpty() && userData.bloodGroup.isEmpty()) {
                        EmptyStateText("No personal info configured")
                    }
                }

                // Medical Conditions
                if (hasMedicalData) {
                    MedicalInfoCard(
                        title = "MEDICAL INFORMATION",
                        icon = Icons.Default.MedicalServices,
                        accentColor = Color(0xFFEF5350)
                    ) {
                        if (userData.allergies.isNotEmpty()) {
                            MedicalField(
                                label = "⚠️ Allergies",
                                value = userData.allergies,
                                bgColor = Color(0xFFE65100).copy(alpha = 0.15f),
                                textColor = Color(0xFFFFAB91)
                            )
                        }
                        if (userData.medicalConditions.isNotEmpty()) {
                            MedicalField(
                                label = "🩺 Medical Conditions",
                                value = userData.medicalConditions,
                                bgColor = Color(0xFF1565C0).copy(alpha = 0.15f),
                                textColor = Color(0xFF90CAF9)
                            )
                        }
                        if (userData.emergencyNote.isNotEmpty()) {
                            MedicalField(
                                label = "📝 Emergency Note",
                                value = userData.emergencyNote,
                                bgColor = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                textColor = Color(0xFFA5D6A7)
                            )
                        }
                    }
                } else {
                    MedicalInfoCard(
                        title = "MEDICAL INFORMATION",
                        icon = Icons.Default.MedicalServices,
                        accentColor = Color(0xFFEF5350)
                    ) {
                        EmptyStateText("No medical information configured.\nAdd allergies, conditions, or notes in your Profile.")
                    }
                }

                // Emergency Contacts
                MedicalInfoCard(
                    title = "EMERGENCY CONTACTS",
                    icon = Icons.Default.Phone,
                    accentColor = Color(0xFF66BB6A)
                ) {
                    if (contacts.isEmpty()) {
                        EmptyStateText("No emergency contacts added.\nAdd contacts in your Profile.")
                    } else {
                        contacts.forEach { contact ->
                            EmergencyContactRow(
                                contact = contact,
                                onCall = {
                                    val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${contact.phoneNumber}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(callIntent)
                                }
                            )
                            if (contact != contacts.last()) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Close button at bottom
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ==========================================
// COMPONENTS
// ==========================================

@Composable
private fun MedicalInfoCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 1.5.sp
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun MedicalField(
    label: String,
    value: String,
    bgColor: Color,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(12.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.95f),
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun EmergencyContactRow(
    contact: EmergencyContact,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onCall)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E7D32).copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.firstOrNull()?.uppercase() ?: "?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF66BB6A)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                contact.phoneNumber,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            if (contact.relation.isNotEmpty()) {
                Text(
                    contact.relation,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }

        // Call button
        FilledIconButton(
            onClick = onCall,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White
            ),
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                Icons.Default.Phone,
                contentDescription = "Call ${contact.name}",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateText(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.4f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        lineHeight = 20.sp
    )
}

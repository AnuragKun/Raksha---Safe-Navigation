package com.arlabs.raksha.features.profile

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arlabs.raksha.Common.GradientBox
import com.arlabs.raksha.Common.*
import com.arlabs.raksha.domain.model.EmergencyContact
import com.arlabs.raksha.domain.model.UserData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val userData by viewModel.userData.collectAsState()
    val contacts by viewModel.emergencyContacts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Handle errors and success messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let {
            resolveContact(context, it)?.let { (name, phone) ->
                viewModel.setContactFromPicker(name, phone)
            }
        }
    }
    // Form States
    var editName by remember(userData.name) { mutableStateOf(userData.name) }
    var editPhone by remember(userData.phone) { mutableStateOf(userData.phone) }
    var editBloodGroup by remember(userData.bloodGroup) { mutableStateOf(userData.bloodGroup) }
    
    var allergies by remember(userData.allergies) { mutableStateOf(userData.allergies) }
    var conditions by remember(userData.medicalConditions) { mutableStateOf(userData.medicalConditions) }
    var note by remember(userData.emergencyNote) { mutableStateOf(userData.emergencyNote) }
    
    var timerMinutes by remember(userData.defaultTimerMinutes) { mutableStateOf(userData.defaultTimerMinutes.toString()) }
    var sosMessage by remember(userData.sosMessageTemplate) { mutableStateOf(userData.sosMessageTemplate) }

    // Contact Add/Edit Dialog
    if (uiState.isContactDialogVisible) {
        ContactDialog(
            isEditing = uiState.editingContact != null,
            name = uiState.dialogName,
            phone = uiState.dialogPhone,
            relation = uiState.dialogRelation,
            isLoading = uiState.isLoading,
            onNameChange = viewModel::onDialogNameChange,
            onPhoneChange = viewModel::onDialogPhoneChange,
            onRelationChange = viewModel::onDialogRelationChange,
            onSave = viewModel::saveContact,
            onDismiss = viewModel::dismissContactDialog,
            onPickContact = { contactPickerLauncher.launch(null) }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AestheticTopBar(
                    title = "Profile",
                    actions = {
                        if (uiState.isEditing) {
                            IconButton(onClick = { viewModel.toggleEditMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                            }
                            IconButton(onClick = {
                                viewModel.saveAllProfileData(
                                    name = editName, phone = editPhone, bloodGroup = editBloodGroup,
                                    allergies = allergies, conditions = conditions, note = note,
                                    timerMinutes = timerMinutes.toIntOrNull() ?: 20, sosMessage = sosMessage
                                )
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White)
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleEditMode() }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = Color.White)
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
                    .padding(horizontal = AestheticHorizontalPadding).verticalScroll(scrollState)
            ) {
                // 1. Identity Card
                IdentityCard(
                    userData = userData, uiState = uiState, navController = navController,
                    editName = editName, onNameChange = { editName = it },
                    editPhone = editPhone, onPhoneChange = { editPhone = it },
                    editBloodGroup = editBloodGroup, onBGChange = { editBloodGroup = it }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Medical ID Card
                MedicalIdCard(
                    userData = userData, uiState = uiState,
                    allergies = allergies, onAllergiesChange = { allergies = it },
                    conditions = conditions, onConditionsChange = { conditions = it },
                    note = note, onNoteChange = { note = it }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 3. Safety Settings Card
                SafetySettingsCard(
                    userData = userData, uiState = uiState,
                    timerMinutes = timerMinutes, onTimerChange = { timerMinutes = it },
                    sosMessage = sosMessage, onSosMessageChange = { sosMessage = it }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 4. Emergency Contacts Section
                EmergencyContactsSection(contacts, viewModel)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// =========================================================================
// 1. IDENTITY CARD
// =========================================================================
@Composable
private fun IdentityCard(
    userData: UserData,
    uiState: ProfileUiState,
    navController: NavController,
    editName: String, onNameChange: (String) -> Unit,
    editPhone: String, onPhoneChange: (String) -> Unit,
    editBloodGroup: String, onBGChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AestheticCornerRadius,
        colors = CardDefaults.cardColors(containerColor = AestheticTransparentWhite)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Avatar
            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "Profile",
                    tint = Color.White, modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isEditing) {
                OutlinedTextField(
                    value = editName, onValueChange = onNameChange,
                    label = { Text("Full Name", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = whiteTextFieldColors(), singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editPhone, onValueChange = onPhoneChange,
                    label = { Text("Phone Number", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = whiteTextFieldColors(), singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editBloodGroup, onValueChange = { onBGChange(it.uppercase()) },
                    label = { Text("Blood Group", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = whiteTextFieldColors(), singleLine = true
                )
            } else {
                Text(
                    text = userData.name.ifEmpty { "Set your name" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(
                    text = userData.email.ifEmpty { "No email" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (userData.phone.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(userData.phone, style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f))
                }
                if (userData.bloodGroup.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bloodtype, contentDescription = null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Blood: ${userData.bloodGroup}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Verification Status (inline)
            VerificationBadge(
                isVerified = userData.isVerified,
                onVerifyClick = { navController.navigate(com.arlabs.raksha.navigation.Routes.VerifyAccountScreen) }
            )
        }
    }
}

@Composable
private fun VerificationBadge(isVerified: Boolean, onVerifyClick: () -> Unit) {
    val bgColor = if (isVerified) Color(0xFF2E7D32).copy(alpha = 0.3f) else AestheticTransparentWhite
    Row(
        modifier = Modifier.fillMaxWidth().clip(AestheticCornerRadius)
            .background(bgColor).clickable(enabled = !isVerified) { onVerifyClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isVerified) Icons.Default.Verified else Icons.Default.Warning,
            contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (isVerified) "Phone Verified ✓" else "Phone Not Verified",
                fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            Text(if (isVerified) "Trusted reporter" else "Tap to verify",
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        if (!isVerified) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Verify",
                tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

// =========================================================================
// 2. MEDICAL ID CARD
// =========================================================================
@Composable
private fun MedicalIdCard(
    userData: UserData, uiState: ProfileUiState,
    allergies: String, onAllergiesChange: (String) -> Unit,
    conditions: String, onConditionsChange: (String) -> Unit,
    note: String, onNoteChange: (String) -> Unit
) {
    val context = LocalContext.current

    SectionCard(
        title = "Medical ID",
        icon = Icons.Default.MedicalServices
    ) {
        if (uiState.isEditing) {
            OutlinedTextField(
                value = allergies, onValueChange = onAllergiesChange,
                label = { Text("Allergies", color = Color.White.copy(alpha = 0.7f)) },
                placeholder = { Text("e.g. Penicillin, Peanuts", color = Color.White.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(), colors = whiteTextFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = conditions, onValueChange = onConditionsChange,
                label = { Text("Medical Conditions", color = Color.White.copy(alpha = 0.7f)) },
                placeholder = { Text("e.g. Asthma, Diabetes", color = Color.White.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(), colors = whiteTextFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = note, onValueChange = onNoteChange,
                label = { Text("Emergency Note", color = Color.White.copy(alpha = 0.7f)) },
                placeholder = { Text("Anything a doctor should know", color = Color.White.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(), colors = whiteTextFieldColors(), maxLines = 3
            )
        } else {
            if (userData.allergies.isEmpty() && userData.medicalConditions.isEmpty() && userData.emergencyNote.isEmpty()) {
                Text("No medical info added yet. Tap Edit to add.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            } else {
                if (userData.allergies.isNotEmpty()) InfoRow("Allergies", userData.allergies)
                if (userData.medicalConditions.isNotEmpty()) InfoRow("Conditions", userData.medicalConditions)
                if (userData.emergencyNote.isNotEmpty()) InfoRow("Note", userData.emergencyNote)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    context.startActivity(
                        android.content.Intent(context, com.arlabs.raksha.features.medicalid.MedicalIdActivity::class.java)
                    )
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB71C1C).copy(alpha = 0.8f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Show Emergency Medical ID", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// =========================================================================
// 3. SAFETY SETTINGS CARD
// =========================================================================
@Composable
private fun SafetySettingsCard(
    userData: UserData, uiState: ProfileUiState,
    timerMinutes: String, onTimerChange: (String) -> Unit,
    sosMessage: String, onSosMessageChange: (String) -> Unit
) {
    SectionCard(
        title = "Safety Settings",
        icon = Icons.Default.Shield
    ) {
        if (uiState.isEditing) {
            OutlinedTextField(
                value = timerMinutes, onValueChange = { onTimerChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Default Timer (minutes)", color = Color.White.copy(alpha = 0.7f)) },
                modifier = Modifier.fillMaxWidth(), colors = whiteTextFieldColors(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = sosMessage, onValueChange = onSosMessageChange,
                label = { Text("SOS Message Template", color = Color.White.copy(alpha = 0.7f)) },
                modifier = Modifier.fillMaxWidth(), colors = whiteTextFieldColors(), maxLines = 3
            )
        } else {
            InfoRow("Default Timer", "${userData.defaultTimerMinutes} minutes")
            InfoRow("SOS Message", userData.sosMessageTemplate)
        }
    }
}

// =========================================================================
// 4. EMERGENCY CONTACTS
// =========================================================================
@Composable
private fun EmergencyContactsSection(contacts: List<EmergencyContact>, viewModel: ProfileViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Emergency Contacts", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = Color.White)
        IconButton(
            onClick = { viewModel.showAddContactDialog() },
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Contact", tint = Color.White)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (contacts.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.People, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No emergency contacts added", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                Text("Add contacts who will be notified in emergencies", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    } else {
        contacts.forEach { contact ->
            EmergencyContactCard(
                contact = contact,
                onEditClick = { viewModel.showEditContactDialog(contact) },
                onDeleteClick = { viewModel.deleteContact(contact.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmergencyContactCard(contact: EmergencyContact, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Contact") },
            text = { Text("Remove ${contact.name} from emergency contacts?") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeleteClick() }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = AestheticCornerRadius,
        colors = CardDefaults.cardColors(containerColor = AestheticTransparentWhiteStrong)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                Text(contact.phoneNumber, fontSize = 13.sp, color = Color.DarkGray)
                if (contact.relation.isNotEmpty()) {
                    Text(contact.relation, fontSize = 12.sp, color = Color.Gray)
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete",
                    tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

// =========================================================================
// CONTACT DIALOG (with "Pick from Contacts" button)
// =========================================================================
@Composable
private fun ContactDialog(
    isEditing: Boolean,
    name: String, phone: String, relation: String,
    isLoading: Boolean,
    onNameChange: (String) -> Unit, onPhoneChange: (String) -> Unit,
    onRelationChange: (String) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit,
    onPickContact: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Contact" else "Add Emergency Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Pick from Contacts button
                if (!isEditing) {
                    OutlinedButton(
                        onClick = onPickContact,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick from Contacts")
                    }
                    HorizontalDivider()
                }
                OutlinedTextField(
                    value = name, onValueChange = onNameChange,
                    label = { Text("Name") }, placeholder = { Text("Contact name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                OutlinedTextField(
                    value = phone, onValueChange = onPhoneChange,
                    label = { Text("Phone Number") }, placeholder = { Text("+91 XXXXX XXXXX") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
                )
                OutlinedTextField(
                    value = relation, onValueChange = onRelationChange,
                    label = { Text("Relation (Optional)") }, placeholder = { Text("e.g. Parent, Friend") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) }
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isLoading && name.isNotBlank() && phone.isNotBlank()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(if (isEditing) "Update" else "Add")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// =========================================================================
// SHARED COMPONENTS
// =========================================================================

@Composable
private fun SectionCard(
    title: String, icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AestheticCornerRadius,
        colors = CardDefaults.cardColors(containerColor = AestheticTransparentWhite)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium)
        Text(value, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun whiteTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black, unfocusedTextColor = Color.White,
    focusedContainerColor = AestheticTransparentWhiteStrong, unfocusedContainerColor = AestheticTransparentWhite,
    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
    cursorColor = Color.White, focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
)

// =========================================================================
// UTILITY: Resolve contact name + phone from URI
// =========================================================================
private fun resolveContact(context: android.content.Context, uri: Uri): Pair<String, String>? {
    var name = ""
    var phone = ""

    try {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
                if (nameIdx >= 0) name = it.getString(nameIdx) ?: ""
                val contactId = if (idIdx >= 0) it.getString(idIdx) else null

                if (contactId != null) {
                    val phoneCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId), null
                    )
                    phoneCursor?.use { pc ->
                        if (pc.moveToFirst()) {
                            val phoneIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (phoneIdx >= 0) phone = pc.getString(phoneIdx) ?: ""
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return if (name.isNotEmpty()) Pair(name, phone) else null
}
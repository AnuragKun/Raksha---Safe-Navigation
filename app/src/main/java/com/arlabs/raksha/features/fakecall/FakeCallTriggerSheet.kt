package com.arlabs.raksha.features.fakecall

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeCallTriggerSheet(
    onDismissRequest: () -> Unit,
    viewModel: FakeCallViewModel = hiltViewModel()
) {
    val callerEntries by viewModel.callerEntries.collectAsState()
    val currentDefault by viewModel.defaultFakeCallCaller.collectAsState()
    val currentDefaultDelay by viewModel.defaultFakeCallDelay.collectAsState()

    var selectedEntry by remember { mutableStateOf<CallerEntry?>(null) }
    var selectedDelaySeconds by remember { mutableIntStateOf(10) }

    var showAddDialog by remember { mutableStateOf(false) }

    // Contact picker state — hoisted to sheet level so it survives dialog dismissal
    var pendingContactName by remember { mutableStateOf("") }
    var pendingContactPhone by remember { mutableStateOf("") }
    var contactPickerCompleted by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Contact picker launcher at the SHEET level (not inside the dialog)
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { contactUri ->
                try {
                    // Get contact name
                    val cursor = context.contentResolver.query(
                        contactUri, null, null, null, null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                pendingContactName = it.getString(nameIndex) ?: ""
                            }

                            // Get contact phone number
                            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                            val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                            if (idIndex >= 0 && hasPhoneIndex >= 0) {
                                val contactId = it.getString(idIndex)
                                val hasPhone = it.getInt(hasPhoneIndex) > 0
                                if (hasPhone) {
                                    val phoneCursor = context.contentResolver.query(
                                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                        null,
                                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                        arrayOf(contactId),
                                        null
                                    )
                                    phoneCursor?.use { pc ->
                                        if (pc.moveToFirst()) {
                                            val phoneIndex = pc.getColumnIndex(
                                                ContactsContract.CommonDataKinds.Phone.NUMBER
                                            )
                                            if (phoneIndex >= 0) {
                                                pendingContactPhone = pc.getString(phoneIndex) ?: ""
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                contactPickerCompleted = true
                showAddDialog = true
            }
        }
    }

    // Make sure we update selection if list changes
    LaunchedEffect(callerEntries) {
        if (selectedEntry != null && !callerEntries.contains(selectedEntry)) {
            selectedEntry = callerEntries.firstOrNull()
        } else if (selectedEntry == null && callerEntries.isNotEmpty()) {
            selectedEntry = callerEntries.first()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E2E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = Color(0xFF00C853))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Fake Call Scheduling",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Select Caller ID", color = Color.LightGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // Caller IDs List
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column {
                    if (callerEntries.isEmpty()) {
                        Text(
                            "No custom callers added. Tap + to add one.",
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp
                        )
                    } else {
                        callerEntries.forEach { entry ->
                            val isDefault = entry.encode() == currentDefault.encode()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedEntry = entry }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = selectedEntry == entry,
                                        onClick = { selectedEntry = entry },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00C853))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(entry.name, color = Color.White, fontSize = 16.sp)
                                            if (isDefault) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "Default",
                                                    tint = Color(0xFFFFD600),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        if (entry.phone.isNotBlank()) {
                                            Text(
                                                entry.phone,
                                                color = Color.LightGray,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.removeCallerEntry(entry) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        }
                    }

                    // Add New Button inside the list card
                    TextButton(
                        onClick = {
                            pendingContactName = ""
                            pendingContactPhone = ""
                            contactPickerCompleted = false
                            showAddDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2196F3))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Custom Caller", color = Color(0xFF2196F3))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Select Delay", color = Color.LightGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // Delay Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val delays = listOf(5 to "5s", 15 to "15s", 30 to "30s", 60 to "1m")
                delays.forEach { (seconds, label) ->
                    val isSelected = selectedDelaySeconds == seconds
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (seconds == 60) 0.dp else 8.dp)
                            .clickable { selectedDelaySeconds = seconds }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF00C853) else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Set as Panic Overlay Default button
            val entry = selectedEntry
            val isCurrentDefault = entry != null &&
                    entry.encode() == currentDefault.encode() &&
                    selectedDelaySeconds == currentDefaultDelay

            OutlinedButton(
                onClick = {
                    entry?.let { viewModel.setAsDefault(it, selectedDelaySeconds) }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = entry != null && !isCurrentDefault,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFFD600),
                    disabledContentColor = Color.Gray
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = entry != null && !isCurrentDefault)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isCurrentDefault) "✓ Panic Overlay Default"
                    else "Set as Panic Overlay Default",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Schedule Button
            Button(
                onClick = {
                    val finalEntry = selectedEntry ?: CallerEntry("Unknown", "+91 98765 43210")
                    viewModel.scheduleFakeCall(finalEntry, selectedDelaySeconds)
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
            ) {
                Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule Fake Call", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAddDialog) {
        AddCallerDialog(
            initialName = pendingContactName,
            initialPhone = pendingContactPhone,
            onDismiss = { showAddDialog = false },
            onSave = { name, phone ->
                viewModel.addCallerId(name, phone)
                selectedEntry = CallerEntry(name.trim(), phone.trim())
                showAddDialog = false
            },
            onPickFromContacts = {
                showAddDialog = false
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                contactPickerLauncher.launch(intent)
            }
        )
    }
}

@Composable
private fun AddCallerDialog(
    initialName: String = "",
    initialPhone: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit,
    onPickFromContacts: () -> Unit
) {
    var newCallerName by remember(initialName) { mutableStateOf(initialName) }
    var newCallerPhone by remember(initialPhone) { mutableStateOf(initialPhone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2B2B3D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Add Caller") },
        text = {
            Column {
                OutlinedTextField(
                    value = newCallerName,
                    onValueChange = { newCallerName = it },
                    label = { Text("Name", color = Color.Gray) },
                    placeholder = { Text("e.g. Mom, Boss", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00C853),
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newCallerPhone,
                    onValueChange = { newCallerPhone = it },
                    label = { Text("Phone Number", color = Color.Gray) },
                    placeholder = { Text("e.g. +91 98765 43210", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00C853),
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Pick from Contacts button — delegates to parent (sheet-level launcher)
                OutlinedButton(
                    onClick = onPickFromContacts,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick from Contacts")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(newCallerName, newCallerPhone) },
                enabled = newCallerName.isNotBlank()
            ) { Text("Save", color = if (newCallerName.isNotBlank()) Color(0xFF00C853) else Color.Gray) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

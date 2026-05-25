package com.arlabs.raksha.features.report

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arlabs.raksha.Common.*
import com.arlabs.raksha.R
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIncidentScreen(
    navController: androidx.navigation.NavController,
    modifier: Modifier = Modifier,
    viewModel: ReportIncidentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {

        // --- Bottom Sheet for Incident Type Selection ---
        if (state.isIncidentTypeSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.hideIncidentTypeSheet() },
                containerColor = Color(0xFF1A1A2E),
                contentColor = Color.White,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            ) {
                IncidentTypeSelectionSheet(
                    selectedType = state.selectedIncidentType,
                    onTypeSelected = { viewModel.onIncidentTypeSelected(it) }
                )
            }
        }

        // --- Bottom Sheet for Location Picker ---
        if (state.isLocationPickerVisible) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.hideLocationPicker() },
                containerColor = Color(0xFF1A1A2E),
                contentColor = Color.White,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            ) {
                LocationPickerSheet(
                    query = state.locationQuery,
                    suggestions = state.locationSuggestions,
                    onQueryChanged = { viewModel.onLocationQueryChanged(it) },
                    onSuggestionSelected = { viewModel.onLocationSuggestionSelected(it) },
                    onUseCurrentLocation = { viewModel.useCurrentLocation() },
                    onPickOnMap = { viewModel.showMapPicker() }
                )
            }
        }

        // --- Full-screen Map Picker Dialog ---
        if (state.isMapPickerVisible) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewModel.hideMapPicker() },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                MapPickerDialog(
                    initialLat = state.latitude ?: 28.6139,
                    initialLng = state.longitude ?: 77.2090,
                    onLocationConfirmed = { lat, lng -> viewModel.onMapLocationSelected(lat, lng) },
                    onDismiss = { viewModel.hideMapPicker() }
                )
            }
        }

        Scaffold(
            topBar = {
                AestheticTopBar(title = "Report Incident")
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = AestheticHorizontalPadding)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- Incident Type Selector ---
                Text(
                    text = "Incident Type",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                ReportInputRow(
                    icon = state.selectedIncidentType?.icon ?: Icons.Default.Warning,
                    text = state.selectedIncidentType?.title ?: "Select Incident Type",
                    onClick = { viewModel.showIncidentTypeSheet() }
                )

                // --- Location Selector ---
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Location",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                ReportInputRow(
                    icon = Icons.Default.LocationOn,
                    text = state.locationText,
                    onClick = { viewModel.showLocationPicker() }
                )

                // --- Description Text Field ---
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Description",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Optional, but recommended",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = state.description,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("Please describe the incident", color = Color.White.copy(alpha=0.4f)) },
                    shape = AestheticCornerRadius,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White,
                        unfocusedContainerColor = AestheticTransparentWhite,
                        focusedContainerColor = AestheticTransparentWhiteStrong,
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    )
                )

                // --- Severity Slider ---
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Severity Level: ${state.severity.toInt()}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Slider(
                    value = state.severity,
                    onValueChange = { viewModel.onSeverityChange(it) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                // --- Submit Button ---
                Spacer(modifier = Modifier.height(24.dp))
                
                if (!state.isVerified) {
                    GlassmorphismCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Phone Verification is required to submit a report.",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Button(
                    onClick = { 
                        if (state.isVerified) {
                            viewModel.onSubmitReport()
                        } else {
                            navController.navigate(com.arlabs.raksha.navigation.Routes.VerifyAccountScreen)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = AestheticCornerRadius,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isVerified) AestheticTransparentWhiteStrong else Color(0xFFE91E63),
                        contentColor = if (state.isVerified) Color.Black else Color.White
                    ),
                    enabled = state.selectedIncidentType != null && !state.isSubmitting
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text(if (state.isVerified) "Submit" else "Verify Phone", fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Reusable composable for clickable input rows.
 */
@Composable
private fun ReportInputRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(AestheticCornerRadius)
            .background(AestheticTransparentWhiteStrong)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.DarkGray)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = Color.Black,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(Icons.Default.EditLocationAlt, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────
// THEMED INCIDENT TYPE SELECTION SHEET
// ─────────────────────────────────────────────────────────────────────

private val categoryColors = mapOf(
    IncidentCategory.HARASSMENT to Color(0xFFE91E63),
    IncidentCategory.ENVIRONMENT to Color(0xFFFF9800),
    IncidentCategory.ACTIVITY to Color(0xFF9C27B0),
    IncidentCategory.OTHER to Color(0xFF607D8B)
)

private val categoryLabels = mapOf(
    IncidentCategory.HARASSMENT to "Harassment",
    IncidentCategory.ENVIRONMENT to "Environment",
    IncidentCategory.ACTIVITY to "Suspicious Activity",
    IncidentCategory.OTHER to "Other"
)

@Composable
private fun IncidentTypeSelectionSheet(
    selectedType: IncidentType?,
    onTypeSelected: (IncidentType) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFE91E63), Color(0xFFAD1457))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Select Incident Type",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            incidentTypes.groupBy { it.category }.forEach { (category, types) ->
                // Category header
                item {
                    val catColor = categoryColors[category] ?: Color.Gray
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(catColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = categoryLabels[category] ?: category.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = catColor,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Incident type items
                items(types) { incidentType ->
                    val isSelected = selectedType == incidentType
                    val catColor = categoryColors[incidentType.category] ?: Color.Gray

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) catColor.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .clickable { onTypeSelected(incidentType) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon with colored background circle
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(catColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = incidentType.icon,
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = incidentType.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )

                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = catColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// LOCATION PICKER SHEET
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun LocationPickerSheet(
    query: String,
    suggestions: List<LocationSuggestion>,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (LocationSuggestion) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onPickOnMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFE91E63), Color(0xFFAD1457))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Choose Location",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Use Current Location button
            OutlinedButton(
                onClick = onUseCurrentLocation,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE91E63)),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFE91E63), Color(0xFFAD1457)))
                )
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Current Location", fontSize = 13.sp)
            }

            // Pick on Map button
            OutlinedButton(
                onClick = onPickOnMap,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9800)),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFFF9800), Color(0xFFF57C00)))
                )
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick on Map", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search field
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search for a location...", color = Color.White.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color.White,
                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White
            )
        )

        // Suggestions list
        AnimatedVisibility(visible = suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .padding(top = 8.dp)
            ) {
                items(suggestions.take(5)) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionSelected(suggestion) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = suggestion.description,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// MAP PICKER DIALOG (Full-screen overlay)
// ─────────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun MapPickerDialog(
    initialLat: Double,
    initialLng: Double,
    onLocationConfirmed: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedLatLng by remember { mutableStateOf(LatLng(initialLat, initialLng)) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLng), 15f)
    }
    var isScrollEnabled by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dark gradient background
        GradientBox(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        "Tap on map to select location",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                // Map
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = true),
                        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true),
                        onMapClick = { latLng ->
                            selectedLatLng = latLng
                        }
                    ) {
                        Marker(
                            state = MarkerState(position = selectedLatLng),
                            title = "Report Location"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm button
                Button(
                    onClick = { onLocationConfirmed(selectedLatLng.latitude, selectedLatLng.longitude) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE91E63),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Location", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

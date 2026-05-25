package com.arlabs.raksha.features.safezone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arlabs.raksha.Common.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeZoneScreen(
    modifier: Modifier = Modifier,
    viewModel: SafeZoneViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit = {}
) {
    val safePlaces by viewModel.safePlaces.collectAsState()
    var selectedCategory by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val filteredPlaces = if (selectedCategory == null) safePlaces else safePlaces.filter { it.type == selectedCategory }
    
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = { AestheticTopBar(title = "Safety Hub") },
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AestheticHorizontalPadding, vertical = 8.dp)
            ) {
                // --- Safety Tips Section ---
                val safetyTips by viewModel.safetyTips.collectAsState()
                if (safetyTips.isNotEmpty()) {
                    Text(
                        text = "Safety Tips",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(safetyTips.size) { index ->
                            val tip = safetyTips[index]
                            Card(
                                modifier = Modifier.width(240.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(tip.emoji, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            tip.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        tip.description,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        lineHeight = 16.sp,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Emergency Services Grid
                Text(
                    text = "Emergency Services",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmergencyCard(
                        icon = Icons.Default.LocalPolice,
                        title = "Police",
                        count = safePlaces.count { it.type == "Police" },
                        color = Color(0xFF1E88E5),
                        isSelected = selectedCategory == "Police",
                        onClick = { selectedCategory = if (selectedCategory == "Police") null else "Police" },
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyCard(
                        icon = Icons.Default.LocalHospital,
                        title = "Hospitals",
                        count = safePlaces.count { it.type == "Hospital" },
                        color = Color(0xFFE53935),
                        isSelected = selectedCategory == "Hospital",
                        onClick = { selectedCategory = if (selectedCategory == "Hospital") null else "Hospital" },
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyCard(
                        icon = Icons.Default.LocalFireDepartment,
                        title = "Fire",
                        count = safePlaces.count { it.type == "Fire Station" },
                        color = Color(0xFFFF9800),
                        isSelected = selectedCategory == "Fire Station",
                        onClick = { selectedCategory = if (selectedCategory == "Fire Station") null else "Fire Station" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Nearby Safe Zones List
                Text(
                    text = "Nearby Safe Zones",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Finding nearby safe places...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (filteredPlaces.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No nearby safe places found for this category.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredPlaces) { place ->
                            SafePlaceItem(place) {
                                com.arlabs.raksha.features.home.SearchDataStore.prefilledDestination = place.name
                                onNavigateHome()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyCard(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(100.dp).clickable(onClick = onClick),
        shape = AestheticCornerRadius,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.2f) else AestheticTransparentWhite
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, color) else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )
            Text(
                text = "$count nearby",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SafePlaceItem(place: SafePlace, onDirectionsClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AestheticCornerRadius,
        colors = CardDefaults.cardColors(containerColor = AestheticTransparentWhiteStrong)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = when (place.type) {
                "Hospital" -> Icons.Default.LocalHospital to Color(0xFFE53935)
                "Police" -> Icons.Default.LocalPolice to Color(0xFF1E88E5)
                "Fire Station" -> Icons.Default.LocalFireDepartment to Color(0xFFFF9800)
                else -> Icons.Default.Shield to Color.DarkGray
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 15.sp
                )
                Text(
                    text = place.address,
                    color = Color.DarkGray,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = place.distance,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDirectionsClick,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha=0.2f), contentColor = color)
                ) {
                    Text("Directions", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
package com.arlabs.raksha.features.livelocation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arlabs.raksha.Common.GlassmorphismCard
import com.arlabs.raksha.Common.GradientBox
import com.arlabs.raksha.features.livelocation.data.LocationPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationViewerScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    viewModel: LiveLocationViewerViewModel = hiltViewModel()
) {
    val session by viewModel.sessionState.collectAsState()
    val points by viewModel.locationPoints.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 5f) // Default India
    }

    // Auto-center camera when a new point arrives (only if we haven't user-panned recently, or just keep centering)
    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            val lastPoint = points.last()
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(LatLng(lastPoint.lat, lastPoint.lng), 16f),
                durationMs = 1000
            )
        }
    }

    GradientBox {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (session != null) "${session?.hostName}'s Location" else "Live Location",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                } else if (session == null) {
                    // Session not found or error
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Session not found", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text("The link might be invalid or expired.", color = Color.LightGray)
                    }
                } else {
                    // Map
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
                    ) {
                        if (points.isNotEmpty()) {
                            val lastPoint = points.last()
                            Marker(
                                state = MarkerState(position = LatLng(lastPoint.lat, lastPoint.lng)),
                                title = session?.hostName,
                                snippet = "Updated just now"
                            )

                            // Polyline trail
                            if (points.size > 1) {
                                val latLngList = points.map { LatLng(it.lat, it.lng) }
                                Polyline(
                                    points = latLngList,
                                    color = Color(0xFFf40c5c), // Raksha pink
                                    width = 10f
                                )
                            }
                        }
                    }

                    // Overlay card for status
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFf40c5c).copy(alpha = 0.55f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val isExpired = System.currentTimeMillis() > (session?.expiresAt ?: 0L)
                            val isActive = session?.isActive == true && !isExpired

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(if (isActive) Color(0xFF00E676) else Color.Red, RoundedCornerShape(50))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isActive) "LIVE NOW" else "SESSION ENDED",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tracking ${session?.hostName ?: "User"}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (points.isNotEmpty()) {
                                val speedKmh = (points.last().speed * 3.6).toInt()
                                Text("Current Speed: $speedKmh km/h", color = Color.White.copy(alpha = 0.85f))

                                Spacer(modifier = Modifier.height(12.dp))

                                val lastPoint = points.last()
                                val context = androidx.compose.ui.platform.LocalContext.current

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Open in Google Maps
                                    Button(
                                        onClick = {
                                            val uri = android.net.Uri.parse("geo:${lastPoint.lat},${lastPoint.lng}?q=${lastPoint.lat},${lastPoint.lng}(Live+Location)")
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                                setPackage("com.google.android.apps.maps")
                                            }
                                            try { context.startActivity(intent) } catch (_: Exception) {
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("https://www.google.com/maps?q=${lastPoint.lat},${lastPoint.lng}")))
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Text("📍 Maps", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }

                                    // Get Directions
                                    Button(
                                        onClick = {
                                            val uri = android.net.Uri.parse("google.navigation:q=${lastPoint.lat},${lastPoint.lng}")
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                                setPackage("com.google.android.apps.maps")
                                            }
                                            try { context.startActivity(intent) } catch (_: Exception) {
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${lastPoint.lat},${lastPoint.lng}")))
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                                    ) {
                                        Text("🧭 Directions", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

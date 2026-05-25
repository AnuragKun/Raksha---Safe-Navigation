package com.arlabs.raksha.features.home

import java.util.Calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Timer
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.arlabs.raksha.Common.GradientBox
import com.arlabs.raksha.R
import com.arlabs.raksha.domain.model.Report
import com.arlabs.raksha.navigation.Routes
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arlabs.raksha.features.home.SearchDataStore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val userName by homeViewModel.userName.collectAsState()

    // Location sheets — hoisted so they can be triggered by deep-link from panic overlay
    var showLocationChoiceSheet by remember { mutableStateOf(false) }
    var showLiveLocationDurationSheet by remember { mutableStateOf(false) }

    // Handle deep-link from panic overlay "Live Location" button
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(Unit) {
        val navigateTo = activity?.intent?.getStringExtra("navigate_to")
        if (navigateTo == "live_location_share") {
            activity.intent?.removeExtra("navigate_to")
            showLiveLocationDurationSheet = true
        }
    }
    // Also handle onNewIntent via MainViewModel.pendingNavigation
    val mainViewModel: com.arlabs.raksha.MainViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
    )
    val pendingNav by mainViewModel.pendingNavigation.collectAsState()
    LaunchedEffect(pendingNav) {
        if (pendingNav == "live_location_share") {
            mainViewModel.clearPendingNavigation()
            showLiveLocationDurationSheet = true
        }
    }


    // Time-based greeting
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "Good Morning ☀\uFE0F"
            in 12..16 -> "Good Afternoon \uD83C\uDF24\uFE0F"
            in 17..20 -> "Good Evening \uD83C\uDF05"
            else -> "Good Night \uD83C\uDF19"
        }
        val personalGreetings = listOf(
            timeGreeting,
            "Welcome back! \uD83D\uDC4B",
            "Stay safe! \uD83D\uDEE1\uFE0F",
            "Hey there! \uD83C\uDF1F",
            "Hope you're doing well! \uD83D\uDE0A"
        )
        // 70% chance of time greeting, 30% chance of random personal greeting
        if ((0..9).random() < 7) timeGreeting else personalGreetings.random()
    }

    val destinationQuery by homeViewModel.searchQuery.collectAsState()
    val currentLocationQuery by homeViewModel.currentLocationQuery.collectAsState()
    val searchResults by homeViewModel.searchResults.collectAsState()
    val currentLocationResults by homeViewModel.currentLocationResults.collectAsState()
    val routeState by homeViewModel.routeState.collectAsState()
    val isShieldActive by homeViewModel.isShieldActive.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val defaultLocation = LatLng(28.6139, 77.2090)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 14f)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Track selected destination placeId for route fetching
    var selectedDestinationPlaceId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        SearchDataStore.prefilledDestination?.let { dest ->
            homeViewModel.onSearchQueryChanged(dest)
            SearchDataStore.prefilledDestination = null
        }
        
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                @SuppressLint("MissingPermission")
                val locationResult = fusedLocationClient.lastLocation
                locationResult.addOnSuccessListener { location ->
                    if (location != null) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(
                            LatLng(location.latitude, location.longitude), 15f
                        )
                        homeViewModel.setUserLocation(location.latitude, location.longitude)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Handle route errors
    LaunchedEffect(routeState.errorMessage) {
        routeState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            homeViewModel.clearRouteError()
        }
    }

    // Zoom to route when loaded
    LaunchedEffect(routeState.routePoints) {
        if (routeState.routePoints.isNotEmpty()) {
            val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
            routeState.routePoints.forEach { boundsBuilder.include(it) }
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
        }
    }

    val mapUiSettings by remember(hasLocationPermission) {
        mutableStateOf(MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = hasLocationPermission))
    }
    
    var isScrollEnabled by remember { mutableStateOf(true) }

    // SOS state
    var showSosDialog by remember { mutableStateOf(false) }
    val isSosSending by homeViewModel.isSosSending.collectAsState()
    val sosResultMessage by homeViewModel.sosResultMessage.collectAsState()

    // SMS permission
    val hasSmsPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSmsPermission.value = granted
        if (granted) {
            showSosDialog = true
        }
    }

    // SOS result snackbar
    LaunchedEffect(sosResultMessage) {
        sosResultMessage?.let { msg ->
            homeViewModel.clearSosResult()
            snackbarHostState.showSnackbar(msg)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Header Row as pinned TopBar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = com.arlabs.raksha.Common.AestheticHorizontalPadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(greeting, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(userName.ifEmpty { "User" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (hasSmsPermission.value) {
                                    showSosDialog = true
                                } else {
                                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        ) {
                            if (isSosSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Warning, contentDescription = "Emergency SOS", tint = Color.White)
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isShieldActive) "Shield ON" else "Shield OFF",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (isShieldActive) Color.Green else Color.White.copy(alpha = 0.7f)
                            )
                            androidx.compose.material3.Switch(
                                checked = isShieldActive,
                                onCheckedChange = {
                                    navController.navigate(Routes.SafetyTimerScreen)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White, checkedTrackColor = Color.Green,
                                    uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },

        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState(), enabled = isScrollEnabled)
                    .padding(horizontal = com.arlabs.raksha.Common.AestheticHorizontalPadding, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Find Your Safe Path", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                // Starting Point Input
                Box(modifier = Modifier.fillMaxWidth().zIndex(2f)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentLocationQuery,
                                onValueChange = { homeViewModel.onCurrentLocationQueryChanged(it) },
                                label = { Text("Starting Point", color = Color.White.copy(alpha = 0.7f)) },
                                leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedContainerColor = com.arlabs.raksha.Common.AestheticTransparentWhite,
                                    unfocusedContainerColor = com.arlabs.raksha.Common.AestheticTransparentWhite,
                                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                    cursorColor = Color.White, focusedLabelColor = Color.White,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                                ),
                                singleLine = true,
                                shape = com.arlabs.raksha.Common.AestheticCornerRadius
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Current Location button
                            IconButton(
                                onClick = {
                                    if (hasLocationPermission) {
                                        @SuppressLint("MissingPermission")
                                        val loc = fusedLocationClient.lastLocation
                                        loc.addOnSuccessListener { location ->
                                            if (location != null) {
                                                homeViewModel.setUserLocation(location.latitude, location.longitude)
                                                homeViewModel.onCurrentLocationQueryChanged(
                                                    "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
                                                )
                                                homeViewModel.clearCurrentLocationSuggestions()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Use current location",
                                    tint = Color.White
                                )
                            }
                        }
                        // Suggestions: show whenever list is non-empty
                        AnimatedVisibility(visible = currentLocationResults.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column {
                                    currentLocationResults.take(5).forEach { prediction ->
                                        Text(
                                            text = prediction.description,
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable {
                                                    homeViewModel.onCurrentLocationQueryChanged(prediction.description)
                                                    homeViewModel.clearCurrentLocationSuggestions()
                                                }
                                                .padding(12.dp),
                                            color = Color.Black, fontSize = 14.sp
                                        )
                                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Destination Input
                Box(modifier = Modifier.fillMaxWidth().zIndex(1f)) {
                    Column {
                        OutlinedTextField(
                            value = destinationQuery,
                            onValueChange = {
                                homeViewModel.onSearchQueryChanged(it)
                                selectedDestinationPlaceId = null
                            },
                            label = { Text("Destination", color = Color.White.copy(alpha = 0.7f)) },
                            leadingIcon = { Icon(Icons.Default.AddLocation, contentDescription = null, tint = Color.White) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedContainerColor = com.arlabs.raksha.Common.AestheticTransparentWhite,
                                unfocusedContainerColor = com.arlabs.raksha.Common.AestheticTransparentWhite,
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White, focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                            ),
                            singleLine = true,
                            shape = com.arlabs.raksha.Common.AestheticCornerRadius
                        )
                        AnimatedVisibility(visible = searchResults.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column {
                                    searchResults.take(5).forEach { prediction ->
                                        Text(
                                            text = prediction.description,
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable {
                                                    homeViewModel.onSearchQueryChanged(prediction.description)
                                                    selectedDestinationPlaceId = prediction.placeId
                                                    homeViewModel.clearDestinationSuggestions()
                                                }
                                                .padding(12.dp),
                                            color = Color.Black, fontSize = 14.sp
                                        )
                                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Map Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val mapTouched = event.changes.any { it.pressed }
                                    isScrollEnabled = !mapTouched
                                }
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                        uiSettings = mapUiSettings
                    ) {
                        val tileProvider by homeViewModel.heatmapTileProvider.collectAsState()
                        tileProvider?.let { TileOverlay(tileProvider = it) }
                        if (routeState.routePoints.isNotEmpty()) {
                            Polyline(points = routeState.routePoints, color = Color(0xFF2196F3), width = 12f)
                        }
                        // Danger hotspot circles on map
                        routeState.routeDangerReports.forEach { report ->
                            Circle(
                                center = LatLng(report.latitude, report.longitude),
                                radius = 150.0,
                                fillColor = Color(0xFFE53935).copy(alpha = 0.2f),
                                strokeColor = Color(0xFFE53935).copy(alpha = 0.6f),
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                // Route info card with transport mode selector
                AnimatedVisibility(visible = routeState.routePoints.isNotEmpty()) {
                    val selectedMode by homeViewModel.selectedTransportMode.collectAsState()
                    var showJourneyTimerDialog by remember { mutableStateOf(false) }

                    Column {
                        // --- Safety Score Header ---
                        val safetyScore = routeState.safetyScore
                        val scoreColor = when {
                            safetyScore >= 80 -> Color(0xFF4CAF50)
                            safetyScore >= 50 -> Color(0xFFFFA726)
                            else -> Color(0xFFE53935)
                        }
                        val scoreLabel = when {
                            safetyScore >= 80 -> "Safe Route"
                            safetyScore >= 50 -> "Use Caution"
                            else -> "High Risk"
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xF01A1A2E))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Score + Distance + Duration row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Safety score badge — solid dark circle with colored border
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0D0D1A))
                                            .then(
                                                Modifier
                                                    .size(60.dp)
                                                    .clip(CircleShape)
                                                    .background(scoreColor.copy(alpha = 0.15f))
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "$safetyScore",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = scoreColor
                                            )
                                            Text(
                                                "/100",
                                                fontSize = 9.sp,
                                                color = scoreColor.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            scoreLabel,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = scoreColor
                                        )
                                        Row {
                                            Text(
                                                "📍 ${routeState.distance}",
                                                fontSize = 13.sp,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                "⏱ ${routeState.duration}",
                                                fontSize = 13.sp,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { homeViewModel.clearRoute() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.5f))
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // --- Transport Mode Selector (pill-style) ---
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    val modes = listOf(
                                        Triple("driving", "🚗", "Car"),
                                        Triple("two_wheeler", "🏍️", "Bike"),
                                        Triple("walking", "🚶", "Walk")
                                    )
                                    modes.forEach { (modeKey, emoji, label) ->
                                        val isSelected = selectedMode == modeKey
                                        val modeInfo = routeState.modeRoutes[modeKey]
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) Color.White.copy(alpha = 0.15f)
                                                    else Color.Transparent
                                                )
                                                .clickable { homeViewModel.selectTransportMode(modeKey) }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(emoji, fontSize = 20.sp)
                                            Text(
                                                label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                                            )
                                            if (modeInfo != null) {
                                                Text(
                                                    modeInfo.duration,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                                                )
                                                Text(
                                                    modeInfo.distance,
                                                    fontSize = 9.sp,
                                                    color = Color.White.copy(alpha = 0.4f)
                                                )
                                            } else {
                                                Text(
                                                    "N/A",
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }

                                // --- Incident Breakdown ---
                                val dangerReports = routeState.routeDangerReports
                                if (dangerReports.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "⚠️ ${dangerReports.size} incident${if (dangerReports.size > 1) "s" else ""} along this route",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFFAB91)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Incident type chips
                                    val grouped = dangerReports.groupBy { it.incidentType }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        grouped.entries.take(4).forEach { (type, reports) ->
                                            val chipColor = when {
                                                type.contains("Harassment", ignoreCase = true) ||
                                                type.contains("Assault", ignoreCase = true) -> Color(0xFFE53935)
                                                type.contains("Stalked", ignoreCase = true) ||
                                                type.contains("Suspicious", ignoreCase = true) -> Color(0xFFFF9800)
                                                else -> Color(0xFFFFC107)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(chipColor.copy(alpha = 0.3f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    "${type.take(15)} ×${reports.size}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "✅ No incidents reported along this route",
                                        fontSize = 12.sp,
                                        color = Color(0xFF81C784)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // --- Time-based safety tip ---
                                val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                                if (hour in 20..23 || hour in 0..5) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF311B92).copy(alpha = 0.3f))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🌙", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "It's late — consider sharing your live location with someone you trust.",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.8f),
                                            lineHeight = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // --- Navigate button ---
                                Button(
                                    onClick = { showJourneyTimerDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Navigate in Google Maps", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // --- Journey Timer Dialog ---
                    if (showJourneyTimerDialog) {
                        val etaMinutes = homeViewModel.parseDurationMinutes(routeState.duration)
                        val bufferMinutes = 10
                        val totalMinutes = etaMinutes + bufferMinutes

                        AlertDialog(
                            onDismissRequest = { showJourneyTimerDialog = false },
                            containerColor = Color(0xFF2D1B3D),
                            titleContentColor = Color.White,
                            textContentColor = Color.White.copy(alpha = 0.85f),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🛡️", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Safety Timer?", fontWeight = FontWeight.Bold)
                                }
                            },
                            text = {
                                Text(
                                    "Your trip is estimated at $etaMinutes min.\n\n" +
                                    "Would you like to start a safety timer for $totalMinutes min " +
                                    "(ETA + ${bufferMinutes} min buffer)?\n\n" +
                                    "If you don't check in, your emergency contacts will be notified."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showJourneyTimerDialog = false
                                        homeViewModel.startJourneyTimer(totalMinutes)
                                        // Launch navigation
                                        launchGoogleMapsNavigation(
                                            context = context,
                                            destQuery = destinationQuery,
                                            selectedMode = selectedMode,
                                            routeState = routeState
                                        )
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
                                ) {
                                    Text("START TIMER & NAVIGATE", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showJourneyTimerDialog = false
                                        // Navigate without timer
                                        launchGoogleMapsNavigation(
                                            context = context,
                                            destQuery = destinationQuery,
                                            selectedMode = selectedMode,
                                            routeState = routeState
                                        )
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
                                ) {
                                    Text("JUST NAVIGATE")
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start Navigation button
                Button(
                    onClick = { selectedDestinationPlaceId?.let { homeViewModel.fetchRoute(it) } },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    enabled = selectedDestinationPlaceId != null && !routeState.isLoading
                ) {
                    if (routeState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    } else {
                        Text("Start Navigation", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))


                // (showLocationChoiceSheet / showLiveLocationDurationSheet are hoisted above)

                // Share Location button — FUNCTIONAL with Live Location extension
                Button(
                    onClick = { showLocationChoiceSheet = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sharelocation),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Share Location", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Panic overlay toggle
                var isPanicOverlayActive by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
                Button(
                    onClick = {
                        if (!android.provider.Settings.canDrawOverlays(context)) {
                            // Request overlay permission
                            val intent = Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else if (isPanicOverlayActive) {
                            com.arlabs.raksha.services.PanicOverlayService.stop(context)
                            isPanicOverlayActive = false
                        } else {
                            com.arlabs.raksha.services.PanicOverlayService.start(context)
                            isPanicOverlayActive = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPanicOverlayActive) Color(0xFFE53935) else Color(0xFF311B92),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (isPanicOverlayActive) "🛡️ Panic Button Active — Tap to Disable"
                        else "🛡️ Enable Panic Overlay",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fake Call button
                var showFakeCallSheet by remember { mutableStateOf(false) }
                Button(
                    onClick = { showFakeCallSheet = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853), contentColor = Color.Black)
                ) {
                    Text("Schedule Fake Call", fontWeight = FontWeight.Bold, color = Color.Black)
                }

                if (showFakeCallSheet) {
                    com.arlabs.raksha.features.fakecall.FakeCallTriggerSheet(
                        onDismissRequest = { showFakeCallSheet = false }
                    )
                }

                if (showLocationChoiceSheet) {
                    com.arlabs.raksha.features.livelocation.ui.LocationShareChoiceSheet(
                        onDismiss = { showLocationChoiceSheet = false },
                        onCurrentLocationSelected = {
                            showLocationChoiceSheet = false
                            val lat = homeViewModel.userLat
                            val lng = homeViewModel.userLng
                            val mapLink = "https://maps.google.com/?q=$lat,$lng"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "My Current Location \u2014 Raksha")
                                putExtra(Intent.EXTRA_TEXT, "Here is my current location:\n$mapLink")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Location via"))
                        },
                        onLiveLocationSelected = {
                            showLocationChoiceSheet = false
                            showLiveLocationDurationSheet = true
                        }
                    )
                }

                if (showLiveLocationDurationSheet) {
                    com.arlabs.raksha.features.livelocation.ui.LiveLocationDurationSheet(
                        context = context,
                        onDismiss = { showLiveLocationDurationSheet = false }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- Nearby Incident Feed ---
                val allReports by homeViewModel.reports.collectAsState()
                val nearbyReports = remember(allReports, homeViewModel.userLat, homeViewModel.userLng) {
                    val now = System.currentTimeMillis()
                    val oneDayMs = 24 * 60 * 60 * 1000L
                    allReports
                        .filter { (now - it.timestamp) < 7 * oneDayMs } // Last 7 days
                        .map { report ->
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(
                                homeViewModel.userLat, homeViewModel.userLng,
                                report.latitude, report.longitude, results
                            )
                            report to results[0].toDouble()
                        }
                        .filter { it.second <= 5000.0 } // Within 5km
                        .sortedByDescending { it.first.timestamp }
                        .take(5)
                }

                if (nearbyReports.isNotEmpty()) {
                    NearbyAlertsCard(
                        reports = nearbyReports
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // SOS Confirmation Dialog
        if (showSosDialog) {
            AlertDialog(
                onDismissRequest = { showSosDialog = false },
                containerColor = Color(0xFF2D1B3D),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.85f),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Emergency SOS", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            "This will send an emergency SMS with your current location to ALL your emergency contacts.\n\n" +
                            "SMS charges may apply.\n\n" +
                            "Are you sure you want to proceed?"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Make sure you've added emergency contacts in your Profile screen.",
                                fontSize = 12.sp,
                                color = Color(0xFFFFCC80),
                                lineHeight = 16.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSosDialog = false
                            homeViewModel.triggerSos()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("SEND SOS", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSosDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Helper to launch Google Maps navigation with proximity alerts and battery optimization.
 */
private fun launchGoogleMapsNavigation(
    context: android.content.Context,
    destQuery: String,
    selectedMode: String,
    routeState: RouteState
) {
    val gmapsMode = when (selectedMode) {
        "driving" -> "d"
        "two_wheeler" -> "d" // Two-wheelers use driving mode
        "walking" -> "w"
        else -> "d"
    }

    // Start proximity alert service
    if (routeState.routePoints.isNotEmpty()) {
        val lastPoint = routeState.routePoints.last()
        com.arlabs.raksha.services.ProximityAlertService.start(
            context = context,
            destLat = lastPoint.latitude,
            destLng = lastPoint.longitude,
            routePoints = routeState.routePoints
        )
    }

    // Request battery optimization exemption
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            try {
                val batteryIntent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(batteryIntent)
            } catch (_: Exception) {}
        }
    }

    val mapUri = android.net.Uri.parse("google.navigation:q=${android.net.Uri.encode(destQuery)}&mode=$gmapsMode")
    val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, mapUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(mapIntent)
    } catch (e: Exception) {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://maps.google.com/?daddr=${android.net.Uri.encode(destQuery)}&dirflg=$gmapsMode")
            )
        )
    }
}

// ─── Nearby Incident Feed ──────────────────────────────────────────

@Composable
private fun NearbyAlertsCard(
    reports: List<Pair<Report, Double>>
) {
    val incidentEmoji = mapOf(
        "Harassment" to "🚨", "Suspicious Activity" to "👁️",
        "Poor Lighting" to "🌑", "Theft" to "💰",
        "Assault" to "⚠️", "Vandalism" to "🔨",
        "Stalking" to "👤", "Drug Activity" to "💊",
        "Other" to "📍"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📢 Nearby Alerts",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "${reports.size} recent",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            reports.forEach { (report, distMeters) ->
                val emoji = incidentEmoji[report.incidentType] ?: "📍"
                val ago = formatTimeAgo(report.timestamp)
                val distText = if (distMeters < 1000) {
                    "${distMeters.toInt()}m away"
                } else {
                    "%.1f km away".format(distMeters / 1000.0)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            report.incidentType,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            report.description.take(60).ifEmpty { "Reported incident" },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Time + distance
                    Column(horizontalAlignment = Alignment.End) {
                        Text(ago, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        Text(distText, fontSize = 11.sp, color = Color(0xFFFF6B6B))
                    }
                }
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val mins = diffMs / 60_000
    val hours = mins / 60
    val days = hours / 24
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
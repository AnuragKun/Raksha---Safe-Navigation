package com.arlabs.raksha.features.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlabs.raksha.data.remote.DirectionsApiService
import com.arlabs.raksha.domain.model.Report
import com.arlabs.raksha.domain.repository.ReportRepository
import com.arlabs.raksha.domain.repository.TimerRepository
import com.arlabs.raksha.services.ProximityAlertService
import java.util.Calendar
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class RouteInfo(
    val distance: String = "",
    val duration: String = ""
)

data class RouteState(
    val routePoints: List<LatLng> = emptyList(),
    val distance: String = "",
    val duration: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val modeRoutes: Map<String, RouteInfo> = emptyMap(), // "driving", "two_wheeler", "walking"
    val routeDangerReports: List<Report> = emptyList(),
    val safetyScore: Int = 100
)

data class PlacePrediction(
    val description: String,
    val placeId: String
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reportRepository: ReportRepository,
    private val directionsApiService: DirectionsApiService,
    private val timerRepository: TimerRepository,
    private val userPreferencesRepository: com.arlabs.raksha.domain.repository.UserPreferencesRepository,
    private val sosManager: com.arlabs.raksha.services.SosManager
) : ViewModel() {

    // User name from DataStore
    val userName: StateFlow<String> = userPreferencesRepository.userData
        .map { it.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PlacePrediction>>(emptyList())
    val searchResults: StateFlow<List<PlacePrediction>> = _searchResults.asStateFlow()

    private val _currentLocationQuery = MutableStateFlow("")
    val currentLocationQuery: StateFlow<String> = _currentLocationQuery.asStateFlow()

    private val _currentLocationResults = MutableStateFlow<List<PlacePrediction>>(emptyList())
    val currentLocationResults: StateFlow<List<PlacePrediction>> = _currentLocationResults.asStateFlow()

    private val _heatmapTileProvider = MutableStateFlow<HeatmapTileProvider?>(null)
    val heatmapTileProvider: StateFlow<HeatmapTileProvider?> = _heatmapTileProvider.asStateFlow()

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()

    private val _routeState = MutableStateFlow(RouteState())
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()

    // Selected transport mode
    private val _selectedTransportMode = MutableStateFlow("driving")
    val selectedTransportMode: StateFlow<String> = _selectedTransportMode.asStateFlow()

    // Cached destination LatLng for mode switching
    private var cachedDestLat: Double = 0.0
    private var cachedDestLng: Double = 0.0

    // Shield state = timer active
    val isShieldActive: StateFlow<Boolean> = timerRepository.isTimerActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // User location
    var userLat: Double = 28.6139
        private set
    var userLng: Double = 77.2090
        private set

    // SOS state
    private val _isSosSending = MutableStateFlow(false)
    val isSosSending: StateFlow<Boolean> = _isSosSending.asStateFlow()

    private val _sosResultMessage = MutableStateFlow<String?>(null)
    val sosResultMessage: StateFlow<String?> = _sosResultMessage.asStateFlow()

    fun triggerSos() {
        viewModelScope.launch {
            _isSosSending.value = true
            val (success, message) = sosManager.sendSosToAllContacts()
            _sosResultMessage.value = message
            _isSosSending.value = false
        }
    }

    fun clearSosResult() {
        _sosResultMessage.value = null
    }

    private var placesClient: PlacesClient? = null
    private var apiKey: String = ""

    init {
        initializePlacesClient()
        loadReportsForArea(userLat, userLng)
    }

    private fun initializePlacesClient() {
        try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName, android.content.pm.PackageManager.GET_META_DATA
            )
            apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
            Log.d(TAG, "API key loaded: ${if (apiKey.isNotEmpty()) "${apiKey.take(8)}..." else "EMPTY"}")

            if (Places.isInitialized()) {
                placesClient = Places.createClient(context)
                Log.d(TAG, "Places client created successfully")
            } else {
                Log.e(TAG, "Places SDK NOT initialized — check RakshaApplication.kt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Places", e)
        }
    }

    fun setUserLocation(lat: Double, lng: Double) {
        userLat = lat
        userLng = lng
        loadReportsForArea(lat, lng)
    }

    fun loadReportsForArea(centerLat: Double, centerLng: Double) {
        viewModelScope.launch {
            reportRepository.getReportsInArea(
                centerLat = centerLat,
                centerLng = centerLng,
                radiusDegrees = 0.05
            ).collectLatest { reportsList ->
                _reports.value = reportsList
                buildHeatmap(reportsList)
            }
        }
    }

    private fun buildHeatmap(reports: List<Report>) {
        try {
            if (reports.isNotEmpty()) {
                val weightedPoints = reports.map { report ->
                    val weight = (report.severity / 10.0).coerceIn(0.1, 1.0)
                    WeightedLatLng(LatLng(report.latitude, report.longitude), weight)
                }
                _heatmapTileProvider.value = HeatmapTileProvider.Builder()
                    .weightedData(weightedPoints)
                    .radius(50)
                    .build()
            } else {
                _heatmapTileProvider.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build heatmap", e)
        }
    }

    fun onMapCameraMoved(lat: Double, lng: Double) {
        val distance = Math.sqrt(Math.pow(lat - userLat, 2.0) + Math.pow(lng - userLng, 2.0))
        if (distance > 0.01) {
            loadReportsForArea(lat, lng)
        }
    }

    // ---------- DESTINATION Search ----------

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) {
            searchPlaces(query, isDestination = true)
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun clearDestinationSuggestions() {
        _searchResults.value = emptyList()
    }

    // ---------- CURRENT LOCATION Search ----------

    fun onCurrentLocationQueryChanged(query: String) {
        _currentLocationQuery.value = query
        if (query.length >= 3) {
            searchPlaces(query, isDestination = false)
        } else {
            _currentLocationResults.value = emptyList()
        }
    }

    fun clearCurrentLocationSuggestions() {
        _currentLocationResults.value = emptyList()
    }

    // ---------- Places Autocomplete ----------

    private fun searchPlaces(query: String, isDestination: Boolean) {
        val client = placesClient
        if (client == null) {
            Log.e(TAG, "searchPlaces called but placesClient is null!")
            return
        }

        // Create a fresh session token for each search burst
        val token = AutocompleteSessionToken.newInstance()

        viewModelScope.launch {
            try {
                val request = FindAutocompletePredictionsRequest.builder()
                    .setSessionToken(token)
                    .setQuery(query)
                    .setCountries("IN")
                    .build()

                Log.d(TAG, "Searching for: '$query' (destination=$isDestination)")
                val response = client.findAutocompletePredictions(request).await()
                val predictions = response.autocompletePredictions.map {
                    PlacePrediction(
                        description = it.getFullText(null).toString(),
                        placeId = it.placeId
                    )
                }
                Log.d(TAG, "Got ${predictions.size} results for '$query'")

                if (isDestination) {
                    _searchResults.value = predictions
                } else {
                    _currentLocationResults.value = predictions
                }
            } catch (e: Exception) {
                Log.e(TAG, "Places autocomplete FAILED for '$query'", e)
                if (isDestination) {
                    _searchResults.value = emptyList()
                } else {
                    _currentLocationResults.value = emptyList()
                }
            }
        }
    }

    // ---------- Route Directions ----------

    fun fetchRoute(destinationPlaceId: String) {
        if (apiKey.isEmpty()) {
            _routeState.value = _routeState.value.copy(errorMessage = "API key not configured")
            return
        }

        viewModelScope.launch {
            _routeState.value = _routeState.value.copy(isLoading = true, errorMessage = null)
            try {
                val client = placesClient ?: throw Exception("Places client not initialized")
                val placeFields = listOf(Place.Field.LAT_LNG)
                val fetchRequest = FetchPlaceRequest.newInstance(destinationPlaceId, placeFields)
                val placeResponse = client.fetchPlace(fetchRequest).await()
                val destLatLng = placeResponse.place.latLng
                    ?: throw Exception("Could not get destination coordinates")

                cachedDestLat = destLatLng.latitude
                cachedDestLng = destLatLng.longitude

                val selectedMode = _selectedTransportMode.value
                val apiMode = if (selectedMode == "two_wheeler") "driving" else selectedMode
                val apiAvoid = if (selectedMode == "two_wheeler") "highways" else null

                // Fetch route for selected mode (for polyline)
                val primaryResult = directionsApiService.getDirections(
                    originLat = userLat,
                    originLng = userLng,
                    destLat = destLatLng.latitude,
                    destLng = destLatLng.longitude,
                    apiKey = apiKey,
                    mode = apiMode,
                    avoid = apiAvoid
                )

                // Fetch all modes in parallel for the info panel
                val modes = listOf("driving", "two_wheeler", "walking")
                val modeResults = mutableMapOf<String, RouteInfo>()
                
                for (mode in modes) {
                    try {
                        val mApiMode = if (mode == "two_wheeler") "driving" else mode
                        val mApiAvoid = if (mode == "two_wheeler") "highways" else null
                        val result = if (mode == selectedMode) {
                            primaryResult
                        } else {
                            directionsApiService.getDirections(
                                originLat = userLat,
                                originLng = userLng,
                                destLat = destLatLng.latitude,
                                destLng = destLatLng.longitude,
                                apiKey = apiKey,
                                mode = mApiMode,
                                avoid = mApiAvoid
                            )
                        }
                        if (result.points.isNotEmpty()) {
                            modeResults[mode] = RouteInfo(
                                distance = result.distance,
                                duration = result.duration
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch $mode route", e)
                    }
                }

                if (primaryResult.points.isEmpty()) {
                    _routeState.value = _routeState.value.copy(
                        isLoading = false, 
                        errorMessage = "No route found",
                        modeRoutes = modeResults
                    )
                } else {
                    // Compute danger reports along route + safety score
                    val dangerReports = computeRouteDangerReports(primaryResult.points)
                    val score = computeSafetyScore(dangerReports)

                    _routeState.value = RouteState(
                        routePoints = primaryResult.points,
                        distance = primaryResult.distance,
                        duration = primaryResult.duration,
                        isLoading = false,
                        modeRoutes = modeResults,
                        routeDangerReports = dangerReports,
                        safetyScore = score
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch route", e)
                _routeState.value = _routeState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to get directions"
                )
            }
        }
    }

    fun selectTransportMode(mode: String) {
        _selectedTransportMode.value = mode
        // Re-fetch the route with the new mode if we have cached coordinates
        if (cachedDestLat != 0.0 && cachedDestLng != 0.0) {
            viewModelScope.launch {
                _routeState.value = _routeState.value.copy(isLoading = true)
                try {
                    val apiMode = if (mode == "two_wheeler") "driving" else mode
                    val apiAvoid = if (mode == "two_wheeler") "highways" else null
                    val result = directionsApiService.getDirections(
                        originLat = userLat,
                        originLng = userLng,
                        destLat = cachedDestLat,
                        destLng = cachedDestLng,
                        apiKey = apiKey,
                        mode = apiMode,
                        avoid = apiAvoid
                    )
                    if (result.points.isNotEmpty()) {
                        val dangerReports = computeRouteDangerReports(result.points)
                        val score = computeSafetyScore(dangerReports)
                        _routeState.value = _routeState.value.copy(
                            routePoints = result.points,
                            distance = result.distance,
                            duration = result.duration,
                            isLoading = false,
                            routeDangerReports = dangerReports,
                            safetyScore = score
                        )
                    } else {
                        _routeState.value = _routeState.value.copy(
                            isLoading = false,
                            errorMessage = "No route found for ${if (mode == "two_wheeler") "Bike" else mode}"
                        )
                    }
                } catch (e: Exception) {
                    _routeState.value = _routeState.value.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    fun clearRoute() {
        _routeState.value = RouteState()
    }

    fun clearRouteError() {
        _routeState.value = _routeState.value.copy(errorMessage = null)
    }

    // ---------- Safety Score Computation ----------

    private fun computeRouteDangerReports(routePoints: List<LatLng>): List<Report> {
        val allReports = _reports.value
        if (allReports.isEmpty() || routePoints.isEmpty()) return emptyList()

        return allReports.filter { report ->
            routePoints.any { point ->
                ProximityAlertService.distanceBetween(
                    point.latitude, point.longitude,
                    report.latitude, report.longitude
                ) < 200.0 // 200m from route
            }
        }
    }

    private fun computeSafetyScore(dangerReports: List<Report>): Int {
        // Base 100, subtract for each report (weighted by severity)
        val incidentPenalty = dangerReports.sumOf { (it.severity * 8).toInt() }.coerceAtMost(70)
        // Night time penalty
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timePenalty = if (hour in 22..23 || hour in 0..4) 15 else 0
        return (100 - incidentPenalty - timePenalty).coerceIn(0, 100)
    }

    // ---------- Journey Timer ----------

    fun parseDurationMinutes(durationStr: String): Int {
        // Parse "25 mins", "1 hour 12 mins", etc.
        var totalMinutes = 0
        val hourMatch = Regex("(\\d+)\\s*hour").find(durationStr)
        val minMatch = Regex("(\\d+)\\s*min").find(durationStr)
        if (hourMatch != null) totalMinutes += (hourMatch.groupValues[1].toIntOrNull() ?: 0) * 60
        if (minMatch != null) totalMinutes += minMatch.groupValues[1].toIntOrNull() ?: 0
        return if (totalMinutes > 0) totalMinutes else 15 // fallback 15 min
    }

    fun startJourneyTimer(minutes: Int) {
        viewModelScope.launch {
            try {
                timerRepository.startTimer(minutes)
                // Start the timer foreground service
                val intent = Intent(context, com.arlabs.raksha.services.TimerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start journey timer", e)
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}

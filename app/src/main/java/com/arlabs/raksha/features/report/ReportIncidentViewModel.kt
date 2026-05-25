package com.arlabs.raksha.features.report


import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject

data class LocationSuggestion(
    val description: String,
    val placeId: String
)

// Data class to hold the entire state of the Report screen
data class ReportIncidentState(
    val selectedIncidentType: IncidentType? = null,
    val locationText: String = "Using Current Location",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String = "",
    val severity: Float = 5f,
    val isIncidentTypeSheetVisible: Boolean = false,
    val isLocationPickerVisible: Boolean = false,
    val isMapPickerVisible: Boolean = false,
    val locationQuery: String = "",
    val locationSuggestions: List<LocationSuggestion> = emptyList(),
    val isSubmitting: Boolean = false,
    val isVerified: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportIncidentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reportRepository: com.arlabs.raksha.domain.repository.ReportRepository,
    private val fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    private val userPreferencesRepository: com.arlabs.raksha.domain.repository.UserPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportIncidentState())
    val state = _state.asStateFlow()

    private var placesClient: PlacesClient? = null

    init {
        fetchCurrentLocation()
        observeVerificationStatus()
        initializePlacesClient()
    }

    private fun initializePlacesClient() {
        try {
            if (Places.isInitialized()) {
                placesClient = Places.createClient(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Places client", e)
        }
    }

    private fun observeVerificationStatus() {
        viewModelScope.launch {
            userPreferencesRepository.userData.collect { userData ->
                _state.update { it.copy(isVerified = userData.isVerified) }
            }
        }
    }

    private fun fetchCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val addressText = reverseGeocode(location.latitude, location.longitude)
                    _state.update {
                        it.copy(
                            locationText = addressText ?: "📍 ${String.format(Locale.US, "%.4f", location.latitude)}, ${String.format(Locale.US, "%.4f", location.longitude)}",
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            _state.update { it.copy(locationText = "Location Permission Denied") }
        } catch (e: Exception) {
             _state.update { it.copy(locationText = "Location Unavailable") }
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.let { addr ->
                val parts = listOfNotNull(
                    addr.thoroughfare,
                    addr.subLocality,
                    addr.locality
                )
                if (parts.isNotEmpty()) parts.joinToString(", ") else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // --- Location Picker ---

    fun showLocationPicker() {
        _state.update { it.copy(isLocationPickerVisible = true, locationQuery = "") }
    }

    fun hideLocationPicker() {
        _state.update { it.copy(isLocationPickerVisible = false, locationSuggestions = emptyList()) }
    }

    fun showMapPicker() {
        _state.update { it.copy(isMapPickerVisible = true, isLocationPickerVisible = false) }
    }

    fun hideMapPicker() {
        _state.update { it.copy(isMapPickerVisible = false) }
    }

    fun useCurrentLocation() {
        fetchCurrentLocation()
        _state.update { it.copy(isLocationPickerVisible = false, isMapPickerVisible = false) }
    }

    fun onLocationQueryChanged(query: String) {
        _state.update { it.copy(locationQuery = query) }
        if (query.length >= 3) {
            searchLocationPlaces(query)
        } else {
            _state.update { it.copy(locationSuggestions = emptyList()) }
        }
    }

    private fun searchLocationPlaces(query: String) {
        val client = placesClient ?: return
        val token = AutocompleteSessionToken.newInstance()

        viewModelScope.launch {
            try {
                val request = FindAutocompletePredictionsRequest.builder()
                    .setSessionToken(token)
                    .setQuery(query)
                    .setCountries("IN")
                    .build()

                val response = client.findAutocompletePredictions(request).await()
                val suggestions = response.autocompletePredictions.map {
                    LocationSuggestion(
                        description = it.getFullText(null).toString(),
                        placeId = it.placeId
                    )
                }
                _state.update { it.copy(locationSuggestions = suggestions) }
            } catch (e: Exception) {
                Log.e(TAG, "Location search failed", e)
                _state.update { it.copy(locationSuggestions = emptyList()) }
            }
        }
    }

    fun onLocationSuggestionSelected(suggestion: LocationSuggestion) {
        val client = placesClient ?: return

        viewModelScope.launch {
            try {
                val placeFields = listOf(Place.Field.LAT_LNG, Place.Field.NAME)
                val request = FetchPlaceRequest.newInstance(suggestion.placeId, placeFields)
                val response = client.fetchPlace(request).await()
                val latLng = response.place.latLng

                if (latLng != null) {
                    _state.update {
                        it.copy(
                            locationText = suggestion.description,
                            latitude = latLng.latitude,
                            longitude = latLng.longitude,
                            isLocationPickerVisible = false,
                            locationSuggestions = emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch place details", e)
            }
        }
    }

    fun onMapLocationSelected(lat: Double, lng: Double) {
        val addressText = reverseGeocode(lat, lng)
        _state.update {
            it.copy(
                locationText = addressText ?: "📍 ${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lng)}",
                latitude = lat,
                longitude = lng,
                isMapPickerVisible = false
            )
        }
    }

    // --- Incident Type ---

    fun onDescriptionChange(text: String) {
        _state.update { currentState ->
            currentState.copy(description = text)
        }
    }

    fun onSeverityChange(severity: Float) {
        _state.update { currentState ->
            currentState.copy(severity = severity)
        }
    }

    fun showIncidentTypeSheet() {
        _state.update { currentState ->
            currentState.copy(isIncidentTypeSheetVisible = true)
        }
    }

    fun hideIncidentTypeSheet() {
        _state.update { currentState ->
            currentState.copy(isIncidentTypeSheetVisible = false)
        }
    }

    fun onIncidentTypeSelected(incidentType: IncidentType) {
        _state.update { currentState ->
            currentState.copy(selectedIncidentType = incidentType)
        }
        hideIncidentTypeSheet()
    }

    fun clearSubmitSuccess() {
        _state.update { it.copy(submitSuccess = false) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun onSubmitReport() {
        val currentState = _state.value
        if (currentState.selectedIncidentType == null) return
        if (currentState.latitude == null || currentState.longitude == null) {
            _state.update { it.copy(errorMessage = "Location not available. Please enable GPS.") }
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            
            val result = reportRepository.submitReport(
                type = currentState.selectedIncidentType.title,
                category = currentState.selectedIncidentType.category.name,
                description = currentState.description,
                severity = currentState.severity,
                latitude = currentState.latitude,
                longitude = currentState.longitude,
                timestamp = System.currentTimeMillis()
            )
            
            _state.update { it.copy(isSubmitting = false) }
            
            when (result) {
                is com.arlabs.raksha.domain.util.Result.Success -> {
                    _state.update {
                        it.copy(
                            description = "",
                            selectedIncidentType = null,
                            severity = 5f,
                            submitSuccess = true
                        )
                    }
                }
                is com.arlabs.raksha.domain.util.Result.Failure -> {
                    _state.update { it.copy(errorMessage = result.message) }
                }
                else -> {}
            }
        }
    }

    companion object {
        private const val TAG = "ReportIncidentVM"
    }
}

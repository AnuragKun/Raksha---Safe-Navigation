package com.arlabs.raksha.features.safezone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Calendar
import javax.inject.Inject

data class SafePlace(
    val name: String,
    val type: String, // Hospital, Police, Fire Station
    val address: String,
    val distance: String,
    val distanceMeters: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class SafetyTip(
    val title: String,
    val description: String,
    val emoji: String = "💡",
    val timeTag: String = "all" // "all", "night", "morning", "commute"
)

@HiltViewModel
class SafeZoneViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "SafeZoneViewModel"
        private const val NEARBY_SEARCH_URL =
            "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
    }

    private val _safePlaces = MutableStateFlow<List<SafePlace>>(emptyList())
    val safePlaces = _safePlaces.asStateFlow()

    private val _safetyTips = MutableStateFlow<List<SafetyTip>>(emptyList())
    val safetyTips: StateFlow<List<SafetyTip>> = _safetyTips.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var apiKey: String = ""

    init {
        loadApiKey()
        fetchUserLocationAndPlaces()
        fetchSafetyTips()
    }

    private fun loadApiKey() {
        try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA
            )
            apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load API key", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchUserLocationAndPlaces() {
        viewModelScope.launch {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                _isLoading.value = false
                _errorMessage.value = "Location permission required"
                return@launch
            }

            if (apiKey.isEmpty()) {
                _isLoading.value = false
                _errorMessage.value = "API key not configured"
                return@launch
            }

            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedClient.lastLocation.await()
                val lat = location?.latitude ?: 28.6139
                val lng = location?.longitude ?: 77.2090
                searchNearbyPlaces(lat, lng)
            } catch (e: Exception) {
                Log.e(TAG, "Location error", e)
                _isLoading.value = false
                _errorMessage.value = "Could not get location"
            }
        }
    }

    private suspend fun searchNearbyPlaces(userLat: Double, userLng: Double) {
        val allPlaces = mutableListOf<SafePlace>()

        // Search for police, hospital, fire_station
        val placeTypes = listOf(
            "police" to "Police",
            "hospital" to "Hospital",
            "fire_station" to "Fire Station"
        )

        for ((placeType, displayType) in placeTypes) {
            try {
                val places = fetchPlacesForType(userLat, userLng, placeType, displayType)
                allPlaces.addAll(places)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch $placeType", e)
            }
        }

        _safePlaces.value = allPlaces.sortedBy { it.distanceMeters }
        _isLoading.value = false
    }

    /**
     * Uses the Google Places Nearby Search REST API (which works with the
     * standard Maps API key — no Places SDK (New) required).
     */
    private suspend fun fetchPlacesForType(
        userLat: Double,
        userLng: Double,
        placeType: String,
        displayType: String
    ): List<SafePlace> = withContext(Dispatchers.IO) {
        try {
            val url = "$NEARBY_SEARCH_URL?" +
                "location=$userLat,$userLng" +
                "&radius=5000" + // 5km
                "&type=$placeType" +
                "&key=$apiKey"

            val response = URL(url).readText()
            val json = JSONObject(response)

            val status = json.getString("status")
            if (status != "OK" && status != "ZERO_RESULTS") {
                Log.e(TAG, "Nearby search returned: $status")
                return@withContext emptyList()
            }

            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            val places = mutableListOf<SafePlace>()

            for (i in 0 until minOf(results.length(), 5)) {
                val result = results.getJSONObject(i)
                val name = result.optString("name", "Unknown")
                val vicinity = result.optString("vicinity", "")
                val geometry = result.optJSONObject("geometry")
                val location = geometry?.optJSONObject("location")
                val placeLat = location?.optDouble("lat") ?: 0.0
                val placeLng = location?.optDouble("lng") ?: 0.0

                val distMeters = calculateDistance(userLat, userLng, placeLat, placeLng)

                places.add(
                    SafePlace(
                        name = name,
                        type = displayType,
                        address = vicinity,
                        distance = formatDistance(distMeters),
                        distanceMeters = distMeters,
                        latitude = placeLat,
                        longitude = placeLng
                    )
                )
            }

            places
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed for $placeType", e)
            emptyList()
        }
    }

    private fun calculateDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].toDouble()
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            String.format("%.1f km", meters / 1000)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Fetch safety tips from Firestore `safety_tips` collection.
     * Falls back to hardcoded defaults if Firestore fetch fails.
     */
    private fun fetchSafetyTips() {
        viewModelScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val snapshot = db.collection("safety_tips")
                    .whereEqualTo("active", true)
                    .get()
                    .await()

                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val currentTimeTag = when (hour) {
                    in 5..8 -> "morning"
                    in 7..10, in 17..20 -> "commute"
                    in 20..23, in 0..5 -> "night"
                    else -> "day"
                }

                val tips = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val description = doc.getString("description") ?: return@mapNotNull null
                    val emoji = doc.getString("emoji") ?: "💡"
                    val timeTag = doc.getString("time_tag") ?: "all"
                    SafetyTip(title, description, emoji, timeTag)
                }.filter { it.timeTag == "all" || it.timeTag == currentTimeTag }

                if (tips.isNotEmpty()) {
                    _safetyTips.value = tips
                } else {
                    _safetyTips.value = getDefaultTips(currentTimeTag)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch safety tips", e)
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val currentTimeTag = when (hour) {
                    in 5..8 -> "morning"
                    in 7..10, in 17..20 -> "commute"
                    in 20..23, in 0..5 -> "night"
                    else -> "day"
                }
                _safetyTips.value = getDefaultTips(currentTimeTag)
            }
        }
    }

    private fun getDefaultTips(timeTag: String): List<SafetyTip> {
        val tips = mutableListOf(
            SafetyTip("Share your location", "Let trusted contacts track your journey in real time.", "📍", "all"),
            SafetyTip("Stay alert in crowds", "Keep belongings close and be aware of your surroundings.", "👀", "all"),
            SafetyTip("Trust your instincts", "If something feels wrong, move to a well-lit, populated area.", "🧠", "all")
        )
        if (timeTag == "night") {
            tips.add(0, SafetyTip("Avoid dark alleys", "Stick to well-lit streets and main roads after dark.", "🌙", "night"))
            tips.add(1, SafetyTip("Use the buddy system", "Travel with a friend or keep someone informed of your route.", "👥", "night"))
        }
        if (timeTag == "commute") {
            tips.add(0, SafetyTip("Stay visible", "Wear reflective clothing while walking or biking during rush hours.", "🦺", "commute"))
        }
        return tips
    }
}

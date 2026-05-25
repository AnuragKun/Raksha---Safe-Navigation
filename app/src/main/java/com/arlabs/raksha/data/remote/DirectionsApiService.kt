package com.arlabs.raksha.data.remote

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject

/**
 * Fetches route directions from the Google Directions REST API.
 * Parses the response to extract a list of LatLng points forming the polyline.
 */
class DirectionsApiService @Inject constructor() {

    companion object {
        private const val TAG = "DirectionsApiService"
        private const val BASE_URL = "https://maps.googleapis.com/maps/api/directions/json"
    }

    /**
     * Fetches directions between origin and destination.
     * @param originLat Origin latitude
     * @param originLng Origin longitude
     * @param destLat Destination latitude
     * @param destLng Destination longitude
     * @param apiKey Google Maps API key
     * @param mode Travel mode: driving, bicycling, walking, or transit
     * @return List of LatLng points forming the route polyline, or empty list on failure
     */
    suspend fun getDirections(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        apiKey: String,
        mode: String = "driving",
        avoid: String? = null
    ): DirectionsResult = withContext(Dispatchers.IO) {
        try {
            var url = "$BASE_URL?" +
                "origin=$originLat,$originLng" +
                "&destination=$destLat,$destLng" +
                "&mode=$mode" +
                "&alternatives=true" +
                "&key=$apiKey"
            if (!avoid.isNullOrEmpty()) {
                url += "&avoid=$avoid"
            }

            val response = URL(url).readText()
            val json = JSONObject(response)

            val status = json.getString("status")
            if (status != "OK") {
                Log.e(TAG, "Directions API returned status: $status")
                return@withContext DirectionsResult(emptyList(), "", "")
            }

            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) {
                return@withContext DirectionsResult(emptyList(), "", "")
            }

            // Parse the first (best) route
            val route = routes.getJSONObject(0)
            val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")
            val decodedPath = decodePolyline(overviewPolyline)

            // Get route summary info
            val leg = route.getJSONArray("legs").getJSONObject(0)
            val distance = leg.getJSONObject("distance").getString("text")
            val duration = leg.getJSONObject("duration").getString("text")

            Log.d(TAG, "Route: $distance, $duration, ${decodedPath.size} points")

            DirectionsResult(
                points = decodedPath,
                distance = distance,
                duration = duration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch directions", e)
            DirectionsResult(emptyList(), "", "")
        }
    }

    /**
     * Decodes an encoded polyline string into a list of LatLng coordinates.
     * See: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
     */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }

        return poly
    }
}

data class DirectionsResult(
    val points: List<LatLng>,
    val distance: String,
    val duration: String
)

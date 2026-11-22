package com.example.ridelink.core

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Net {
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "RideLink/0.2 (personal project)")
        c.connectTimeout = 10000
        c.readTimeout = 15000
        try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

data class Place(val name: String, val sub: String, val lat: Double, val lon: Double)
data class Step(val text: String, val distanceM: Double)
data class RouteInfo(
    val points: List<Pair<Double, Double>>, // (lon, lat)
    val distanceM: Double,
    val durationS: Double,
    val steps: List<Step>,
)

/**
 * Search uses Photon (OpenStreetMap data); routing uses the public OSRM demo server.
 * Both are free test services with usage limits and a car profile, not for heavy use.
 */
object NavStore {
    val destination = MutableStateFlow<Place?>(null)
    val route = MutableStateFlow<RouteInfo?>(null)
    val results = MutableStateFlow<List<Place>>(emptyList())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun clear() {
        destination.value = null
        route.value = null
        results.value = emptyList()
        error.value = null
    }

    suspend fun search(query: String, near: Location?) {
        if (query.isBlank()) return
        busy.value = true
        error.value = null
        try {
            var url = "https://photon.komoot.io/api/?limit=6&q=" + URLEncoder.encode(query, "UTF-8")
            if (near != null) url += "&lat=${near.latitude}&lon=${near.longitude}"
            val arr = JSONObject(Net.get(url)).getJSONArray("features")
            val found = (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                val p = f.getJSONObject("properties")
                val c = f.getJSONObject("geometry").getJSONArray("coordinates")
                val name = p.optString("name").ifBlank { p.optString("street", "Unnamed place") }
                val sub = listOf(p.optString("city"), p.optString("state"), p.optString("country"))
                    .filter { it.isNotBlank() }.joinToString(", ")
                Place(name, sub, c.getDouble(1), c.getDouble(0))
            }
            results.value = found
            if (found.isEmpty()) error.value = "No places found"
        } catch (e: Exception) {
            error.value = "Search failed. Check your internet connection."
        } finally {
            busy.value = false
        }
    }

    suspend fun routeTo(place: Place, from: Location) {
        destination.value = place
        results.value = emptyList()
        busy.value = true
        error.value = null
        try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                "${from.longitude},${from.latitude};${place.lon},${place.lat}" +
                "?overview=full&geometries=geojson&steps=true"
            val j = JSONObject(Net.get(url))
            if (j.optString("code") != "Ok") {
                route.value = null
                error.value = "No route found"
                return
            }
            val r = j.getJSONArray("routes").getJSONObject(0)
            val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
            val points = (0 until coords.length()).map { i ->
                val c = coords.getJSONArray(i)
                Pair(c.getDouble(0), c.getDouble(1))
            }
            val steps = ArrayList<Step>()
            val legs = r.getJSONArray("legs")
            for (l in 0 until legs.length()) {
                val st = legs.getJSONObject(l).getJSONArray("steps")
                for (i in 0 until st.length()) {
                    val s = st.getJSONObject(i)
                    val m = s.getJSONObject("maneuver")
                    steps.add(
                        Step(
                            stepText(m.optString("type"), m.optString("modifier"), s.optString("name")),
                            s.optDouble("distance", 0.0),
                        )
                    )
                }
            }
            route.value = RouteInfo(points, r.getDouble("distance"), r.getDouble("duration"), steps)
        } catch (e: Exception) {
            error.value = "Could not get a route. Check your internet connection."
        } finally {
            busy.value = false
        }
    }

    private fun stepText(type: String, modifier: String, name: String): String {
        val onto = if (name.isBlank()) "" else " onto $name"
        return when (type) {
            "depart" -> if (name.isBlank()) "Head out" else "Head out on $name"
            "arrive" -> "Arrive at your destination"
            "roundabout", "rotary" -> "Take the roundabout$onto"
            else -> (if (modifier.isBlank()) "Continue" else "Go $modifier") + onto
        }
    }
}

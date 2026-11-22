package com.example.ridelink.core

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One reading from any sensor module, e.g. SensorReading("Front", "32 psi"). */
data class SensorReading(val label: String, val value: String, val warn: Boolean = false)

/** Single source of truth for everything the UI and device modules share. */
data class RideState(
    val location: Location? = null,
    val speedKmh: Float = 0f,
    val bearing: Float = 0f,
    val altitudeM: Double = 0.0,
    val sensors: Map<String, SensorReading> = emptyMap(),
)

object RideStateStore {
    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state

    fun setLocation(loc: Location) = _state.update {
        it.copy(
            location = loc,
            speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f,
            bearing = loc.bearing,
            altitudeM = loc.altitude,
        )
    }

    /** Modules (TPMS, etc.) call this to publish readings. Key must be unique per sensor. */
    fun setSensor(key: String, reading: SensorReading) =
        _state.update { it.copy(sensors = it.sensors + (key to reading)) }

    fun clearSensors(prefix: String) =
        _state.update { it.copy(sensors = it.sensors.filterKeys { k -> !k.startsWith(prefix) }) }
}

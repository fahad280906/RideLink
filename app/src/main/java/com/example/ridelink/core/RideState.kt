package com.example.ridelink.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One reading from any sensor module, e.g. SensorReading("Front tyre", "32 psi"). */
data class SensorReading(val label: String, val value: String)

/** Single source of truth for everything the UI and device modules share. */
data class RideState(
    val lat: Double? = null,
    val lon: Double? = null,
    val speedKmh: Float = 0f,
    val bearing: Float = 0f,
    val sensors: Map<String, SensorReading> = emptyMap(),
)

object RideStateStore {
    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state

    fun setLocation(lat: Double, lon: Double, speedMs: Float, bearing: Float) =
        _state.update { it.copy(lat = lat, lon = lon, speedKmh = speedMs * 3.6f, bearing = bearing) }

    /** Modules (TPMS, etc.) call this to publish readings. Key must be unique per sensor. */
    fun setSensor(key: String, reading: SensorReading) =
        _state.update { it.copy(sensors = it.sensors + (key to reading)) }
}

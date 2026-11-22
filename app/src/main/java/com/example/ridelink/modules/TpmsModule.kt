package com.example.ridelink.modules

import com.example.ridelink.core.DeviceModule
import com.example.ridelink.core.ModuleStatus
import com.example.ridelink.core.RideStateStore
import com.example.ridelink.core.SensorReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Placeholder for a future tyre-pressure system. Shows how a module publishes data. */
class TpmsModule : DeviceModule {
    override val id = "tpms"
    override val name = "Tyre pressure (TPMS)"
    private val _status = MutableStateFlow(ModuleStatus.Disconnected)
    override val status: StateFlow<ModuleStatus> = _status

    override suspend fun connect() {
        // TODO: scan for your sensor's BLE advertisements, parse pressure, then:
        // RideStateStore.setSensor("tpms_front", SensorReading("Front tyre", "32 psi"))
        _status.value = ModuleStatus.Error
    }

    override suspend fun disconnect() { _status.value = ModuleStatus.Disconnected }
}

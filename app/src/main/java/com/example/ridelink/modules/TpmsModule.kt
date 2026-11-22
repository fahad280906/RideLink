package com.example.ridelink.modules

import com.example.ridelink.core.DeviceModule
import com.example.ridelink.core.ModuleStatus
import com.example.ridelink.core.RideStateStore
import com.example.ridelink.core.SensorReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tyre pressure module. Real sensor support is not implemented yet (it depends on the
 * sensor you buy). Demo mode publishes fake readings so you can see how the app will look.
 */
class TpmsModule : DeviceModule {
    override val id = "tpms"
    override val name = "Tyre pressure (TPMS)"
    override val note = "No real sensor support yet. Use the demo switch to preview the display."
    private val _status = MutableStateFlow(ModuleStatus.Disconnected)
    override val status: StateFlow<ModuleStatus> = _status

    private fun reading(label: String, psi: Double) =
        SensorReading(label, "%.0f psi".format(psi), warn = psi < 28)

    fun setDemo(on: Boolean) {
        if (on) {
            RideStateStore.setSensor("tpms_front", reading("Front", 32.0))
            RideStateStore.setSensor("tpms_rear", reading("Rear", 34.0))
            _status.value = ModuleStatus.Connected
        } else {
            RideStateStore.clearSensors("tpms")
            _status.value = ModuleStatus.Disconnected
        }
    }

    override suspend fun connect() {
        // TODO: scan for your sensor's BLE advertisements, parse pressure, then publish
        // with RideStateStore.setSensor("tpms_front", ...)
        _status.value = ModuleStatus.Error
    }

    override suspend fun disconnect() = setDemo(false)
}

package com.example.ridelink.modules

import com.example.ridelink.core.DeviceModule
import com.example.ridelink.core.ModuleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Guerrilla 450 dash. NOT IMPLEMENTED YET.
 * Royal Enfield has no public SDK, so the Bluetooth/Wi-Fi link must be worked out
 * and proven on your own bike before anything else is built on top of it.
 */
class DashModule : DeviceModule {
    override val id = "dash"
    override val name = "Guerrilla 450 dash"
    private val _status = MutableStateFlow(ModuleStatus.Disconnected)
    override val status: StateFlow<ModuleStatus> = _status

    override suspend fun connect() {
        // TODO: 1) discover + pair with dash  2) open Wi-Fi link  3) send map frames
        _status.value = ModuleStatus.Error
    }

    override suspend fun disconnect() { _status.value = ModuleStatus.Disconnected }
}

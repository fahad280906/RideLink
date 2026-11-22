package com.example.ridelink.core

import kotlinx.coroutines.flow.StateFlow

enum class ModuleStatus { Disconnected, Connecting, Connected, Error }

/**
 * Every external device (bike dash, TPMS, helmet, ...) is one DeviceModule.
 * To add a new device: implement this, publish data via RideStateStore,
 * and add it to ModuleRegistry. Nothing else in the app changes.
 */
interface DeviceModule {
    val id: String
    val name: String
    val status: StateFlow<ModuleStatus>
    suspend fun connect()
    suspend fun disconnect()
}

object ModuleRegistry {
    val modules: List<DeviceModule> = listOf(
        com.example.ridelink.modules.DashModule(),
        com.example.ridelink.modules.TpmsModule(),
    )
}

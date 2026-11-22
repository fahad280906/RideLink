package com.example.ridelink.ui

import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.ridelink.core.RideStateStore
import com.example.ridelink.location.locationUpdates
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

// Free demo style for testing. Swap for your own tile source before real use.
private const val STYLE_URL = "https://demotiles.maplibre.org/style.json"

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(ctx) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    // Forward lifecycle events to the MapView.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Load the map and turn on the "you are here" marker, fed by our own location flow.
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.setStyle(STYLE_URL) { style ->
                val lc = m.locationComponent
                lc.activateLocationComponent(
                    LocationComponentActivationOptions.builder(ctx, style)
                        .useDefaultLocationEngine(false).build()
                )
                @Suppress("MissingPermission")
                lc.isLocationComponentEnabled = true
                lc.cameraMode = CameraMode.TRACKING
                lc.renderMode = RenderMode.COMPASS
                styleReady = true
            }
        }
    }

    // One location source -> shared ride state + map marker.
    LaunchedEffect(styleReady) {
        if (!styleReady) return@LaunchedEffect
        locationUpdates(ctx).collect { loc: Location ->
            RideStateStore.setLocation(loc.latitude, loc.longitude, loc.speed, loc.bearing)
            map?.locationComponent?.forceLocationUpdate(loc)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

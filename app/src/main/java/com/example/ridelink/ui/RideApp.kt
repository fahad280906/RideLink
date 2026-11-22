package com.example.ridelink.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridelink.core.RideRecorder
import com.example.ridelink.core.RideStateStore
import com.example.ridelink.location.locationUpdates

private enum class AppTab(val label: String, val icon: String) {
    Navigate("Map", "🗺️"),
    Rides("Rides", "🏁"),
    Wallpapers("Wallpapers", "🖼️"),
    Bike("Bike", "🏍️"),
}

@Composable
fun RideApp() {
    val ctx = LocalContext.current
    val perm = Manifest.permission.ACCESS_FINE_LOCATION
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }

    if (granted) {
        RideContent()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Location permission is needed to show the map.")
        }
    }
}

@Composable
private fun RideContent() {
    val ctx = LocalContext.current

    // One location source for the whole app: feeds the shared state and the ride recorder.
    LaunchedEffect(Unit) {
        locationUpdates(ctx).collect { loc ->
            RideStateStore.setLocation(loc)
            RideRecorder.onLocation(loc)
        }
    }

    // Keep the screen on while a ride is being recorded.
    val rec by RideRecorder.rec.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(rec.active) {
        view.keepScreenOn = rec.active
        onDispose { view.keepScreenOn = false }
    }

    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(t.icon) },
                        label = { Text(t.label) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> MapTab()
                1 -> RidesTab()
                2 -> WallpapersTab()
                else -> BikeTab()
            }
        }
    }
}

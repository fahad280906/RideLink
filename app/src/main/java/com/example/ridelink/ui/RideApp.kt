package com.example.ridelink.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridelink.core.ModuleRegistry
import com.example.ridelink.core.RideState
import com.example.ridelink.core.RideStateStore

@Composable
fun RideApp() {
    val ctx = LocalContext.current
    val perm = Manifest.permission.ACCESS_FINE_LOCATION
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }

    if (!granted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Location permission is needed to show the map.")
        }
        return
    }

    val ride by RideStateStore.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        MapScreen(Modifier.fillMaxSize())
        StatusPanel(ride, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

@Composable
private fun StatusPanel(ride: RideState, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.0f".format(ride.speedKmh), fontSize = 56.sp, style = MaterialTheme.typography.displayMedium)
                Text(" km/h", Modifier.padding(bottom = 10.dp))
            }
            ride.sensors.values.forEach { Text("${it.label}: ${it.value}") }
            Spacer(Modifier.height(8.dp))
            ModuleRegistry.modules.forEach { m ->
                val s by m.status.collectAsStateWithLifecycle()
                Text("${m.name}: $s", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

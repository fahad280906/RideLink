package com.example.ridelink.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridelink.core.DeviceModule
import com.example.ridelink.core.ModuleRegistry
import com.example.ridelink.core.ModuleStatus
import com.example.ridelink.core.RideRepository
import com.example.ridelink.core.RideStateStore
import com.example.ridelink.core.Wallpapers
import com.example.ridelink.core.fmtDist
import com.example.ridelink.core.fmtDur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Rides ----------------
@Composable
fun RidesTab() {
    val ctx = LocalContext.current
    var rides by remember { mutableStateOf(RideRepository.all(ctx)) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Rides", style = MaterialTheme.typography.headlineMedium)
        Text("${rides.size} rides · ${fmtDist(rides.sumOf { it.distanceM })} total")
        if (rides.isEmpty()) {
            Text("No rides yet. Tap Start ride on the Map tab.", Modifier.padding(top = 24.dp))
        }
        LazyColumn(
            Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rides, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.format(Date(r.startedAt)), fontWeight = FontWeight.Bold)
                            Text("${fmtDist(r.distanceM)} · ${fmtDur(r.durationS)}")
                            Text(
                                "avg ${r.avgKmh.toInt()} · max ${r.maxKmh.toInt()} km/h",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = {
                            RideRepository.delete(ctx, r.id)
                            rides = RideRepository.all(ctx)
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

// ---------------- Wallpapers ----------------
@Composable
fun WallpapersTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val files = remember(version) { Wallpapers.list(ctx) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val f = withContext(Dispatchers.IO) { Wallpapers.importFrom(ctx, uri) }
                if (f == null) Toast.makeText(ctx, "Could not read that image", Toast.LENGTH_SHORT).show()
                version++
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Wallpapers", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick a photo. It is cropped to a square and previewed round, like the dash.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.padding(top = 12.dp),
        ) { Text("Add wallpaper") }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(files, key = { it.path }) { f ->
                WallpaperCard(f, onDelete = {
                    Wallpapers.delete(f)
                    version++
                })
            }
        }
    }
}

@Composable
private fun WallpaperCard(file: File, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(file.path) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (bmp != null) {
            Image(bmp, contentDescription = "Wallpaper", modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape))
        }
        Row {
            TextButton(onClick = {
                Toast.makeText(ctx, "Sending to the dash is not available yet", Toast.LENGTH_SHORT).show()
            }) { Text("Send to dash") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

// ---------------- Bike ----------------
@Composable
fun BikeTab() {
    val scope = rememberCoroutineScope()
    val ride by RideStateStore.state.collectAsStateWithLifecycle()
    var demo by remember { mutableStateOf(ModuleRegistry.tpms.status.value == ModuleStatus.Connected) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bike", style = MaterialTheme.typography.headlineMedium)
        ModuleRegistry.modules.forEach { m ->
            ModuleCard(m) { scope.launch { m.connect() } }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tyre pressure demo data", Modifier.weight(1f))
                    Switch(checked = demo, onCheckedChange = {
                        demo = it
                        ModuleRegistry.tpms.setDemo(it)
                    })
                }
                val tyres = ride.sensors.filterKeys { it.startsWith("tpms") }.values
                if (tyres.isEmpty()) {
                    Text("Turn on to preview how tyre pressure appears.", style = MaterialTheme.typography.bodySmall)
                }
                tyres.forEach {
                    Text(
                        "${it.label}: ${it.value}",
                        color = if (it.warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(m: DeviceModule, onConnect: () -> Unit) {
    val status by m.status.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontWeight = FontWeight.Bold)
                    Text(status.name, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onConnect) { Text("Connect") }
            }
            Text(m.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

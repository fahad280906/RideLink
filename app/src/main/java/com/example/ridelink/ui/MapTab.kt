package com.example.ridelink.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridelink.core.NavStore
import com.example.ridelink.core.Place
import com.example.ridelink.core.RideRecorder
import com.example.ridelink.core.RideStateStore
import com.example.ridelink.core.compass
import com.example.ridelink.core.fmtDist
import com.example.ridelink.core.fmtDur
import kotlinx.coroutines.launch

@Composable
fun MapTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val ride by RideStateStore.state.collectAsStateWithLifecycle()
    val rec by RideRecorder.rec.collectAsStateWithLifecycle()
    val route by NavStore.route.collectAsStateWithLifecycle()
    val dest by NavStore.destination.collectAsStateWithLifecycle()
    val results by NavStore.results.collectAsStateWithLifecycle()
    val busy by NavStore.busy.collectAsStateWithLifecycle()
    val error by NavStore.error.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var recenter by remember { mutableIntStateOf(0) }
    var showSteps by remember { mutableStateOf(false) }

    fun go(place: Place) {
        val loc = ride.location
        if (loc == null) {
            NavStore.error.value = "Waiting for GPS..."
            return
        }
        focus.clearFocus()
        scope.launch { NavStore.routeTo(place, loc) }
    }

    Box(Modifier.fillMaxSize()) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            location = ride.location,
            route = route,
            dest = dest,
            recenter = recenter,
        ) { lat, lon ->
            go(Place("Dropped pin", "%.4f, %.4f".format(lat, lon), lat, lon))
        }

        // Search bar and results
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp)),
                placeholder = { Text("Search a place, or long-press the map") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    scope.launch { NavStore.search(query, ride.location) }
                }),
                trailingIcon = {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                },
            )
            error?.let {
                Text(
                    it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                )
            }
            if (results.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column {
                        results.forEach { p ->
                            Column(Modifier.fillMaxWidth().clickable { go(p) }.padding(12.dp)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                if (p.sub.isNotBlank()) Text(p.sub, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        SmallFloatingActionButton(
            onClick = { recenter++ },
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
        ) { Text("⌖", fontSize = 20.sp) }

        // Route card and ride stats
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            route?.let { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(dest?.name ?: "Route", fontWeight = FontWeight.Bold)
                                Text("${fmtDist(r.distanceM)} · ${fmtDur(r.durationS.toLong())}")
                            }
                            TextButton(onClick = { showSteps = !showSteps }) {
                                Text(if (showSteps) "Hide steps" else "Steps")
                            }
                            TextButton(onClick = { NavStore.clear() }) { Text("Clear") }
                        }
                        if (showSteps) {
                            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                                r.steps.forEach {
                                    Text(
                                        "• ${it.text} (${fmtDist(it.distanceM)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "Estimate for a car. Live turn alerts come with the dash connection.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.0f".format(ride.speedKmh), fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text(" km/h", Modifier.padding(bottom = 10.dp))
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Alt ${ride.altitudeM.toInt()} m", style = MaterialTheme.typography.bodySmall)
                            Text("Heading ${compass(ride.bearing)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rec.active) {
                        Text("Ride: ${fmtDist(rec.distanceM)} · ${fmtDur(rec.durationS)} · max ${rec.maxKmh.toInt()} km/h")
                    }
                    val tyres = ride.sensors.filterKeys { it.startsWith("tpms") }.values
                    if (tyres.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            tyres.forEach {
                                Text(
                                    "${it.label} ${it.value}",
                                    color = if (it.warn) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (rec.active) {
                                val saved = RideRecorder.stop(ctx)
                                Toast.makeText(
                                    ctx,
                                    if (saved != null) "Ride saved" else "Ride too short to save",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                RideRecorder.start()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(if (rec.active) "Stop ride" else "Start ride") }
                }
            }
        }
    }
}

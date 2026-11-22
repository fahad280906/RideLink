package com.example.ridelink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.example.ridelink.ui.RideApp
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Color(0xFFE3A72F), onPrimary = Color.Black)
            ) { RideApp() }
        }
    }
}

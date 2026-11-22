package com.example.ridelink.ui

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ridelink.core.Place
import com.example.ridelink.core.RouteInfo
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// Free OpenStreetMap-based style from OpenFreeMap (no API key).
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun MapLibreView(
    modifier: Modifier,
    location: Location?,
    route: RouteInfo?,
    dest: Place?,
    recenter: Int,
    onLongPress: (Double, Double) -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(ctx) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val latestLongPress by rememberUpdatedState(onLongPress)

    // Forward lifecycle events to the MapView (the observer replays CREATE/START/RESUME).
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Load the style, add route/destination layers, switch on the "you are here" marker.
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.setStyle(STYLE_URL) { s ->
                s.addSource(GeoJsonSource("route-src"))
                s.addLayer(
                    LineLayer("route-casing", "route-src").withProperties(
                        lineColor("#0b3d91"), lineWidth(9f),
                        lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    )
                )
                s.addLayer(
                    LineLayer("route-line", "route-src").withProperties(
                        lineColor("#2f80ff"), lineWidth(6f),
                        lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    )
                )
                s.addSource(GeoJsonSource("dest-src"))
                s.addLayer(
                    CircleLayer("dest-circle", "dest-src").withProperties(
                        circleRadius(9f), circleColor("#e5484d"),
                        circleStrokeWidth(3f), circleStrokeColor("#ffffff"),
                    )
                )
                val lc = m.locationComponent
                lc.activateLocationComponent(
                    LocationComponentActivationOptions.builder(ctx, s)
                        .useDefaultLocationEngine(false).build()
                )
                lc.isLocationComponentEnabled = true
                lc.cameraMode = CameraMode.TRACKING
                lc.renderMode = RenderMode.COMPASS
                lc.zoomWhileTracking(16.0)
                m.addOnMapLongClickListener { ll ->
                    latestLongPress(ll.latitude, ll.longitude)
                    true
                }
                style = s
            }
        }
    }

    // Feed GPS fixes to the location marker.
    LaunchedEffect(style, location) {
        val loc = location ?: return@LaunchedEffect
        if (style != null) map?.locationComponent?.forceLocationUpdate(loc)
    }

    // Draw the route and destination pin, then zoom to fit the route.
    LaunchedEffect(style, route, dest) {
        val s = style ?: return@LaunchedEffect
        val routeSrc = s.getSourceAs<GeoJsonSource>("route-src")
        if (route == null) {
            routeSrc?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        } else {
            routeSrc?.setGeoJson(
                Feature.fromGeometry(
                    LineString.fromLngLats(route.points.map { Point.fromLngLat(it.first, it.second) })
                )
            )
        }
        val destSrc = s.getSourceAs<GeoJsonSource>("dest-src")
        if (dest == null) {
            destSrc?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        } else {
            destSrc?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(dest.lon, dest.lat)))
        }
        val m = map
        if (route != null && m != null && route.points.size > 1) {
            m.locationComponent.cameraMode = CameraMode.NONE
            val bounds = LatLngBounds.Builder()
                .includes(route.points.map { LatLng(it.second, it.first) })
                .build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140))
        }
    }

    // "Recenter" button.
    LaunchedEffect(recenter) {
        if (recenter == 0) return@LaunchedEffect
        val m = map
        if (m != null && style != null) {
            m.locationComponent.cameraMode = CameraMode.TRACKING
            m.locationComponent.zoomWhileTracking(16.0)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

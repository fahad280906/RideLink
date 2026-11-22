package com.example.ridelink.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ---------- formatting ----------
fun fmtDist(m: Double): String = if (m >= 1000) "%.1f km".format(m / 1000) else "${m.toInt()} m"

fun fmtDur(s: Long): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "$h h $m min" else if (m > 0) "$m min" else "$s s"
}

fun compass(bearing: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val n = (bearing % 360f + 360f) % 360f
    return dirs[((n + 22.5f) / 45f).toInt() % 8]
}

// ---------- ride history ----------
data class Ride(
    val id: Long,
    val startedAt: Long,
    val durationS: Long,
    val distanceM: Double,
    val maxKmh: Float,
) {
    val avgKmh: Float get() = if (durationS > 0) (distanceM / durationS * 3.6).toFloat() else 0f
}

object RideRepository {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("rides", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Ride> {
        val arr = JSONArray(prefs(ctx).getString("list", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Ride(
                o.getLong("id"), o.getLong("startedAt"), o.getLong("durationS"),
                o.getDouble("distanceM"), o.getDouble("maxKmh").toFloat(),
            )
        }.sortedByDescending { it.startedAt }
    }

    private fun save(ctx: Context, rides: List<Ride>) {
        val arr = JSONArray()
        rides.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("startedAt", it.startedAt)
                    .put("durationS", it.durationS)
                    .put("distanceM", it.distanceM)
                    .put("maxKmh", it.maxKmh.toDouble())
            )
        }
        prefs(ctx).edit().putString("list", arr.toString()).apply()
    }

    fun add(ctx: Context, ride: Ride) = save(ctx, all(ctx) + ride)
    fun delete(ctx: Context, id: Long) = save(ctx, all(ctx).filter { it.id != id })
}

// ---------- ride recorder ----------
data class Recording(
    val active: Boolean = false,
    val startedAt: Long = 0L,
    val durationS: Long = 0L,
    val distanceM: Double = 0.0,
    val maxKmh: Float = 0f,
)

object RideRecorder {
    private val _rec = MutableStateFlow(Recording())
    val rec: StateFlow<Recording> = _rec
    private var last: Location? = null

    fun start() {
        last = null
        _rec.value = Recording(active = true, startedAt = System.currentTimeMillis())
    }

    fun onLocation(loc: Location) {
        val r = _rec.value
        if (!r.active || loc.accuracy > 25f) return
        var dist = r.distanceM
        val prev = last
        if (prev == null) {
            last = loc
        } else {
            val step = prev.distanceTo(loc)
            if (step >= 3f) {
                dist += step
                last = loc
            }
        }
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        _rec.value = r.copy(
            durationS = (System.currentTimeMillis() - r.startedAt) / 1000,
            distanceM = dist,
            maxKmh = maxOf(r.maxKmh, kmh),
        )
    }

    /** Returns the saved ride, or null if it was too short (under 50 m) to keep. */
    fun stop(ctx: Context): Ride? {
        val r = _rec.value
        _rec.value = Recording()
        last = null
        if (!r.active || r.distanceM < 50) return null
        val ride = Ride(r.startedAt, r.startedAt, r.durationS, r.distanceM, r.maxKmh)
        RideRepository.add(ctx, ride)
        return ride
    }
}

// ---------- wallpapers ----------
object Wallpapers {
    // Placeholder size until the dash's real screen resolution is known.
    const val DASH_SIZE = 800

    private fun dir(ctx: Context) = File(ctx.filesDir, "wallpapers").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Centre-crops the picked image to a square and saves it. Returns null if unreadable. */
    fun importFrom(ctx: Context, uri: Uri): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DASH_SIZE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, DASH_SIZE, DASH_SIZE, true)
        val file = File(dir(ctx), "wp_${System.currentTimeMillis()}.png")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun delete(file: File) = file.delete()
}

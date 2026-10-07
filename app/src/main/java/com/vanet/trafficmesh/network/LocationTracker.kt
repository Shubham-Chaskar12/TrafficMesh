package com.vanet.trafficmesh.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationTracker(context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // Data class to hold our current speed and direction
    data class GpsState(
        val speedKmh: Float = 0f,
        val heading: Float = 0f
    )

    private val _gpsState = MutableStateFlow(GpsState())
    val gpsState: StateFlow<GpsState> = _gpsState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start() {
        // Request highly accurate GPS updates every 1 second (1000 milliseconds)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    fun stop() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return

            // Android gives speed in meters per second. We multiply by 3.6 to get km/h.
            val speed = if (location.hasSpeed()) location.speed * 3.6f else 0f
            val bearing = if (location.hasBearing()) location.bearing else 0f

            _gpsState.value = GpsState(speedKmh = speed, heading = bearing)
        }
    }
}
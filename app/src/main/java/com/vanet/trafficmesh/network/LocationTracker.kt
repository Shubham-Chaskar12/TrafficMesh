package com.vanet.trafficmesh.sensor

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationTracker(context: Context) {
    data class GpsState(val speedKmh: Float = 0f, val heading: Float = 0f)

    private val _gpsState = MutableStateFlow(GpsState())
    val gpsState: StateFlow<GpsState> = _gpsState.asStateFlow()

    fun start() {
        // GPS implementation will go here later
    }

    fun stop() {
    }
}
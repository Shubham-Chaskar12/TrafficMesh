package com.vanet.trafficmesh.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class KinematicsEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val alpha = 0.25f
    private var filteredZ = 0.0f

    private val windowCapacity = 13
    private val sampleWindow = ArrayDeque<Float>(windowCapacity)

    private val emergencyBrakeThreshold = -3.5f

    data class MotionState(
        val longitudinalAcceleration: Float = 0.0f,
        val isEmergencyBraking: Boolean = false,
        val sustainedSamplesCount: Int = 0
    )

    private val _motionState = MutableStateFlow(MotionState())
    val motionState: StateFlow<MotionState> = _motionState.asStateFlow()

    fun start() {
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        sampleWindow.clear()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        val rawLongitudinal = event.values[2]

        filteredZ = (alpha * rawLongitudinal) + ((1.0f - alpha) * filteredZ)

        if (sampleWindow.size >= windowCapacity) {
            sampleWindow.pollFirst()
        }
        sampleWindow.addLast(filteredZ)

        val windowAverage = sampleWindow.sum() / sampleWindow.size
        val isBrakingHard = windowAverage < emergencyBrakeThreshold

        _motionState.value = MotionState(
            longitudinalAcceleration = windowAverage,
            isEmergencyBraking = isBrakingHard,
            sustainedSamplesCount = sampleWindow.size
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
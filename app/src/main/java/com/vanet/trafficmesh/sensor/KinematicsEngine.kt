package com.vanet.trafficmesh.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KinematicsEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    data class MotionState(
        val longitudinalAcceleration: Float = 0f,
        val isEmergencyBraking: Boolean = false,
        val yawRateDegrees: Float = 0f
    )

    private val _motionState = MutableStateFlow(MotionState())
    val motionState: StateFlow<MotionState> = _motionState.asStateFlow()

    private var smoothedAccelY = 0f
    private val alpha = 0.2f

    fun start() {
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var currentBraking = _motionState.value.isEmergencyBraking
        var currentYawRate = _motionState.value.yawRateDegrees

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                smoothedAccelY = alpha * event.values[1] + (1 - alpha) * smoothedAccelY
                currentBraking = smoothedAccelY < -4.0f
            }
            Sensor.TYPE_GYROSCOPE -> {
                val yawRateRad = event.values[1]
                currentYawRate = Math.toDegrees(yawRateRad.toDouble()).toFloat()
            }
        }

        _motionState.value = MotionState(smoothedAccelY, currentBraking, currentYawRate)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
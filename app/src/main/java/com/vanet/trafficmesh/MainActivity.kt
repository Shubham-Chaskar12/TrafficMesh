package com.vanet.trafficmesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vanet.trafficmesh.model.VehicleTelemetryPacket
import com.vanet.trafficmesh.network.MeshNetwork
import com.vanet.trafficmesh.sensor.KinematicsEngine
import com.vanet.trafficmesh.sensor.LocationTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var kinematicsEngine: KinematicsEngine
    private lateinit var locationTracker: LocationTracker
    private lateinit var meshNetwork: MeshNetwork
    private lateinit var statusText: TextView

    private var isShowingAlert = false // UI Lock flag

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startAllSystems()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusText = TextView(this).apply {
            textSize = 22f
            setPadding(50, 100, 50, 50)
            text = "Initializing Systems..."
        }
        setContentView(statusText)

        kinematicsEngine = KinematicsEngine(this)
        locationTracker = LocationTracker(this)
        meshNetwork = MeshNetwork(this)

        checkAndRequestPermissions()
        observeKinematics()
        observeNetworkAlerts()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT))
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (missing.isEmpty()) {
            startAllSystems()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startAllSystems() {
        kinematicsEngine.start()
        locationTracker.start()
        meshNetwork.startMesh()
        startTelemetryLoop() // Start our continuous data broadcast
        startGhostCarSimulator()
    }

    // This background loop packages our live sensor data and broadcasts it twice a second
    private fun startTelemetryLoop() {
        lifecycleScope.launch {
            while (true) {
                val motion = kinematicsEngine.motionState.value
                val gps = locationTracker.gpsState.value

                // If braking hard, flag is 1. Otherwise, 0.
                val hazard: Short = if (motion.isEmergencyBraking) 1 else 0

                val packet = VehicleTelemetryPacket(
                    nodeId = meshNetwork.myNodeId,
                    timestampMs = System.currentTimeMillis().toInt(),
                    speedKmh = gps.speedKmh.toInt().toShort(),
                    headingDeg = gps.heading.toInt().toShort(),
                    longitudinalG = (motion.longitudinalAcceleration * 100).toInt().toShort(),
                    hazardFlag = hazard
                )

                meshNetwork.broadcastTelemetry(packet)
                delay(500) // Wait 500ms before sending the next packet
            }
        }
    }

    // 👻 SIMULATOR: Acts like a second phone on the network for testing
    private fun startGhostCarSimulator() {
        lifecycleScope.launch {
            kotlinx.coroutines.delay(10000)

            while (true) {
                isShowingAlert = true // 🔒 Lock the UI

                statusText.text = "👻 GHOST CAR 7777 BRAKED HARD! 🚨\nSpeed dropped to: 24 km/h"
                statusText.setBackgroundColor(android.graphics.Color.parseColor("#CC0000"))
                statusText.setTextColor(android.graphics.Color.WHITE)

                kotlinx.coroutines.delay(3000)

                statusText.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                statusText.setTextColor(android.graphics.Color.BLACK)

                isShowingAlert = false // 🔓 Unlock the UI

                kotlinx.coroutines.delay(8000)
            }
        }
    }

    private fun observeKinematics() {
        lifecycleScope.launch {
            kinematicsEngine.motionState.collect { state ->
                // If an alert is on the screen, do not overwrite the text!
                if (isShowingAlert) return@collect

                val gps = locationTracker.gpsState.value
                val status = if (state.isEmergencyBraking) {
                    "⚠️ EMERGENCY BRAKING!\n\nSpeed: ${gps.speedKmh.toInt()} km/h\nAccel: %.2f m/s²".format(state.longitudinalAcceleration)
                } else {
                    "✅ Cruising\n\nSpeed: ${gps.speedKmh.toInt()} km/h\nAccel: %.2f m/s²".format(state.longitudinalAcceleration)
                }
                statusText.text = status
            }
        }
    }

    private fun observeNetworkAlerts() {
        lifecycleScope.launch {
            meshNetwork.incomingTelemetry.collect { packet ->
                if (packet.hazardFlag.toInt() == 1) {
                    isShowingAlert = true // 🔒 Lock the UI

                    statusText.text = "🚨 CAR ${packet.nodeId} BRAKED HARD! 🚨\nSpeed dropped to: ${packet.speedKmh} km/h"
                    statusText.setBackgroundColor(android.graphics.Color.RED)
                    statusText.setTextColor(android.graphics.Color.WHITE)

                    kotlinx.coroutines.delay(3000)

                    statusText.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    statusText.setTextColor(android.graphics.Color.BLACK)

                    isShowingAlert = false // 🔓 Unlock the UI
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        kinematicsEngine.stop()
        locationTracker.stop()
        meshNetwork.stopMesh()
    }
}
package com.vanet.trafficmesh

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vanet.trafficmesh.model.VehicleTelemetryPacket
import com.vanet.trafficmesh.network.MeshNetwork
import com.vanet.trafficmesh.sensor.KinematicsEngine
import com.vanet.trafficmesh.sensor.LocationTracker
import com.vanet.trafficmesh.ui.RadarView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var kinematicsEngine: KinematicsEngine
    private lateinit var locationTracker: LocationTracker
    private lateinit var meshNetwork: MeshNetwork

    private lateinit var statusText: TextView
    private lateinit var radarView: RadarView // Our new graphical UI

    private var isShowingAlert = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startAllSystems()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup the graphical Radar View to take up the top portion of the screen
        radarView = RadarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // 2. Setup the Text View to sit at the bottom
        statusText = TextView(this).apply {
            textSize = 20f
            setPadding(40, 60, 40, 60)
            text = "Initializing Systems..."
            setTextColor(Color.WHITE)
        }

        // 3. Stack them together vertically
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Very dark grey
            addView(radarView)
            addView(statusText)
        }

        setContentView(mainLayout)

        kinematicsEngine = KinematicsEngine(this)
        locationTracker = LocationTracker(this)
        meshNetwork = MeshNetwork(this)

        checkAndRequestPermissions()
        observeKinematics()
        observeNetworkAlerts()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE)
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
        startTelemetryLoop()
        startGhostCarSimulator()
    }

    private fun startTelemetryLoop() {
        lifecycleScope.launch {
            while (true) {
                val motion = kinematicsEngine.motionState.value
                val gps = locationTracker.gpsState.value
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
                delay(500)
            }
        }
    }

    // 👻 The Ghost Car Simulator now controls the visual graphics!
    private fun startGhostCarSimulator() {
        lifecycleScope.launch {
            while (true) {
                // PHASE 1: Cruising (Ghost car is 40 meters ahead, not braking)
                radarView.activeNodes = listOf(RadarView.RadarNode("7777", 40f, false))
                radarView.invalidate() // Forces the graphics to redraw

                delay(8000)

                // PHASE 2: Emergency! (Ghost car suddenly closes the gap to 20 meters and brakes)
                isShowingAlert = true
                radarView.activeNodes = listOf(RadarView.RadarNode("7777", 20f, true))
                radarView.invalidate()

                statusText.text = "👻 GHOST CAR 7777 BRAKED HARD! 🚨\nGap closed to 20m!"
                statusText.setBackgroundColor(Color.parseColor("#CC0000"))
                statusText.setTextColor(Color.WHITE)

                delay(3000) // Show alert for 3 seconds

                statusText.setBackgroundColor(Color.TRANSPARENT)
                statusText.setTextColor(Color.WHITE)
                isShowingAlert = false
            }
        }
    }

    private fun observeKinematics() {
        lifecycleScope.launch {
            kinematicsEngine.motionState.collect { state ->
                // Update your car's graphics instantly based on sensors
                radarView.isEgoBraking = state.isEmergencyBraking
                radarView.invalidate()

                if (isShowingAlert) return@collect

                val gps = locationTracker.gpsState.value
                val status = if (state.isEmergencyBraking) {
                    "⚠️ YOU ARE BRAKING!\nSpeed: ${gps.speedKmh.toInt()} km/h\nAccel: %.2f m/s²".format(state.longitudinalAcceleration)
                } else {
                    "✅ Cruising\nSpeed: ${gps.speedKmh.toInt()} km/h\nAccel: %.2f m/s²".format(state.longitudinalAcceleration)
                }
                statusText.text = status
            }
        }
    }

    private fun observeNetworkAlerts() {
        lifecycleScope.launch {
            meshNetwork.incomingTelemetry.collect { packet ->
                if (packet.hazardFlag.toInt() == 1) {
                    isShowingAlert = true

                    // Add the real networking car to the visual radar at an estimated 25 meters
                    radarView.activeNodes = listOf(RadarView.RadarNode(packet.nodeId.toString(), 25f, true))
                    radarView.invalidate()

                    statusText.text = "🚨 CAR ${packet.nodeId} BRAKED HARD! 🚨\nSpeed dropped to: ${packet.speedKmh} km/h"
                    statusText.setBackgroundColor(Color.RED)
                    statusText.setTextColor(Color.WHITE)

                    delay(3000)

                    statusText.setBackgroundColor(Color.TRANSPARENT)
                    statusText.setTextColor(Color.WHITE)
                    isShowingAlert = false
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
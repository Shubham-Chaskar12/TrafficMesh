package com.vanet.trafficmesh

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vanet.trafficmesh.model.VehicleTelemetryPacket
import com.vanet.trafficmesh.model.VehicleType
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
    private lateinit var radarView: RadarView

    private var isShowingAlert = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startAllSystems()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup the Radar UI
        radarView = RadarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // 2. Setup the interactive Car Selector Buttons
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 20, 10, 20)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        val types = listOf(VehicleType.HATCHBACK, VehicleType.SEDAN, VehicleType.SUV, VehicleType.TRUCK)
        types.forEach { vehicle ->
            val btn = Button(this).apply {
                text = vehicle.displayName
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    radarView.egoVehicle = vehicle
                    radarView.invalidate() // Redraw the UI instantly
                }
            }
            btnLayout.addView(btn)
        }

        // 3. Setup the Status Text
        statusText = TextView(this).apply {
            textSize = 20f
            setPadding(40, 40, 40, 60)
            text = "Initializing Systems..."
            setTextColor(Color.WHITE)
        }

        // 4. Stack them vertically
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            addView(radarView)
            addView(btnLayout)
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

                // Broadcasting our stable 16-byte packet
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

    private fun startGhostCarSimulator() {
        lifecycleScope.launch {
            while (true) {
                // Ghost car cruising 40m ahead as an SUV
                radarView.activeNodes = listOf(RadarView.RadarNode("7777", 40f, false, VehicleType.SUV))
                radarView.invalidate()

                delay(8000)

                // Ghost car slams brakes!
                isShowingAlert = true
                radarView.activeNodes = listOf(RadarView.RadarNode("7777", 20f, true, VehicleType.SUV))
                radarView.invalidate()

                statusText.text = "👻 GHOST CAR 7777 BRAKED HARD! 🚨\nGap closed to 20m!"
                statusText.setBackgroundColor(Color.parseColor("#CC0000"))
                statusText.setTextColor(Color.WHITE)

                delay(3000)

                statusText.setBackgroundColor(Color.TRANSPARENT)
                statusText.setTextColor(Color.WHITE)
                isShowingAlert = false
            }
        }
    }

    private fun observeKinematics() {
        lifecycleScope.launch {
            kinematicsEngine.motionState.collect { state ->
                radarView.isEgoBraking = state.isEmergencyBraking
                radarView.invalidate()

                if (isShowingAlert) return@collect

                val gps = locationTracker.gpsState.value

                // Read the Gyroscope to detect lane changes
                val maneuverStatus = when {
                    state.yawRateDegrees > 10f -> "🔄 MERGING LEFT"
                    state.yawRateDegrees < -10f -> "🔄 MERGING RIGHT"
                    else -> "⬆️ HOLDING LANE"
                }

                val status = if (state.isEmergencyBraking) {
                    "⚠️ YOU ARE BRAKING!\n$maneuverStatus\nSpeed: ${gps.speedKmh.toInt()} km/h"
                } else {
                    "✅ Cruising\n$maneuverStatus\nSpeed: ${gps.speedKmh.toInt()} km/h"
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

                    val distanceEst = 25f
                    // Render incoming real-network cars as standard Sedans for now
                    radarView.activeNodes = listOf(RadarView.RadarNode(packet.nodeId.toString(), distanceEst, true, VehicleType.SEDAN))
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
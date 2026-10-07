package com.vanet.trafficmesh

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
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

    private lateinit var commandBanner: TextView
    private lateinit var radarView: RadarView

    // Map Widgets
    private lateinit var mapToggleButton: TextView
    private lateinit var smallMapWidget: View
    private lateinit var expandedMapWidget: View

    private lateinit var speedText: TextView
    private lateinit var nodeText: TextView
    private lateinit var statusText: TextView

    // Panels
    private lateinit var mainSettingsPanel: LinearLayout
    private lateinit var registerVehiclePanel: LinearLayout
    private lateinit var rideDetailsPanel: LinearLayout

    private val navItems = mutableListOf<LinearLayout>()

    private var isShowingAlert = false
    private val liveTrafficMap = mutableMapOf<String, RadarView.RadarNode>()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startAllSystems()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density

        // --- 1. TOP LEFT TELEMETRY PANEL ---
        val leftTelemetryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.TOP
            }
        }

        speedText = TextView(this).apply {
            text = "0"
            textSize = 48f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
        }
        val speedLabel = TextView(this).apply {
            text = "KM/H"
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        nodeText = TextView(this).apply {
            text = "📡 MESH NODES: 0"
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#00FFFF"))
            setPadding(0, 20, 0, 0)
        }
        statusText = TextView(this).apply {
            text = "STATUS: STANDBY"
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#00FF87"))
        }

        leftTelemetryPanel.addView(speedText)
        leftTelemetryPanel.addView(speedLabel)
        leftTelemetryPanel.addView(nodeText)
        leftTelemetryPanel.addView(statusText)

        // --- 2. THE COMMAND BANNER ---
        commandBanner = TextView(this).apply {
            text = "SYSTEM NORMAL - CRUISING"
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#A0A2AB"))
            gravity = Gravity.CENTER
            setPadding(40, 50, 40, 50)
            visibility = View.VISIBLE

            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(40f, 40f, 40f, 40f, 0f, 0f, 0f, 0f)
                setColor(Color.parseColor("#15161C"))
                setStroke(4, Color.parseColor("#2A2D35"))
            }

            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
        }

        // --- 3. THE RADAR ---
        radarView = RadarView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // --- 4. MAP WIDGETS & TOGGLE BUTTON ---
        smallMapWidget = buildMockGoogleMapsWidget(density, isExpanded = false) {
            smallMapWidget.visibility = View.GONE
            expandedMapWidget.visibility = View.VISIBLE
        }.apply { visibility = View.GONE }

        expandedMapWidget = buildMockGoogleMapsWidget(density, isExpanded = true) {
            expandedMapWidget.visibility = View.GONE
            smallMapWidget.visibility = View.VISIBLE
        }.apply { visibility = View.GONE }

        mapToggleButton = TextView(this).apply {
            text = "🗺️ MAP"
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(30, 40, 30, 40)
            background = GradientDrawable().apply {
                cornerRadius = 100f
                setColor(Color.parseColor("#64b365"))
                setStroke(5, Color.parseColor("#e84f4f"))
            }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.TOP
                setMargins(0, (40 * density).toInt(), (40 * density).toInt(), 0)
            }
            setOnClickListener {
                if (smallMapWidget.visibility == View.VISIBLE || expandedMapWidget.visibility == View.VISIBLE) {
                    smallMapWidget.visibility = View.GONE
                    expandedMapWidget.visibility = View.GONE
                } else {
                    smallMapWidget.visibility = View.VISIBLE
                }
            }
        }

        // --- 5. BUILD SETTINGS & RIDE DETAILS MENUS ---
        buildMenus(density)

        // --- 6. RADAR CONTAINER ---
        val radarContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#08080C"))

            addView(radarView)
            addView(leftTelemetryPanel)
            addView(mapToggleButton)
            addView(smallMapWidget)
            addView(expandedMapWidget)
            addView(rideDetailsPanel)
            addView(mainSettingsPanel)
            addView(registerVehiclePanel)
            addView(commandBanner)
        }

        // --- 7. BOTTOM NAVIGATION BAR ---
        val bottomNavBar = buildBottomNavigationBar(density)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#08080C"))
            addView(radarContainer)
            addView(bottomNavBar)
        }

        setContentView(rootLayout)

        kinematicsEngine = KinematicsEngine(this)
        locationTracker = LocationTracker(this)
        meshNetwork = MeshNetwork(this)

        checkAndRequestPermissions()
        observeKinematics()
        observeNetworkAlerts()
        observeGps()
    }

    private fun buildBottomNavigationBar(density: Float): LinearLayout {
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#12141A"))
            setPadding(0, (15 * density).toInt(), 0, (15 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12141A"))
                setStroke(2, Color.parseColor("#2A2D35"))
            }
        }

        // Updated tabs: Replaced Map with Ride Details
        val homeItem = createNavItem("🏠", "Home", true) { selectTab(0) }
        val detailsItem = createNavItem("📊", "Ride Details", false) { selectTab(1) }
        val alertsItem = createNavItem("🔔", "Alerts", false) { selectTab(2) }
        val settingsItem = createNavItem("⚙️", "Settings", false) { selectTab(3) }

        navItems.add(homeItem)
        navItems.add(detailsItem)
        navItems.add(alertsItem)
        navItems.add(settingsItem)

        navBar.addView(homeItem)
        navBar.addView(detailsItem)
        navBar.addView(alertsItem)
        navBar.addView(settingsItem)

        return navBar
    }

    private fun createNavItem(icon: String, labelText: String, isSelected: Boolean, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val iconView = TextView(this@MainActivity).apply {
                text = icon
                textSize = 24f
                gravity = Gravity.CENTER
                alpha = if (isSelected) 1.0f else 0.5f
            }

            val labelView = TextView(this@MainActivity).apply {
                text = labelText
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(if (isSelected) Color.parseColor("#3399FF") else Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                setPadding(0, 5, 0, 0)
            }

            addView(iconView)
            addView(labelView)
            setOnClickListener { onClick() }
        }
    }

    private fun selectTab(index: Int) {
        navItems.forEachIndexed { i, item ->
            val iconView = item.getChildAt(0) as TextView
            val labelView = item.getChildAt(1) as TextView

            if (i == index) {
                iconView.alpha = 1.0f
                labelView.setTextColor(Color.parseColor("#3399FF"))
            } else {
                iconView.alpha = 0.5f
                labelView.setTextColor(Color.parseColor("#888888"))
            }
        }

        // Close all center overlays first
        mainSettingsPanel.visibility = View.GONE
        registerVehiclePanel.visibility = View.GONE
        rideDetailsPanel.visibility = View.GONE

        when (index) {
            0 -> { // Home
                // Just leaves the radar visible
            }
            1 -> { // Ride Details
                rideDetailsPanel.visibility = View.VISIBLE
            }
            2 -> { // Alerts (Placeholder)

            }
            3 -> { // Settings
                mainSettingsPanel.visibility = View.VISIBLE
            }
        }
    }

    private fun buildMenus(density: Float) {
        // --- RIDE DETAILS PANEL ---
        rideDetailsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#EE0B0C10"))
            setPadding(50, 50, 50, 50)
            layoutParams = FrameLayout.LayoutParams((300 * density).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(Color.parseColor("#F212141A"))
                setStroke(3, Color.parseColor("#3A3D45"))
            }

            val title = TextView(this@MainActivity).apply {
                text = "RIDE DETAILS"
                textSize = 16f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            val stats = TextView(this@MainActivity).apply {
                text = "\nDistance: 12.4 km\nAvg Speed: 45 km/h\nTravel Time: 24 mins\nMesh Relays: 142"
                textSize = 14f
                setTextColor(Color.parseColor("#AAAAAA"))
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 0)
            }
            addView(title)
            addView(stats)
        }

        // --- MAIN SETTINGS PANEL ---
        mainSettingsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#EE0B0C10"))
            setPadding(50, 50, 50, 50)
            layoutParams = FrameLayout.LayoutParams((300 * density).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(Color.parseColor("#F212141A"))
                setStroke(3, Color.parseColor("#3A3D45"))
            }
        }

        val settingsTitle = TextView(this).apply {
            text = "SYSTEM SETTINGS"
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        mainSettingsPanel.addView(settingsTitle)

        val options = listOf("Register Vehicle", "Mesh Network Config", "Display Preferences", "About TrafficMesh")
        options.forEach { optionText ->
            val btn = TextView(this).apply {
                text = optionText
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 40)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 20)
                }
                background = GradientDrawable().apply {
                    cornerRadius = 15f
                    setColor(Color.parseColor("#1F2229"))
                }
                setOnClickListener {
                    if (optionText == "Register Vehicle") {
                        mainSettingsPanel.visibility = View.GONE
                        registerVehiclePanel.visibility = View.VISIBLE
                    }
                }
            }
            mainSettingsPanel.addView(btn)
        }

        // --- REGISTER VEHICLE PANEL ---
        registerVehiclePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#12141A"))
            setPadding(60, 60, 60, 60)
            layoutParams = FrameLayout.LayoutParams((340 * density).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#12141A"))
                setStroke(3, Color.parseColor("#2A2D35"))
            }
        }

        val regTitle = TextView(this).apply {
            text = "Register Your Vehicle"
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#3399FF"))
            gravity = Gravity.CENTER
        }
        val regSubtitle = TextView(this).apply {
            text = "Select vehicle type"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 40)
        }
        registerVehiclePanel.addView(regTitle)
        registerVehiclePanel.addView(regSubtitle)

        val gridLayout = GridLayout(this).apply {
            columnCount = 3
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }

        val vehicleIcons = listOf(
            Pair(VehicleType.HATCHBACK, "🚗"), Pair(VehicleType.SEDAN, "🚘"), Pair(VehicleType.SUV, "🚙"), Pair(VehicleType.TRUCK, "🚛")
        )

        var selectedType = radarView.egoVehicle
        val cardViews = mutableListOf<LinearLayout>()

        vehicleIcons.forEachIndexed { index, (type, icon) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(20, 30, 20, 30)
                val params = GridLayout.LayoutParams().apply {
                    width = (80 * density).toInt()
                    height = (90 * density).toInt()
                    setMargins(10, 10, 10, 10)
                }
                layoutParams = params

                val isSelected = type == selectedType
                background = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(if (isSelected) Color.parseColor("#1A253A") else Color.parseColor("#1C1E26"))
                    if (isSelected) setStroke(4, Color.parseColor("#3399FF")) else setStroke(2, Color.parseColor("#2A2D35"))
                }

                val emoji = TextView(this@MainActivity).apply { text = icon; textSize = 28f; gravity = Gravity.CENTER }
                val label = TextView(this@MainActivity).apply {
                    text = type.displayName
                    textSize = 12f
                    setTextColor(if (isSelected) Color.parseColor("#3399FF") else Color.parseColor("#AAAAAA"))
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setPadding(0, 10, 0, 0)
                }

                addView(emoji)
                addView(label)
                cardViews.add(this)

                setOnClickListener {
                    selectedType = type
                    cardViews.forEachIndexed { i, c ->
                        val childLabel = c.getChildAt(1) as TextView
                        val isCardSelected = i == index
                        (c.background as GradientDrawable).apply {
                            setColor(if (isCardSelected) Color.parseColor("#1A253A") else Color.parseColor("#1C1E26"))
                            if (isCardSelected) setStroke(4, Color.parseColor("#3399FF")) else setStroke(2, Color.parseColor("#2A2D35"))
                        }
                        childLabel.setTextColor(if (isCardSelected) Color.parseColor("#3399FF") else Color.parseColor("#AAAAAA"))
                    }
                }
            }
            gridLayout.addView(card)
        }

        registerVehiclePanel.addView(gridLayout)

        val inputLabel = TextView(this).apply {
            text = "Vehicle Name (Optional)"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 40, 0, 10)
        }
        val nameInput = EditText(this).apply {
            hint = "e.g. MyCar"
            setHintTextColor(Color.parseColor("#555555"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(30, 30, 30, 30)
            inputType = InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply {
                cornerRadius = 15f
                setColor(Color.parseColor("#1C1E26"))
                setStroke(2, Color.parseColor("#2A2D35"))
            }
        }
        registerVehiclePanel.addView(inputLabel)
        registerVehiclePanel.addView(nameInput)

        val continueBtn = TextView(this).apply {
            text = "Continue"
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 50, 0, 0)
            }
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0066FF"))
            }
            setOnClickListener {
                radarView.egoVehicle = selectedType
                radarView.invalidate()
                registerVehiclePanel.visibility = View.GONE
                selectTab(0)
            }
        }
        registerVehiclePanel.addView(continueBtn)
    }

    private fun buildMockGoogleMapsWidget(density: Float, isExpanded: Boolean, onToggle: () -> Unit): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val sizePx = if (isExpanded) (300 * density).toInt() else (140 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                if (isExpanded) {
                    gravity = Gravity.CENTER
                } else {
                    gravity = Gravity.END or Gravity.TOP
                    // Placed securely under the map toggle button
                    setMargins(0, (100 * density).toInt(), (40 * density).toInt(), 0)
                }
            }
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(Color.parseColor("#12141A"))
                setStroke(3, Color.parseColor("#2A2D35"))
            }
            clipToOutline = true
        }

        val mapArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#5a678f"))

            val navArrow = TextView(this@MainActivity).apply {
                text = "➤"
                textSize = if (isExpanded) 48f else 32f
                setTextColor(Color.parseColor("#3399FF"))
                rotation = -45f
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
            }

            // The disappearing Enlarge/Minimize icon
            val toggleBtn = TextView(this@MainActivity).apply {
                text = if (isExpanded) "⤣" else "⤢" // Minimize or Enlarge arrows
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(20, 20, 20, 20)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.END
                }
                setOnClickListener { onToggle() }
            }

            val googleMapsLabel = TextView(this@MainActivity).apply {
                text = "Google Maps"
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.parseColor("#A0A0A0"))
                setPadding(20, 10, 20, 10)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                }
            }
            addView(navArrow)
            addView(toggleBtn)
            addView(googleMapsLabel)
        }

        val infoArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Color.parseColor("#5a5e4e"))
            setPadding(20, 20, 20, 20)
            gravity = Gravity.CENTER_VERTICAL
            val arrows = TextView(this@MainActivity).apply {
                text = "↰ "
                textSize = 16f
                setTextColor(Color.parseColor("#88C0FF"))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val distanceText = TextView(this@MainActivity).apply {
                text = "2.4 km to dest."
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            addView(arrows)
            addView(distanceText)
        }
        container.addView(mapArea)
        container.addView(infoArea)
        return container
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
        if (missing.isEmpty()) { startAllSystems() } else { permissionLauncher.launch(missing.toTypedArray()) }
    }

    private fun startAllSystems() {
        kinematicsEngine.start()
        locationTracker.start()
        meshNetwork.startMesh()
        startTelemetryLoop()
        statusText.text = "STATUS: ACTIVE"
    }

    private fun startTelemetryLoop() {
        lifecycleScope.launch {
            while (true) {
                val motion = kinematicsEngine.motionState.value
                val gps = locationTracker.gpsState.value
                val conditionCode: Byte = when {
                    motion.isEmergencyBraking -> 1
                    motion.yawRateDegrees > 10f -> 2
                    motion.yawRateDegrees < -10f -> 3
                    else -> 0
                }
                val packet = VehicleTelemetryPacket(
                    nodeId = meshNetwork.myNodeId,
                    timestampMs = System.currentTimeMillis().toInt(),
                    lat = 0f, lon = 0f,
                    speedKmh = gps.speedKmh.toInt().toShort(),
                    headingDeg = gps.heading.toInt().toShort(),
                    longitudinalG = (motion.longitudinalAcceleration * 100).toInt().toShort(),
                    conditionCode = conditionCode, ttl = 3
                )
                meshNetwork.broadcastTelemetry(packet)
                delay(500)
            }
        }
    }

    private fun observeKinematics() {
        lifecycleScope.launch {
            kinematicsEngine.motionState.collect { state ->
                radarView.isEgoBraking = state.isEmergencyBraking
                radarView.egoRotationDegrees = -(state.yawRateDegrees).coerceIn(-30f, 30f)
                radarView.invalidate()

                if (isShowingAlert) return@collect

                if (state.isEmergencyBraking) {
                    showBanner("⚠️ HARD BRAKING DETECTED", "#FF3366", "#FFFFFF")
                } else if (state.yawRateDegrees > 10f) {
                    showBanner("🔄 MERGING LEFT", "#E67E22", "#FFFFFF")
                } else if (state.yawRateDegrees < -10f) {
                    showBanner("🔄 MERGING RIGHT", "#E67E22", "#FFFFFF")
                } else {
                    showBanner("SYSTEM NORMAL - CRUISING", "#15161C", "#A0A2AB")
                }
            }
        }
    }

    private fun observeGps() {
        lifecycleScope.launch { locationTracker.gpsState.collect { gps -> speedText.text = gps.speedKmh.toInt().toString() } }
    }

    private fun observeNetworkAlerts() {
        lifecycleScope.launch {
            meshNetwork.incomingTelemetry.collect { packet ->
                val nodeId = packet.nodeId.toString()
                val code = packet.conditionCode.toInt()

                val existingNode = liveTrafficMap[nodeId]
                val laneOffset = existingNode?.deltaXMeters ?: listOf(-7f, -3.5f, 0f, 3.5f, 7f).random()
                val distanceAhead = existingNode?.deltaYMeters ?: (10..40).random().toFloat()
                val assignedType = existingNode?.type ?: listOf(VehicleType.SEDAN, VehicleType.HATCHBACK, VehicleType.SUV).random()

                liveTrafficMap[nodeId] = RadarView.RadarNode(
                    id = nodeId,
                    deltaXMeters = laneOffset,
                    deltaYMeters = distanceAhead,
                    isHazard = code == 1,
                    type = assignedType
                )

                radarView.activeNodes = liveTrafficMap.values.toList()
                radarView.invalidate()
                nodeText.text = "📡 MESH NODES: ${liveTrafficMap.size}"

                if (code > 0) {
                    isShowingAlert = true
                    when (code) {
                        1 -> showBanner("🚨 CAR $nodeId BRAKED HARD!", "#FF3366", "#FFFFFF")
                        2 -> showBanner("🔄 CAR $nodeId MERGING LEFT", "#E67E22", "#FFFFFF")
                        3 -> showBanner("🔄 CAR $nodeId MERGING RIGHT", "#E67E22", "#FFFFFF")
                    }
                    delay(3000)
                    isShowingAlert = false
                    showBanner("SYSTEM NORMAL - CRUISING", "#15161C", "#A0A2AB")
                }
            }
        }
    }

    private fun showBanner(message: String, hexBgColor: String, hexTextColor: String) {
        commandBanner.text = message
        commandBanner.setTextColor(Color.parseColor(hexTextColor))
        (commandBanner.background as GradientDrawable).setColor(Color.parseColor(hexBgColor))
    }

    override fun onDestroy() {
        super.onDestroy()
        kinematicsEngine.stop()
        locationTracker.stop()
        meshNetwork.stopMesh()
    }
}
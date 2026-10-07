package com.vanet.trafficmesh.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import com.vanet.trafficmesh.model.VehicleType

class RadarView(context: Context) : View(context) {

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val bgPaint = Paint().apply { color = Color.parseColor("#1C1E26") }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#6C6F7D")
        strokeWidth = 6f // Slightly thinner to fit 5 lanes nicely
        pathEffect = DashPathEffect(floatArrayOf(60f, 40f), 0f)
    }

    private val meshLinkPaint = Paint().apply {
        color = Color.parseColor("#00FFFF")
        strokeWidth = 5f
        isAntiAlias = true
        alpha = 220
        setShadowLayer(20f, 0f, 0f, Color.parseColor("#00FFFF"))
    }

    private val egoPaint = Paint().apply {
        color = Color.parseColor("#00FF87")
        setShadowLayer(35f, 0f, 0f, Color.parseColor("#00FF87"))
    }

    private val peerPaint = Paint().apply {
        color = Color.parseColor("#3399FF")
        setShadowLayer(35f, 0f, 0f, Color.parseColor("#3399FF"))
    }

    private val hazardPaint = Paint().apply {
        color = Color.parseColor("#FF2A55")
        setShadowLayer(45f, 0f, 0f, Color.parseColor("#FF2A55"))
    }

    private val cabinPaint = Paint().apply {
        color = Color.parseColor("#15161C")
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#A0A2AB")
        textSize = 20f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
    }

    private val carTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
    }

    var isEgoBraking = false
    var egoVehicle = VehicleType.SEDAN
    var egoRotationDegrees = 0f

    private val PIXELS_PER_METER = 45f

    data class RadarNode(val id: String, val deltaXMeters: Float, val deltaYMeters: Float, val isHazard: Boolean, val type: VehicleType)
    var activeNodes = listOf<RadarNode>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // DRAW 5 LANES (Requires 4 dividing lines at 20%, 40%, 60%, 80%)
        canvas.drawLine(w * 0.2f, 0f, w * 0.2f, h, linePaint)
        canvas.drawLine(w * 0.4f, 0f, w * 0.4f, h, linePaint)
        canvas.drawLine(w * 0.6f, 0f, w * 0.6f, h, linePaint)
        canvas.drawLine(w * 0.8f, 0f, w * 0.8f, h, linePaint)

        // Ego vehicle is locked to the dead center (Lane 3)
        val egoX = w / 2f
        val egoY = h * 0.50f

        val distanceMarkers = listOf(15f, 30f, -15f)
        distanceMarkers.forEach { dist ->
            val markY = egoY - (dist * PIXELS_PER_METER)
            canvas.drawText("${Math.abs(dist).toInt()}m", 20f, markY - 10f, labelPaint)
            canvas.drawLine(0f, markY, w, markY, Paint().apply { color = Color.parseColor("#2A2D38"); strokeWidth = 3f })
        }

        // 1. DRAW MESH CONNECTIONS
        activeNodes.forEach { node ->
            val nodeX = egoX + (node.deltaXMeters * PIXELS_PER_METER)
            val nodeY = egoY - (node.deltaYMeters * PIXELS_PER_METER)
            canvas.drawLine(egoX, egoY, nodeX, nodeY, meshLinkPaint)
        }

        // 2. DRAW EGO CAR
        drawVehicle(canvas, egoX, egoY, egoVehicle, if (isEgoBraking) hazardPaint else egoPaint, "YOU", egoRotationDegrees)

        // 3. DRAW PEER CARS
        activeNodes.forEach { node ->
            val nodeX = egoX + (node.deltaXMeters * PIXELS_PER_METER)
            val nodeY = egoY - (node.deltaYMeters * PIXELS_PER_METER)
            drawVehicle(canvas, nodeX, nodeY, node.type, if (node.isHazard) hazardPaint else peerPaint, node.id, 0f)
        }
    }

    private fun drawVehicle(canvas: Canvas, x: Float, y: Float, type: VehicleType, bodyPaint: Paint, label: String, rotationDegrees: Float) {
        canvas.save()
        canvas.rotate(rotationDegrees, x, y)
        val w = (type.widthMeters * PIXELS_PER_METER) / 2f
        val l = (type.lengthMeters * PIXELS_PER_METER) / 2f

        canvas.drawRoundRect(x - w, y - l, x + w, y + l, 16f, 16f, bodyPaint)

        val roofW = w * 0.75f
        val roofL = l * 0.60f
        val roofOffsetY = l * 0.1f

        canvas.drawRoundRect(x - roofW, (y - roofL) + roofOffsetY, x + roofW, (y + roofL) + roofOffsetY, 8f, 8f, cabinPaint)
        canvas.drawText(label, x, y + roofOffsetY + 7f, carTextPaint)
        canvas.restore()
    }
}
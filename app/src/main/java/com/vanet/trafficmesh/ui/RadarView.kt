package com.vanet.trafficmesh.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import com.vanet.trafficmesh.model.VehicleType

class RadarView(context: Context) : View(context) {

    private val bgPaint = Paint().apply { color = Color.parseColor("#1A1A2E") }
    private val linePaint = Paint().apply {
        color = Color.parseColor("#4A4E69")
        strokeWidth = 8f
        pathEffect = DashPathEffect(floatArrayOf(40f, 40f), 0f)
    }
    private val egoPaint = Paint().apply { color = Color.parseColor("#00FF87") }
    private val peerPaint = Paint().apply { color = Color.parseColor("#60A5FA") }
    private val hazardPaint = Paint().apply { color = Color.parseColor("#FF3366") }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    var isEgoBraking = false

    // Default to a standard Sedan
    var egoVehicle = VehicleType.SEDAN

    // 1 physical meter equals 25 pixels on the screen
    private val PIXELS_PER_METER = 25f

    data class RadarNode(val id: String, val distanceMeters: Float, val isBraking: Boolean, val type: VehicleType = VehicleType.SEDAN)
    var activeNodes = listOf<RadarNode>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Background & Lanes
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawLine(w * 0.25f, 0f, w * 0.25f, h, linePaint)
        canvas.drawLine(w * 0.75f, 0f, w * 0.75f, h, linePaint)

        // Calculate EGO (Your Car) exact pixel dimensions based on the chosen type
        val egoX = w / 2f
        val egoY = h * 0.85f
        val halfWidth = (egoVehicle.widthMeters * PIXELS_PER_METER) / 2f
        val halfLength = (egoVehicle.lengthMeters * PIXELS_PER_METER) / 2f

        // Draw Your Car scaled to its real size
        canvas.drawRoundRect(
            egoX - halfWidth, egoY - halfLength,
            egoX + halfWidth, egoY + halfLength,
            15f, 15f,
            if (isEgoBraking) hazardPaint else egoPaint
        )
        canvas.drawText(egoVehicle.displayName, egoX, egoY + halfLength + 40f, textPaint)

        // Draw the Ghost Cars (also scaled to their specific dimensions)
        activeNodes.forEach { node ->
            val nodeX = w / 2f
            val nodeY = egoY - (node.distanceMeters * PIXELS_PER_METER) // Keep visual distance accurate to scale

            val nHalfW = (node.type.widthMeters * PIXELS_PER_METER) / 2f
            val nHalfL = (node.type.lengthMeters * PIXELS_PER_METER) / 2f

            canvas.drawRoundRect(
                nodeX - nHalfW, nodeY - nHalfL,
                nodeX + nHalfW, nodeY + nHalfL,
                15f, 15f,
                if (node.isBraking) hazardPaint else peerPaint
            )
            canvas.drawText("CAR ${node.id}", nodeX, nodeY - nHalfL - 20f, textPaint)
        }
    }
}
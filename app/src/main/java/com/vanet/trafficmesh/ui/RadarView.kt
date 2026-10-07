package com.vanet.trafficmesh.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View

class RadarView(context: Context) : View(context) {

    // --- Paint Styles for our Graphics ---
    private val bgPaint = Paint().apply { color = Color.parseColor("#1A1A2E") } // Deep Space Blue
    private val linePaint = Paint().apply {
        color = Color.parseColor("#4A4E69")
        strokeWidth = 8f
        pathEffect = DashPathEffect(floatArrayOf(40f, 40f), 0f) // Creates dashed lane lines
    }
    private val egoPaint = Paint().apply { color = Color.parseColor("#00FF87") } // Neon Green (Your Car)
    private val peerPaint = Paint().apply { color = Color.parseColor("#60A5FA") } // Light Blue (Other Cars)
    private val hazardPaint = Paint().apply { color = Color.parseColor("#FF3366") } // Neon Red (Braking)

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 35f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // --- Live Data Trackers ---
    var isEgoBraking = false

    // Defines a car on the radar
    data class RadarNode(val id: String, val distanceMeters: Float, val isBraking: Boolean)
    var activeNodes = listOf<RadarNode>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw Highway Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Draw Dashed Lane Lines
        canvas.drawLine(w * 0.25f, 0f, w * 0.25f, h, linePaint)
        canvas.drawLine(w * 0.75f, 0f, w * 0.75f, h, linePaint)

        // 3. Draw Your Car (Bottom Center)
        val egoX = w / 2f
        val egoY = h * 0.85f // Placed near the bottom of the screen
        canvas.drawRoundRect(egoX - 45f, egoY - 90f, egoX + 45f, egoY + 90f, 25f, 25f, if (isEgoBraking) hazardPaint else egoPaint)
        canvas.drawText("YOU", egoX, egoY + 130f, textPaint)

        // 4. Draw Connected Cars (The Ghost Car)
        activeNodes.forEach { node ->
            val nodeX = w / 2f
            // Map physical meters to screen pixels (1 meter = 15 pixels)
            val nodeY = egoY - (node.distanceMeters * 15f)

            canvas.drawRoundRect(nodeX - 45f, nodeY - 90f, nodeX + 45f, nodeY + 90f, 25f, 25f, if (node.isBraking) hazardPaint else peerPaint)
            canvas.drawText("CAR ${node.id}", nodeX, nodeY - 110f, textPaint)
        }
    }
}
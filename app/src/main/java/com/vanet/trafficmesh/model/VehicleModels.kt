package com.vanet.trafficmesh.model

import java.nio.ByteBuffer

// --- 1. UI DIMENSIONS (Used by RadarView to draw the car) ---
enum class VehicleType(val displayName: String, val widthMeters: Float, val lengthMeters: Float) {
    HATCHBACK("Hatchback", 1.7f, 4.0f),
    SEDAN("Sedan", 1.9f, 4.8f),
    SUV("SUV", 2.0f, 5.2f),
    TRUCK("Truck", 2.5f, 7.5f)
}

// --- 2. NETWORK PACKET (Your stable 16-byte baseline) ---
data class VehicleTelemetryPacket(
    val nodeId: Int,
    val timestampMs: Int,
    val speedKmh: Short,
    val headingDeg: Short,
    val longitudinalG: Short,
    val hazardFlag: Short
) {
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(16)
        buffer.putInt(nodeId)
        buffer.putInt(timestampMs)
        buffer.putShort(speedKmh)
        buffer.putShort(headingDeg)
        buffer.putShort(longitudinalG)
        buffer.putShort(hazardFlag)
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): VehicleTelemetryPacket {
            val buffer = ByteBuffer.wrap(bytes)
            return VehicleTelemetryPacket(
                nodeId = buffer.int,
                timestampMs = buffer.int,
                speedKmh = buffer.short,
                headingDeg = buffer.short,
                longitudinalG = buffer.short,
                hazardFlag = buffer.short
            )
        }
    }
}
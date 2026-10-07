package com.vanet.trafficmesh.model

import java.nio.ByteBuffer

enum class VehicleType(val displayName: String, val lengthMeters: Float, val widthMeters: Float) {
    TWO_WHEELER("Two-Wheeler", 2.0f, 0.8f),
    HATCHBACK("Hatchback", 3.8f, 1.7f),
    SEDAN("Sedan", 4.6f, 1.8f),
    SUV("SUV / Van", 4.9f, 1.9f),
    HEAVY_VEHICLE("Bus / Truck", 10.5f, 2.5f)
}

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
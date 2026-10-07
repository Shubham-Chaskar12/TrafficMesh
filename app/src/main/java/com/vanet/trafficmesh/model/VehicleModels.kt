package com.vanet.trafficmesh.model

import java.nio.ByteBuffer

// --- 1. UI DIMENSIONS ---
enum class VehicleType(val displayName: String, val widthMeters: Float, val lengthMeters: Float) {
    HATCHBACK("Hatchback", 1.7f, 4.0f),
    SEDAN("Sedan", 1.9f, 4.8f),
    SUV("SUV", 2.0f, 5.2f),
    TRUCK("Truck", 2.5f, 7.5f)
}

// --- 2. THE 24-BYTE MESH PACKET ---
data class VehicleTelemetryPacket(
    val nodeId: Int,
    val timestampMs: Int,
    val lat: Float,
    val lon: Float,
    val speedKmh: Short,
    val headingDeg: Short,
    val longitudinalG: Short,
    val conditionCode: Byte,
    val ttl: Byte
) {
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(24)
        buffer.putInt(nodeId)
        buffer.putInt(timestampMs)
        buffer.putFloat(lat)
        buffer.putFloat(lon)
        buffer.putShort(speedKmh)
        buffer.putShort(headingDeg)
        buffer.putShort(longitudinalG)
        buffer.put(conditionCode)
        buffer.put(ttl)
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): VehicleTelemetryPacket {
            val buffer = ByteBuffer.wrap(bytes)
            return VehicleTelemetryPacket(
                nodeId = buffer.int,
                timestampMs = buffer.int,
                lat = buffer.float,
                lon = buffer.float,
                speedKmh = buffer.short,
                headingDeg = buffer.short,
                longitudinalG = buffer.short,
                conditionCode = buffer.get(),
                ttl = buffer.get()
            )
        }
    }
}
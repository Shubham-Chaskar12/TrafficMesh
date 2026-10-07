package com.vanet.trafficmesh.network

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.vanet.trafficmesh.model.VehicleTelemetryPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MeshNetwork(private val context: Context) {

    private val strategy = Strategy.P2P_CLUSTER
    private val connectionsClient = Nearby.getConnectionsClient(context)
    val myNodeId = (1000..9999).random()

    private val connectedEndpoints = mutableListOf<String>()
    private val processedPackets = LinkedHashSet<String>()

    private val _incomingTelemetry = MutableSharedFlow<VehicleTelemetryPacket>()
    val incomingTelemetry = _incomingTelemetry.asSharedFlow()

    fun startMesh() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(myNodeId.toString(), context.packageName, connectionLifecycleCallback, options)

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(context.packageName, endpointDiscoveryCallback, discoveryOptions)
    }

    fun broadcastTelemetry(packet: VehicleTelemetryPacket, excludeEndpointId: String? = null) {
        val targets = connectedEndpoints.filter { it != excludeEndpointId }
        if (targets.isEmpty()) return

        val payload = Payload.fromBytes(packet.toByteArray())
        connectionsClient.sendPayload(targets, payload)
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(myNodeId.toString(), endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess && !connectedEndpoints.contains(endpointId)) {
                connectedEndpoints.add(endpointId)
            }
        }
        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val data = payload.asBytes()
            if (data != null && data.size == 24) { // Checked for new 24 byte size
                val packet = VehicleTelemetryPacket.fromByteArray(data)
                val messageSignature = "${packet.nodeId}_${packet.timestampMs}"

                if (!processedPackets.contains(messageSignature)) {
                    processedPackets.add(messageSignature)
                    if (processedPackets.size > 200) processedPackets.remove(processedPackets.first())

                    CoroutineScope(Dispatchers.Main).launch {
                        _incomingTelemetry.emit(packet)
                    }

                    if (packet.conditionCode > 0 && packet.ttl > 0) {
                        val forwardedPacket = packet.copy(ttl = (packet.ttl - 1).toByte())
                        broadcastTelemetry(forwardedPacket, excludeEndpointId = endpointId)
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun stopMesh() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectedEndpoints.clear()
        processedPackets.clear()
    }
}
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
    val myNodeId = (1000..9999).random() // Changed to an Int so it fits in our 16-byte packet

    private val connectedEndpoints = mutableListOf<String>()

    // Now we emit the full decoded packet instead of just a String
    private val _incomingTelemetry = MutableSharedFlow<VehicleTelemetryPacket>()
    val incomingTelemetry = _incomingTelemetry.asSharedFlow()

    fun startMesh() {
        Log.d("TrafficMesh", "Starting Google Nearby Mesh as Node: $myNodeId")
        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(myNodeId.toString(), context.packageName, connectionLifecycleCallback, options)
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(context.packageName, endpointDiscoveryCallback, options)
    }

    // NEW: Function to broadcast our live telemetry packet continuously
    fun broadcastTelemetry(packet: VehicleTelemetryPacket) {
        if (connectedEndpoints.isEmpty()) return

        // Convert our packet into a 16-byte array and send it
        val payload = Payload.fromBytes(packet.toByteArray())
        connectionsClient.sendPayload(connectedEndpoints, payload)
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
            if (result.status.isSuccess) connectedEndpoints.add(endpointId)
        }
        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val data = payload.asBytes()
            // Ensure we only process packets that are exactly 16 bytes
            if (data != null && data.size == 16) {
                val packet = VehicleTelemetryPacket.fromByteArray(data)

                // Send the decoded packet up to the UI
                CoroutineScope(Dispatchers.Main).launch {
                    _incomingTelemetry.emit(packet)
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun stopMesh() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
    }
}
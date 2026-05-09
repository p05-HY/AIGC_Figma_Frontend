package com.example.blueheartv.system

import org.json.JSONObject

data class NetworkStatus(
    val connected: Boolean,
    val validated: Boolean,
    val type: String,
    val isMetered: Boolean?,
    val ip: String?,
    val capabilityEstimate: CapabilityEstimate?,
    val transportSpecific: TransportSpecific?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("connected", connected)
            put("validated", validated)
            put("type", type)
            if (isMetered != null) {
                put("isMetered", isMetered)
            }
            if (ip != null) {
                put("ip", ip)
            }
            if (capabilityEstimate != null) {
                put("capabilityEstimate", capabilityEstimate.toJson())
            }
            if (transportSpecific != null) {
                put("transportSpecific", transportSpecific.toJson())
            }
        }
    }
}

data class CapabilityEstimate(
    val downKbps: Int,
    val upKbps: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("downKbps", downKbps)
            put("upKbps", upKbps)
            put("isEstimate", true)
        }
    }
}

sealed interface TransportSpecific {
    fun toJson(): JSONObject
}

data class WifiTransportSpecific(
    val rssi: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val ssid: String?
) : TransportSpecific {
    override fun toJson(): JSONObject {
        val wifi = JSONObject()
        if (rssi != null) wifi.put("rssi", rssi)
        if (linkSpeedMbps != null) wifi.put("linkSpeedMbps", linkSpeedMbps)
        if (frequencyMhz != null) wifi.put("frequencyMhz", frequencyMhz)
        if (ssid != null) wifi.put("ssid", ssid)
        return JSONObject().put("wifi", wifi)
    }
}

data class CellularTransportSpecific(
    val level: Int?
) : TransportSpecific {
    override fun toJson(): JSONObject {
        val cellular = JSONObject()
        if (level != null) cellular.put("level", level)
        return JSONObject().put("cellular", cellular)
    }
}

package com.example.blueheartv.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager

class NetworkStatusProvider(
    context: Context
) {
    private val appContext = context.applicationContext

    fun getStatus(): NetworkStatus {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkStatus(
                connected = false,
                validated = false,
                type = "unknown",
                isMetered = null,
                ip = null,
                capabilityEstimate = null,
                transportSpecific = null
            )

        val activeNetwork = connectivityManager.activeNetwork
            ?: return NetworkStatus(
                connected = false,
                validated = false,
                type = "none",
                isMetered = null,
                ip = null,
                capabilityEstimate = null,
                transportSpecific = null
            )

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
        val type = resolveType(capabilities)
        val isMetered = connectivityManager.isActiveNetworkMetered
        val ip = linkProperties?.linkAddresses?.let { pickIp(it) }

        return NetworkStatus(
            connected = true,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            type = type,
            isMetered = isMetered,
            ip = ip,
            capabilityEstimate = buildCapabilityEstimate(capabilities),
            transportSpecific = buildTransportSpecific(type)
        )
    }

    private fun resolveType(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return "unknown"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "unknown"
        }
    }

    private fun buildCapabilityEstimate(capabilities: NetworkCapabilities?): CapabilityEstimate? {
        if (capabilities == null) return null
        val downKbps = capabilities.linkDownstreamBandwidthKbps
        val upKbps = capabilities.linkUpstreamBandwidthKbps
        if (downKbps <= 0 && upKbps <= 0) return null
        return CapabilityEstimate(downKbps = downKbps, upKbps = upKbps)
    }

    private fun buildTransportSpecific(type: String): TransportSpecific? {
        return when (type) {
            "wifi" -> buildWifiTransportSpecific()
            "cellular" -> buildCellularTransportSpecific()
            else -> null
        }
    }

    private fun buildWifiTransportSpecific(): TransportSpecific? {
        val wifiManager = appContext.getSystemService(WifiManager::class.java) ?: return null
        val info = wifiManager.connectionInfo ?: return null
        val rssi = info.rssi.takeIf { it != INVALID_RSSI }
        val linkSpeedMbps = info.linkSpeed.takeIf { it > 0 }
        val frequencyMhz = info.frequency.takeIf { it > 0 }
        val ssid = normalizeSsid(info.ssid)

        if (rssi == null && linkSpeedMbps == null && frequencyMhz == null && ssid == null) {
            return null
        }
        return WifiTransportSpecific(
            rssi = rssi,
            linkSpeedMbps = linkSpeedMbps,
            frequencyMhz = frequencyMhz,
            ssid = ssid
        )
    }

    private fun buildCellularTransportSpecific(): TransportSpecific? {
        val telephonyManager = appContext.getSystemService(TelephonyManager::class.java) ?: return null
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager.signalStrength?.level
        } else {
            null
        }
        if (level == null) return null
        return CellularTransportSpecific(level = level)
    }

    private fun normalizeSsid(ssid: String?): String? {
        val value = ssid?.trim()?.trim('"')
        if (value.isNullOrBlank()) return null
        if (value.equals("<unknown ssid>", ignoreCase = true)) return null
        return value
    }

    private fun pickIp(addresses: List<LinkAddress>): String? {
        val ipv4 = addresses.firstOrNull { it.address.hostAddress?.contains('.') == true }
        if (ipv4 != null) return ipv4.address.hostAddress
        return addresses.firstOrNull()?.address?.hostAddress
    }

    private companion object {
        private const val INVALID_RSSI = -127
    }
}

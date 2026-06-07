package com.fiatlife.app.data.network

import android.net.Uri
import java.net.InetAddress

/**
 * Detect LAN / private hosts (Start9, WireGuard tunnel, mDNS, etc.) and build an
 * OkHttp client that accepts self-signed TLS — same idea as the web server's
 * `FL_INSECURE_RELAY_TLS` / `should_auto_insecure_relay_tls` path.
 */
object LanTls {

    fun hostFromUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return try {
            Uri.parse(trimmed).host
        } catch (_: Exception) {
            null
        }
    }

    fun isLanOrPrivateHost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h.isEmpty()) return false
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "[::1]") return true
        if (h.endsWith(".local") || h.endsWith(".startos")) return true
        return try {
            val addr = InetAddress.getByName(h)
            addr.isLoopbackAddress ||
                addr.isSiteLocalAddress ||
                isPrivateIpv4(addr.hostAddress)
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateIpv4(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) return false
        val (a, b) = octets[0] to octets[1]
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            else -> false
        }
    }

    /** True when [url] uses TLS (wss/https) and may need a trust bypass on home LAN/VPN. */
    fun usesTls(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("wss://") || lower.startsWith("https://")
    }

    /**
     * When the user has enabled the setting, trust self-signed certs for all TLS URLs
     * (wss/https). Matches nomoxcel: the user explicitly opts in for relays they control.
     */
    fun shouldTrustSelfSigned(url: String, userEnabled: Boolean): Boolean {
        if (!userEnabled) return false
        return usesTls(url)
    }
}

package com.fiatlife.app.data.network

/**
 * Relay URL validation. Only wss:// is allowed — ws:// would send private data
 * unencrypted between relay and client.
 */
object RelayUrlValidator {

    fun isSafeRelayUrl(url: String?): Boolean = errorMessage(url) == null

    fun errorMessage(url: String?): String? {
        val u = url?.trim() ?: return "Enter a relay URL"
        if (u.isEmpty()) return "Enter a relay URL"
        if (u.startsWith("ws://")) {
            return "Only wss:// is allowed. ws:// would send private data unencrypted between relay and client."
        }
        if (!u.startsWith("wss://")) {
            return "Relay URL must start with wss:// (encrypted connection)"
        }
        val host = u.removePrefix("wss://").split("/").firstOrNull()?.split(":")?.firstOrNull()?.trim() ?: ""
        if (host.isEmpty()) return "Enter a relay hostname after wss:// (e.g. wss://relay.example.com)"
        if (!host.contains(".") && host != "localhost" && !host.startsWith("[")) {
            return "Relay hostname doesn't look valid — check for typos"
        }
        return null
    }

    fun normalize(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.startsWith("wss://")) return trimmed
        val withoutScheme = trimmed.removePrefix("ws://")
        return "wss://$withoutScheme"
    }
}

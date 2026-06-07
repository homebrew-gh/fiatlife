package com.fiatlife.app.data.network

import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides OkHttp clients for relay WebSockets and Blossom HTTPS. Picks a trusting
 * client when the user has enabled self-signed TLS for wss/https URLs.
 */
@Singleton
class NetworkClients @Inject constructor(
    private val relayTlsSettings: RelayTlsSettings
) {
    fun clientFor(url: String): OkHttpClient =
        RelayOkHttpClient.create(relayTlsSettings.shouldTrust(url))
}

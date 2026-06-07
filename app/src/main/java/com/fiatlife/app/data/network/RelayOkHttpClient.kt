package com.fiatlife.app.data.network

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * OkHttp clients for Nostr relay WebSockets and Blossom HTTPS.
 *
 * Public relays use the system trust store. Self-hosted relays (e.g. Start9) often serve
 * `wss://` with a private CA that Android does not trust — enable the self-signed TLS
 * setting for relays you control on your LAN/VPN only.
 */
object RelayOkHttpClient {

    fun create(trustInsecureTls: Boolean): OkHttpClient {
        val builder = baseBuilder()
        if (trustInsecureTls) {
            applyInsecureRelayTls(builder)
        }
        return builder.build()
    }

    private fun baseBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

    private fun applyInsecureRelayTls(builder: OkHttpClient.Builder) {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val trustManagers = arrayOf<TrustManager>(trustAll)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, SecureRandom())
        builder.sslSocketFactory(sslContext.socketFactory, trustAll)
        builder.hostnameVerifier { _, _ -> true }
    }
}

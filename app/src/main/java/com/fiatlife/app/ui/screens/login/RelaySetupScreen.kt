package com.fiatlife.app.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fiatlife.app.data.network.RelayOkHttpClient
import com.fiatlife.app.data.network.RelayUrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import kotlin.coroutines.resume

@Composable
fun RelaySetupScreen(
    onSave: (relayUrl: String, blossomUrl: String, trustSelfSignedLanTls: Boolean) -> Unit,
    onTrustTlsChange: (Boolean) -> Unit,
    isLoading: Boolean = false,
    loadingMessage: String = "Connecting to relay...",
    initialTrustSelfSignedLanTls: Boolean = false
) {
    var relayUrl by remember { mutableStateOf("wss://") }
    var blossomUrl by remember { mutableStateOf("") }
    var trustSelfSignedLanTls by remember { mutableStateOf(initialTrustSelfSignedLanTls) }
    var status by remember { mutableStateOf<RelayStatus>(RelayStatus.Idle) }
    val client = remember(trustSelfSignedLanTls) {
        RelayOkHttpClient.create(trustSelfSignedLanTls)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AttachMoney,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Relay Setup",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Enter your relay to load profile/settings.\nYou can update this later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = relayUrl,
                onValueChange = { input ->
                    relayUrl = enforceWssPrefix(input)
                    if (status is RelayStatus.Error) status = RelayStatus.Idle
                },
                label = { Text("Relay URL") },
                placeholder = { Text("wss://relay.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                enabled = !isLoading && status !is RelayStatus.Connecting
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Self-signed LAN certificate",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Enable for Start9 or other self-hosted relays on your LAN/VPN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = trustSelfSignedLanTls,
                    onCheckedChange = { enabled ->
                        trustSelfSignedLanTls = enabled
                        onTrustTlsChange(enabled)
                        if (status is RelayStatus.Error) status = RelayStatus.Idle
                    },
                    enabled = !isLoading && status !is RelayStatus.Connecting
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = blossomUrl,
                onValueChange = { blossomUrl = it },
                label = { Text("Blossom URL (optional)") },
                placeholder = { Text("https://blossom.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                enabled = !isLoading && status !is RelayStatus.Connecting
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val s = status) {
                is RelayStatus.Connecting -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connecting to relay…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is RelayStatus.Success -> {
                    Text(
                        text = "Connected.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is RelayStatus.Error -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
                RelayStatus.Idle -> { }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = loadingMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    when (status) {
                        is RelayStatus.Success -> {
                            val normalized = RelayUrlValidator.normalize(relayUrl)
                            onSave(normalized, blossomUrl, trustSelfSignedLanTls)
                            return@Button
                        }
                        else -> {
                            RelayUrlValidator.errorMessage(relayUrl)?.let { msg ->
                                status = RelayStatus.Error(msg)
                                return@Button
                            }
                            status = RelayStatus.Connecting
                        }
                    }
                },
                enabled = relayUrl.removePrefix("wss://").isNotBlank() &&
                    !isLoading &&
                    status !is RelayStatus.Connecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Cloud, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (status is RelayStatus.Success) "Save & Load Profile" else "Connect",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    RelayUrlValidator.errorMessage(relayUrl)?.let { msg ->
                        status = RelayStatus.Error(msg)
                        return@OutlinedButton
                    }
                    val normalized = RelayUrlValidator.normalize(relayUrl)
                    onSave(normalized, blossomUrl, trustSelfSignedLanTls)
                },
                enabled = relayUrl.removePrefix("wss://").isNotBlank() &&
                    !isLoading &&
                    status !is RelayStatus.Connecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save without connection test")
            }
        }
    }

    LaunchedEffect(status) {
        if (status is RelayStatus.Connecting) {
            RelayUrlValidator.errorMessage(relayUrl)?.let { msg ->
                status = RelayStatus.Error(msg)
                return@LaunchedEffect
            }
            val normalized = RelayUrlValidator.normalize(relayUrl)
            val result = withTimeoutOrNull(20_000L) {
                tryConnectRelay(client, normalized, trustSelfSignedLanTls)
            } ?: RelayStatus.Error("Connection timed out after 20 seconds")
            status = result
            if (result is RelayStatus.Success) {
                delay(400)
            }
        }
    }
}

private sealed class RelayStatus {
    data object Idle : RelayStatus()
    data object Connecting : RelayStatus()
    data object Success : RelayStatus()
    data class Error(val message: String) : RelayStatus()
}

private suspend fun tryConnectRelay(
    client: okhttp3.OkHttpClient,
    url: String,
    trustSelfSignedLanTls: Boolean,
): RelayStatus = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).build()
    try {
        suspendCancellableCoroutine<RelayStatus> { cont ->
            val listener = object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    webSocket.close(1000, null)
                    if (cont.isActive) cont.resume(RelayStatus.Success)
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    if (cont.isActive) {
                        cont.resume(RelayStatus.Error(formatRelayConnectError(t, trustSelfSignedLanTls)))
                    }
                }
            }
            client.newWebSocket(request, listener)
            cont.invokeOnCancellation { }
        }
    } catch (e: Exception) {
        RelayStatus.Error(formatRelayConnectError(e, trustSelfSignedLanTls))
    }
}

private fun formatRelayConnectError(t: Throwable, trustSelfSignedLanTls: Boolean): String {
    val msg = t.message ?: "Unknown error"
    val certFailure = msg.contains("CertPathValidator", ignoreCase = true) ||
        msg.contains("trust anchor", ignoreCase = true) ||
        t.cause?.message?.contains("CertPathValidator", ignoreCase = true) == true
    return if (certFailure && !trustSelfSignedLanTls) {
        "Could not connect: $msg. Enable “Self-signed LAN certificate” above for Start9/LAN relays."
    } else {
        "Could not connect: $msg"
    }
}

private fun enforceWssPrefix(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return "wss://"
    val withoutScheme = trimmed
        .removePrefix("wss://")
        .removePrefix("ws://")
    return "wss://$withoutScheme"
}

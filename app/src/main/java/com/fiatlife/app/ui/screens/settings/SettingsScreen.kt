package com.fiatlife.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiatlife.app.ui.components.SectionCard
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPrivateKey by remember { mutableStateOf(false) }
    var importKeyDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Connection Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isConnected)
                        ProfitGreen.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (state.isConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (state.isConnected) ProfitGreen else MaterialTheme.colorScheme.error
                    )
                    Column {
                        Text(
                            text = if (state.isConnected) "Connected to Relay" else "Not Connected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.isConnected) "Data is syncing via Nostr"
                            else "Configure relay to enable sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Nostr Key Management
        item {
            SectionCard(
                title = "Nostr Identity",
                icon = Icons.Filled.Key
            ) {
                if (state.isKeyGenerated) {
                    Text(
                        text = "Public Key (npub)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            text = state.publicKeyHex,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Private Key (nsec)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (showPrivateKey) state.privateKeyHex
                                else "••••••••••••••••••••",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { showPrivateKey = !showPrivateKey }) {
                            Icon(
                                imageVector = if (showPrivateKey) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No key configured. Generate a new one or import an existing key.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.generateNewKey() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generate")
                    }
                    OutlinedButton(
                        onClick = { importKeyDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }
                }
            }
        }

        // Relay Configuration
        item {
            SectionCard(
                title = "Nostr Relay",
                icon = Icons.Filled.Cloud
            ) {
                OutlinedTextField(
                    value = state.relayUrl,
                    onValueChange = { viewModel.updateRelayUrl(it) },
                    label = { Text("Relay URL") },
                    placeholder = { Text("wss://your-relay.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = {
                        Icon(Icons.Filled.Link, contentDescription = null)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NIP-42 authentication is handled automatically",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Blossom Server
        item {
            SectionCard(
                title = "Blossom Server",
                icon = Icons.Filled.Storage
            ) {
                OutlinedTextField(
                    value = state.blossomUrl,
                    onValueChange = { viewModel.updateBlossomUrl(it) },
                    label = { Text("Blossom Server URL") },
                    placeholder = { Text("https://blossom.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Used for storing bill statements and attachments",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Data & Privacy
        item {
            SectionCard(
                title = "Data & Privacy",
                icon = Icons.Filled.Security
            ) {
                InfoRow(
                    label = "Encryption",
                    value = "NIP-44 (XChaCha20-Poly1305)"
                )
                InfoRow(
                    label = "Data Storage",
                    value = "Encrypted Nostr events (kind 30078)"
                )
                InfoRow(
                    label = "File Storage",
                    value = "Blossom protocol (BUD-01)"
                )
                InfoRow(
                    label = "Authentication",
                    value = "NIP-42 relay auth"
                )
            }
        }

        // Save button
        item {
            Button(
                onClick = { viewModel.saveAndConnect() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Connect", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (state.statusMessage.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = state.statusMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // About
        item {
            SectionCard(
                title = "About",
                icon = Icons.Filled.Info
            ) {
                Text(
                    text = "FiatLife v1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Track your fiat finances with privacy-first Nostr storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (importKeyDialog) {
        ImportKeyDialog(
            onDismiss = { importKeyDialog = false },
            onImport = { key ->
                viewModel.importPrivateKey(key)
                importKeyDialog = false
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ImportKeyDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Private Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter your private key in hex format",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it.trim() },
                    label = { Text("Private Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(keyInput) },
                enabled = keyInput.length == 64
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

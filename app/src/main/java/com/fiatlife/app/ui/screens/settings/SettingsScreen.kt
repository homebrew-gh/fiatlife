package com.fiatlife.app.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiatlife.app.data.notification.NotifDetailLevel
import com.fiatlife.app.ui.components.SectionCard
import com.fiatlife.app.ui.screens.pin.SetPinSheet
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var showSetPinSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) viewModel.setBillNotifEnabled(true)
    }

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

        // Nostr Identity
        item {
            SectionCard(
                title = "Nostr Identity",
                icon = Icons.Filled.Key
            ) {
                if (state.publicKeyHex.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (state.authType == "amber")
                                Icons.Filled.Security else Icons.Filled.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (state.authType == "amber")
                                "Signed in with Amber (NIP-55)"
                            else "Signed in with local key",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Public Key",
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
                } else {
                    Text(
                        text = "No identity configured.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (state.isBlossomConfigured)
                            Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (state.isBlossomConfigured) ProfitGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (state.isBlossomConfigured) "Configured \u2014 ready for attachments"
                            else if (state.blossomUrl.isEmpty()) "Enter a URL and save to enable attachments"
                            else "Save & Connect to configure",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isBlossomConfigured) ProfitGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // App PIN Lock
        item {
            SectionCard(
                title = "App Lock",
                icon = Icons.Filled.Lock
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PIN Lock",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Require PIN when returning to app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isPinLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !state.hasPinSet) {
                                showSetPinSheet = true
                            } else {
                                viewModel.setPinLockEnabled(enabled)
                            }
                        }
                    )
                }

                if (state.hasPinSet) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showSetPinSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change PIN")
                    }
                }
            }
        }

        // Bill Notifications
        item {
            SectionCard(
                title = "Bill Reminders",
                icon = Icons.Filled.Notifications
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Due Date Reminders",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Get notified before bills are due",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.billNotifEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setBillNotifEnabled(enabled)
                            }
                        }
                    )
                }

                if (state.billNotifEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Notification Detail",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.billNotifDetailLevel == NotifDetailLevel.PRIVATE,
                            onClick = { viewModel.setBillNotifDetailLevel(NotifDetailLevel.PRIVATE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Private")
                        }
                        SegmentedButton(
                            selected = state.billNotifDetailLevel == NotifDetailLevel.DETAILED,
                            onClick = { viewModel.setBillNotifDetailLevel(NotifDetailLevel.DETAILED) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Detailed")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.billNotifDetailLevel == NotifDetailLevel.PRIVATE)
                            "Shows generic reminder — no bill name or amount"
                        else
                            "Shows bill name, amount, and category",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Remind Me",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf(1, 3, 7)
                        options.forEachIndexed { index, days ->
                            SegmentedButton(
                                selected = state.billNotifDaysBefore == days,
                                onClick = { viewModel.setBillNotifDaysBefore(days) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                            ) {
                                Text(
                                    when (days) {
                                        1 -> "1 day"
                                        else -> "$days days"
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Days before due date to send reminder",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    value = if (state.authType == "amber") "NIP-55 (Amber)" else "NIP-42 relay auth"
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

        // Logout
        item {
            OutlinedButton(
                onClick = { showLogoutConfirmation = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
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

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Sign Out") },
            text = {
                Text("This will clear your saved credentials and disconnect from the relay. " +
                     "Your data remains on the relay and can be restored by signing in again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSetPinSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSetPinSheet = false }
        ) {
            SetPinSheet(
                onSetPin = { pin ->
                    val ok = viewModel.pinPrefs.setPin(pin)
                    if (ok) viewModel.onPinSet()
                    ok
                },
                onDismiss = { showSetPinSheet = false }
            )
        }
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

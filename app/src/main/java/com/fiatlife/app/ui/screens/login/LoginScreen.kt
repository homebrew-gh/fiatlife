package com.fiatlife.app.ui.screens.login

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val TAG = "LoginScreen"

/**
 * Parses the NIP-55 get_public_key activity result from Amber.
 * Returns (pubkey, signerPackage) or (null, null) on failure.
 */
fun parseAmberResult(result: ActivityResult): Pair<String?, String?> {
    return try {
        if (result.resultCode != android.app.Activity.RESULT_OK) return Pair(null, null)
        val data = result.data ?: return Pair(null, null)

        val pubkey = safeGetStringExtra(data, "result")
            ?: safeGetStringExtra(data, "pubkey")
            ?: safeGetStringExtra(data, "signature")
            ?: data.data?.getQueryParameter("result")
            ?: data.data?.getQueryParameter("pubkey")
            ?: ""
        val signerPkg = safeGetStringExtra(data, "package")
            ?: safeGetStringExtra(data, "signer_package")
            ?: data.data?.getQueryParameter("package")
            ?: ""

        val pk = pubkey.trim().take(128).takeIf { it.isNotBlank() }
        val pkg = signerPkg.trim().take(256).takeIf { it.isNotBlank() }
        Log.d(TAG, "parseAmberResult: pk=${pk?.take(8)}..., pkg=$pkg")
        Pair(pk, pkg)
    } catch (e: Throwable) {
        Log.e(TAG, "parseAmberResult: ${e.message}")
        Pair(null, null)
    }
}

private fun safeGetStringExtra(intent: Intent, key: String): String? = try {
    intent.getStringExtra(key)?.takeIf { it.isNotBlank() }
} catch (_: Throwable) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNsecSubmit: (String) -> Boolean,
    onLoginSuccess: () -> Unit,
    onLaunchAmber: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nsecInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    val isAmberInstalled = remember(context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    Surface(modifier = modifier.fillMaxSize()) {
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
                text = "FiatLife",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Track your fiat finances with\nprivacy-first Nostr storage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isAmberInstalled) {
                Button(
                    onClick = {
                        error = null
                        onLaunchAmber()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Amber", style = MaterialTheme.typography.titleMedium)
                }

                Text(
                    text = "Use the Amber signer app (NIP-55).\nYour private key stays in Amber.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "  or  ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "Install Amber from F-Droid or Obtainium to sign in with NIP-55 " +
                                "without pasting your key here. Or paste your nsec below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            OutlinedTextField(
                value = nsecInput,
                onValueChange = {
                    nsecInput = it
                    error = null
                },
                label = { Text("Secret key (nsec1... or hex)") },
                placeholder = { Text("nsec1...") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    val trimmed = nsecInput.trim()
                    if (trimmed.isBlank()) {
                        error = "Enter your nsec or hex private key"
                        return@OutlinedButton
                    }
                    if (onNsecSubmit(trimmed)) {
                        nsecInput = ""
                        onLoginSuccess()
                    } else {
                        error = "Invalid key format. Use nsec1... or 64-char hex."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    if (isAmberInstalled) "Sign in with nsec" else "Sign in",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

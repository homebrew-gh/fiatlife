package com.fiatlife.app.ui.screens.pin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fiatlife.app.ui.theme.Green20
import com.fiatlife.app.ui.theme.Green40
import com.fiatlife.app.ui.theme.Green95
import com.fiatlife.app.ui.theme.Green99

@Composable
fun PinLockScreen(
    onUnlocked: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Green20
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.fillMaxHeight(0.2f))

            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Green95
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "FiatLife Locked",
                style = MaterialTheme.typography.headlineSmall,
                color = Green99
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = Green95.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12); error = null },
                label = { Text("PIN", color = Green95) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { msg -> { Text(msg, color = MaterialTheme.colorScheme.error) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, color = Green99),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Green95,
                    unfocusedBorderColor = Green95.copy(alpha = 0.6f),
                    cursorColor = Green95,
                    focusedLabelColor = Green95,
                    unfocusedLabelColor = Green95.copy(alpha = 0.8f),
                    focusedTextColor = Green99,
                    unfocusedTextColor = Green99
                )
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (pin.length < 4) {
                        error = "PIN must be at least 4 digits"
                        return@Button
                    }
                    if (onVerifyPin(pin)) {
                        pin = ""
                        error = null
                        onUnlocked()
                    } else {
                        error = "Incorrect PIN"
                        pin = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green40,
                    contentColor = Color.White
                )
            ) {
                Text("Unlock", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private val Green40 = Color(0xFF006E1C)

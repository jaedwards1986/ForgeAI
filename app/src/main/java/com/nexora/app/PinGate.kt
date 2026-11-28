package com.nexora.app

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** First-launch PIN enrollment and launch-time local PIN gate. */
@Composable
fun PinGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nexora_lock", Context.MODE_PRIVATE) }
    var unlocked by remember { mutableStateOf(false) }
    var setupConfirm by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var enteredPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val enrolled = prefs.contains(KEY_HASH)

    if (unlocked) {
        content()
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NEXORA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    !enrolled && !setupConfirm -> "Create your four-digit launch PIN"
                    !enrolled -> "Confirm your four-digit PIN"
                    else -> "Enter your four-digit PIN"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = enteredPin,
                onValueChange = { value ->
                    enteredPin = value.filter(Char::isDigit).take(4)
                    error = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("4-digit PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = enteredPin.length == 4,
                onClick = {
                    when {
                        !enrolled && !setupConfirm -> {
                            firstPin = enteredPin
                            enteredPin = ""
                            setupConfirm = true
                        }
                        !enrolled -> {
                            if (enteredPin == firstPin) {
                                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                                val hash = PinHash.derive(enteredPin, salt)
                                prefs.edit()
                                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                                    .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                                    .apply()
                                firstPin = ""
                                unlocked = true
                            } else {
                                firstPin = ""
                                enteredPin = ""
                                setupConfirm = false
                                error = "PINs did not match. Start again."
                            }
                        }
                        else -> {
                            val salt = prefs.getString(KEY_SALT, null)?.let {
                                runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
                            }
                            val expected = prefs.getString(KEY_HASH, null)?.let {
                                runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
                            }
                            if (salt != null && expected != null &&
                                MessageDigest.isEqual(expected, PinHash.derive(enteredPin, salt))
                            ) {
                                enteredPin = ""
                                unlocked = true
                            } else {
                                enteredPin = ""
                                error = "Incorrect PIN."
                            }
                        }
                    }
                }
            ) {
                Text(if (enrolled) "Unlock" else if (setupConfirm) "Confirm PIN" else "Set PIN")
            }
        }
    }
}

private object PinHash {
    fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 160_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

private const val KEY_SALT = "pin_salt"
private const val KEY_HASH = "pin_hash"

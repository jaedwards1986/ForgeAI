package ai.davoted.craving.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.davoted.craving.conversation.ChatViewModel
import ai.davoted.craving.conversation.ChatViewModelFactory

@Composable
fun LocalInferenceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) { ChatViewModelFactory(context) }
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val state by chatViewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) chatViewModel.importModel(uri)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "DAVOTEDCRAVING",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text("Factual archive conversation", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.modelName, style = MaterialTheme.typography.titleMedium)
                Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { modelPicker.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*")) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.isBusy) "Please wait…" else "Import / select GGUF model") }
            }
        }

        Text("Discuss source-linked public material and your private archive. Unverified claims are identified as such.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Conversation", style = MaterialTheme.typography.titleLarge)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Text(
                        "Import a compatible GGUF model to begin local generation. Conversations are encrypted on this device. No remote inference fallback is used.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.messages) { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.role == "assistant") MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(message.role.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(message.content)
                    }
                }
            }
        }

        Text(state.lastPresentation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canGenerate,
            placeholder = { Text("Write a message…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                chatViewModel.sendPrompt(input)
                input = ""
            })
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    chatViewModel.sendPrompt(input)
                    input = ""
                },
                enabled = state.canGenerate && input.isNotBlank()
            ) { Text(if (state.isBusy) "Working…" else "Generate locally") }
        }
    }
}


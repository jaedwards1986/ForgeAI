package com.nexora.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.nexora.app.model.LocalModel
import com.nexora.app.model.LocalModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private enum class Destination(val label: String) {
    CHATS("Chats"), CHARACTERS("Characters"), MODELS("Models"), SETTINGS("Settings")
}


private val NexoraColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF65D6CB),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF062B2A),
    secondary = androidx.compose.ui.graphics.Color(0xFF91BFC0),
    background = androidx.compose.ui.graphics.Color(0xFF0B121B),
    surface = androidx.compose.ui.graphics.Color(0xFF121D29),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1B2A38),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE8F0F5),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFA7B8C5),
    outline = androidx.compose.ui.graphics.Color(0xFF344858)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 18, 27)
        window.navigationBarColor = android.graphics.Color.rgb(11, 18, 27)
        setContent {
            MaterialTheme(colorScheme = NexoraColors) {
                PinGate {
                    NexoraScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexoraScreen() {
    val context = LocalContext.current
    val store = remember { LocalModelStore(context) }
    val engine = remember { AiChat.getInferenceEngine(context) }
    val engineState by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    val dataStore = remember { NexoraDataStore(context) }

    var destination by remember { mutableStateOf(Destination.CHATS) }
    var models by remember { mutableStateOf<List<LocalModel>>(emptyList()) }
    var selected by remember { mutableStateOf(store.selectedModel()) }
    var status by remember { mutableStateOf("Your conversations stay on this device.") }
    var systemPrompt by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(dataStore.loadMessages()) }
    var characters by remember { mutableStateOf(dataStore.loadCharacters()) }
    var generating by remember { mutableStateOf(false) }
    var modelLoaded by remember { mutableStateOf(engineState is InferenceEngine.State.ModelReady) }

    fun refreshModels() {
        scope.launch {
            models = store.list()
            selected = store.selectedModel()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = "Importing model…"
            try {
                val model = store.import(uri, queryDisplayName(context, uri))
                refreshModels()
                status = "Imported ${model.fileName}."
            } catch (error: Exception) {
                status = error.message ?: "Model import failed."
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshModels()
        modelLoaded = engine.state.value is InferenceEngine.State.ModelReady
    }
    LaunchedEffect(messages) { dataStore.saveMessages(messages) }
    LaunchedEffect(characters) { dataStore.saveCharacters(characters) }

    fun loadModel(fileName: String) {
        scope.launch {
            status = "Preparing local runtime…"
            try {
                val ready = withTimeout(180_000L) {
                    engine.state.first {
                        it is InferenceEngine.State.Initialized ||
                            it is InferenceEngine.State.Error ||
                            it is InferenceEngine.State.ModelReady
                    }
                }
                if (ready is InferenceEngine.State.Error) throw ready.exception
                if (engine.state.value is InferenceEngine.State.ModelReady) {
                    withContext(Dispatchers.IO) { engine.cleanUp() }
                }
                status = "Loading $fileName…"
                engine.loadModel(store.modelFile(fileName).absolutePath)
                if (systemPrompt.isNotBlank()) engine.setSystemPrompt(systemPrompt)
                store.select(fileName)
                selected = fileName
                modelLoaded = true
                destination = Destination.CHATS
                status = "Model ready. Generation runs locally."
            } catch (error: Exception) {
                modelLoaded = false
                status = error.message ?: "Could not load model."
            }
        }
    }

    fun sendMessage() {
        val message = draft.trim()
        if (message.isEmpty() || generating || !modelLoaded) return
        draft = ""
        messages = messages + SavedMessage("You", message) + SavedMessage("NEXORA", "")
        generating = true
        scope.launch {
            try {
                engine.sendUserPrompt(message).collect { token ->
                    val last = messages.lastOrNull()
                    if (last?.speaker == "NEXORA") {
                        messages = messages.dropLast(1) + last.copy(text = last.text + token)
                    }
                }
            } catch (error: Exception) {
                status = error.message ?: "Generation failed."
            } finally {
                generating = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("NEXORA", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(
                            when (destination) {
                                Destination.CHATS -> "Your private space"
                                Destination.CHARACTERS -> "Character library"
                                Destination.MODELS -> "Local model library"
                                Destination.SETTINGS -> "Preferences"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = destination == Destination.CHATS,
                    onClick = { destination = Destination.CHATS },
                    icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                    label = { Text("Chats", maxLines = 1) }
                )
                NavigationBarItem(
                    selected = destination == Destination.CHARACTERS,
                    onClick = { destination = Destination.CHARACTERS },
                    icon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
                    label = { Text("Characters", maxLines = 1) }
                )
                NavigationBarItem(
                    selected = destination == Destination.MODELS,
                    onClick = { destination = Destination.MODELS },
                    icon = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                    label = { Text("Models", maxLines = 1) }
                )
                NavigationBarItem(
                    selected = destination == Destination.SETTINGS,
                    onClick = { destination = Destination.SETTINGS },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("Settings", maxLines = 1) }
                )
            }
        }
    ) { innerPadding ->
        when (destination) {
            Destination.CHATS -> ChatScreen(
                modifier = Modifier.padding(innerPadding),
                modelName = selected,
                modelLoaded = modelLoaded,
                messages = messages,
                draft = draft,
                generating = generating,
                status = status,
                onDraftChange = { draft = it },
                onSend = { sendMessage() },
                onOpenModels = { destination = Destination.MODELS }
            )
            Destination.CHARACTERS -> CharactersScreen(Modifier.padding(innerPadding), characters) { characters = it }
            Destination.MODELS -> ModelsScreen(
                modifier = Modifier.padding(innerPadding),
                models = models,
                selected = selected,
                status = status,
                systemPrompt = systemPrompt,
                onPromptChange = { systemPrompt = it },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onLoad = { loadModel(it) },
                onDelete = { fileName ->
                    scope.launch {
                        try {
                            store.delete(fileName)
                            if (selected == fileName) {
                                selected = null
                                modelLoaded = false
                            }
                            refreshModels()
                            status = "Deleted $fileName."
                        } catch (error: Exception) {
                            status = error.message ?: "Could not delete model."
                        }
                    }
                }
            )
            Destination.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                selectedModel = selected,
                modelLoaded = modelLoaded,
                status = status
            )
        }
    }
}

@Composable
private fun ChatScreen(
    modifier: Modifier,
    modelName: String?,
    modelLoaded: Boolean,
    messages: List<SavedMessage>,
    draft: String,
    generating: Boolean,
    status: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenModels: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Conversation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (modelLoaded) modelName ?: "Local model ready" else "No model loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (modelLoaded) {
                Box(
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text("LOCAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (!modelLoaded) {
            Spacer(Modifier.height(22.dp))
            EmptyStateCard(
                icon = { Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) },
                title = "Your AI. On your device.",
                body = "Import a GGUF model to start a private conversation. NEXORA does not send your chats to a remote inference service.",
                action = "Choose a model",
                onAction = onOpenModels
            )
            Spacer(Modifier.weight(1f))
        } else {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text("Start a conversation", style = MaterialTheme.typography.titleMedium)
                        Text("Your messages will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { line ->
                        val isUser = line.speaker == "You"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(0.88f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    line.speaker,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                Text(line.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }

        if (modelLoaded) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message NEXORA…") },
                    enabled = !generating,
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp)
                )
                Button(
                    onClick = onSend,
                    enabled = !generating && draft.isNotBlank(),
                    modifier = Modifier.padding(start = 6.dp).size(48.dp),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = "Send")
                }
            }
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModelsScreen(
    modifier: Modifier,
    models: List<LocalModel>,
    selected: String?,
    status: String,
    systemPrompt: String,
    onPromptChange: (String) -> Unit,
    onImport: () -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your models", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Models are stored in private app storage on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import GGUF model")
        }
        if (models.isEmpty()) {
            EmptyStateCard(
                icon = { Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
                title = "No models yet",
                body = "Choose a GGUF file from your device. Large models may take several minutes to import.",
                action = "Browse files",
                onAction = onImport
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(models, key = { it.fileName }) { model ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.fileName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(formatBytes(model.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                                if (selected == model.fileName) {
                                    Text("SELECTED", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onLoad(model.fileName) }, modifier = Modifier.weight(1f)) { Text("Load & chat") }
                                OutlinedButton(onClick = { onDelete(model.fileName) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete model")
                                }
                            }
                        }
                    }
                }
            }
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        Text("Prompt controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("System prompt") },
            placeholder = { Text("Optional instructions for the model…") },
            minLines = 2,
            maxLines = 4
        )
        Text("This text is visible and editable. It is applied when a model is loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CharactersScreen(
    modifier: Modifier,
    characters: List<CharacterCard>,
    onSave: (List<CharacterCard>) -> Unit
) {
    var editing by remember { mutableStateOf<CharacterCard?>(null) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }
    var examples by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    fun beginEdit(card: CharacterCard?) {
        editing = card
        creating = true
        name = card?.name.orEmpty()
        description = card?.description.orEmpty()
        personality = card?.personality.orEmpty()
        scenario = card?.scenario.orEmpty()
        greeting = card?.greeting.orEmpty()
        examples = card?.exampleDialogue.orEmpty()
        prompt = card?.systemPrompt.orEmpty()
    }

    if (creating) {
        Column(modifier = modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) "New character" else "Edit character", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = false }) { Text("Cancel") }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Short description") }, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(personality, { personality = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Personality") }, minLines = 3)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(scenario, { scenario = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Scenario / setting") }, minLines = 3)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(greeting, { greeting = it }, modifier = Modifier.fillMaxWidth(), label = { Text("First message / greeting") }, minLines = 3)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(examples, { examples = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Example dialogue") }, minLines = 3)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Additional instructions") }, minLines = 3)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val prior = editing
                            val saved = CharacterCard(
                                id = prior?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                description = description.trim(),
                                personality = personality.trim(),
                                scenario = scenario.trim(),
                                greeting = greeting.trim(),
                                exampleDialogue = examples.trim(),
                                systemPrompt = prompt.trim()
                            )
                            if (prior == null) onSave(characters + saved)
                            else onSave(characters.map { if (it.id == prior.id) saved else it })
                            creating = false
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save character") }
                }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Characters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Character cards are saved on this device and stay separate from the model.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { beginEdit(null) }) { Text("Create") }
            }
            if (characters.isEmpty()) {
                EmptyStateCard(
                    icon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) },
                    title = "Build your first character",
                    body = "Define a personality, scenario, greeting, example dialogue, and custom instructions. Your cards remain available when you change models.",
                    action = "Create character",
                    onAction = { beginEdit(null) }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(characters, key = { it.id }) { card ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (card.description.isNotBlank()) Text(card.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (card.personality.isNotBlank()) Text("Personality: " + card.personality, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { beginEdit(card) }, modifier = Modifier.weight(1f)) { Text("Edit") }
                                    TextButton(onClick = { onSave(characters.filterNot { it.id == card.id }) }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun SettingsScreen(
    modifier: Modifier,
    selectedModel: String?,
    modelLoaded: Boolean,
    status: String
) {
    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Privacy & runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SettingRow("Inference", "On-device")
                SettingRow("Cloud chat services", "Not used")
                SettingRow("Model status", if (modelLoaded) "Loaded" else "Not loaded")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(selectedModel ?: "No model selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NEXORA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Local-first AI chat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Version 0.3.0 · Android", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyStateCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { icon() }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (action != null) Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )
    return cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) String.format("%.2f GB", gib)
    else String.format("%.0f MB", bytes / (1024.0 * 1024.0))
}

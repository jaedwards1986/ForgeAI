package ai.davoted.craving.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.webkit.MimeTypeMap
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.davoted.craving.storage.ArchiveEntry
import ai.davoted.craving.storage.LocalArchiveStore
import ai.davoted.craving.ai.ArchiveAccessLevel
import ai.davoted.craving.ai.ArchiveAuthenticityStatus
import ai.davoted.craving.ai.ArchiveCategory
import ai.davoted.craving.ai.ArchiveContext
import ai.davoted.craving.ai.ArchiveSource
import ai.davoted.craving.ai.UserArchiveFraming
import ai.davoted.craving.conversation.ChatViewModel
import ai.davoted.craving.conversation.ChatViewModelFactory
import ai.davoted.craving.conversation.ConversationMode
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun ArchiveScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val chatViewModel: ChatViewModel = viewModel(factory = remember(context) { ChatViewModelFactory(context.applicationContext) })
    val scope = rememberCoroutineScope()
    val store = remember { LocalArchiveStore(context) }
    val entries = remember { mutableStateListOf<ArchiveEntry>() }
    var title by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Your private collection is stored on this device.") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        runCatching { store.load() }.onSuccess {
            entries.clear()
            entries.addAll(it.sortedByDescending(ArchiveEntry::addedAtMillis))
        }.onFailure {
            status = "Archive index could not be opened: ${it.message ?: "unknown error"}"
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                val imported = runCatching {
                    importArchiveMedia(context, store, uri, title.ifBlank { "Untitled collection item" }, sourceUrl.trim())
                }
                imported.fold(
                    onSuccess = { entry ->
                        entries.add(0, entry)
                        title = ""
                        sourceUrl = ""
                        runCatching { store.save(entries.toList()) }
                            .onSuccess { status = "Added to your private archive." }
                            .onFailure { status = "Media copied, but archive index save failed: ${it.message ?: "unknown error"}" }
                    },
                    onFailure = { status = "Import failed: ${it.message ?: "unknown error"}" }
                )
                busy = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("THE NATASHA NICE ARCHIVE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("Your private collection", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Import authentic photographs and videos you have sourced. Each item keeps its source information; no substitute or fabricated media is added.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Item title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Original source URL (recommended)") },
                    singleLine = true
                )
                Button(
                    onClick = { picker.launch(arrayOf("image/*", "video/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Importing…" else "Import authentic photo or video") }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("COLLECTION", style = MaterialTheme.typography.titleMedium)
            Text("${entries.size} items", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("PUBLIC CAREER REFERENCES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Source-linked public catalog facts. These are secondary references, not primary-source verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(NatashaPublicArchiveFacts, key = { it.id }) { fact ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(fact.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(fact.detail, style = MaterialTheme.typography.bodyMedium)
                        Text(fact.provenanceNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fact.sourceUrl)))
                                }.onFailure {
                                    status = "Could not open source: ${it.message ?: "unknown error"}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open ${fact.sourceLabel}") }
                    }
                }
            }
            item {
                Text("YOUR PRIVATE COLLECTION", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (entries.isEmpty()) {
                item {
                    Text("Your archive is empty. Add an authentic item from a source you trust; the app records its provenance locally.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(entries, key = ArchiveEntry::id) { entry ->
                ArchiveEntryCard(
                    entry = entry,
                    onUseInConversation = {
                        val category = if (entry.mimeType.startsWith("video/")) ArchiveCategory.VIDEO else ArchiveCategory.PHOTOGRAPH
                        chatViewModel.selectArchiveContext(
                            ArchiveContext(
                                archiveItemId = entry.id,
                                title = entry.title,
                                category = category,
                                source = ArchiveSource(
                                    sourceId = "user-import:${entry.id}",
                                    url = entry.sourceUrl,
                                    capturedAtMillis = entry.addedAtMillis,
                                    accessLevel = ArchiveAccessLevel.USER_PROVIDED,
                                    authenticityStatus = ArchiveAuthenticityStatus.UNVERIFIED
                                ),
                                userFraming = UserArchiveFraming(userAuthored = true),
                                selectedAtMillis = System.currentTimeMillis()
                            )
                        )
                        chatViewModel.setMode(ConversationMode.FACTUAL)
                        status = "Selected for factual archive discussion. Imported media remains unverified until independently checked."
                    },
                    onFavorite = {
                        val index = entries.indexOfFirst { it.id == entry.id }
                        if (index >= 0) {
                            entries[index] = entries[index].copy(isFavorite = !entries[index].isFavorite)
                            scope.launch { runCatching { store.save(entries.toList()) } }
                        }
                    },
                    onDelete = {
                        entries.removeAll { it.id == entry.id }
                        scope.launch {
                            runCatching {
                                File(entry.privateFilePath).delete()
                                store.save(entries.toList())
                            }.onFailure { status = "Could not update archive: ${it.message ?: "unknown error"}" }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ArchiveEntryCard(
    entry: ArchiveEntry,
    onUseInConversation: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val file = remember(entry.privateFilePath) { File(entry.privateFilePath) }
            if (entry.mimeType.startsWith("image/")) {
                val bitmap = remember(entry.privateFilePath) {
                    BitmapFactory.decodeFile(
                        entry.privateFilePath,
                        BitmapFactory.Options().apply { inSampleSize = 3 }
                    )?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = entry.title,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                } else {
                    Text("Image preview unavailable")
                }
            } else if (entry.mimeType.startsWith("video/")) {
                AndroidView(
                    factory = { viewContext ->
                        VideoView(viewContext).apply {
                            setMediaController(MediaController(viewContext))
                            setVideoURI(Uri.fromFile(file))
                        }
                    },
                    update = { view -> if (view.tag != entry.id) {
                        view.tag = entry.id
                        view.setVideoURI(Uri.fromFile(file))
                    } },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
            Text(
                if (entry.sourceUrl.isBlank()) "Source: not recorded" else "Source: ${entry.sourceUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(entry.mimeType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUseInConversation) { Text("Use in conversation") }
                OutlinedButton(onClick = onFavorite) { Text(if (entry.isFavorite) "★ Favorite" else "☆ Favorite") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDelete) { Text("Remove") }
            }
        }
    }
}

private suspend fun importArchiveMedia(
    context: Context,
    store: LocalArchiveStore,
    uri: Uri,
    title: String,
    sourceUrl: String
): ArchiveEntry = withContext(Dispatchers.IO) {
    val mimeType = context.contentResolver.getType(uri)
        ?: error("Could not determine whether this is a photo or video.")
    require(mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
        "Only image and video files can be added to the archive."
    }
    val directory = store.mediaDirectory()
    check(directory.exists() || directory.mkdirs()) { "Could not create private archive storage." }
    File(directory, ".nomedia").let { if (!it.exists()) it.createNewFile() }
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
    val id = UUID.randomUUID().toString()
    val destination = File(directory, "$id.$extension")
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("The selected media could not be opened.")
        input.use { source ->
            destination.outputStream().use { output -> source.copyTo(output) }
        }
        check(destination.length() > 0L) { "The selected media file is empty." }
        ArchiveEntry(
            id = id,
            title = title,
            privateFilePath = destination.absolutePath,
            mimeType = mimeType,
            sourceUrl = sourceUrl,
            addedAtMillis = System.currentTimeMillis()
        )
    } catch (failure: Throwable) {
        destination.delete()
        throw failure
    }
}

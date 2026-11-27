package ai.davoted.craving.conversation

import android.content.Context
import android.net.Uri
import ai.davoted.craving.ai.ArchiveContext
import ai.davoted.craving.ai.ArchiveMemoryState
import ai.davoted.craving.ai.ArchiveTransformResult
import ai.davoted.craving.ai.ConversationMessage
import ai.davoted.craving.ai.ExperienceControls
import ai.davoted.craving.ai.PersonaOrchestrator
import ai.davoted.craving.ai.ExperiencePresentationMapper
import ai.davoted.craving.ai.LlamaCppAdapter
import ai.davoted.craving.ai.LocalModelStatus
import ai.davoted.craving.storage.ContinuitySnapshot
import ai.davoted.craving.storage.LocalContinuityStore
import ai.davoted.craving.storage.StoredMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream

enum class ConversationMode { FACTUAL }

data class ConversationUiState(
    val messages: List<ConversationMessage> = emptyList(),
    val modelName: String = "No model loaded",
    val modelStatus: LocalModelStatus = LocalModelStatus.UNLOADED,
    val statusMessage: String = "Choose a GGUF model",
    val isBusy: Boolean = true,
    val lastPresentation: String = "No generated response yet",
    val selectedModelPath: String? = null,
    val mode: ConversationMode = ConversationMode.FACTUAL,
    val selectedArchiveItemId: String? = null
) {
    val canGenerate: Boolean get() = !isBusy && modelStatus == LocalModelStatus.READY
}

/**
 * Owns the local conversation session. UI code never calls the adapter or persistence layer.
 * All state published to Compose is immutable and observable.
 */
class ConversationService(
    context: Context,
    private val adapter: LlamaCppAdapter = LlamaCppAdapter(),
    private val store: LocalContinuityStore = LocalContinuityStore(context.applicationContext)
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mapper = ExperiencePresentationMapper()
    private val personaOrchestrator = PersonaOrchestrator()
    @Volatile private var selectedArchiveContext: ArchiveContext? = null
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(
        ConversationUiState(
            modelStatus = adapter.status,
            statusMessage = if (adapter.status == LocalModelStatus.UNAVAILABLE) {
                "Native runtime unavailable"
            } else "Choose a GGUF model"
        )
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    suspend fun initialize() = operationMutex.withLock {
        publish(_state.value.copy(isBusy = true, statusMessage = "Restoring local conversation…"))
        try {
            val snapshot = store.load()
            val messages = snapshot.messages.map { ConversationMessage(it.role, it.content) }
            val path = snapshot.selectedModelPath
            adapter.restoreExperienceState(
                snapshot.experienceMood,
                snapshot.experienceIntensity,
                snapshot.experiencePacing,
                snapshot.experiencePhase
            )
            _state.value = _state.value.copy(
                messages = messages,
                selectedModelPath = path,
                mode = ConversationMode.FACTUAL,
                modelName = path?.let { File(it).name } ?: "No model loaded",
                statusMessage = if (path == null) "Choose a GGUF model" else "Restoring local model…"
            )
            if (path != null) {
                val model = File(path)
                if (model.isFile) {
                    val result = adapter.loadModel(path)
                    result.fold(
                        onSuccess = {
                            publish(_state.value.copy(
                                modelStatus = adapter.status,
                                statusMessage = "Model ready — local inference enabled"
                            ))
                        },
                        onFailure = {
                            publish(_state.value.copy(
                                modelStatus = adapter.status,
                                statusMessage = "Saved model could not be loaded: ${it.message ?: "unknown error"}"
                            ))
                        }
                    )
                } else {
                    publish(_state.value.copy(
                        modelStatus = LocalModelStatus.UNLOADED,
                        statusMessage = "Saved model file is missing. Import it again."
                    ))
                }
            } else {
                publish(_state.value.copy(modelStatus = adapter.status))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            publish(_state.value.copy(statusMessage = "Local memory could not be opened: ${failure.message ?: "unknown error"}"))
        } finally {
            publish(_state.value.copy(isBusy = false))
        }
    }

    suspend fun importAndLoad(uri: Uri) = operationMutex.withLock {
        publish(_state.value.copy(isBusy = true, statusMessage = "Importing model into private app storage…"))
        var importedFile: File? = null
        try {
            val file = importGguf(uri)
            importedFile = file
            publish(_state.value.copy(modelName = file.name, statusMessage = "Loading GGUF on device…"))
            val result = adapter.loadModel(file.absolutePath)
            result.fold(
                onSuccess = {
                    importedFile = null
                    publish(_state.value.copy(
                        modelName = file.name,
                        modelStatus = adapter.status,
                        selectedModelPath = file.absolutePath,
                        statusMessage = "Model ready — local inference enabled"
                    ))
                    persist(file.absolutePath)
                },
                onFailure = {
                    file.delete()
                    importedFile = null
                    publish(_state.value.copy(
                        modelStatus = adapter.status,
                        statusMessage = "Model load failed: ${it.message ?: "unknown error"}"
                    ))
                }
            )
        } catch (cancelled: CancellationException) {
            importedFile?.delete()
            throw cancelled
        } catch (failure: Exception) {
            importedFile?.delete()
            publish(_state.value.copy(statusMessage = "Model import failed: ${failure.message ?: "unknown error"}"))
        } finally {
            publish(_state.value.copy(isBusy = false))
        }
    }

    suspend fun setMode(mode: ConversationMode) = operationMutex.withLock {
        _state.value = _state.value.copy(mode = mode)
        persist(_state.value.selectedModelPath)
    }

    fun selectArchiveContext(context: ArchiveContext?) {
        selectedArchiveContext = context
        _state.value = _state.value.copy(selectedArchiveItemId = context?.archiveItemId)
    }

    suspend fun sendPrompt(promptText: String) = operationMutex.withLock {
        val prompt = promptText.trim()
        if (prompt.isEmpty() || !_state.value.canGenerate) return@withLock
        val updatedMessages = _state.value.messages + ConversationMessage("user", prompt)
        publish(_state.value.copy(messages = updatedMessages, isBusy = true, statusMessage = "Generating locally…"))
        try {
            persist(_state.value.selectedModelPath)
            val snapshot = store.load()
            val archiveExperience = run {
                val transformed = personaOrchestrator.transformArchiveContext(
                    context = selectedArchiveContext,
                    memory = ArchiveMemoryState(
                        stateId = "continuity-session",
                        confirmedPreferences = snapshot.confirmedPreferences,
                        exclusions = snapshot.exclusions
                    ),
                    controls = ExperienceControls(),
                    personaState = adapter.experienceState
                )
                when (transformed) {
                    is ArchiveTransformResult.Success -> transformed.experience
                    is ArchiveTransformResult.Rejected -> throw IllegalArgumentException(
                        "Selected archive context rejected: ${transformed.reason}"
                    )
                    is ArchiveTransformResult.NoArchiveContext -> null
                }
            }

            val result = adapter.generate(
                messages = updatedMessages,
                confirmedPreferences = snapshot.confirmedPreferences,
                exclusions = snapshot.exclusions,
                archiveExperience = archiveExperience
            )
            result.fold(
                onSuccess = { turn ->
                    val completed = updatedMessages + ConversationMessage("assistant", turn.assistantText)
                    val presentation = mapper.map(turn.experienceState)
                    publish(_state.value.copy(
                        messages = completed,
                        modelStatus = adapter.status,
                        statusMessage = "Model ready — local inference enabled",
                        lastPresentation = "Validated state: ${turn.experienceState.mood} · intensity ${turn.experienceState.intensity} · ${turn.experienceState.phase} | Visual: ${presentation.visual} · Audio cue: ${presentation.audio} · Haptic cue: ${presentation.haptics}"
                    ))
                    persist(_state.value.selectedModelPath)
                },
                onFailure = {
                    publish(_state.value.copy(
                        modelStatus = adapter.status,
                        statusMessage = "Generation failed: ${it.message ?: "unknown error"}"
                    ))
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            publish(_state.value.copy(statusMessage = "Conversation failed: ${failure.message ?: "unknown error"}"))
        } finally {
            publish(_state.value.copy(isBusy = false, modelStatus = adapter.status))
        }
    }

    private suspend fun persist(selectedModel: String?) {
        val previous = store.load()
        val snapshot = previous.copy(
            messages = _state.value.messages.map {
                StoredMessage(it.role, it.content, System.currentTimeMillis())
            },
            selectedModelPath = selectedModel,
            conversationMode = _state.value.mode.name,
            experienceMood = adapter.experienceState.mood.name,
            experienceIntensity = adapter.experienceState.intensity,
            experiencePacing = adapter.experienceState.pacing.name,
            experiencePhase = adapter.experienceState.phase.name
        )
        store.save(snapshot)
    }

    private fun publish(value: ConversationUiState) {
        _state.value = value.copy(modelStatus = adapter.status)
    }

    private suspend fun importGguf(uri: Uri): File = withContext(Dispatchers.IO) {
        val directory = File(appContext.filesDir, "models")
        check(directory.exists() || directory.mkdirs()) { "Could not create private model directory." }
        val destination = File.createTempFile("model-", ".gguf", directory)
        try {
            val source = appContext.contentResolver.openInputStream(uri)
                ?: error("The selected model could not be opened.")
            source.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            check(destination.length() >= 4L) { "The selected file is empty or truncated." }
            val magic = ByteArray(4)
            FileInputStream(destination).use { stream ->
                check(stream.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                    "The selected file is not a GGUF model."
                }
            }
            destination
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    override fun close() {
        adapter.close()
    }
}

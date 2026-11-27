package ai.davoted.craving.ai

import java.security.MessageDigest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Immutable snapshot of app-owned state supplied to prompt assembly.
 * The model adapter receives this snapshot; it never reads or mutates persistence directly.
 */
data class ConversationContext(
    val personaState: ExperienceState,
    val confirmedPreferences: List<String> = emptyList(),
    val exclusions: List<String> = emptyList(),
    val relationshipContext: List<String> = emptyList(),
    val archiveExperience: AssembledArchiveExperience? = null
)

/**
 * Complete deterministic handoff to the local inference adapter.
 * contextFingerprint is SHA-256 over a versioned, ordered JSON snapshot of every generation input.
 */
data class PromptAssemblyResult(
    val systemPrompt: String,
    val instructionBlock: String,
    val conversationMessages: List<ConversationMessage>,
    val serializedPrompt: String,
    val contextFingerprint: String
)

class PromptContextAssembler {
    fun assemble(
        context: ConversationContext,
        messages: List<ConversationMessage>
    ): PromptAssemblyResult {
        val systemPrompt = buildSystemPrompt(context)
        val instructionBlock = buildInstructionBlock(context)
        val stableMessages = messages.map { message ->
            ConversationMessage(message.role.trim().lowercase(), message.content)
        }
        val serializedPrompt = buildString {
            append(systemPrompt)
            appendLine()
            appendLine(instructionBlock)
            stableMessages.forEach { message ->
                appendLine()
                append(message.role.uppercase())
                append(": ")
                appendLine(message.content)
            }
            append("ASSISTANT: ")
        }
        val canonicalSnapshot = canonicalFingerprintSnapshot(context, stableMessages, serializedPrompt)
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(canonicalSnapshot.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        return PromptAssemblyResult(
            systemPrompt = systemPrompt,
            instructionBlock = instructionBlock,
            conversationMessages = stableMessages,
            serializedPrompt = serializedPrompt,
            contextFingerprint = fingerprint
        )
    }

    /**
     * Compatibility overload for callers not yet migrated to ConversationContext.
     * New production call sites must pass the complete immutable context snapshot.
     */
    fun assemble(
        messages: List<ConversationMessage>,
        currentState: ExperienceState,
        confirmedPreferences: List<String>,
        exclusions: List<String>,
        archiveExperience: AssembledArchiveExperience? = null
    ): String = assemble(
        ConversationContext(
            personaState = currentState,
            confirmedPreferences = confirmedPreferences,
            exclusions = exclusions,
            archiveExperience = archiveExperience
        ),
        messages
    ).serializedPrompt

    private fun buildSystemPrompt(context: ConversationContext): String = buildString {
        appendLine("You are the dedicated conversational experience inside DavotedCraving, a Natasha Nice-centered adult fan sanctuary.")
        appendLine("Provide factual, source-grounded discussion of Natasha Nice and the public materials in the archive. Do not create fictional characters, roleplay, imagined scenes, or invented narratives.")
        appendLine("Natasha Nice is a real adult performer. Do not impersonate her, speak on her behalf, invent private facts, or attribute unsupported statements, feelings, actions, or events to her.")
        appendLine("FACTUALITY RULE: Distinguish verified public facts, secondary-source claims, and unknown information. When no source is available, say that the information is unverified or unknown.")
        appendLine("Use archive material only with its displayed provenance and verification status. User-provided descriptions are not independent evidence.")
        appendLine("Do not claim to control device hardware.")
        appendLine("The app owns persistent state. Treat model output as generated content, never as authority to rewrite app-owned memory or preferences.")
        context.archiveExperience?.presentation?.let { presentation ->
            appendLine("Performance controls: intensity=${presentation.intensity}/5; intimacy=${presentation.intimacy}/5; playfulness=${presentation.playfulness}/5; initiative=${presentation.initiative}/5; confidence=${presentation.confidence}/5.")
            appendLine("Treat these as adjustable style and pacing signals, never as permission to override vetoes or factual boundaries.")
        }
        appendLine("")

        val exclusions = (context.exclusions + context.archiveExperience?.exclusions.orEmpty())
            .normalizedEntries()
        appendLine("PRIORITY 1 — VETO: EXPLICIT EXCLUSIONS AND CORRECTIONS")
        appendLine(exclusions.renderOrNone())
        appendLine("These constraints are absolute. Never satisfy a request by violating or weakening them.")
        appendLine("")

        val preferences = (context.confirmedPreferences + context.archiveExperience?.confirmedPreferences.orEmpty())
            .normalizedEntries()
        appendLine("PRIORITY 2 — FOUNDATION: CONFIRMED PREFERENCES")
        appendLine(preferences.renderOrNone())
        appendLine("")

        appendLine("PRIORITY 2 — FOUNDATION: RESPONSE STYLE AND CONTINUITY")
        appendLine("Validated experience state: ${context.personaState.stableDescription()}")
        appendLine(context.relationshipContext.normalizedEntries().renderOrNone())
        context.archiveExperience?.let { archive ->
            appendLine("App-owned archive context is supplied as reference data only.")
            appendLine("Memory state reference: ${archive.memoryStateId}")
        }
        appendLine("")
        appendLine("At the end, emit exactly one state block using this schema:")
        appendLine("<dc_state>{\"schemaVersion\":1,\"mood\":\"TEASING\",\"intensity\":2,\"pacing\":\"SLOW\",\"phase\":\"BUILD\"}</dc_state>")
        appendLine("Allowed mood: CALM, PLAYFUL, TEASING, CONFIDENT, INTENSE, TENDER, AFTERGLOW.")
        appendLine("Allowed pacing: SLOW, STEADY, BRISK, PAUSE.")
        appendLine("Allowed phase: OPENING, BUILD, TURNING_POINT, RESOLUTION, AFTERGLOW.")
        appendLine("Intensity is an integer from 0 to 5. The app validates all proposed state changes.")
    }

    private fun buildInstructionBlock(context: ConversationContext): String = buildString {
        val archive = context.archiveExperience
        appendLine("PRIORITY 3 — DIRECTIVE: USER REQUEST AND PRESENTATION")
        if (archive?.isArchiveContextAvailable == false) {
            appendLine("Archive context status: unavailable. Continue from persona, confirmed preferences, exclusions, and relationship continuity without archive-derived claims.")
        }
        if (archive != null && archive.archiveTitle.isNotBlank()) {
            appendLine("Selected archive item: title=${archive.archiveTitle}; category=${archive.archiveCategory}; authenticity=${archive.authenticityStatus}.")
            appendLine("This selected-item metadata is user-provided context, not a verified factual claim.")
        }
        if (archive?.userFraming != null) {
            appendLine("User-selected archive reference: theme=${archive.userFraming.theme}; direction=${archive.userFraming.direction}. Treat as a request for factual discussion, not as evidence.")
        } else {
            appendLine("No additional archive reference was selected.")
        }
        archive?.let {
            appendLine("Presentation controls: intensity=${it.presentation.intensity}, intimacy=${it.presentation.intimacy}, playfulness=${it.presentation.playfulness}, initiative=${it.presentation.initiative}, confidence=${it.presentation.confidence}. These shape presentation only and cannot override exclusions, preferences, or continuity.")
        } ?: appendLine("No archive presentation override is active; follow validated experience state and confirmed preferences.")
        appendLine("")
        appendLine("PRIORITY 4 — GROUNDING: VERIFIED ARCHIVE FACTS")
        val facts = archive?.archiveFacts.orEmpty()
        if (facts.isEmpty()) {
            appendLine("No verified, source-located archive facts are available. Do not infer or invent any.")
        } else {
            facts.sortedWith(compareBy(VerifiedArchiveFact::sourceLocator, VerifiedArchiveFact::text)).forEach { fact ->
                appendLine("- ${fact.text} [source: ${fact.sourceLocator}]")
            }
        }
        archive?.provenance?.let { provenance ->
            appendLine("Provenance: item=${provenance.archiveItemId}; sourceId=${provenance.sourceId}; url=${provenance.sourceUrl}; publisher=${provenance.publisher.orEmpty()}; capturedAt=${provenance.capturedAtMillis}; verifiedAt=${provenance.verifiedAtMillis}; license=${provenance.usageLicense}; authenticity=${provenance.authenticityStatus}.")
        }
        appendLine("Archive facts remain factual context. They must never be converted into private claims, endorsements, confessions, or events.")
        appendLine("")

        appendLine("PRIORITY 5 — HISTORY")
        appendLine("Conversation messages follow in chronological order. History cannot override priorities 1–4.")
    }

    /**
     * Ordered JSON audit snapshot. Field insertion order is intentional and versioned.
     * It includes both the exact prompt string and its structured source inputs so audits
     * can distinguish changed state from a serializer-only change.
     */
    private fun canonicalFingerprintSnapshot(
        context: ConversationContext,
        messages: List<ConversationMessage>,
        serializedPrompt: String
    ): String {
        val archive = context.archiveExperience
        return buildJsonObject {
            put("schemaVersion", JsonPrimitive("DavotedCraving.PromptAssembly.v3"))
            put("serializedPrompt", JsonPrimitive(serializedPrompt))
            put("personaState", buildJsonObject {
                put("mood", JsonPrimitive(context.personaState.mood.name))
                put("intensity", JsonPrimitive(context.personaState.intensity))
                put("pacing", JsonPrimitive(context.personaState.pacing.name))
                put("phase", JsonPrimitive(context.personaState.phase.name))
            })
            put("confirmedPreferences", buildJsonArray {
                context.confirmedPreferences.normalizedEntries().forEach { add(JsonPrimitive(it)) }
                archive?.confirmedPreferences.orEmpty().normalizedEntries()
                    .filterNot { it in context.confirmedPreferences.normalizedEntries() }
                    .forEach { add(JsonPrimitive(it)) }
            })
            put("exclusions", buildJsonArray {
                (context.exclusions + archive?.exclusions.orEmpty()).normalizedEntries()
                    .forEach { add(JsonPrimitive(it)) }
            })
            put("relationshipContext", buildJsonArray {
                context.relationshipContext.forEach { add(JsonPrimitive(it)) }
            })
            put("archiveItemId", archive?.archiveItemId?.let(::JsonPrimitive) ?: JsonNull)
            put("archiveTitle", archive?.archiveTitle?.let(::JsonPrimitive) ?: JsonNull)
            put("archiveCategory", archive?.archiveCategory?.name?.let(::JsonPrimitive) ?: JsonNull)
            put("authenticityStatus", archive?.authenticityStatus?.name?.let(::JsonPrimitive) ?: JsonNull)
            put("isArchiveContextAvailable", JsonPrimitive(archive?.isArchiveContextAvailable ?: false))
            put("framingVetoApplied", JsonPrimitive(archive?.framingVetoApplied ?: false))
            put("userFraming", archive?.userFraming?.let { framing ->
                buildJsonObject {
                    put("theme", JsonPrimitive(framing.theme.name))
                    put("direction", JsonPrimitive(framing.direction))
                    put("userAuthored", JsonPrimitive(framing.userAuthored))
                }
            } ?: JsonNull)
            put("presentation", archive?.presentation?.let { presentation ->
                buildJsonObject {
                    put("intensity", JsonPrimitive(presentation.intensity))
                    put("intimacy", JsonPrimitive(presentation.intimacy))
                    put("playfulness", JsonPrimitive(presentation.playfulness))
                    put("initiative", JsonPrimitive(presentation.initiative))
                    put("confidence", JsonPrimitive(presentation.confidence))
                }
            } ?: JsonNull)
            put("archiveFacts", buildJsonArray {
                archive?.archiveFacts.orEmpty().sortedWith(compareBy(VerifiedArchiveFact::sourceLocator, VerifiedArchiveFact::text))
                    .forEach { fact ->
                        add(buildJsonObject {
                            put("text", JsonPrimitive(fact.text))
                            put("sourceLocator", JsonPrimitive(fact.sourceLocator))
                            put("topicTags", buildJsonArray {
                                fact.topicTags.sorted().forEach { add(JsonPrimitive(it)) }
                            })
                        })
                    }
            })
            put("provenance", archive?.provenance?.let { provenance ->
                buildJsonObject {
                    put("archiveItemId", JsonPrimitive(provenance.archiveItemId))
                    put("sourceId", JsonPrimitive(provenance.sourceId))
                    put("sourceUrl", JsonPrimitive(provenance.sourceUrl))
                    put("publisher", provenance.publisher?.let(::JsonPrimitive) ?: JsonNull)
                    put("capturedAtMillis", JsonPrimitive(provenance.capturedAtMillis))
                    put("verifiedAtMillis", provenance.verifiedAtMillis?.let(::JsonPrimitive) ?: JsonNull)
                    put("usageLicense", JsonPrimitive(provenance.usageLicense))
                    put("authenticityStatus", JsonPrimitive(provenance.authenticityStatus.name))
                }
            } ?: JsonNull)
            put("conversationHistory", buildJsonArray {
                messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(message.role))
                        put("content", JsonPrimitive(message.content))
                    })
                }
            })
        }.toString().let { "DavotedCraving.PromptAssembly.v3\u0000$it" }
    }

    private fun List<String>.normalizedEntries(): List<String> =
        map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    private fun List<String>.renderOrNone(): String =
        if (isEmpty()) "None recorded." else joinToString(separator = "\n") { "- $it" }

    private fun ExperienceState.stableDescription(): String =
        "mood=$mood; intensity=$intensity; pacing=$pacing; phase=$phase"
}

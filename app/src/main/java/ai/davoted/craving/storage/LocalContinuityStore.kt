package ai.davoted.craving.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class StoredMessage(
    val role: String,
    val content: String,
    val timestampMillis: Long
)

@Serializable
data class ContinuitySnapshot(
    val schemaVersion: Int = 1,
    val messages: List<StoredMessage> = emptyList(),
    val confirmedPreferences: List<String> = emptyList(),
    val exclusions: List<String> = emptyList(),
    val favoriteScenarioIds: List<String> = emptyList(),
    val favoriteArchiveIds: List<String> = emptyList(),
    val selectedModelPath: String? = null,
    val conversationMode: String = "FACTUAL",
    val experienceMood: String? = null,
    val experienceIntensity: Int? = null,
    val experiencePacing: String? = null,
    val experiencePhase: String? = null
)

/**
 * App-owned encrypted continuity store. JSON is encrypted with an AES-256-GCM key
 * held by Android Keystore; writes replace the previous snapshot atomically.
 */
class LocalContinuityStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "continuity/continuity.enc")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    suspend fun load(): ContinuitySnapshot = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!file.exists()) return@synchronized ContinuitySnapshot()
            try {
                val payload = file.readBytes()
                require(payload.size > 12) { "Continuity snapshot is truncated." }
                val iv = payload.copyOfRange(0, 12)
                val ciphertext = payload.copyOfRange(12, payload.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                val plaintext = cipher.doFinal(ciphertext)
                json.decodeFromString<ContinuitySnapshot>(plaintext.decodeToString())
            } catch (failure: Exception) {
                throw IllegalStateException("Could not decrypt local continuity data.", failure)
            }
        }
    }

    suspend fun save(snapshot: ContinuitySnapshot) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val directory = file.parentFile
                ?: throw IllegalStateException("Continuity storage directory is unavailable.")
            check(directory.exists() || directory.mkdirs()) {
                "Could not create continuity storage directory."
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(json.encodeToString(snapshot).encodeToByteArray())
            val payload = cipher.iv + encrypted
            val temporary = File(directory, "continuity.enc.tmp")
            val backup = File(directory, "continuity.enc.bak")
            try {
                FileOutputStream(temporary).use { stream ->
                    stream.write(payload)
                    stream.fd.sync()
                }
                if (file.exists()) {
                    if (backup.exists()) backup.delete()
                    check(file.renameTo(backup)) { "Could not preserve previous continuity snapshot." }
                }
                if (!temporary.renameTo(file)) {
                    if (backup.exists()) backup.renameTo(file)
                    throw IllegalStateException("Could not commit continuity snapshot.")
                }
                backup.delete()
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "davotedcraving_continuity_v1"
    }
}

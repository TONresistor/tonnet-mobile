package com.tonnet.browser.data

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Bookmark(
    val url: String,
    val title: String,
    val createdAt: Long,
)

interface BookmarkRepository {
    suspend fun all(): Result<List<Bookmark>>
    suspend fun toggle(url: String, title: String): Result<Boolean>
    suspend fun remove(url: String): Result<Boolean>
}

class EncryptedBookmarkStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BookmarkRepository {
    private val preferences = context.getSharedPreferences("tonnet_bookmarks_v2", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun all(): Result<List<Bookmark>> = access { read() }

    override suspend fun toggle(url: String, title: String): Result<Boolean> = access {
        val current = read().toMutableList()
        val existing = current.indexOfFirst { it.url == url }
        val added = existing < 0
        if (added) {
            current += Bookmark(url, title.ifBlank { url }, System.currentTimeMillis())
        } else {
            current.removeAt(existing)
        }
        save(current)
        added
    }

    override suspend fun remove(url: String): Result<Boolean> = access {
        val current = read().toMutableList()
        val removed = current.removeAll { it.url == url }
        if (removed) save(current)
        removed
    }

    private suspend fun <T> access(block: () -> T): Result<T> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    Result.success(block())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
        }

    private fun read(): List<Bookmark> {
        val encrypted = preferences.getString(CONTENT_KEY, null) ?: return emptyList()
        return decode(decrypt(encrypted))
    }

    @SuppressLint("UseKtx")
    private fun save(bookmarks: List<Bookmark>) {
        val array = JSONArray()
        bookmarks.sortedByDescending { it.createdAt }.forEach { bookmark ->
            array.put(
                JSONObject()
                    .put("url", bookmark.url)
                    .put("title", bookmark.title.take(200))
                    .put("createdAt", bookmark.createdAt),
            )
        }
        val encrypted = encrypt(array.toString())
        check(preferences.edit().putString(CONTENT_KEY, encrypted).commit()) {
            "Could not persist encrypted bookmarks"
        }
        check(preferences.getString(CONTENT_KEY, null) == encrypted) {
            "Encrypted bookmark write verification failed"
        }
    }

    private fun decode(json: String): List<Bookmark> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(Bookmark(item.getString("url"), item.getString("title"), item.getLong("createdAt")))
            }
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_LENGTH)))
        return cipher.doFinal(bytes.copyOfRange(IV_LENGTH, bytes.size)).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "tonnet.bookmarks.v2"
        const val CONTENT_KEY = "ciphertext"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}

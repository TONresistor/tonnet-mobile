package com.tonnet.browser.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBookmarkStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    @After
    fun clearStore() {
        check(preferences.edit().clear().commit())
    }

    @Test
    fun corruptedCiphertextIsReportedAndPreserved() = runBlocking {
        val corrupted = "not-valid-ciphertext"
        check(preferences.edit().putString(CONTENT_KEY, corrupted).commit())
        val store = EncryptedBookmarkStore(context)

        assertTrue(store.all().isFailure)
        assertEquals(corrupted, preferences.getString(CONTENT_KEY, null))
        assertTrue(store.toggle("http://foundation.ton/", "TON").isFailure)
        assertEquals(corrupted, preferences.getString(CONTENT_KEY, null))
    }

    @Test
    fun successfulWriteCanBeReadBackFromEncryptedStorage() = runBlocking {
        val store = EncryptedBookmarkStore(context)

        assertEquals(true, store.toggle("http://foundation.ton/", "TON").getOrThrow())
        val bookmark = store.all().getOrThrow().single()

        assertEquals("http://foundation.ton/", bookmark.url)
        assertEquals("TON", bookmark.title)
        assertTrue(preferences.getString(CONTENT_KEY, null).orEmpty().isNotBlank())
    }

    @Test
    fun concurrentTogglesAreSerializedWithoutCorruptingStorage() = runBlocking {
        val store = EncryptedBookmarkStore(context)

        coroutineScope {
            val first = async { store.toggle("http://foundation.ton/", "TON") }
            val second = async { store.toggle("http://foundation.ton/", "TON") }
            first.await().getOrThrow()
            second.await().getOrThrow()
        }

        assertTrue(store.all().getOrThrow().isEmpty())
        assertTrue(preferences.getString(CONTENT_KEY, null).orEmpty().isNotBlank())
    }

    private companion object {
        const val PREFERENCES_NAME = "tonnet_bookmarks_v2"
        const val CONTENT_KEY = "ciphertext"
    }
}

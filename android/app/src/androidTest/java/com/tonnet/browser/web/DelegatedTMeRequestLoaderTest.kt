package com.tonnet.browser.web

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebResourceResponseCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.ArrayDeque

@RunWith(AndroidJUnit4::class)
class DelegatedTMeRequestLoaderTest {
    @Test
    fun requestUsesOnlyTheLoopbackProxyAndFiltersSensitiveHeaders() {
        val factory = FakeConnectionFactory(
            ResponseSpec(
                headers = mapOf(
                    "Content-Type" to listOf("text/plain; charset=utf-8"),
                    "Content-Encoding" to listOf("gzip"),
                    "X-TON" to listOf("yes"),
                ),
                body = "ok",
            ),
        )
        val loader = DelegatedTMeRequestLoader(factory, ERROR_COPY)

        val response = loader.load(
            DelegatedTonRequest(
                url = "https://collectible.t.me/path",
                method = "GET",
                headers = mapOf(
                    "Referer" to "http://secret.ton/",
                    "Host" to "collectible.t.me",
                    "X-Requested-With" to "com.tonnet.browser",
                    "X-Test" to "kept",
                ),
            ),
            proxyPort = 4242,
        )

        val opened = factory.opened.single()
        assertEquals("http", opened.url.protocol)
        assertEquals("collectible.t.me", opened.url.host)
        assertEquals(Proxy.Type.HTTP, opened.proxy.type())
        assertEquals(4242, (opened.proxy.address() as InetSocketAddress).port)
        assertNull(opened.connection.getRequestProperty("Referer"))
        assertNull(opened.connection.getRequestProperty("Host"))
        assertNull(opened.connection.getRequestProperty("X-Requested-With"))
        assertEquals("kept", opened.connection.getRequestProperty("X-Test"))
        assertFalse(response.responseHeaders.containsKey("Content-Encoding"))
        assertEquals("yes", response.responseHeaders["X-TON"])
        assertEquals("ok", requireNotNull(response.data).bufferedReader().readText())
        response.data.close()
        assertTrue(opened.connection.disconnected)
    }

    @Test
    fun redirectOutsideDelegatedTONNamesFailsClosed() {
        val factory = FakeConnectionFactory(
            ResponseSpec(
                statusCode = 302,
                headers = mapOf("Location" to listOf("https://example.com/")),
            ),
        )
        val loader = DelegatedTMeRequestLoader(factory, ERROR_COPY)

        val response = loader.load(
            DelegatedTonRequest("http://collectible.t.me/", "GET", emptyMap()),
            proxyPort = 4242,
        )

        assertEquals(502, response.statusCode)
        assertTrue(factory.opened.single().connection.disconnected)
    }

    @Test
    fun crossHostRedirectDropsCredentialsAndKeepsSafeHeaders() {
        val factory = FakeConnectionFactory(
            ResponseSpec(
                statusCode = 302,
                headers = mapOf("Location" to listOf("https://second.t.me/next")),
            ),
            ResponseSpec(body = "ok"),
        )
        val loader = DelegatedTMeRequestLoader(factory, ERROR_COPY)

        val response = loader.load(
            DelegatedTonRequest(
                url = "https://first.t.me/start",
                method = "GET",
                headers = mapOf(
                    "Authorization" to "Bearer private",
                    "Cookie" to "session=private",
                    "Origin" to "https://first.t.me",
                    "X-Test" to "kept",
                ),
            ),
            proxyPort = 4242,
        )

        val first = factory.opened[0].connection
        val second = factory.opened[1].connection
        assertEquals("Bearer private", first.getRequestProperty("Authorization"))
        assertEquals("session=private", first.getRequestProperty("Cookie"))
        assertEquals("https://first.t.me", first.getRequestProperty("Origin"))
        assertNull(second.getRequestProperty("Authorization"))
        assertNull(second.getRequestProperty("Cookie"))
        assertNull(second.getRequestProperty("Origin"))
        assertEquals("kept", second.getRequestProperty("X-Test"))
        assertEquals("ok", requireNotNull(response.data).bufferedReader().readText())
        response.data.close()
    }

    @Test
    fun unsupportedMethodsAndMissingProxyNeverOpenAConnection() {
        val factory = FakeConnectionFactory()
        val loader = DelegatedTMeRequestLoader(factory, ERROR_COPY)

        val post = loader.load(
            DelegatedTonRequest("http://collectible.t.me/", "POST", emptyMap()),
            proxyPort = 4242,
        )
        val missingProxy = loader.load(
            DelegatedTonRequest("http://collectible.t.me/", "GET", emptyMap()),
            proxyPort = null,
        )

        assertEquals(405, post.statusCode)
        assertEquals(502, missingProxy.statusCode)
        assertTrue(factory.opened.isEmpty())
    }

    @Test
    fun responseKeepsSetCookieValuesSeparate() {
        val factory = FakeConnectionFactory(
            ResponseSpec(
                headers = mapOf(
                    "Content-Type" to listOf("text/html; charset=utf-8"),
                    "Set-Cookie" to listOf(
                        "session=private; Path=/; HttpOnly",
                        "expires=later; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Path=/",
                    ),
                ),
            ),
        )
        val loader = DelegatedTMeRequestLoader(factory, ERROR_COPY)

        val response = loader.load(
            DelegatedTonRequest("http://collectible.t.me/", "GET", emptyMap()),
            proxyPort = 4242,
        )
        val compat = WebResourceResponseCompat.toWebResourceResponseCompat(response)

        assertEquals(
            listOf(
                "session=private; Path=/; HttpOnly",
                "expires=later; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Path=/",
            ),
            compat.cookies,
        )
        response.data.close()
    }

    private data class ResponseSpec(
        val statusCode: Int = 200,
        val headers: Map<String, List<String>> = mapOf(
            "Content-Type" to listOf("text/html; charset=utf-8"),
        ),
        val body: String = "",
    )

    private companion object {
        val ERROR_COPY = TonWebErrorCopy(
            networkUnavailable = "TON unavailable",
            requestNotSupported = "Request not supported.",
            tonSiteUnavailable = "TON Site unavailable",
            blockedForPrivacy = "Blocked for privacy.",
        )
    }

    private class FakeConnectionFactory(
        vararg specs: ResponseSpec,
    ) : HttpConnectionFactory {
        private val pending = ArrayDeque(specs.toList())
        val opened = mutableListOf<OpenedConnection>()

        override fun open(target: URL, proxy: Proxy): HttpURLConnection {
            val connection = FakeHttpURLConnection(
                target,
                if (pending.isEmpty()) ResponseSpec() else pending.removeFirst(),
            )
            opened += OpenedConnection(target, proxy, connection)
            return connection
        }
    }

    private data class OpenedConnection(
        val url: URL,
        val proxy: Proxy,
        val connection: FakeHttpURLConnection,
    )

    private class FakeHttpURLConnection(
        target: URL,
        private val spec: ResponseSpec,
    ) : HttpURLConnection(target) {
        var disconnected = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = true

        override fun getResponseCode(): Int = spec.statusCode

        override fun getResponseMessage(): String = "Test Response"

        override fun getContentType(): String? =
            spec.headers.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value
                ?.firstOrNull()

        override fun getHeaderField(name: String?): String? =
            spec.headers.entries
                .firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.firstOrNull()

        override fun getHeaderFields(): Map<String?, List<String>> =
            spec.headers.mapKeys { it.key }

        override fun getInputStream(): InputStream =
            ByteArrayInputStream(spec.body.toByteArray())

        override fun getErrorStream(): InputStream? =
            if (spec.statusCode >= 400) ByteArrayInputStream(spec.body.toByteArray()) else null
    }
}

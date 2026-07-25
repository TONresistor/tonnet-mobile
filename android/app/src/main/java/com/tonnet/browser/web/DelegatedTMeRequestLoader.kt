package com.tonnet.browser.web

import android.annotation.SuppressLint
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import androidx.webkit.WebResourceResponseCompat
import com.tonnet.browser.core.TonUrlPolicy
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Locale

internal data class DelegatedTonRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
)

internal fun interface HttpConnectionFactory {
    fun open(target: URL, proxy: Proxy): HttpURLConnection
}

internal data class TonWebErrorCopy(
    val networkUnavailable: String,
    val requestNotSupported: String,
    val tonSiteUnavailable: String,
    val blockedForPrivacy: String,
)

internal class DelegatedTMeRequestLoader(
    private val connectionFactory: HttpConnectionFactory,
    private val errorCopy: TonWebErrorCopy,
) {
    fun isDelegated(url: String): Boolean {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return false
        val httpUrl = uri.buildUpon().scheme("http").build().toString()
        return TonUrlPolicy.normalize(httpUrl).getOrNull()?.host?.endsWith(".t.me") == true
    }

    fun load(request: DelegatedTonRequest, proxyPort: Int?): WebResourceResponse {
        val port = proxyPort ?: return proxyErrorResponse(errorCopy.networkUnavailable)
        if (request.method != "GET" && request.method != "HEAD") {
            return response(
                statusCode = 405,
                reason = "Method Not Allowed",
                body = errorCopy.requestNotSupported,
            )
        }

        val target = request.url.toUri().buildUpon().scheme("http").build().toString()
        return runCatching {
            open(
                target = target,
                method = request.method,
                requestHeaders = request.headers,
                port = port,
                redirectsRemaining = MAX_REDIRECTS,
            )
        }.getOrElse {
            proxyErrorResponse(errorCopy.tonSiteUnavailable)
        }
    }

    @SuppressLint("RequiresFeature")
    private fun open(
        target: String,
        method: String,
        requestHeaders: Map<String, String>,
        port: Int,
        redirectsRemaining: Int,
    ): WebResourceResponse {
        require(isDelegated(target))
        val connection = connectionFactory.open(
            URL(target),
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)),
        ).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            useCaches = false
            requestHeaders.forEach { (name, value) ->
                if (name.lowercase(Locale.ROOT) !in STRIPPED_REQUEST_HEADERS) {
                    setRequestProperty(name, value)
                }
            }
        }

        val statusCode = connection.responseCode
        if (statusCode in REDIRECT_STATUS_CODES) {
            val location = connection.getHeaderField("Location")
            if (location != null && redirectsRemaining > 0) {
                val redirected = URL(URL(target), location)
                val redirectedTarget = URL(
                    "http",
                    redirected.host,
                    redirected.port.takeIf { it != -1 } ?: 80,
                    redirected.file,
                ).toString()
                connection.disconnect()
                require(isDelegated(redirectedTarget))
                return open(
                    target = redirectedTarget,
                    method = if (statusCode == 307 || statusCode == 308) method else "GET",
                    requestHeaders = redirectedHeaders(
                        requestHeaders = requestHeaders,
                        sourceHost = URL(target).host,
                        destinationHost = redirected.host,
                    ),
                    port = port,
                    redirectsRemaining = redirectsRemaining - 1,
                )
            }
        }

        val contentType = connection.contentType.orEmpty()
        val mimeType = contentType.substringBefore(';').trim().ifEmpty { "text/html" }
        val charset = contentType
            .substringAfter(';', "")
            .split(';')
            .firstNotNullOfOrNull { part ->
                part.trim().takeIf { it.startsWith("charset=", ignoreCase = true) }
                    ?.substringAfter('=')
            }
            ?.ifBlank { null }
            ?: "utf-8"
        val headerFields = connection.headerFields
        val responseCookies = headerFields.entries
            .filter { (name, _) -> name.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value }
        val responseHeaders = headerFields
            .filterKeys {
                it != null &&
                    it.lowercase(Locale.ROOT) !in STRIPPED_RESPONSE_HEADERS &&
                    !it.equals("Set-Cookie", ignoreCase = true)
            }
            .mapValues { (_, values) -> values.joinToString(", ") }
        val rawStream = when {
            method == "HEAD" -> ByteArrayInputStream(ByteArray(0))
            statusCode >= 400 -> connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
            else -> connection.inputStream
        }

        return WebResourceResponseCompat(
            mimeType,
            charset,
            statusCode,
            connection.responseMessage?.takeIf { it.isNotBlank() } ?: "TON Site Response",
            responseHeaders,
            DisconnectingInputStream(rawStream, connection),
        ).apply {
            if (responseCookies.isNotEmpty()) {
                setCookies(responseCookies)
            }
        }.toWebResourceResponse()
    }

    private fun redirectedHeaders(
        requestHeaders: Map<String, String>,
        sourceHost: String,
        destinationHost: String,
    ): Map<String, String> {
        if (sourceHost.equals(destinationHost, ignoreCase = true)) return requestHeaders
        return requestHeaders.filterKeys { name ->
            name.lowercase(Locale.ROOT) !in CROSS_HOST_STRIPPED_REQUEST_HEADERS
        }
    }

    private fun proxyErrorResponse(message: String): WebResourceResponse =
        response(statusCode = 502, reason = "Bad Gateway", body = message)

    private fun response(
        statusCode: Int,
        reason: String,
        body: String,
    ): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        statusCode,
        reason,
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(body.toByteArray()),
    )

    private class DisconnectingInputStream(
        input: InputStream,
        private val connection: HttpURLConnection,
    ) : FilterInputStream(input) {
        override fun close() {
            try {
                super.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        val STRIPPED_REQUEST_HEADERS = setOf(
            "accept-encoding",
            "connection",
            "content-length",
            "host",
            "proxy-connection",
            "referer",
            "x-requested-with",
        )
        val STRIPPED_RESPONSE_HEADERS = setOf(
            "connection",
            "content-encoding",
            "content-length",
            "proxy-connection",
            "transfer-encoding",
        )
        val CROSS_HOST_STRIPPED_REQUEST_HEADERS = setOf(
            "authorization",
            "cookie",
            "cookie2",
            "origin",
            "proxy-authorization",
        )
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_REDIRECTS = 5
    }
}

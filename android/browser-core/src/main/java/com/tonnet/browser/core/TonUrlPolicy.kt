package com.tonnet.browser.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object TonUrlPolicy {
    private const val MAX_INPUT_LENGTH = 4_096
    private const val MAX_HOST_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
    private const val TON_SITE_PREFIX = "tonsite://"
    private const val TON_WALLET_PREFIX = "ton://"
    private val validLabel = Regex("[a-z0-9_-]+")
    private val allowedSuffixes = listOf(".ton", ".adnl", ".t.me")

    fun normalizeUserInput(input: String): Result<TonUrl> {
        val trimmed = input.trim()
        val label = trimmed.lowercase(Locale.ROOT)
        val expanded = if (
            label.length in 1..MAX_LABEL_LENGTH &&
            validLabel.matches(label)
        ) {
            "$label.ton"
        } else {
            trimmed
        }
        return normalize(expanded)
    }

    fun normalize(input: String): Result<TonUrl> = runCatching {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "Address is empty" }
        require(trimmed.length <= MAX_INPUT_LENGTH) { "Address is too long" }
        require(trimmed.none { it.code < 0x20 || it.code == 0x7f }) { "Control characters are not allowed" }
        require('\\' !in trimmed) { "Backslashes are not allowed" }

        val candidate = when {
            trimmed.startsWith(TON_SITE_PREFIX, ignoreCase = true) ->
                "http://${trimmed.substring(TON_SITE_PREFIX.length)}"
            "://" !in trimmed -> "http://$trimmed"
            else -> trimmed
        }

        val parsed = try {
            URI(candidate)
        } catch (error: URISyntaxException) {
            throw IllegalArgumentException("Invalid address", error)
        }

        require(parsed.scheme?.lowercase(Locale.ROOT) == "http") { "Only HTTP TON Sites are supported" }
        require(parsed.rawUserInfo == null) { "User information is not allowed" }
        require(parsed.port == -1 || parsed.port == 80) { "Only the standard TON Site port is allowed" }
        require(parsed.rawAuthority?.contains('@') != true) { "User information is not allowed" }

        val rawAuthority = parsed.rawAuthority ?: throw IllegalArgumentException("Missing host")
        val rawHost = parsed.host ?: rawAuthority
        require(rawHost.all { it.code in 0x21..0x7e }) { "Unicode hosts must be entered as punycode" }
        require(':' !in rawHost) { "Invalid host" }
        val host = rawHost.lowercase(Locale.ROOT).trimEnd('.')
        require(host.isNotEmpty() && host.length <= MAX_HOST_LENGTH) { "Invalid host" }
        val suffix = allowedSuffixes.firstOrNull(host::endsWith)
        require(suffix != null) { "Only .ton, .adnl and .t.me hosts are allowed" }

        val name = host.removeSuffix(suffix)
        require(name.isNotEmpty()) { "Missing TON Site name" }
        val labels = name.split('.')
        require(labels.all { it.isNotEmpty() && it.length <= MAX_LABEL_LENGTH && validLabel.matches(it) }) {
            "Invalid TON Site name"
        }

        val canonical = buildString {
            append("http://")
            append(host)
            append(parsed.rawPath?.ifEmpty { "/" } ?: "/")
            parsed.rawQuery?.let { append('?').append(it) }
            parsed.rawFragment?.let { append('#').append(it) }
        }

        TonUrl(canonical, host)
    }

    fun isAllowed(url: String?): Boolean = url != null && normalize(url).isSuccess

    fun isTonSiteUri(url: String?): Boolean =
        hasSafePrefix(url, TON_SITE_PREFIX)

    fun isWalletUri(url: String?): Boolean =
        hasSafePrefix(url, TON_WALLET_PREFIX)

    private fun hasSafePrefix(url: String?, prefix: String): Boolean {
        val trimmed = url?.trim() ?: return false
        return trimmed.length in 1..MAX_INPUT_LENGTH &&
            trimmed.none { it.code < 0x20 || it.code == 0x7f } &&
            trimmed.startsWith(prefix, ignoreCase = true)
    }
}

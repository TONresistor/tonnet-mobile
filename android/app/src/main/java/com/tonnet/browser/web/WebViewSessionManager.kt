package com.tonnet.browser.web

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import java.security.SecureRandom
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class WebViewSessionManager {
    private var profile: Profile? = null
    private var privacySeed: String? = null

    val profileName: String
        @SuppressLint("RequiresFeature")
        get() {
            check(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
            return requireNotNull(profile).name
        }

    val fingerprintingSeed: String
        get() = requireNotNull(privacySeed)

    @SuppressLint("WrongConstant")
    fun unsupportedFeatures(): List<String> = REQUIRED_FEATURES.filterNot(WebViewFeature::isFeatureSupported)

    @SuppressLint("RequiresFeature")
    fun startSession(): Result<Unit> = runCatching {
        check(unsupportedFeatures().isEmpty())
        val store = ProfileStore.getInstance()
        store.getAllProfileNames()
            .filter { it.startsWith(LEGACY_PROFILE_PREFIX) }
            .forEach { staleName ->
                check(store.deleteProfile(staleName)) {
                    "Could not delete stale private WebView profile"
                }
            }

        profile = store.getOrCreateProfile(PERSISTENT_PROFILE_NAME).also(::configure)
        privacySeed = newPrivacySeed()
    }

    @SuppressLint("RequiresFeature")
    fun clearBrowsingData(executor: Executor, after: (Result<Unit>) -> Unit) {
        val completed = AtomicBoolean(false)
        fun complete(result: Result<Unit>) {
            if (completed.compareAndSet(false, true)) after(result)
        }

        val current = profile
        if (current == null) {
            complete(Result.success(Unit))
            return
        }
        runCatching {
            check(WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))
            WebView.clearClientCertPreferences(null)
            current.geolocationPermissions.clearAll()
            WebStorageCompat.deleteBrowsingData(current.webStorage, executor) {
                complete(Result.success(Unit))
            }
        }.onFailure { error ->
            complete(Result.failure(error))
        }
    }

    @SuppressLint("RequiresFeature")
    fun clearAndEndSession(executor: Executor, after: (Result<Unit>) -> Unit = {}) {
        clearBrowsingData(executor) { result ->
            releaseSession()
            after(result)
        }
    }

    fun releaseSession() {
        profile = null
        privacySeed = null
    }

    @SuppressLint("RequiresFeature")
    private fun configure(configured: Profile) {
        configured.cookieManager.setAcceptCookie(true)
        configured.geolocationPermissions.clearAll()
        configured.serviceWorkerController.serviceWorkerWebSettings.apply {
            blockNetworkLoads = true
            allowContentAccess = false
            allowFileAccess = false
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
    }

    companion object {
        private const val LEGACY_PROFILE_PREFIX = "tonnet-session-"
        private const val PERSISTENT_PROFILE_NAME = "tonnet-browser-v2"
        private val REQUIRED_FEATURES = listOf(
            WebViewFeature.PROXY_OVERRIDE,
            WebViewFeature.MULTI_PROFILE,
            WebViewFeature.DELETE_BROWSING_DATA,
            WebViewFeature.DOCUMENT_START_SCRIPT,
            WebViewFeature.COOKIE_INTERCEPT,
        )
        private val SECURE_RANDOM = SecureRandom()
        private val HEX = "0123456789abcdef".toCharArray()

        private fun newPrivacySeed(): String {
            val bytes = ByteArray(16).also(SECURE_RANDOM::nextBytes)
            return CharArray(bytes.size * 2).also { output ->
                bytes.forEachIndexed { index, byte ->
                    val value = byte.toInt() and 0xff
                    output[index * 2] = HEX[value ushr 4]
                    output[index * 2 + 1] = HEX[value and 0x0f]
                }
            }.concatToString()
        }
    }
}

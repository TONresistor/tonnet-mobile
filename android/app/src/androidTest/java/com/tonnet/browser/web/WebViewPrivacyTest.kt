package com.tonnet.browser.web

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewMediaIntegrityApiStatusConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebViewPrivacyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun browsingDataPersistsWhenSessionIsReleasedWithoutClearing() {
        val firstManager = WebViewSessionManager()
        lateinit var firstProfileName: String
        startCleanSession(firstManager)
        instrumentation.runOnMainSync {
            firstProfileName = firstManager.profileName
            val profile = ProfileStore.getInstance().getOrCreateProfile(firstProfileName)
            profile.cookieManager.setCookie(TEST_URL, "private=value")
            profile.cookieManager.flush()
            assertTrue(profile.cookieManager.getCookie(TEST_URL).orEmpty().contains("private=value"))
            firstManager.releaseSession()
        }

        val secondManager = WebViewSessionManager()
        instrumentation.runOnMainSync {
            secondManager.startSession().getOrThrow()
            assertEquals(firstProfileName, secondManager.profileName)
            val profile = ProfileStore.getInstance().getOrCreateProfile(secondManager.profileName)
            assertTrue(profile.cookieManager.getCookie(TEST_URL).orEmpty().contains("private=value"))
        }

        clearSession(secondManager)
    }

    @Test
    fun browsingDataDeletionCompletesAndKeepsStableProfile() {
        val firstManager = WebViewSessionManager()
        lateinit var firstProfileName: String
        startCleanSession(firstManager)
        instrumentation.runOnMainSync {
            firstProfileName = firstManager.profileName
            val profile = ProfileStore.getInstance().getOrCreateProfile(firstProfileName)
            profile.cookieManager.setCookie(TEST_URL, "private=value")
            profile.cookieManager.flush()
            assertTrue(profile.cookieManager.getCookie(TEST_URL).orEmpty().contains("private=value"))
        }

        val cleared = CountDownLatch(1)
        var clearResult: Result<Unit>? = null
        instrumentation.runOnMainSync {
            firstManager.clearAndEndSession(ContextCompat.getMainExecutor(context)) { result ->
                clearResult = result
                cleared.countDown()
            }
        }
        assertTrue("WebView deletion callback timed out", cleared.await(10, TimeUnit.SECONDS))
        requireNotNull(clearResult).getOrThrow()

        val secondManager = WebViewSessionManager()
        instrumentation.runOnMainSync {
            secondManager.startSession().getOrThrow()
            assertEquals(firstProfileName, secondManager.profileName)
            val profile = ProfileStore.getInstance().getOrCreateProfile(secondManager.profileName)
            assertNull(profile.cookieManager.getCookie(TEST_URL))
            secondManager.releaseSession()
        }
    }

    @Test
    fun createdWebViewEnforcesPrivacyCriticalSettings() {
        val manager = WebViewSessionManager()
        lateinit var webView: WebView
        startCleanSession(manager)
        instrumentation.runOnMainSync {
            webView = TonWebViewFactory.create(
                context = context,
                profileName = manager.profileName,
                javaScriptEnabled = true,
                antiFingerprintingEnabled = true,
                fingerprintingSeed = manager.fingerprintingSeed,
                events = NoOpWebViewEvents,
            )

            assertFalse(webView.settings.allowFileAccess)
            assertFalse(webView.settings.allowContentAccess)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)
            assertFalse(webView.settings.javaScriptCanOpenWindowsAutomatically)
            assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(webView))
            assertTrue(
                WebSettingsCompat.areCookiesIncludedInShouldInterceptRequest(webView.settings),
            )
            if (
                WebViewFeature.isFeatureSupported(
                    WebViewFeature.ATTRIBUTION_REGISTRATION_BEHAVIOR,
                )
            ) {
                assertEquals(
                    WebSettingsCompat.ATTRIBUTION_BEHAVIOR_DISABLED,
                    WebSettingsCompat.getAttributionRegistrationBehavior(webView.settings),
                )
            }
            if (
                WebViewFeature.isFeatureSupported(
                    WebViewFeature.WEBVIEW_MEDIA_INTEGRITY_API_STATUS,
                )
            ) {
                assertEquals(
                    WebViewMediaIntegrityApiStatusConfig.WEBVIEW_MEDIA_INTEGRITY_API_DISABLED,
                    WebSettingsCompat.getWebViewMediaIntegrityApiStatus(webView.settings)
                        .defaultStatus,
                )
            }
            webView.destroy()
        }
        clearSession(manager)
    }

    @Test
    fun antiFingerprintingKeepsNativeJavaScriptShape() {
        val manager = WebViewSessionManager()
        lateinit var baseline: WebView
        lateinit var protected: WebView
        val baselineLoaded = CountDownLatch(1)
        val protectedLoaded = CountDownLatch(1)

        startCleanSession(manager)
        instrumentation.runOnMainSync {
            baseline = WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        baselineLoaded.countDown()
                    }
                }
            }
            protected = TonWebViewFactory.create(
                context = context,
                profileName = manager.profileName,
                javaScriptEnabled = true,
                antiFingerprintingEnabled = true,
                fingerprintingSeed = manager.fingerprintingSeed,
                events = object : TonWebViewEvents by NoOpWebViewEvents {
                    override fun onPageFinished(view: WebView, url: String?) {
                        protectedLoaded.countDown()
                    }
                },
            )
            baseline.loadDataWithBaseURL(TEST_URL, EMPTY_PAGE, "text/html", "utf-8", null)
            protected.loadDataWithBaseURL(TEST_URL, EMPTY_PAGE, "text/html", "utf-8", null)
        }

        assertTrue("Baseline WebView load timed out", baselineLoaded.await(10, TimeUnit.SECONDS))
        assertTrue("Protected WebView load timed out", protectedLoaded.await(10, TimeUnit.SECONDS))

        assertEquals(
            evaluateJavaScript(baseline, BLOCKED_GLOBAL_PRESENCE),
            evaluateJavaScript(protected, BLOCKED_GLOBAL_PRESENCE),
        )
        assertEquals(
            evaluateJavaScript(baseline, CANVAS_DESCRIPTOR),
            evaluateJavaScript(protected, CANVAS_DESCRIPTOR),
        )
        assertEquals(
            evaluateJavaScript(baseline, CANVAS_NATIVE_SOURCE),
            evaluateJavaScript(protected, CANVAS_NATIVE_SOURCE),
        )
        assertEquals(
            evaluateJavaScript(baseline, CANVAS_FUNCTION_SHAPE),
            evaluateJavaScript(protected, CANVAS_FUNCTION_SHAPE),
        )

        instrumentation.runOnMainSync {
            baseline.destroy()
            protected.destroy()
        }
        clearSession(manager)
    }

    private fun evaluateJavaScript(webView: WebView, expression: String): String {
        val evaluated = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(expression) { value ->
                result = value
                evaluated.countDown()
            }
        }
        assertTrue("JavaScript evaluation timed out", evaluated.await(10, TimeUnit.SECONDS))
        return requireNotNull(result)
    }

    private fun clearSession(manager: WebViewSessionManager) {
        val cleared = CountDownLatch(1)
        var clearResult: Result<Unit>? = null
        instrumentation.runOnMainSync {
            manager.clearAndEndSession(ContextCompat.getMainExecutor(context)) { result ->
                clearResult = result
                cleared.countDown()
            }
        }
        assertTrue("WebView deletion callback timed out", cleared.await(10, TimeUnit.SECONDS))
        requireNotNull(clearResult).getOrThrow()
    }

    private fun startCleanSession(manager: WebViewSessionManager) {
        val unsupported = manager.unsupportedFeatures()
        assumeTrue(
            "Requires current WebView provider: ${unsupported.joinToString()}",
            unsupported.isEmpty(),
        )
        val cleared = CountDownLatch(1)
        var clearResult: Result<Unit>? = null
        instrumentation.runOnMainSync {
            manager.startSession().getOrThrow()
            manager.clearBrowsingData(ContextCompat.getMainExecutor(context)) { result ->
                clearResult = result
                cleared.countDown()
            }
        }
        assertTrue("WebView deletion callback timed out", cleared.await(10, TimeUnit.SECONDS))
        requireNotNull(clearResult).getOrThrow()
    }

    private companion object {
        const val TEST_URL = "http://privacy-test.ton/"
        const val EMPTY_PAGE = "<!doctype html><html><body></body></html>"
        const val BLOCKED_GLOBAL_PRESENCE = """
            ['Sensor','NDEFReader','XRSystem','SpeechRecognition']
              .map((name) => name in globalThis ? '1' : '0')
              .join('')
        """
        const val CANVAS_DESCRIPTOR = """
            (() => {
              const value = Object.getOwnPropertyDescriptor(
                HTMLCanvasElement.prototype,
                'toDataURL'
              );
              return [value.writable, value.enumerable, value.configurable].join(':');
            })()
        """
        const val CANVAS_NATIVE_SOURCE = """
            Function.prototype.toString.call(HTMLCanvasElement.prototype.toDataURL)
        """
        const val CANVAS_FUNCTION_SHAPE = """
            (() => {
              const value = HTMLCanvasElement.prototype.toDataURL;
              return [
                value.name,
                value.length,
                Object.prototype.hasOwnProperty.call(value, 'prototype')
              ].join(':');
            })()
        """

        val NoOpWebViewEvents = object : TonWebViewEvents {
            override fun onPageStarted(view: WebView, url: String?) = Unit
            override fun onPageFinished(view: WebView, url: String?) = Unit
            override fun onTitleChanged(view: WebView, title: String?) = Unit
            override fun onProgressChanged(view: WebView, progress: Int) = Unit
            override fun onTonSiteLink(url: String) = Unit
            override fun onWalletLink(url: String) = Unit
            override fun onNavigationBlocked(url: String?) = Unit
            override fun onPageError(view: WebView, error: TonPageError) = Unit
            override fun onRendererGone(view: WebView) = Unit
        }
    }
}

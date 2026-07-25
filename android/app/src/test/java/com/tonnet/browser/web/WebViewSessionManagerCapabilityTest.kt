package com.tonnet.browser.web

import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewSessionManagerCapabilityTest {
    @Test
    fun unavailableFeaturesAreReportedWithoutStartingWebView() {
        val manager = WebViewSessionManager { false }

        assertEquals(
            listOf(
                WebViewFeature.PROXY_OVERRIDE,
                WebViewFeature.MULTI_PROFILE,
                WebViewFeature.DELETE_BROWSING_DATA,
                WebViewFeature.DOCUMENT_START_SCRIPT,
                WebViewFeature.COOKIE_INTERCEPT,
            ),
            manager.unsupportedFeatures(),
        )
    }
}

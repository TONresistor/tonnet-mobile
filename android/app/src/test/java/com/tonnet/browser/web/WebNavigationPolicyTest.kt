package com.tonnet.browser.web

import org.junit.Assert.assertEquals
import org.junit.Test

class WebNavigationPolicyTest {
    @Test
    fun `TON Site links are opened only in the main frame`() {
        assertEquals(
            WebNavigationDecision.OPEN_TON_SITE,
            WebNavigationPolicy.decide("tonsite://site.ton", true, false),
        )
        assertEquals(
            WebNavigationDecision.BLOCK_SILENTLY,
            WebNavigationPolicy.decide("tonsite://site.ton/script.js", false, false),
        )
    }

    @Test
    fun `wallet links require an explicit main-frame gesture`() {
        assertEquals(
            WebNavigationDecision.OPEN_WALLET,
            WebNavigationPolicy.decide("ton://transfer/example", true, true),
        )
        assertEquals(
            WebNavigationDecision.BLOCK_SILENTLY,
            WebNavigationPolicy.decide("ton://transfer/example", true, false),
        )
        assertEquals(
            WebNavigationDecision.BLOCK_SILENTLY,
            WebNavigationPolicy.decide("ton://transfer/example", false, true),
        )
    }

    @Test
    fun `main-frame clearnet navigation is blocked and reported`() {
        assertEquals(
            WebNavigationDecision.ALLOW_WEBVIEW,
            WebNavigationPolicy.decide("http://site.ton/", true, false),
        )
        assertEquals(
            WebNavigationDecision.BLOCK_AND_NOTIFY,
            WebNavigationPolicy.decide("https://example.com/", true, true),
        )
        assertEquals(
            WebNavigationDecision.ALLOW_WEBVIEW,
            WebNavigationPolicy.decide("https://example.com/image.png", false, false),
        )
    }
}

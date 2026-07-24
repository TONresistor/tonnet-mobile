package com.tonnet.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressPresentationTest {
    @Test
    fun `TON Site badge is shown only for an unfocused supported address`() {
        assertEquals(
            true,
            AddressPresentation.shouldShowTonSiteBadge("http://foundation.ton/", focused = false),
        )
        assertEquals(
            true,
            AddressPresentation.shouldShowTonSiteBadge("http://abcdef_0123.adnl/", focused = false),
        )
        assertEquals(
            true,
            AddressPresentation.shouldShowTonSiteBadge("http://collectible.t.me/", focused = false),
        )
        assertEquals(
            false,
            AddressPresentation.shouldShowTonSiteBadge("http://foundation.ton/", focused = true),
        )
        assertEquals(
            false,
            AddressPresentation.shouldShowTonSiteBadge("https://example.com/", focused = false),
        )
        assertEquals(false, AddressPresentation.shouldShowTonSiteBadge(null, focused = false))
    }

    @Test
    fun `empty address stays empty`() {
        assertEquals("", AddressPresentation.display(null, focused = false))
        assertEquals("", AddressPresentation.display("", focused = true))
        assertEquals("", AddressPresentation.display("   ", focused = false))
    }

    @Test
    fun `focused address exposes the TON Site scheme`() {
        assertEquals(
            "tonsite://foundation.ton/path?q=1#section",
            AddressPresentation.display(
                "http://foundation.ton/path?q=1#section",
                focused = true,
            ),
        )
    }

    @Test
    fun `unfocused root address hides scheme and trailing slash`() {
        assertEquals(
            "foundation.ton",
            AddressPresentation.display("http://foundation.ton/", focused = false),
        )
    }

    @Test
    fun `unfocused address preserves path query and fragment`() {
        assertEquals(
            "foundation.ton/path/?q=1#section",
            AddressPresentation.display(
                "http://foundation.ton/path/?q=1#section",
                focused = false,
            ),
        )
    }

    @Test
    fun `unfocused root query preserves its slash`() {
        assertEquals(
            "foundation.ton/?q=1",
            AddressPresentation.display("http://foundation.ton/?q=1", focused = false),
        )
    }

    @Test
    fun `startup uses an external address before the configured homepage`() {
        assertEquals(
            "http://link.ton/",
            StartupNavigation.firstAddress(
                intentAddress = "http://link.ton/",
                homepage = "http://home.ton/",
            ),
        )
    }

    @Test
    fun `startup uses homepage only when no external address exists`() {
        assertEquals(
            "http://home.ton/",
            StartupNavigation.firstAddress(
                intentAddress = null,
                homepage = "http://home.ton/",
            ),
        )
        assertEquals(null, StartupNavigation.firstAddress(null, null))
    }

    @Test
    fun `chrome hides only the required layers`() {
        assertEquals(
            BrowserChromeVisibility(showTop = true, showBottom = true, showSettings = false),
            BrowserChromePolicy.visibility(BrowserOverlay.NONE, imeVisible = false),
        )
        assertEquals(
            BrowserChromeVisibility(showTop = true, showBottom = false, showSettings = false),
            BrowserChromePolicy.visibility(BrowserOverlay.NONE, imeVisible = true),
        )
        assertEquals(
            BrowserChromeVisibility(showTop = false, showBottom = true, showSettings = true),
            BrowserChromePolicy.visibility(BrowserOverlay.SETTINGS, imeVisible = false),
        )
        assertEquals(
            BrowserChromeVisibility(showTop = false, showBottom = false, showSettings = true),
            BrowserChromePolicy.visibility(BrowserOverlay.SETTINGS, imeVisible = true),
        )
    }

    @Test
    fun `tab bar blur starts on Android 12`() {
        assertEquals(false, TabBarBlurPolicy.isSupported(30))
        assertEquals(true, TabBarBlurPolicy.isSupported(31))
        assertEquals(true, TabBarBlurPolicy.isSupported(36))
        assertEquals(1f, TabBarBlurPolicy.SCALE_FACTOR, 0f)
        assertEquals(25f, TabBarBlurPolicy.RADIUS_PX, 0f)
    }

    @Test
    fun `sheet list height is bounded and remains scrollable`() {
        assertEquals(0, BrowserSheetSizing.listHeight(0, 60, 600, 132))
        assertEquals(120, BrowserSheetSizing.listHeight(2, 60, 600, 132))
        assertEquals(468, BrowserSheetSizing.listHeight(20, 60, 600, 132))
        assertEquals(60, BrowserSheetSizing.listHeight(1, 60, 150, 132))
    }

    @Test
    fun `chrome actions follow address focus and page state`() {
        val page = BrowserChromeState(
            address = "http://foundation.ton/",
            addressHasText = true,
            addressFocused = false,
            isBookmarked = true,
            canGoBack = false,
            canGoForward = false,
            proxyReady = true,
            tabCount = 1,
            overlay = BrowserOverlay.NONE,
        )
        assertEquals(false, page.showClearAddress)
        assertEquals(true, page.showBookmarkToggle)
        assertEquals(true, page.canReload)

        val editing = page.copy(addressFocused = true)
        assertEquals(true, editing.showClearAddress)
        assertEquals(false, editing.showBookmarkToggle)

        val blank = page.copy(address = null, addressHasText = false)
        assertEquals(false, blank.showClearAddress)
        assertEquals(false, blank.showBookmarkToggle)
        assertEquals(false, blank.canReload)
    }

    @Test
    fun `closing the active tab selects the adjacent tab`() {
        val ids = listOf(10L, 20L, 30L)
        assertEquals(30L, TabClosePolicy.nextActiveId(ids, closedId = 20L, activeId = 20L))
        assertEquals(20L, TabClosePolicy.nextActiveId(ids, closedId = 30L, activeId = 30L))
        assertEquals(20L, TabClosePolicy.nextActiveId(ids, closedId = 10L, activeId = 20L))
        assertEquals(null, TabClosePolicy.nextActiveId(listOf(10L), closedId = 10L, activeId = 10L))
    }
}

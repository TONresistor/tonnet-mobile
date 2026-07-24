package com.tonnet.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserSwipePolicyTest {
    @Test
    fun `right swipe navigates back and left swipe navigates forward`() {
        assertEquals(
            BrowserSwipeAction.BACK,
            BrowserSwipePolicy.action(90f, 12f, 64f, canGoBack = true, canGoForward = true),
        )
        assertEquals(
            BrowserSwipeAction.FORWARD,
            BrowserSwipePolicy.action(-90f, 12f, 64f, canGoBack = true, canGoForward = true),
        )
    }

    @Test
    fun `vertical short and unavailable swipes do nothing`() {
        assertEquals(
            BrowserSwipeAction.NONE,
            BrowserSwipePolicy.action(40f, 8f, 64f, canGoBack = true, canGoForward = true),
        )
        assertEquals(
            BrowserSwipeAction.NONE,
            BrowserSwipePolicy.action(90f, 100f, 64f, canGoBack = true, canGoForward = true),
        )
        assertEquals(
            BrowserSwipeAction.NONE,
            BrowserSwipePolicy.action(90f, 8f, 64f, canGoBack = false, canGoForward = true),
        )
        assertEquals(
            BrowserSwipeAction.NONE,
            BrowserSwipePolicy.action(-90f, 8f, 64f, canGoBack = true, canGoForward = false),
        )
    }
}

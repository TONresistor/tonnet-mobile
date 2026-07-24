package com.tonnet.browser.web

import android.content.Context
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserNavigationControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun tabChangesKeepNavigationStateSynchronized() {
        instrumentation.runOnMainSync {
            val navigation = createController()
            val first = requireCreated(navigation.create(activate = true))
            val firstWebView = requireNotNull(first.webView)
            navigation.onPageStarted(firstWebView, "http://first.ton/page")
            navigation.onProgressChanged(firstWebView, 42)
            navigation.setBookmarked(true)

            assertEquals("http://first.ton/page", navigation.state.address)
            assertEquals(42, navigation.state.progress)
            assertTrue(navigation.state.isBookmarked)

            val second = requireCreated(navigation.create(activate = true))
            assertEquals(second.model.id, navigation.state.activeTabId)
            assertNull(navigation.state.address)
            assertFalse(navigation.state.isBookmarked)

            val closed = navigation.close(second.model.id) as CloseTabResult.Closed
            assertEquals(first.model.id, closed.activeSession?.model?.id)
            assertEquals("http://first.ton/page", navigation.state.address)
            assertEquals(1, navigation.state.tabCount)
            navigation.destroyAll()
        }
    }

    @Test
    fun invalidAddressDoesNotCreateATab() {
        instrumentation.runOnMainSync {
            val navigation = createController()

            assertEquals(LoadResult.InvalidAddress, navigation.load("not a valid address"))
            assertEquals(0, navigation.size)
            assertNull(navigation.state.activeTabId)
        }
    }

    private fun createController(): BrowserNavigationController {
        val tabs = BrowserTabController(
            container = FrameLayout(context),
            createWebView = { WebView(context) },
        )
        return BrowserNavigationController(tabs) { "New tab" }
    }

    private fun requireCreated(result: CreateTabResult): BrowserTabController.TabSession =
        (result as CreateTabResult.Created).session
}

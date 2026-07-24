package com.tonnet.browser.web

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.tonnet.browser.core.BrowserTab
import com.tonnet.browser.ui.TabClosePolicy
import java.util.concurrent.atomic.AtomicLong

internal class BrowserTabController(
    private val container: FrameLayout,
    private val createWebView: () -> WebView,
    private val maxTabs: Int = DEFAULT_MAX_TABS,
    private val maxLiveWebViews: Int = DEFAULT_MAX_LIVE_WEBVIEWS,
) {
    private val sessions = mutableListOf<TabSession>()
    private val ids = AtomicLong(1)

    var activeId: Long? = null
        private set

    val size: Int
        get() = sessions.size

    fun all(): List<TabSession> = sessions.toList()

    fun active(): TabSession? = sessions.firstOrNull { it.model.id == activeId }

    fun forWebView(webView: WebView): TabSession? =
        sessions.firstOrNull { it.webView === webView }

    fun create(title: String): TabSession? {
        if (sessions.size >= maxTabs) return null
        return TabSession(
            model = BrowserTab(
                id = ids.getAndIncrement(),
                title = title,
            ),
        ).also(sessions::add)
    }

    fun activate(id: Long): TabSession? {
        val next = sessions.firstOrNull { it.model.id == id } ?: return null
        active()?.webView?.let(::detach)
        activeId = id
        next.model.lastActivatedAt = System.nanoTime()
        val webView = ensureWebView(next)
        detach(webView)
        container.removeAllViews()
        container.addView(webView, MATCH_PARENT_LAYOUT)
        enforceLiveWebViewLimit()
        return next
    }

    fun ensureWebView(session: TabSession): WebView {
        session.webView?.let { return it }
        val webView = createWebView()
        session.webView = webView
        val savedState = session.savedState
        session.savedState = null
        if (savedState != null && webView.restoreState(savedState) == null) {
            session.model.currentUrl?.let(webView::loadUrl)
        }
        return webView
    }

    fun close(id: Long): ClosedTab? {
        val target = sessions.firstOrNull { it.model.id == id } ?: return null
        val result = ClosedTab(
            wasActive = id == activeId,
            nextActiveId = TabClosePolicy.nextActiveId(
                tabIds = sessions.map { it.model.id },
                closedId = id,
                activeId = activeId,
            ),
        )
        destroy(target)
        sessions.remove(target)
        if (result.wasActive) activeId = null
        return result
    }

    fun resetActive(title: String) {
        active()?.let { session ->
            destroyWebView(session)
            session.model.currentUrl = null
            session.model.title = title
        }
        container.removeAllViews()
    }

    fun recreateWebViews() {
        sessions.forEach(::destroyWebView)
        container.removeAllViews()
    }

    fun replaceRenderer(session: TabSession): WebView {
        destroyWebView(session)
        return ensureWebView(session)
    }

    fun discardRenderer(session: TabSession) {
        destroyWebView(session)
        session.savedState = null
    }

    fun destroyAll() {
        sessions.toList().forEach(::destroy)
        sessions.clear()
        activeId = null
        container.removeAllViews()
    }

    private fun enforceLiveWebViewLimit() {
        sessions
            .filter { it.model.id != activeId && it.webView != null }
            .sortedByDescending { it.model.lastActivatedAt }
            .drop((maxLiveWebViews - 1).coerceAtLeast(0))
            .forEach(::suspend)
    }

    private fun suspend(session: TabSession) {
        val webView = session.webView ?: return
        session.savedState = Bundle().also(webView::saveState)
        dispose(webView)
        session.webView = null
    }

    private fun destroy(session: TabSession) {
        destroyWebView(session)
        session.savedState = null
    }

    private fun destroyWebView(session: TabSession) {
        session.webView?.let(::dispose)
        session.webView = null
    }

    private fun dispose(webView: WebView) {
        detach(webView)
        runCatching { webView.settings.blockNetworkLoads = true }
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun detach(webView: WebView) {
        (webView.parent as? FrameLayout)?.removeView(webView)
    }

    data class TabSession(
        val model: BrowserTab,
        var webView: WebView? = null,
        var savedState: Bundle? = null,
    )

    data class ClosedTab(
        val wasActive: Boolean,
        val nextActiveId: Long?,
    )

    private companion object {
        const val DEFAULT_MAX_TABS = 8
        const val DEFAULT_MAX_LIVE_WEBVIEWS = 3
        val MATCH_PARENT_LAYOUT = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
    }
}

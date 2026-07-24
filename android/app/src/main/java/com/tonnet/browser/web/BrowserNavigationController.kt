package com.tonnet.browser.web

import android.webkit.WebView
import com.tonnet.browser.core.TonUrlPolicy
import com.tonnet.browser.web.BrowserTabController.TabSession

internal class BrowserNavigationController(
    private val tabs: BrowserTabController,
    private val newTabTitle: () -> String,
) {
    var state = BrowserNavigationState()
        private set

    val size: Int
        get() = tabs.size

    fun all(): List<TabSession> = tabs.all()

    fun active(): TabSession? = tabs.active()

    fun forWebView(webView: WebView): TabSession? = tabs.forWebView(webView)

    fun queue(address: String?) {
        state = state.copy(pendingAddress = address)
    }

    fun takeQueued(): String? {
        val address = state.pendingAddress
        state = state.copy(pendingAddress = null)
        return address
    }

    fun create(activate: Boolean): CreateTabResult {
        val session = tabs.create(newTabTitle()) ?: return CreateTabResult.LimitReached
        if (activate) tabs.activate(session.model.id)
        syncActiveState()
        return CreateTabResult.Created(session)
    }

    fun activate(id: Long): TabSession? {
        val session = tabs.activate(id) ?: return null
        syncActiveState()
        return session
    }

    fun close(id: Long): CloseTabResult {
        val closed = tabs.close(id) ?: return CloseTabResult.NotFound
        val active = when {
            tabs.size == 0 -> when (val created = create(activate = true)) {
                is CreateTabResult.Created -> created.session
                CreateTabResult.LimitReached -> null
            }
            closed.wasActive -> tabs.activate(requireNotNull(closed.nextActiveId))
            else -> tabs.active()
        }
        syncActiveState()
        return CloseTabResult.Closed(active)
    }

    fun load(input: String): LoadResult {
        val normalized = TonUrlPolicy.normalizeUserInput(input).getOrNull()
            ?: return LoadResult.InvalidAddress
        val session = active() ?: when (val created = create(activate = true)) {
            is CreateTabResult.Created -> created.session
            CreateTabResult.LimitReached -> return LoadResult.LimitReached
        }
        val webView = tabs.ensureWebView(session)
        session.model.currentUrl = normalized.value
        state = state.copy(
            address = normalized.value,
            loading = true,
            progress = 0,
        )
        webView.loadUrl(normalized.value)
        return LoadResult.Loaded(session)
    }

    fun resetToHome() {
        tabs.resetActive(newTabTitle())
        state = state.copy(
            address = null,
            isBookmarked = false,
            loading = false,
            progress = 0,
            pendingAddress = null,
        )
    }

    fun destroyAll() {
        tabs.destroyAll()
        state = BrowserNavigationState()
    }

    fun recreateWebViews() {
        tabs.recreateWebViews()
        syncActiveState()
    }

    fun ensureWebView(session: TabSession): WebView = tabs.ensureWebView(session)

    fun replaceRenderer(session: TabSession): WebView = tabs.replaceRenderer(session)

    fun discardRenderer(session: TabSession) = tabs.discardRenderer(session)

    fun onPageStarted(webView: WebView, url: String?): TabSession? {
        val session = forWebView(webView) ?: return null
        TonUrlPolicy.normalize(url.orEmpty()).getOrNull()?.let { normalized ->
            session.model.currentUrl = normalized.value
            if (session.model.id == tabs.activeId) {
                state = state.copy(address = normalized.value)
            }
        }
        if (session.model.id == tabs.activeId) {
            state = state.copy(loading = true)
        }
        return session
    }

    fun onPageFinished(webView: WebView): Boolean {
        if (forWebView(webView)?.model?.id != tabs.activeId) return false
        state = state.copy(loading = false, progress = 100)
        return true
    }

    fun onTitleChanged(webView: WebView, title: String?) {
        val session = forWebView(webView) ?: return
        session.model.title = title
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_TITLE_LENGTH)
            ?: session.model.currentUrl
            ?: newTabTitle()
    }

    fun onProgressChanged(webView: WebView, progress: Int): Boolean {
        if (forWebView(webView)?.model?.id != tabs.activeId) return false
        state = state.copy(progress = progress.coerceIn(0, 100))
        return true
    }

    fun setBookmarked(bookmarked: Boolean) {
        state = state.copy(isBookmarked = bookmarked)
    }

    private fun syncActiveState() {
        val active = tabs.active()
        val address = active?.model?.currentUrl
        state = state.copy(
            address = address,
            isBookmarked = if (state.address == address) state.isBookmarked else false,
            loading = false,
            progress = 0,
            tabCount = tabs.size,
            activeTabId = tabs.activeId,
        )
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 120
    }
}

internal data class BrowserNavigationState(
    val address: String? = null,
    val isBookmarked: Boolean = false,
    val loading: Boolean = false,
    val progress: Int = 0,
    val pendingAddress: String? = null,
    val tabCount: Int = 0,
    val activeTabId: Long? = null,
)

internal sealed interface CreateTabResult {
    data class Created(val session: TabSession) : CreateTabResult
    data object LimitReached : CreateTabResult
}

internal sealed interface CloseTabResult {
    data class Closed(val activeSession: TabSession?) : CloseTabResult
    data object NotFound : CloseTabResult
}

internal sealed interface LoadResult {
    data class Loaded(val session: TabSession) : LoadResult
    data object InvalidAddress : LoadResult
    data object LimitReached : LoadResult
}

package com.tonnet.browser.web

import com.tonnet.browser.core.TonUrlPolicy

internal enum class WebNavigationDecision {
    ALLOW_WEBVIEW,
    OPEN_TON_SITE,
    OPEN_WALLET,
    BLOCK_SILENTLY,
    BLOCK_AND_NOTIFY,
}

internal object WebNavigationPolicy {
    fun decide(
        url: String,
        isMainFrame: Boolean,
        hasUserGesture: Boolean,
    ): WebNavigationDecision = when {
        TonUrlPolicy.isWalletUri(url) ->
            if (isMainFrame && hasUserGesture) {
                WebNavigationDecision.OPEN_WALLET
            } else {
                WebNavigationDecision.BLOCK_SILENTLY
            }
        TonUrlPolicy.isTonSiteUri(url) ->
            if (isMainFrame) {
                WebNavigationDecision.OPEN_TON_SITE
            } else {
                WebNavigationDecision.BLOCK_SILENTLY
            }
        !isMainFrame -> WebNavigationDecision.ALLOW_WEBVIEW
        TonUrlPolicy.isAllowed(url) -> WebNavigationDecision.ALLOW_WEBVIEW
        else -> WebNavigationDecision.BLOCK_AND_NOTIFY
    }
}

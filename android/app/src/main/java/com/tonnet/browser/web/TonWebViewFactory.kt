package com.tonnet.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewMediaIntegrityApiStatusConfig
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import com.tonnet.browser.R
import com.tonnet.browser.core.TonUrlPolicy
import java.io.ByteArrayInputStream

interface TonWebViewEvents {
    fun onPageStarted(view: WebView, url: String?)
    fun onPageFinished(view: WebView, url: String?)
    fun onTitleChanged(view: WebView, title: String?)
    fun onProgressChanged(view: WebView, progress: Int)
    fun onTonSiteLink(url: String)
    fun onWalletLink(url: String)
    fun onNavigationBlocked(url: String?)
    fun onPageError(view: WebView, error: TonPageError)
    fun onRendererGone(view: WebView)
}

sealed interface TonPageError {
    data object DownloadsDisabled : TonPageError
    data object LoadFailed : TonPageError
    data class ProxyHttpError(val statusCode: Int) : TonPageError
}

object TonWebViewFactory {
    @SuppressLint("RequiresFeature", "WrongConstant")
    @Suppress("DEPRECATION")
    fun create(
        context: Context,
        profileName: String,
        javaScriptEnabled: Boolean,
        antiFingerprintingEnabled: Boolean,
        fingerprintingSeed: String,
        events: TonWebViewEvents,
        proxyPort: () -> Int? = { null },
    ): WebView {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        check(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        check(WebViewFeature.isFeatureSupported(WebViewFeature.COOKIE_INTERCEPT))
        val webView = WebView(context)
        WebViewCompat.setProfile(webView, profileName)

        webView.setBackgroundColor(ContextCompat.getColor(context, R.color.tonnet_background))
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptCanOpenWindowsAutomatically = false
            this.javaScriptEnabled = javaScriptEnabled
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            saveFormData = false
            userAgentString = genericUserAgent(context)
            safeBrowsingEnabled = false
            WebSettingsCompat.setCookiesIncludedInShouldInterceptRequest(this, true)
        }

        val errorCopy = TonWebErrorCopy(
            networkUnavailable = context.getString(R.string.network_unavailable),
            requestNotSupported = context.getString(R.string.request_not_supported),
            tonSiteUnavailable = context.getString(R.string.tonsite_unavailable),
            blockedForPrivacy = context.getString(R.string.blocked_for_privacy),
        )
        webView.webViewClient = TonWebViewClient(events, proxyPort, errorCopy)
        webView.webChromeClient = TonWebChromeClient(events)
        webView.setDownloadListener { _, _, _, _, _ ->
            events.onPageError(webView, TonPageError.DownloadsDisabled)
        }

        webView.settings.blockNetworkLoads = false
        webView.settings.loadsImagesAutomatically = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = false
        applyPermanentPrivacySettings(webView.settings)

        webView.setNetworkAvailable(true)
        webView.clearHistory()
        webView.clearCache(true)

        WebViewCompat.addDocumentStartJavaScript(
            webView,
            JavaScriptPrivacyPolicy.documentStartScript(
                antiFingerprintingEnabled = antiFingerprintingEnabled,
                sessionSeed = fingerprintingSeed,
            ),
            setOf("*"),
        )

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) {
            WebViewCompat.setWebViewRenderProcessClient(
                webView,
                ContextCompat.getMainExecutor(context),
                object : WebViewRenderProcessClient() {
                    override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
                        renderer?.terminate()
                    }

                    override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) = Unit
                },
            )
        }

        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        return webView
    }

    @SuppressLint("RequiresFeature")
    private fun applyPermanentPrivacySettings(settings: WebSettings) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ATTRIBUTION_REGISTRATION_BEHAVIOR)) {
            WebSettingsCompat.setAttributionRegistrationBehavior(
                settings,
                WebSettingsCompat.ATTRIBUTION_BEHAVIOR_DISABLED,
            )
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_MEDIA_INTEGRITY_API_STATUS)) {
            WebSettingsCompat.setWebViewMediaIntegrityApiStatus(
                settings,
                WebViewMediaIntegrityApiStatusConfig.Builder(
                    WebViewMediaIntegrityApiStatusConfig.WEBVIEW_MEDIA_INTEGRITY_API_DISABLED,
                ).build(),
            )
        }
    }

    private fun genericUserAgent(context: Context): String {
        val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
        val chromiumMajor = CHROMIUM_MAJOR.find(defaultUserAgent)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: FALLBACK_CHROMIUM_MAJOR
        return "Mozilla/5.0 (Linux; Android; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chromiumMajor.0.0.0 Mobile Safari/537.36"
    }

    private val CHROMIUM_MAJOR = Regex("""(?:Chrome|Chromium)/(\d+)""")
    private const val FALLBACK_CHROMIUM_MAJOR = 120
}

private class TonWebViewClient(
    private val events: TonWebViewEvents,
    private val proxyPort: () -> Int?,
    private val errorCopy: TonWebErrorCopy,
) : WebViewClient() {
    private val delegatedTMeRequestLoader = DelegatedTMeRequestLoader(
        connectionFactory = HttpConnectionFactory { target, proxy ->
            target.openConnection(proxy) as java.net.HttpURLConnection
        },
        errorCopy = errorCopy,
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return when (
            WebNavigationPolicy.decide(
                url = url,
                isMainFrame = request.isForMainFrame,
                hasUserGesture = request.hasGesture(),
            )
        ) {
            WebNavigationDecision.ALLOW_WEBVIEW -> false
            WebNavigationDecision.OPEN_TON_SITE -> {
                events.onTonSiteLink(url)
                true
            }
            WebNavigationDecision.OPEN_WALLET -> {
                events.onWalletLink(url)
                true
            }
            WebNavigationDecision.BLOCK_SILENTLY -> true
            WebNavigationDecision.BLOCK_AND_NOTIFY -> {
                events.onNavigationBlocked(url)
                true
            }
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        val scheme = uri.scheme?.lowercase()
        if (scheme in INTERNAL_SCHEMES) return null
        if (delegatedTMeRequestLoader.isDelegated(uri.toString())) {
            return delegatedTMeRequestLoader.load(
                DelegatedTonRequest(
                    url = uri.toString(),
                    method = request.method,
                    headers = request.requestHeaders,
                ),
                proxyPort(),
            )
        }
        if (scheme == "http" && TonUrlPolicy.isAllowed(uri.toString())) return null
        return blockedResponse()
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        events.onPageStarted(view, url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        events.onPageFinished(view, url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) events.onPageError(view, TonPageError.LoadFailed)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            events.onPageError(view, TonPageError.ProxyHttpError(errorResponse.statusCode))
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        events.onRendererGone(view)
        return true
    }

    private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Forbidden",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(errorCopy.blockedForPrivacy.toByteArray()),
    )

    private companion object {
        val INTERNAL_SCHEMES = setOf("about", "data", "blob")
    }
}

private class TonWebChromeClient(private val events: TonWebViewEvents) : WebChromeClient() {
    override fun onReceivedTitle(view: WebView, title: String?) = events.onTitleChanged(view, title)

    override fun onProgressChanged(view: WebView, newProgress: Int) = events.onProgressChanged(view, newProgress)

    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        filePathCallback.onReceiveValue(null)
        return true
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean = false

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
}

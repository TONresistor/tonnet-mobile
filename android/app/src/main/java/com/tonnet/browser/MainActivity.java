package com.tonnet.browser;

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.getcapacitor.BridgeActivity;
import com.tonnet.browser.plugins.TonProxyPlugin;

public class MainActivity extends BridgeActivity {

    // Privacy-friendly User-Agent (generic Chrome on Android)
    private static final String CUSTOM_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

    private PrivacyWebViewClient privacyWebViewClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register plugins
        registerPlugin(TonProxyPlugin.class);

        // Increase HTTP connection pool — default is 5 per host, which saturates
        // when multiple .ton sub-resources are fetched concurrently through the proxy
        System.setProperty("http.maxConnections", "20");

        super.onCreate(savedInstanceState);

        WebView.setWebContentsDebuggingEnabled(
                0 != (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE));

        // Configure WebView for privacy
        WebView webView = getBridge().getWebView();
        if (webView != null) {
            WebSettings settings = webView.getSettings();

            // MIXED_CONTENT_ALWAYS_ALLOW is required: Capacitor serves from https://localhost
            // but .ton sites are loaded via HTTP through the local proxy in iframes.
            // COMPATIBILITY_MODE blocks these HTTP iframes as mixed content.
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

            // Set custom User-Agent to avoid fingerprinting
            settings.setUserAgentString(CUSTOM_USER_AGENT);

            // Disable invasive features for privacy
            settings.setGeolocationEnabled(false);
            settings.setSaveFormData(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);

            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setTextZoom(100);

            // Block access to content:// URIs (prevents leaking local provider data)
            settings.setAllowContentAccess(false);

            // Require user gesture to start media playback
            settings.setMediaPlaybackRequiresUserGesture(true);

            settings.setSafeBrowsingEnabled(true);

            // Block third-party cookies
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false);
            }

            // Apply privacy WebViewClient with tracker blocking
            privacyWebViewClient = new PrivacyWebViewClient(getBridge());
            webView.setWebViewClient(privacyWebViewClient);
        }
    }

    public void configureProxy(int port) {
        if (privacyWebViewClient != null) {
            privacyWebViewClient.setProxyPort(port);
        }

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.w("MainActivity", "ProxyController not supported on this WebView version");
            return;
        }

        ProxyConfig proxyConfig = new ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:" + port)
            .addBypassRule("localhost")
            .build();

        ProxyController.getInstance().setProxyOverride(
            proxyConfig,
            command -> command.run(),
            () -> {}
        );
    }

    @Override
    public void onDestroy() {
        // Clear proxy configuration when activity is destroyed
        clearProxy();
        super.onDestroy();
    }

    public void clearProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            return;
        }

        ProxyController.getInstance().clearProxyOverride(
            command -> command.run(),
            () -> {}
        );
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            WebView webView = getBridge().getWebView();
            if (webView != null) {
                webView.clearCache(false);
            }
        }
    }

    public void clearBrowsingDataNative() {
        android.webkit.WebView webView = getBridge().getWebView();
        webView.clearCache(true);
        webView.clearHistory();
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        android.webkit.WebStorage.getInstance().deleteAllData();
    }
}

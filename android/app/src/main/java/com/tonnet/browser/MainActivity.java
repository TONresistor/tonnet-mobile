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

        // Configure WebView for privacy
        WebView webView = getBridge().getWebView();
        if (webView != null) {
            WebSettings settings = webView.getSettings();

            // Allow mixed content (HTTP in HTTPS context) for .ton sites
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

            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true);
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

    public void clearProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            return;
        }

        ProxyController.getInstance().clearProxyOverride(
            command -> command.run(),
            () -> {}
        );
    }
}

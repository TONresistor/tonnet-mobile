package com.tonnet.browser;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;

import com.getcapacitor.BridgeActivity;
import com.tonnet.browser.plugins.TonProxyPlugin;

public class MainActivity extends BridgeActivity {

    // Privacy-friendly User-Agent (generic Chrome on Android)
    private static final String CUSTOM_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register plugins
        registerPlugin(TonProxyPlugin.class);

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

            // Apply privacy WebViewClient with tracker blocking
            webView.setWebViewClient(new PrivacyWebViewClient(getBridge()));
        }
    }

    public void configureProxy(int port) {
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
        ProxyController.getInstance().clearProxyOverride(
            command -> command.run(),
            () -> {}
        );
    }
}

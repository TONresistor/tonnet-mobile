package com.tonnet.browser;

import android.content.Context;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Privacy-focused WebViewClient that blocks known trackers.
 * Extends BridgeWebViewClient to maintain Capacitor functionality.
 */
public class PrivacyWebViewClient extends BridgeWebViewClient {

    private static final String TAG = "PrivacyWebViewClient";
    private final Set<String> blockedDomains = new HashSet<>();
    private final Context context;
    private int blockedCount = 0;

    public PrivacyWebViewClient(Bridge bridge) {
        super(bridge);
        this.context = bridge.getContext();
        loadBlocklist();
    }

    /**
     * Load the blocklist from assets/blocklist.txt
     */
    private void loadBlocklist() {
        try {
            InputStream is = context.getAssets().open("blocklist.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip comments and empty lines
                if (!line.isEmpty() && !line.startsWith("#")) {
                    blockedDomains.add(line.toLowerCase());
                }
            }
            reader.close();
            Log.i(TAG, "Loaded " + blockedDomains.size() + " blocked domains");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load blocklist: " + e.getMessage());
        }
    }

    /**
     * Intercept requests and block known trackers
     */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String host = request.getUrl().getHost();
        String url = request.getUrl().toString();

        // Handle .ton/.adnl requests through our proxy manually
        if (host != null && (host.endsWith(".ton") || host.endsWith(".adnl") || host.endsWith(".t.me"))) {
            Log.i(TAG, "[TON] " + request.getMethod() + " " + url);
            return proxyTonRequest(request);
        }

        if (host != null && shouldBlock(host.toLowerCase())) {
            blockedCount++;
            Log.d(TAG, "Blocked [" + blockedCount + "]: " + host);
            // Return empty response to block the request
            return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                new ByteArrayInputStream("".getBytes())
            );
        }

        return super.shouldInterceptRequest(view, request);
    }

    /**
     * Check if a domain should be blocked
     */
    private boolean shouldBlock(String host) {
        // Direct match
        if (blockedDomains.contains(host)) {
            return true;
        }

        // Check if it's a subdomain of a blocked domain
        for (String blocked : blockedDomains) {
            if (host.endsWith("." + blocked)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the number of blocked requests
     */
    public int getBlockedCount() {
        return blockedCount;
    }

    /**
     * Reload the blocklist (can be called if user updates settings)
     */
    public void reloadBlocklist() {
        blockedDomains.clear();
        loadBlocklist();
    }

    /**
     * Proxy .ton requests through our local proxy server
     */
    private WebResourceResponse proxyTonRequest(WebResourceRequest request) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(request.getUrl().toString());

            // Use our local proxy
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 8080));
            connection = (HttpURLConnection) url.openConnection(proxy);

            // Set request method
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);

            // Copy headers from original request
            Map<String, String> headers = request.getRequestHeaders();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            // Execute request
            connection.connect();

            int responseCode = connection.getResponseCode();
            String contentType = connection.getContentType();
            String encoding = connection.getContentEncoding();

            // Get response stream
            InputStream inputStream;
            if (responseCode >= 400) {
                inputStream = connection.getErrorStream();
                if (inputStream == null) {
                    inputStream = new ByteArrayInputStream(("Error: " + responseCode).getBytes());
                }
            } else {
                inputStream = connection.getInputStream();
            }

            // Parse content type and charset
            String mimeType = "text/html";
            String charset = "UTF-8";
            if (contentType != null) {
                String[] parts = contentType.split(";");
                mimeType = parts[0].trim();
                for (String part : parts) {
                    if (part.trim().toLowerCase().startsWith("charset=")) {
                        charset = part.trim().substring(8);
                    }
                }
            }

            Log.i(TAG, "[TON] Response: " + responseCode + " " + mimeType + " " + request.getUrl());

            // Build response with headers
            WebResourceResponse response = new WebResourceResponse(mimeType, charset, inputStream);

            // Copy response headers
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            if (responseHeaders != null) {
                java.util.HashMap<String, String> flatHeaders = new java.util.HashMap<>();
                for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        flatHeaders.put(entry.getKey(), entry.getValue().get(0));
                    }
                }
                response.setResponseHeaders(flatHeaders);
            }

            response.setStatusCodeAndReasonPhrase(responseCode, connection.getResponseMessage());
            return response;

        } catch (Exception e) {
            Log.e(TAG, "[TON] Proxy error: " + e.getMessage() + " for " + request.getUrl());
            // Return error page
            String errorHtml = "<html><body><h1>Connection Error</h1><p>" + e.getMessage() + "</p></body></html>";
            return new WebResourceResponse(
                "text/html",
                "UTF-8",
                new ByteArrayInputStream(errorHtml.getBytes())
            );
        }
    }
}

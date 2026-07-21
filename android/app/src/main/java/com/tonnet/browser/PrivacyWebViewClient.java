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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Privacy-focused WebViewClient that blocks known trackers. Extends BridgeWebViewClient to maintain
 * Capacitor functionality.
 */
public class PrivacyWebViewClient extends BridgeWebViewClient {

  private static final String TAG = "PrivacyWebViewClient";
  private volatile Set<String> blockedDomains = new HashSet<>();
  private final Context context;
  private final AtomicInteger blockedCount = new AtomicInteger(0);
  private volatile int proxyPort = ProxyPort.DEFAULT;

  public PrivacyWebViewClient(Bridge bridge) {
    super(bridge);
    this.context = bridge.getContext();
    // Load blocklist asynchronously to avoid ANR
    new Thread(this::loadBlocklist).start();
  }

  /** Load the blocklist from assets/blocklist.txt */
  private void loadBlocklist() {
    try (InputStream input = context.getAssets().open("blocklist.txt");
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      Set<String> newDomains = new HashSet<>();
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (!line.isEmpty() && !line.startsWith("#")) {
          newDomains.add(line.toLowerCase(Locale.ROOT));
        }
      }
      blockedDomains = newDomains; // atomic reference swap
      Log.i(TAG, "Loaded " + newDomains.size() + " blocked domains");
    } catch (IOException e) {
      Log.e(TAG, "Failed to load blocklist: " + e.getMessage());
    }
  }

  /** Intercept requests and block known trackers */
  @Override
  public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
    String host = request.getUrl().getHost();
    String normalizedHost = host == null ? null : host.toLowerCase(Locale.ROOT);
    // Handle .ton/.adnl requests through our proxy manually
    if (normalizedHost != null
        && (normalizedHost.endsWith(".ton") || normalizedHost.endsWith(".adnl"))
        && ProxyRequestPolicy.supportsManualInterception(request.getMethod())) {
      Log.i(TAG, "[TON] Intercepting " + request.getMethod() + " request");
      return proxyTonRequest(request);
    }

    if (normalizedHost != null && shouldBlock(normalizedHost)) {
      blockedCount.incrementAndGet();
      Log.d(TAG, "Blocked [" + blockedCount.get() + "]: " + host);
      // Return empty response to block the request
      return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    return super.shouldInterceptRequest(view, request);
  }

  /** Check if a domain should be blocked */
  private boolean shouldBlock(String host) {
    if (blockedDomains.contains(host)) return true;
    int dot = host.indexOf('.');
    while (dot != -1) {
      host = host.substring(dot + 1);
      if (blockedDomains.contains(host)) return true;
      dot = host.indexOf('.');
    }
    return false;
  }

  /** Set the proxy port used for .ton requests */
  public void setProxyPort(int port) {
    this.proxyPort = ProxyPort.normalize(port);
  }

  /** Proxy .ton requests through our local proxy server */
  private WebResourceResponse proxyTonRequest(WebResourceRequest request) {
    HttpURLConnection connection = null;
    InputStream responseStream = null;
    long t0 = System.currentTimeMillis();
    try {
      URL url = new URL(request.getUrl().toString());

      // Use our local proxy
      Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", this.proxyPort));
      connection = (HttpURLConnection) url.openConnection(proxy);

      // Set request method
      connection.setRequestMethod(request.getMethod());
      connection.setConnectTimeout(30000);
      connection.setReadTimeout(60000);
      connection.setInstanceFollowRedirects(true);
      connection.setUseCaches(false);

      // Copy headers from original request, stripping privacy-sensitive ones
      Map<String, String> headers = request.getRequestHeaders();
      Set<String> stripHeaders =
          new HashSet<>(
              Arrays.asList(
                  "referer",
                  "origin",
                  "if-match",
                  "if-modified-since",
                  "if-none-match",
                  "if-range",
                  "if-unmodified-since"));
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        String headerName = entry.getKey();
        if (headerName != null && !stripHeaders.contains(headerName.toLowerCase(Locale.ROOT))) {
          connection.setRequestProperty(headerName, entry.getValue());
        }
      }

      // Inject privacy headers
      connection.setRequestProperty("DNT", "1");
      connection.setRequestProperty("Sec-GPC", "1");

      // Execute request
      long t1 = System.currentTimeMillis();
      connection.connect();
      long t2 = System.currentTimeMillis();

      int responseCode = connection.getResponseCode();
      long t3 = System.currentTimeMillis();
      String contentType = connection.getContentType();
      int contentLength = connection.getContentLength();

      // Get response stream — pass directly to WebView for streaming.
      InputStream inputStream;
      if (responseCode >= 400) {
        inputStream = connection.getErrorStream();
        if (inputStream == null) {
          inputStream =
              new ByteArrayInputStream(("Error: " + responseCode).getBytes(StandardCharsets.UTF_8));
        }
      } else {
        inputStream = connection.getInputStream();
      }
      HttpURLConnection responseConnection = connection;
      responseStream = new CloseActionInputStream(inputStream, responseConnection::disconnect);

      // Parse content type and charset
      String mimeType = "text/html";
      String charset = "UTF-8";
      if (contentType != null) {
        String[] parts = contentType.split(";");
        mimeType = parts[0].trim();
        for (String part : parts) {
          if (part.trim().toLowerCase(Locale.ROOT).startsWith("charset=")) {
            charset = part.trim().substring(8);
          }
        }
      }

      long t4 = System.currentTimeMillis();
      Log.i(
          TAG,
          "[TON] "
              + responseCode
              + " "
              + mimeType
              + " len="
              + contentLength
              + " connect="
              + (t2 - t1)
              + "ms firstByte="
              + (t3 - t1)
              + "ms total="
              + (t4 - t0)
              + "ms "
              + "request");

      // Build response with streaming body
      WebResourceResponse response = new WebResourceResponse(mimeType, charset, responseStream);

      // Copy response headers, stripping framing restrictions
      // since we ARE the browser (not a third-party iframe embedder)
      Map<String, List<String>> responseHeaders = connection.getHeaderFields();
      if (responseHeaders != null) {
        java.util.HashMap<String, String> flatHeaders = new java.util.HashMap<>();
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
          if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
            continue;
          }
          String key = entry.getKey().toLowerCase(Locale.ROOT);
          // Strip framing/security headers that conflict with our iframe architecture.
          // We ARE the browser — .ton sites are loaded in an iframe inside Capacitor
          // (https://localhost), so X-Frame-Options and CSP must be stripped to avoid
          // frame-ancestors blocks and mixed-content CSP violations on sub-resources.
          if (key.equals("x-frame-options") || key.equals("content-security-policy")) {
            continue;
          }
          flatHeaders.put(entry.getKey(), entry.getValue().get(0));
        }
        response.setResponseHeaders(flatHeaders);
      }

      int webViewStatusCode = WebResponseStatus.normalizeCode(responseCode);
      String webViewReason =
          WebResponseStatus.normalizeReason(webViewStatusCode, connection.getResponseMessage());
      response.setStatusCodeAndReasonPhrase(webViewStatusCode, webViewReason);
      return response;

    } catch (Exception e) {
      closeQuietly(responseStream);
      if (connection != null) {
        connection.disconnect();
      }
      Log.e(TAG, "[TON] Proxy request failed: " + e.getClass().getSimpleName());
      // Return error page
      String safeMessage = escapeHtml(e.getMessage());
      String errorHtml =
          "<html><body><h1>Connection Error</h1><p>" + safeMessage + "</p></body></html>";
      return new WebResourceResponse(
          "text/html",
          "UTF-8",
          new ByteArrayInputStream(errorHtml.getBytes(StandardCharsets.UTF_8)));
    }
  }

  private static void closeQuietly(InputStream input) {
    if (input == null) {
      return;
    }
    try {
      input.close();
    } catch (IOException ignored) {
      // The connection is disconnected by CloseActionInputStream even if body close fails.
    }
  }

  /** Escape HTML special characters to prevent XSS */
  private static String escapeHtml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}

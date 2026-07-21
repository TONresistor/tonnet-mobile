package com.tonnet.browser;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import com.getcapacitor.BridgeActivity;
import com.tonnet.browser.plugins.TonProxyPlugin;

public class MainActivity extends BridgeActivity {

  public interface BrowserOperationCallback {
    void onSuccess();

    void onFailure(String code, String message, Throwable cause);
  }

  // Privacy-friendly User-Agent (generic Chrome on Android)
  private static final String CUSTOM_USER_AGENT =
      "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
  private static final long BROWSER_CALLBACK_TIMEOUT_MS = 5_000L;

  private final Handler callbackHandler = new Handler(Looper.getMainLooper());
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

      settings.setSafeBrowsingEnabled(false);

      // Block third-party cookies
      try {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
      } catch (RuntimeException error) {
        Log.e("MainActivity", "Unable to apply the initial cookie policy", error);
      }

      if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false);
      }

      // Apply privacy WebViewClient with tracker blocking
      privacyWebViewClient = new PrivacyWebViewClient(getBridge());
      webView.setWebViewClient(privacyWebViewClient);
    }
  }

  @SuppressLint("RequiresFeature") // Guarded by the explicit PROXY_OVERRIDE feature check below.
  public void configureProxy(int port, BrowserOperationCallback callback) {
    TimedCompletion<Integer> completion =
        timedCompletion(
            new OnceCompletion.Callback<Integer>() {
              @Override
              public void onSuccess(Integer configuredPort) {
                if (privacyWebViewClient != null) {
                  privacyWebViewClient.setProxyPort(configuredPort);
                }
                notifySuccess(callback);
              }

              @Override
              public void onFailure(String code, String message, Throwable cause) {
                notifyFailure(callback, code, message, cause);
              }
            },
            "WEBVIEW_PROXY_TIMEOUT",
            "Timed out while configuring the WebView proxy");
    if (completion.isCompleted()) {
      return;
    }

    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
      completion.reject(
          "WEBVIEW_PROXY_UNSUPPORTED",
          "Proxy override is not supported by this Android WebView",
          null);
      return;
    }

    ProxyConfig proxyConfig =
        new ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:" + port)
            .addBypassRule("localhost")
            .build();

    try {
      ProxyController.getInstance()
          .setProxyOverride(
              proxyConfig,
              command -> command.run(),
              () -> {
                if (!completion.resolve(port)) {
                  Log.w("MainActivity", "Ignoring a late WebView proxy configuration callback");
                }
              });
    } catch (RuntimeException | LinkageError error) {
      completion.reject("WEBVIEW_PROXY_FAILED", "Failed to configure the WebView proxy", error);
    }
  }

  @Override
  public void onDestroy() {
    // Stop native Go proxy to prevent zombie processes
    TonProxyPlugin.stopNativeProxy();
    // Clear proxy configuration when activity is destroyed
    clearProxy(
        new BrowserOperationCallback() {
          @Override
          public void onSuccess() {
            Log.i("MainActivity", "WebView proxy cleared during Activity destruction");
          }

          @Override
          public void onFailure(String code, String message, Throwable cause) {
            Log.w("MainActivity", message, cause);
          }
        });
    super.onDestroy();
  }

  @SuppressLint("RequiresFeature") // Guarded by the explicit PROXY_OVERRIDE feature check below.
  public void clearProxy(BrowserOperationCallback callback) {
    TimedCompletion<Void> completion =
        timedCompletion(
            operationCompletion(callback),
            "WEBVIEW_PROXY_TIMEOUT",
            "Timed out while clearing the WebView proxy");
    if (completion.isCompleted()) {
      return;
    }

    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
      completion.reject(
          "WEBVIEW_PROXY_UNSUPPORTED",
          "Proxy override is not supported by this Android WebView",
          null);
      return;
    }

    try {
      ProxyController.getInstance()
          .clearProxyOverride(
              command -> command.run(),
              () -> {
                if (!completion.resolve(null)) {
                  Log.w("MainActivity", "Ignoring a late WebView proxy clear callback");
                }
              });
    } catch (RuntimeException | LinkageError error) {
      completion.reject("WEBVIEW_PROXY_FAILED", "Failed to clear the WebView proxy", error);
    }
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

  /**
   * Clears only browser-owned data selected by the caller. Web Storage deliberately remains managed
   * by TypeScript so native cleanup cannot remove application preferences.
   */
  public void clearBrowsingData(
      boolean cache, boolean cookies, boolean history, BrowserOperationCallback callback) {
    TimedCompletion<Void> completion =
        timedCompletion(
            operationCompletion(callback),
            "COOKIE_CLEAR_TIMEOUT",
            "Timed out while clearing browser cookies");
    if (completion.isCompleted()) {
      return;
    }

    try {
      WebView webView = getBridge().getWebView();
      if (webView == null) {
        completion.resolve(null);
        return;
      }

      if (cache) {
        webView.clearCache(true);
      }
      if (history) {
        webView.clearHistory();
      }
      if (cookies) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(
            removed -> {
              try {
                cookieManager.flush();
                completion.resolve(null);
              } catch (RuntimeException error) {
                completion.reject(
                    "BROWSING_DATA_CLEAR_FAILED", "Failed to flush browser cookies", error);
              }
            });
        return;
      }

      completion.resolve(null);
    } catch (RuntimeException | LinkageError error) {
      completion.reject("BROWSING_DATA_CLEAR_FAILED", "Failed to clear browser data", error);
    }
  }

  private OnceCompletion.Callback<Void> operationCompletion(BrowserOperationCallback callback) {
    return new OnceCompletion.Callback<Void>() {
      @Override
      public void onSuccess(Void ignored) {
        notifySuccess(callback);
      }

      @Override
      public void onFailure(String code, String message, Throwable cause) {
        notifyFailure(callback, code, message, cause);
      }
    };
  }

  private <T> TimedCompletion<T> timedCompletion(
      OnceCompletion.Callback<T> callback, String timeoutCode, String timeoutMessage) {
    return new TimedCompletion<>(
        callback,
        (task, delayMillis) -> {
          if (!callbackHandler.postDelayed(task, delayMillis)) {
            throw new IllegalStateException("Handler rejected the timeout task");
          }
          return () -> callbackHandler.removeCallbacks(task);
        },
        BROWSER_CALLBACK_TIMEOUT_MS,
        timeoutCode,
        timeoutMessage);
  }

  private static void notifySuccess(BrowserOperationCallback callback) {
    try {
      callback.onSuccess();
    } catch (RuntimeException | LinkageError error) {
      Log.e("MainActivity", "Browser operation success callback failed", error);
    }
  }

  private static void notifyFailure(
      BrowserOperationCallback callback, String code, String message, Throwable cause) {
    try {
      callback.onFailure(code, message, cause);
    } catch (RuntimeException | LinkageError error) {
      Log.e("MainActivity", "Browser operation failure callback failed", error);
    }
  }
}

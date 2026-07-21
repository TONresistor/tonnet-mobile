package com.tonnet.browser.plugins;

import android.app.Activity;
import android.util.Log;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.tonnet.browser.MainActivity;
import com.tonnet.browser.NetworkConnection;
import com.tonnet.browser.OnceCompletion;
import com.tonnet.browser.ProxyPort;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * TonProxyPlugin - Capacitor plugin for TON Proxy
 *
 * <p>Manages the TON network proxy for browsing .ton domains. Generates config.json for
 * tonutils-proxy, delegates to StartProxyInDir. Tunnel relays are discovered via DHT overlay (no
 * static seed file).
 */
@CapacitorPlugin(name = "TonProxy")
public class TonProxyPlugin extends Plugin {

  private static final String TAG = "TonProxyPlugin";
  private static final int MAX_LOG_LINES = 200;
  private static final OperationGeneration OPERATIONS = new OperationGeneration();
  private static final Object NATIVE_STOP_LOCK = new Object();

  private enum LifecycleState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING
  }

  // The native Go proxy is process-wide, so its lifecycle and metadata must be process-wide too.
  private static LifecycleState lifecycleState = LifecycleState.STOPPED;
  private static int currentPort = ProxyPort.DEFAULT;
  private static boolean isAnonymous = false;
  private static final boolean LIBRARIES_LOADED;

  // Java log buffer
  private static final java.util.LinkedList<String> logBuffer = new java.util.LinkedList<>();

  private static void log(String message) {
    Log.i(TAG, message);
    addToBuffer("[Java] " + message);
  }

  private static void logError(String message) {
    Log.e(TAG, message);
    addToBuffer("[Java][ERROR] " + message);
  }

  private static synchronized void addToBuffer(String line) {
    String timestamp =
        new java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new java.util.Date());
    logBuffer.add(timestamp + " " + line);
    while (logBuffer.size() > MAX_LOG_LINES) {
      logBuffer.removeFirst();
    }
  }

  private static synchronized String getLogBuffer() {
    StringBuilder sb = new StringBuilder();
    for (String line : logBuffer) {
      sb.append(line).append("\n");
    }
    return sb.toString();
  }

  private static OnceCompletion<JSObject> completionFor(PluginCall call) {
    return new OnceCompletion<>(
        new OnceCompletion.Callback<JSObject>() {
          @Override
          public void onSuccess(JSObject value) {
            try {
              if (value == null) {
                call.resolve();
              } else {
                call.resolve(value);
              }
            } catch (RuntimeException | LinkageError error) {
              logError("Failed to deliver plugin success: " + error.getMessage());
            }
          }

          @Override
          public void onFailure(String code, String message, Throwable cause) {
            try {
              if (cause instanceof Exception) {
                call.reject(message, code, (Exception) cause);
              } else {
                call.reject(message, code);
              }
            } catch (RuntimeException | LinkageError error) {
              logError("Failed to deliver plugin failure: " + error.getMessage());
            }
          }
        });
  }

  // Load the native libraries
  static {
    boolean loaded = true;
    try {
      System.loadLibrary("tonutils-proxy");
      addToBuffer("[Java] Loaded tonutils-proxy native library");
    } catch (LinkageError | SecurityException e) {
      loaded = false;
      addToBuffer("[Java][ERROR] Failed to load tonutils-proxy: " + e.getMessage());
    }

    try {
      System.loadLibrary("tonproxy-jni");
      addToBuffer("[Java] Loaded tonproxy-jni bridge library");
    } catch (LinkageError | SecurityException e) {
      loaded = false;
      addToBuffer("[Java][ERROR] Failed to load tonproxy-jni: " + e.getMessage());
    }
    LIBRARIES_LOADED = loaded;
  }

  // Native method declarations
  private static native long ReserveProxyStart();

  private static native String StartProxyInDir(short port, String dirPath, long token);

  private static native String StopProxy();

  private static native boolean IsProxyActive();

  private static String invokeNativeStop() {
    synchronized (NATIVE_STOP_LOCK) {
      String result = StopProxy();
      if (!"OK".equals(result)) {
        throw new IllegalStateException("Native stop failed: " + result);
      }
      return result;
    }
  }

  /**
   * Stop the native proxy from outside the plugin (e.g. Activity.onDestroy). Prevents zombie Go
   * processes when the app is killed.
   */
  public static void stopNativeProxy() {
    long stopOperation;
    synchronized (OPERATIONS) {
      stopOperation = OPERATIONS.invalidate();
      if (!LIBRARIES_LOADED) {
        lifecycleState = LifecycleState.STOPPED;
        isAnonymous = false;
        return;
      }
      lifecycleState = LifecycleState.STOPPING;
    }

    new Thread(
            () -> {
              try {
                String result = invokeNativeStop();
                completeStop(stopOperation);
                addToBuffer("[Java] Proxy stopped via stopNativeProxy(): " + result);
              } catch (Exception | LinkageError error) {
                synchronized (OPERATIONS) {
                  if (OPERATIONS.isLatest(stopOperation)) {
                    // Keep stop retryable when the native state cannot be confirmed.
                    lifecycleState = LifecycleState.RUNNING;
                  }
                }
                addToBuffer("[Java][ERROR] stopNativeProxy failed: " + error.getMessage());
              }
            },
            "ton-proxy-destroy-stop")
        .start();
  }

  /** Generate a JSON array of 32 random unsigned bytes (0-255). */
  private static JSONArray generateKeyArray(SecureRandom rng) throws Exception {
    byte[] bytes = new byte[32];
    rng.nextBytes(bytes);
    return bytesToIntArray(bytes);
  }

  /** Convert a byte array to a JSON array of unsigned integers (0-255). */
  private static JSONArray bytesToIntArray(byte[] bytes) throws Exception {
    JSONArray arr = new JSONArray();
    for (byte b : bytes) {
      arr.put(b & 0xFF);
    }
    return arr;
  }

  /**
   * Write or patch config.json in proxyDir. If the file already exists, only TunnelSectionsNum and
   * NodesPoolConfigPath are patched. Otherwise a fresh config with new random keys is generated.
   */
  private void writeProxyConfig(File proxyDir, boolean tunnelEnabled) throws Exception {
    File configFile = new File(proxyDir, "config.json");
    // tonutils-proxy v1.9+ discovers tunnel relays via DHT overlay
    String nodesPoolPath = "";
    int tunnelSections = tunnelEnabled ? 2 : 0;

    if (configFile.exists()) {
      // Patch existing config — preserve keys
      StringBuilder sb = new StringBuilder();
      try (FileReader reader = new FileReader(configFile)) {
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
          sb.append(buf, 0, n);
        }
      }
      JSONObject config = new JSONObject(sb.toString());
      JSONObject tunnel = config.getJSONObject("TunnelConfig");
      tunnel.put("TunnelSectionsNum", tunnelSections);
      tunnel.put("NodesPoolConfigPath", nodesPoolPath);
      try (FileWriter writer = new FileWriter(configFile)) {
        writer.write(config.toString(2));
      }
      log("Patched existing config.json (tunnel=" + tunnelEnabled + ")");
      return;
    }

    // Generate fresh config
    SecureRandom rng = new SecureRandom();

    JSONObject channelsConfig = new JSONObject();
    JSONObject supportedCoins = new JSONObject();
    JSONObject tonCoin = new JSONObject();
    tonCoin.put("Enabled", true);
    supportedCoins.put("Ton", tonCoin);
    supportedCoins.put("Jettons", new JSONObject());
    supportedCoins.put("ExtraCurrencies", new JSONObject());
    channelsConfig.put("SupportedCoins", supportedCoins);
    channelsConfig.put("BufferTimeToCommit", 10800);
    channelsConfig.put("QuarantineDurationSec", 21600);
    channelsConfig.put("ConditionalCloseDurationSec", 10800);
    channelsConfig.put("MinSafeVirtualChannelTimeoutSec", 300);

    JSONObject payments = new JSONObject();
    payments.put("ADNLServerKey", generateKeyArray(rng));
    payments.put("PaymentsNodeKey", generateKeyArray(rng));
    payments.put("WalletPrivateKey", generateKeyArray(rng));
    payments.put("DBPath", "./payments-db/");
    payments.put("SecureProofPolicy", false);
    payments.put("ChannelsConfig", channelsConfig);

    JSONObject tunnelConfig = new JSONObject();
    tunnelConfig.put("TunnelServerKey", generateKeyArray(rng));
    tunnelConfig.put("TunnelThreads", Runtime.getRuntime().availableProcessors());
    tunnelConfig.put("TunnelSectionsNum", tunnelSections);
    tunnelConfig.put("NodesPoolConfigPath", nodesPoolPath);
    tunnelConfig.put("PaymentsEnabled", false);
    tunnelConfig.put("Payments", payments);

    JSONObject config = new JSONObject();
    config.put("Version", 1);
    config.put("ADNLKey", generateKeyArray(rng));
    config.put("CustomTunnelNetworkConfigPath", "");
    config.put("TunnelConfig", tunnelConfig);

    try (FileWriter writer = new FileWriter(configFile)) {
      writer.write(config.toString(2));
    }
    log("Generated new config.json (tunnel=" + tunnelEnabled + ")");
  }

  /**
   * Start the TON proxy server
   *
   * @param call PluginCall with options: - port (int, optional): Port to run proxy on (default
   *     {@link ProxyPort#DEFAULT}) - anonymous (boolean, optional): Use anonymous/tunnel routing
   *     (default false)
   */
  @PluginMethod
  public void start(PluginCall call) {
    OnceCompletion<JSObject> completion = completionFor(call);
    int requestedPort = call.getInt("port", ProxyPort.DEFAULT);
    int port = ProxyPort.normalize(requestedPort);
    boolean anonymous = call.getBoolean("anonymous", false);

    if (port != requestedPort) {
      log("Invalid proxy port " + requestedPort + "; using " + ProxyPort.DEFAULT);
    }

    log("Starting proxy on port " + port + ", anonymous: " + anonymous);

    if (!LIBRARIES_LOADED) {
      String errorMsg =
          "Proxy library not available. Please ensure libtonutils-proxy.so is installed.";
      logError(errorMsg);
      completion.reject("LIBRARY_NOT_LOADED", errorMsg, null);
      return;
    }

    try {
      if (!NetworkConnection.hasValidatedInternet(getContext())) {
        completion.reject("OFFLINE", "No validated Internet connection is available", null);
        return;
      }
    } catch (RuntimeException error) {
      logError("Failed to read network status: " + error.getMessage());
      completion.reject("NETWORK_STATUS_FAILED", "Unable to verify the Internet connection", error);
      return;
    }

    long operation;
    long nativeStartToken;
    synchronized (OPERATIONS) {
      if (lifecycleState == LifecycleState.RUNNING) {
        try {
          if (!IsProxyActive()) {
            OPERATIONS.invalidate();
            lifecycleState = LifecycleState.STOPPED;
            isAnonymous = false;
          }
        } catch (Exception | LinkageError error) {
          completion.reject("NATIVE_STATUS_FAILED", "Failed to verify the native proxy", error);
          return;
        }
      }

      if (lifecycleState == LifecycleState.RUNNING) {
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("port", currentPort);
        result.put("anonymous", isAnonymous);
        result.put("message", "Proxy already running");
        completion.resolve(result);
        return;
      }
      if (lifecycleState != LifecycleState.STOPPED) {
        completion.reject("PROXY_BUSY", "Proxy lifecycle transition already in progress", null);
        return;
      }

      try {
        nativeStartToken = ReserveProxyStart();
      } catch (Exception | LinkageError error) {
        logError("Failed to reserve native proxy start: " + error.getMessage());
        completion.reject("NATIVE_START_FAILED", "Failed to reserve native proxy start", error);
        return;
      }

      lifecycleState = LifecycleState.STARTING;
      currentPort = port;
      isAnonymous = anonymous;
      operation = OPERATIONS.beginStart();
    }

    final int finalPort = port;
    final boolean finalAnonymous = anonymous;

    new Thread(
            () -> {
              boolean nativeCallAttempted = false;
              try {
                if (!isCurrentStart(operation)) {
                  rejectCancelledStart(completion);
                  return;
                }

                // Prepare proxy directory and config files
                File proxyDir = new File(getContext().getFilesDir(), "proxy");
                if (!proxyDir.isDirectory() && !proxyDir.mkdirs()) {
                  throw new IllegalStateException("Unable to create proxy directory");
                }

                writeProxyConfig(proxyDir, finalAnonymous);

                if (!isCurrentStart(operation)) {
                  rejectCancelledStart(completion);
                  return;
                }

                log(
                    "Calling native StartProxyInDir("
                        + finalPort
                        + ", "
                        + proxyDir.getAbsolutePath()
                        + ")");
                nativeCallAttempted = true;
                String result =
                    StartProxyInDir(
                        (short) finalPort, proxyDir.getAbsolutePath(), nativeStartToken);
                log("Native StartProxyInDir result: " + result);

                if (!isCurrentStart(operation)) {
                  rejectObsoleteNativeStart(
                      completion, operation, mayRepresentRunningProxy(result));
                  return;
                }

                if (!"OK".equals(result)) {
                  handleCurrentStartFailure(operation, result);
                  completion.reject(
                      "NATIVE_START_FAILED", "Failed to start proxy: " + result, null);
                  return;
                }

                Activity activity = getActivity();
                if (activity == null || activity.isFinishing()) {
                  logError("Activity destroyed before proxy configuration");
                  stopStartIfOwnedAsync(operation);
                  completion.reject("ACTIVITY_UNAVAILABLE", "Activity is unavailable", null);
                  return;
                }
                activity.runOnUiThread(
                    () -> finishStartOnUiThread(completion, operation, finalPort, finalAnonymous));
              } catch (Exception | LinkageError e) {
                logError("Error starting proxy: " + e.getMessage());
                if (nativeCallAttempted) {
                  stopStartIfOwnedAsync(operation);
                } else {
                  markStartFailed(operation);
                }
                completion.reject(
                    "NATIVE_START_FAILED", "Failed to start proxy: " + e.getMessage(), e);
              }
            },
            "ton-proxy-start")
        .start();
  }

  private static boolean isCurrentStart(long operation) {
    synchronized (OPERATIONS) {
      return lifecycleState == LifecycleState.STARTING
          && OPERATIONS.classify(operation) == OperationGeneration.Disposition.CURRENT;
    }
  }

  private static boolean mayRepresentRunningProxy(String result) {
    return "OK".equals(result)
        || "ALREADY_STARTED".equals(result)
        || "ALREADY_RUNNING".equals(result);
  }

  private void finishStartOnUiThread(
      OnceCompletion<JSObject> completion, long operation, int port, boolean anonymous) {
    if (!isCurrentStart(operation)) {
      stopStartIfOwnedAsync(operation);
      rejectCancelledStart(completion);
      return;
    }

    Activity activity = getActivity();
    if (!(activity instanceof MainActivity) || activity.isFinishing()) {
      logError("Activity is unavailable for proxy configuration");
      stopStartIfOwnedAsync(operation);
      completion.reject("ACTIVITY_UNAVAILABLE", "Activity is unavailable", null);
      return;
    }

    MainActivity mainActivity = (MainActivity) activity;
    try {
      mainActivity.configureProxy(
          port,
          new MainActivity.BrowserOperationCallback() {
            @Override
            public void onSuccess() {
              finishConfiguredStart(completion, operation, port, anonymous);
            }

            @Override
            public void onFailure(String code, String message, Throwable cause) {
              logError(message);
              stopStartIfOwnedAsync(operation);
              completion.reject(code, message, cause);
            }
          });
    } catch (RuntimeException | LinkageError error) {
      logError("Failed to configure WebView proxy: " + error.getMessage());
      stopStartIfOwnedAsync(operation);
      completion.reject("WEBVIEW_PROXY_FAILED", "Failed to configure the WebView proxy", error);
    }
  }

  private void finishConfiguredStart(
      OnceCompletion<JSObject> completion, long operation, int port, boolean anonymous) {
    boolean configured;
    synchronized (OPERATIONS) {
      configured =
          lifecycleState == LifecycleState.STARTING
              && OPERATIONS.classify(operation) == OperationGeneration.Disposition.CURRENT;
      if (configured) {
        currentPort = port;
        isAnonymous = anonymous;
        lifecycleState = LifecycleState.RUNNING;
      }
    }

    if (!configured) {
      stopStartIfOwnedAsync(operation);
      rejectCancelledStart(completion);
      return;
    }

    log("WebView proxy configured on port " + port);
    JSObject response = new JSObject();
    response.put("success", true);
    response.put("port", port);
    response.put("anonymous", anonymous);
    completion.resolve(response);
  }

  private void rejectCancelledStart(OnceCompletion<JSObject> completion) {
    log("Ignoring obsolete proxy start result");
    completion.reject("START_CANCELLED", "Proxy start was cancelled", null);
  }

  private void rejectObsoleteNativeStart(
      OnceCompletion<JSObject> completion, long operation, boolean nativeMayBeRunning) {
    if (nativeMayBeRunning) {
      stopStartIfOwnedAsync(operation);
    }
    rejectCancelledStart(completion);
  }

  /**
   * Stops a cancelled start only when no newer start owns the process-wide native proxy. The
   * generation check and native stop share the same lock, so a newer start cannot interleave.
   */
  private void stopStartIfOwned(long operation) {
    long cleanupOperation;
    synchronized (OPERATIONS) {
      OperationGeneration.Disposition disposition = OPERATIONS.classify(operation);
      if (lifecycleState != LifecycleState.STARTING
          || disposition != OperationGeneration.Disposition.CURRENT) {
        return;
      }
      cleanupOperation = OPERATIONS.invalidate();
      lifecycleState = LifecycleState.STOPPING;
    }

    try {
      String result = invokeNativeStop();
      log("Cancelled native start cleanup result: " + result);
      clearProxyAfterNativeStop(cleanupOperation, null);
    } catch (Exception | LinkageError error) {
      synchronized (OPERATIONS) {
        if (OPERATIONS.isLatest(cleanupOperation)) {
          lifecycleState = LifecycleState.RUNNING;
        }
      }
      logError("Failed to clean up cancelled native start: " + error.getMessage());
    }
  }

  private void stopStartIfOwnedAsync(long operation) {
    new Thread(() -> stopStartIfOwned(operation), "ton-proxy-start-cleanup").start();
  }

  private static void markStartFailed(long operation) {
    synchronized (OPERATIONS) {
      if (lifecycleState != LifecycleState.STARTING
          || OPERATIONS.classify(operation) != OperationGeneration.Disposition.CURRENT) {
        return;
      }
      OPERATIONS.invalidate();
      lifecycleState = LifecycleState.STOPPED;
      isAnonymous = false;
    }
  }

  private void handleCurrentStartFailure(long operation, String result) {
    logError("Proxy start failed: " + result);
    if (mayRepresentRunningProxy(result)) {
      stopStartIfOwnedAsync(operation);
    } else {
      markStartFailed(operation);
    }
  }

  /** Stop the TON proxy server */
  @PluginMethod
  public void stop(PluginCall call) {
    OnceCompletion<JSObject> completion = completionFor(call);
    long stopOperation;
    synchronized (OPERATIONS) {
      if (lifecycleState == LifecycleState.STOPPED) {
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "Proxy not running");
        completion.resolve(result);
        return;
      }
      if (lifecycleState == LifecycleState.STOPPING) {
        completion.reject("PROXY_BUSY", "Proxy stop already in progress", null);
        return;
      }

      stopOperation = OPERATIONS.invalidate();
      lifecycleState = LifecycleState.STOPPING;
    }

    log("Stopping proxy");

    new Thread(
            () -> {
              try {
                log("Calling native StopProxy()");
                String result = invokeNativeStop();
                log("Native StopProxy result: " + result);
                clearProxyAfterNativeStop(stopOperation, completion);
              } catch (Exception | LinkageError e) {
                logError("Error stopping proxy: " + e.getMessage());
                synchronized (OPERATIONS) {
                  if (OPERATIONS.isLatest(stopOperation)) {
                    // Native state is unknown; keep stop retryable and report it conservatively.
                    lifecycleState = LifecycleState.RUNNING;
                  }
                }
                completion.reject(
                    "NATIVE_STOP_FAILED", "Failed to stop proxy: " + e.getMessage(), e);
              }
            },
            "ton-proxy-stop")
        .start();
  }

  private void clearProxyAfterNativeStop(long stopOperation, OnceCompletion<JSObject> completion) {
    Activity activity = getActivity();
    if (!(activity instanceof MainActivity) || activity.isFinishing()) {
      completeStop(stopOperation);
      if (completion != null) {
        completion.reject(
            "ACTIVITY_UNAVAILABLE",
            "Native proxy stopped, but the Activity is unavailable for WebView cleanup",
            null);
      }
      return;
    }

    MainActivity mainActivity = (MainActivity) activity;
    try {
      activity.runOnUiThread(
          () -> {
            if (!isLatestOperation(stopOperation)) {
              log("Ignoring obsolete proxy clear before applying it");
              if (completion != null) {
                completion.reject("STOP_CANCELLED", "Proxy stop was superseded", null);
              }
              return;
            }

            try {
              mainActivity.clearProxy(
                  new MainActivity.BrowserOperationCallback() {
                    @Override
                    public void onSuccess() {
                      if (!isLatestOperation(stopOperation)) {
                        log("Ignoring obsolete proxy clear callback");
                        if (completion != null) {
                          completion.reject("STOP_CANCELLED", "Proxy stop was superseded", null);
                        }
                        return;
                      }

                      completeStop(stopOperation);
                      log("WebView proxy cleared");
                      if (completion != null) {
                        JSObject response = new JSObject();
                        response.put("success", true);
                        completion.resolve(response);
                      }
                    }

                    @Override
                    public void onFailure(String code, String message, Throwable cause) {
                      if (isLatestOperation(stopOperation)) {
                        completeStop(stopOperation);
                      }
                      logError(message);
                      if (completion != null) {
                        completion.reject(code, message, cause);
                      }
                    }
                  });
            } catch (RuntimeException | LinkageError error) {
              finishProxyClearFailure(stopOperation, completion, error);
            }
          });
    } catch (RuntimeException | LinkageError error) {
      finishProxyClearFailure(stopOperation, completion, error);
    }
  }

  private static void finishProxyClearFailure(
      long stopOperation, OnceCompletion<JSObject> completion, Throwable error) {
    if (isLatestOperation(stopOperation)) {
      completeStop(stopOperation);
    }
    logError("Failed to clear WebView proxy: " + error.getMessage());
    if (completion != null) {
      completion.reject("WEBVIEW_PROXY_FAILED", "Failed to clear the WebView proxy", error);
    }
  }

  private static boolean isLatestOperation(long operation) {
    synchronized (OPERATIONS) {
      return OPERATIONS.isLatest(operation);
    }
  }

  private static void completeStop(long operation) {
    synchronized (OPERATIONS) {
      if (!OPERATIONS.isLatest(operation)) {
        return;
      }
      lifecycleState = LifecycleState.STOPPED;
      isAnonymous = false;
    }
  }

  /** Get the current proxy status */
  @PluginMethod
  public void getStatus(PluginCall call) {
    JSObject result = new JSObject();
    synchronized (OPERATIONS) {
      if (lifecycleState == LifecycleState.RUNNING) {
        try {
          if (!IsProxyActive()) {
            OPERATIONS.invalidate();
            lifecycleState = LifecycleState.STOPPED;
            isAnonymous = false;
            log("Native proxy is no longer active");
          }
        } catch (Exception | LinkageError error) {
          logError("Failed to read native proxy status: " + error.getMessage());
          call.reject("Failed to read native proxy status", "NATIVE_STATUS_FAILED");
          return;
        }
      }

      result.put("running", lifecycleState == LifecycleState.RUNNING);
      result.put("port", currentPort);
      result.put("anonymous", isAnonymous);
    }
    call.resolve(result);
  }

  /** Get proxy logs from Java buffer */
  @PluginMethod
  public void getLogs(PluginCall call) {
    JSObject result = new JSObject();
    result.put("logs", getLogBuffer());
    call.resolve(result);
  }

  /** Set third-party cookie policy */
  @PluginMethod
  public void setThirdPartyCookies(PluginCall call) {
    OnceCompletion<JSObject> completion = completionFor(call);
    boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
    Activity activity = getActivity();
    if (!(activity instanceof BridgeActivity) || activity.isFinishing()) {
      completion.reject("ACTIVITY_UNAVAILABLE", "Activity is unavailable", null);
      return;
    }

    try {
      activity.runOnUiThread(
          () -> {
            try {
              android.webkit.WebView webView = ((BridgeActivity) activity).getBridge().getWebView();
              if (webView == null) {
                completion.reject("WEBVIEW_UNAVAILABLE", "WebView is unavailable", null);
                return;
              }
              android.webkit.CookieManager.getInstance()
                  .setAcceptThirdPartyCookies(webView, enabled);
              completion.resolve(null);
            } catch (RuntimeException | LinkageError error) {
              completion.reject(
                  "COOKIE_POLICY_FAILED", "Failed to update the cookie policy", error);
            }
          });
    } catch (RuntimeException | LinkageError error) {
      completion.reject("COOKIE_POLICY_FAILED", "Failed to update the cookie policy", error);
    }
  }

  /** Clear selected browser-owned data without deleting Web Storage or app preferences. */
  @PluginMethod
  public void clearBrowsingData(PluginCall call) {
    OnceCompletion<JSObject> completion = completionFor(call);
    boolean cache = Boolean.TRUE.equals(call.getBoolean("cache", false));
    boolean cookies = Boolean.TRUE.equals(call.getBoolean("cookies", false));
    boolean history = Boolean.TRUE.equals(call.getBoolean("history", false));
    Activity activity = getActivity();

    if (!(activity instanceof MainActivity) || activity.isFinishing()) {
      completion.reject("ACTIVITY_UNAVAILABLE", "Activity is unavailable", null);
      return;
    }

    try {
      activity.runOnUiThread(
          () -> {
            try {
              ((MainActivity) activity)
                  .clearBrowsingData(
                      cache,
                      cookies,
                      history,
                      new MainActivity.BrowserOperationCallback() {
                        @Override
                        public void onSuccess() {
                          completion.resolve(null);
                        }

                        @Override
                        public void onFailure(String code, String message, Throwable cause) {
                          completion.reject(code, message, cause);
                        }
                      });
            } catch (RuntimeException | LinkageError error) {
              completion.reject(
                  "BROWSING_DATA_CLEAR_FAILED", "Failed to clear browser data", error);
            }
          });
    } catch (RuntimeException | LinkageError error) {
      completion.reject("BROWSING_DATA_CLEAR_FAILED", "Failed to clear browser data", error);
    }
  }
}

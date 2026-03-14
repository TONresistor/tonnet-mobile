package com.tonnet.browser.plugins;

import android.util.Log;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TonProxyPlugin - Capacitor plugin for TON Proxy
 *
 * This plugin manages the TON network proxy which enables
 * browsing .ton domains through the TON DHT network.
 *
 * Uses libtonutils-proxy.so (standard) and libtonnet-proxy.so (anonymous).
 */
@CapacitorPlugin(name = "TonProxy")
public class TonProxyPlugin extends Plugin {

    private static final String TAG = "TonProxyPlugin";
    private static final int MAX_LOG_LINES = 200;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile int currentPort = 8080;
    private volatile boolean isAnonymous = false;
    private static boolean libraryLoaded = false;
    private static boolean anonymousLibraryLoaded = false;

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
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(new java.util.Date());
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

    private static synchronized void clearLogBuffer() {
        logBuffer.clear();
    }

    // Load the native libraries
    static {
        try {
            // Load the standard Go proxy library
            System.loadLibrary("tonutils-proxy");
            addToBuffer("[Java] Loaded tonutils-proxy native library");
            libraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            libraryLoaded = false;
            addToBuffer("[Java][ERROR] Failed to load tonutils-proxy: " + e.getMessage());
        }

        try {
            // Load the anonymous Go proxy library
            System.loadLibrary("tonnet-proxy");
            addToBuffer("[Java] Loaded tonnet-proxy native library");
            anonymousLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            anonymousLibraryLoaded = false;
            addToBuffer("[Java][ERROR] Failed to load tonnet-proxy: " + e.getMessage());
        }

        try {
            // Load our JNI bridge (links to both libraries)
            System.loadLibrary("tonproxy-jni");
            addToBuffer("[Java] Loaded tonproxy-jni bridge library");
        } catch (UnsatisfiedLinkError e) {
            libraryLoaded = false;
            anonymousLibraryLoaded = false;
            addToBuffer("[Java][ERROR] Failed to load tonproxy-jni: " + e.getMessage());
        }
    }

    // Native method declarations - Standard proxy (tonutils-proxy)
    private static native String StartProxy(short port);
    private static native String StopProxy();

    // Native method declarations - Anonymous proxy (tonnet-proxy)
    private static native String StartAnonymousProxy(short port);
    private static native String StartAnonymousProxyWithConfig(String configJSON);
    private static native String StopAnonymousProxy();
    private static native String GetAnonymousProxyStatus();
    private static native String GetProxyLogs();
    private static native void ClearProxyLogs();

    /**
     * Start the TON proxy server
     *
     * @param call PluginCall with options:
     *             - port (int, optional): Port to run proxy on (default 8080)
     *             - anonymous (boolean, optional): Use anonymous routing (default false)
     */
    @PluginMethod
    public void start(PluginCall call) {
        if (!isRunning.compareAndSet(false, true)) {
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("port", currentPort);
            result.put("anonymous", isAnonymous);
            result.put("message", "Proxy already running");
            call.resolve(result);
            return;
        }

        int port = call.getInt("port", 8080);
        boolean anonymous = call.getBoolean("anonymous", false);
        boolean circuitRotation = call.getBoolean("circuitRotation", false);
        String rotateInterval = call.getString("rotateInterval", "10m");

        log( "Starting proxy on port " + port + ", anonymous: " + anonymous +
                   ", rotation: " + circuitRotation + ", interval: " + rotateInterval);

        // Check if the required library is loaded
        if (anonymous && !anonymousLibraryLoaded) {
            String errorMsg = "Anonymous proxy library not available. Please ensure tonnet-proxy.so is installed.";
            logError( errorMsg);
            isRunning.set(false);
            call.reject(errorMsg, "LIBRARY_NOT_LOADED");
            return;
        }

        if (!anonymous && !libraryLoaded) {
            String errorMsg = "Standard proxy library not available. Please ensure tonutils-proxy.so is installed.";
            logError( errorMsg);
            isRunning.set(false);
            call.reject(errorMsg, "LIBRARY_NOT_LOADED");
            return;
        }

        // Start proxy in a background thread
        final int finalPort = port;
        final boolean finalAnonymous = anonymous;
        final boolean finalCircuitRotation = circuitRotation;
        final String finalRotateInterval = rotateInterval;

        new Thread(() -> {
            try {
                String result;
                if (finalAnonymous) {
                    // Build config JSON for anonymous proxy
                    String configJSON = String.format(
                        "{\"port\":%d,\"rotateInterval\":\"%s\",\"retries\":3}",
                        finalPort,
                        finalCircuitRotation ? finalRotateInterval : ""  // Empty string = no rotation
                    );
                    log( "Calling native StartAnonymousProxyWithConfig: " + configJSON);
                    result = StartAnonymousProxyWithConfig(configJSON);
                    log( "Native StartAnonymousProxyWithConfig result: " + result);
                } else {
                    log( "Calling native StartProxy(" + finalPort + ")");
                    result = StartProxy((short) finalPort);
                    log( "Native StartProxy result: " + result);
                }

                getActivity().runOnUiThread(() -> {
                    // Success if result is "OK", null, empty, or "ALREADY_STARTED"/"ALREADY_RUNNING"
                    boolean success = result == null || result.isEmpty() ||
                                      result.equals("OK") ||
                                      result.equals("ALREADY_STARTED") ||
                                      result.equals("ALREADY_RUNNING");

                    if (success) {
                        currentPort = finalPort;
                        isAnonymous = finalAnonymous;

                        // Configure WebView to use proxy
                        try {
                            com.tonnet.browser.MainActivity mainActivity =
                                (com.tonnet.browser.MainActivity) getActivity();
                            mainActivity.configureProxy(finalPort);
                            log( "WebView proxy configured on port " + finalPort);
                        } catch (Exception e) {
                            logError( "Failed to configure WebView proxy: " + e.getMessage());
                        }

                        JSObject response = new JSObject();
                        response.put("success", true);
                        response.put("port", finalPort);
                        response.put("anonymous", finalAnonymous);
                        call.resolve(response);

                        // Notify listeners
                        JSObject event = new JSObject();
                        event.put("port", finalPort);
                        event.put("anonymous", finalAnonymous);
                        notifyListeners("proxyStarted", event);
                    } else {
                        String errorMsg = result;
                        logError( "Proxy start failed: " + errorMsg);
                        isRunning.set(false);
                        call.reject("Failed to start proxy: " + errorMsg);

                        JSObject event = new JSObject();
                        event.put("error", errorMsg);
                        notifyListeners("proxyError", event);
                    }
                });
            } catch (Exception e) {
                logError("Error starting proxy: " + e.getMessage());
                isRunning.set(false);
                getActivity().runOnUiThread(() -> {
                    call.reject("Failed to start proxy: " + e.getMessage());

                    JSObject event = new JSObject();
                    event.put("error", e.getMessage());
                    notifyListeners("proxyError", event);
                });
            }
        }).start();
    }

    /**
     * Stop the TON proxy server
     */
    @PluginMethod
    public void stop(PluginCall call) {
        if (!isRunning.get()) {
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "Proxy not running");
            call.resolve(result);
            return;
        }

        log( "Stopping proxy (anonymous: " + isAnonymous + ")");

        final boolean wasAnonymous = isAnonymous;

        new Thread(() -> {
            try {
                String result;
                if (wasAnonymous) {
                    log( "Calling native StopAnonymousProxy()");
                    result = StopAnonymousProxy();
                    log( "Native StopAnonymousProxy result: " + result);
                } else {
                    log( "Calling native StopProxy()");
                    result = StopProxy();
                    log( "Native StopProxy result: " + result);
                }

                getActivity().runOnUiThread(() -> {
                    isRunning.set(false);
                    isAnonymous = false;

                    // Clear WebView proxy configuration
                    try {
                        com.tonnet.browser.MainActivity mainActivity =
                            (com.tonnet.browser.MainActivity) getActivity();
                        mainActivity.clearProxy();
                        log( "WebView proxy cleared");
                    } catch (Exception e) {
                        logError( "Failed to clear WebView proxy: " + e.getMessage());
                    }

                    JSObject response = new JSObject();
                    response.put("success", true);
                    call.resolve(response);

                    notifyListeners("proxyStopped", new JSObject());
                });
            } catch (Exception e) {
                logError("Error stopping proxy: " + e.getMessage());
                getActivity().runOnUiThread(() -> {
                    // Still mark as stopped even on error
                    isRunning.set(false);
                    isAnonymous = false;
                    call.reject("Failed to stop proxy: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Get the current proxy status
     *
     * @return JSObject with:
     *         - running (boolean): Whether proxy is active
     *         - port (int): Current proxy port
     *         - anonymous (boolean): Whether anonymous mode is enabled
     */
    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject result = new JSObject();
        result.put("running", isRunning.get());
        result.put("port", currentPort);
        result.put("anonymous", isAnonymous);
        result.put("libraryLoaded", libraryLoaded);
        result.put("anonymousLibraryLoaded", anonymousLibraryLoaded);
        call.resolve(result);
    }

    /**
     * Check if the proxy is connected/running
     */
    @PluginMethod
    public void isConnected(PluginCall call) {
        JSObject result = new JSObject();
        result.put("connected", isRunning.get());
        call.resolve(result);
    }

    /**
     * Check if the native libraries are available
     * Call this before attempting to start the proxy to provide
     * a better user experience with appropriate error messages.
     */
    @PluginMethod
    public void isLibraryAvailable(PluginCall call) {
        JSObject result = new JSObject();
        result.put("available", libraryLoaded);
        result.put("anonymousAvailable", anonymousLibraryLoaded);
        if (!libraryLoaded) {
            result.put("error", "Standard proxy library not loaded. Ensure tonutils-proxy.so is present.");
        }
        if (!anonymousLibraryLoaded) {
            result.put("anonymousError", "Anonymous proxy library not loaded. Ensure tonnet-proxy.so is present.");
        }
        call.resolve(result);
    }

    /**
     * Get proxy logs - combines Java and native Go logs
     */
    @PluginMethod
    public void getLogs(PluginCall call) {
        JSObject result = new JSObject();
        StringBuilder allLogs = new StringBuilder();

        // Add Java logs
        allLogs.append(getLogBuffer());

        // Add native Go logs if available
        if (anonymousLibraryLoaded) {
            try {
                String nativeLogs = GetProxyLogs();
                if (nativeLogs != null && !nativeLogs.isEmpty()) {
                    // Add [Go] prefix to each line
                    for (String line : nativeLogs.split("\n")) {
                        if (!line.isEmpty()) {
                            allLogs.append("[Go] ").append(line).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                allLogs.append("[ERROR] Failed to get native logs: ").append(e.getMessage()).append("\n");
            }
        }

        result.put("logs", allLogs.toString());
        call.resolve(result);
    }

    /**
     * Clear proxy logs
     */
    @PluginMethod
    public void clearLogs(PluginCall call) {
        // Clear Java logs
        clearLogBuffer();

        // Clear native logs
        if (anonymousLibraryLoaded) {
            try {
                ClearProxyLogs();
            } catch (Exception e) {
                logError("Failed to clear native logs: " + e.getMessage());
            }
        }
        call.resolve();
    }
}

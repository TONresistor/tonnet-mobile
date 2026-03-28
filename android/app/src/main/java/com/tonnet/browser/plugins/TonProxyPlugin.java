package com.tonnet.browser.plugins;

import android.app.Activity;
import android.util.Base64;
import android.util.Log;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TonProxyPlugin - Capacitor plugin for TON Proxy
 *
 * Manages the TON network proxy for browsing .ton domains.
 * Generates config.json and nodes-pool.json matching tonutils-proxy format,
 * then delegates to StartProxyInDir.
 */
@CapacitorPlugin(name = "TonProxy")
public class TonProxyPlugin extends Plugin {

    private static final String TAG = "TonProxyPlugin";
    private static final int MAX_LOG_LINES = 200;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile int currentPort = 8080;
    private volatile boolean isAnonymous = false;
    private static boolean libraryLoaded = false;

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
            System.loadLibrary("tonutils-proxy");
            addToBuffer("[Java] Loaded tonutils-proxy native library");
            libraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            libraryLoaded = false;
            addToBuffer("[Java][ERROR] Failed to load tonutils-proxy: " + e.getMessage());
        }

        try {
            System.loadLibrary("tonproxy-jni");
            addToBuffer("[Java] Loaded tonproxy-jni bridge library");
        } catch (UnsatisfiedLinkError e) {
            libraryLoaded = false;
            addToBuffer("[Java][ERROR] Failed to load tonproxy-jni: " + e.getMessage());
        }
    }

    // Native method declarations
    private static native String StartProxyInDir(short port, String dirPath);
    private static native String StopProxy();

    /**
     * Generate a JSON array of 32 random unsigned bytes (0-255).
     */
    private static JSONArray generateKeyArray(SecureRandom rng) throws Exception {
        byte[] bytes = new byte[32];
        rng.nextBytes(bytes);
        return bytesToIntArray(bytes);
    }

    /**
     * Convert a byte array to a JSON array of unsigned integers (0-255).
     */
    private static JSONArray bytesToIntArray(byte[] bytes) throws Exception {
        JSONArray arr = new JSONArray();
        for (byte b : bytes) {
            arr.put(b & 0xFF);
        }
        return arr;
    }

    /**
     * Write or patch config.json in proxyDir.
     * If the file already exists, only TunnelSectionsNum and NodesPoolConfigPath are patched.
     * Otherwise a fresh config with new random keys is generated.
     */
    private void writeProxyConfig(File proxyDir, boolean tunnelEnabled) throws Exception {
        File configFile = new File(proxyDir, "config.json");
        String nodesPoolPath = tunnelEnabled
            ? new File(proxyDir, "nodes-pool.json").getAbsolutePath()
            : "";
        int tunnelSections = tunnelEnabled ? 2 : 1;

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
     * Write nodes-pool.json if it does not already exist.
     * Node keys are base64-decoded to byte arrays then stored as int arrays (0-255).
     */
    private void writeNodesPool(File proxyDir) throws Exception {
        File poolFile = new File(proxyDir, "nodes-pool.json");
        if (poolFile.exists()) {
            log("nodes-pool.json already exists, skipping");
            return;
        }

        String[] nodeKeysB64 = {
            "0nAqzFCklgG1vJFgKHqU7Z87c7RHYn345e4jPnxqnxM=",
            "cOYXQd4ov4pc7OjX26wm90VF35e44NGL6SwGnepiVSE=",
            "DVXr339Go5qPh5eLvVtDWlw16hBrapUXb0u9acYGUiI=",
            "CYHphrvG8HL0CXfTk3egLBRxoS8NR40YtcWZdvl3HJw="
        };

        JSONArray nodesArray = new JSONArray();
        for (String keyB64 : nodeKeysB64) {
            byte[] keyBytes = Base64.decode(keyB64, Base64.DEFAULT);
            JSONObject node = new JSONObject();
            node.put("Key", bytesToIntArray(keyBytes));
            node.put("Payment", JSONObject.NULL);
            nodesArray.put(node);
        }

        JSONObject pool = new JSONObject();
        pool.put("NodesPool", nodesArray);

        try (FileWriter writer = new FileWriter(poolFile)) {
            writer.write(pool.toString(2));
        }
        log("Generated nodes-pool.json with " + nodeKeysB64.length + " nodes");
    }

    /**
     * Start the TON proxy server
     *
     * @param call PluginCall with options:
     *             - port (int, optional): Port to run proxy on (default 8080)
     *             - anonymous (boolean, optional): Use anonymous/tunnel routing (default false)
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
        if (port <= 1024 || port > 65535) {
            port = 8080;
        }
        boolean anonymous = call.getBoolean("anonymous", false);

        log("Starting proxy on port " + port + ", anonymous: " + anonymous);

        if (!libraryLoaded) {
            String errorMsg = "Proxy library not available. Please ensure tonutils-proxy.so is installed.";
            logError(errorMsg);
            isRunning.set(false);
            call.reject(errorMsg, "LIBRARY_NOT_LOADED");
            return;
        }

        final int finalPort = port;
        final boolean finalAnonymous = anonymous;

        new Thread(() -> {
            try {
                // Prepare proxy directory and config files
                File proxyDir = new File(getContext().getFilesDir(), "proxy");
                proxyDir.mkdirs();

                writeProxyConfig(proxyDir, finalAnonymous);
                if (finalAnonymous) {
                    writeNodesPool(proxyDir);
                }

                log("Calling native StartProxyInDir(" + finalPort + ", " + proxyDir.getAbsolutePath() + ")");
                String result = StartProxyInDir((short) finalPort, proxyDir.getAbsolutePath());
                log("Native StartProxyInDir result: " + result);

                Activity activity = getActivity();
                if (activity == null || activity.isFinishing()) {
                    logError("Activity destroyed, cannot post result");
                    isRunning.set(false);
                    return;
                }
                activity.runOnUiThread(() -> {
                    boolean success = result == null || result.isEmpty() ||
                                      result.equals("OK") ||
                                      result.equals("ALREADY_STARTED") ||
                                      result.equals("ALREADY_RUNNING");

                    if (success) {
                        currentPort = finalPort;
                        isAnonymous = finalAnonymous;

                        Activity currentActivity = getActivity();
                        if (currentActivity instanceof com.tonnet.browser.MainActivity) {
                            ((com.tonnet.browser.MainActivity) currentActivity).configureProxy(finalPort);
                            log("WebView proxy configured on port " + finalPort);
                        } else {
                            logError("Activity is not MainActivity");
                        }

                        JSObject response = new JSObject();
                        response.put("success", true);
                        response.put("port", finalPort);
                        response.put("anonymous", finalAnonymous);
                        call.resolve(response);

                        JSObject event = new JSObject();
                        event.put("port", finalPort);
                        event.put("anonymous", finalAnonymous);
                        notifyListeners("proxyStarted", event);
                    } else {
                        logError("Proxy start failed: " + result);
                        isRunning.set(false);
                        call.reject("Failed to start proxy: " + result);

                        JSObject event = new JSObject();
                        event.put("error", result);
                        notifyListeners("proxyError", event);
                    }
                });
            } catch (Exception e) {
                logError("Error starting proxy: " + e.getMessage());
                isRunning.set(false);
                Activity activityOnError = getActivity();
                if (activityOnError == null || activityOnError.isFinishing()) {
                    logError("Activity destroyed, cannot post result");
                    return;
                }
                activityOnError.runOnUiThread(() -> {
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

        log("Stopping proxy");

        new Thread(() -> {
            try {
                log("Calling native StopProxy()");
                String result = StopProxy();
                log("Native StopProxy result: " + result);

                Activity activityStop = getActivity();
                if (activityStop == null || activityStop.isFinishing()) {
                    logError("Activity destroyed, cannot post result");
                    isRunning.set(false);
                    return;
                }
                activityStop.runOnUiThread(() -> {
                    isRunning.set(false);
                    isAnonymous = false;

                    Activity currentActivity = getActivity();
                    if (currentActivity instanceof com.tonnet.browser.MainActivity) {
                        ((com.tonnet.browser.MainActivity) currentActivity).clearProxy();
                        log("WebView proxy cleared");
                    } else {
                        logError("Activity is not MainActivity");
                    }

                    JSObject response = new JSObject();
                    response.put("success", true);
                    call.resolve(response);

                    notifyListeners("proxyStopped", new JSObject());
                });
            } catch (Exception e) {
                logError("Error stopping proxy: " + e.getMessage());
                Activity activityStopError = getActivity();
                if (activityStopError == null || activityStopError.isFinishing()) {
                    logError("Activity destroyed, cannot post result");
                    isRunning.set(false);
                    return;
                }
                activityStopError.runOnUiThread(() -> {
                    isRunning.set(false);
                    isAnonymous = false;
                    call.reject("Failed to stop proxy: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Get the current proxy status
     */
    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject result = new JSObject();
        result.put("running", isRunning.get());
        result.put("port", currentPort);
        result.put("anonymous", isAnonymous);
        result.put("libraryLoaded", libraryLoaded);
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
     */
    @PluginMethod
    public void isLibraryAvailable(PluginCall call) {
        JSObject result = new JSObject();
        result.put("available", libraryLoaded);
        if (!libraryLoaded) {
            result.put("error", "Proxy library not loaded. Ensure tonutils-proxy.so is present.");
        }
        call.resolve(result);
    }

    /**
     * Get proxy logs from Java buffer
     */
    @PluginMethod
    public void getLogs(PluginCall call) {
        JSObject result = new JSObject();
        result.put("logs", getLogBuffer());
        call.resolve(result);
    }

    /**
     * Clear proxy logs
     */
    @PluginMethod
    public void clearLogs(PluginCall call) {
        clearLogBuffer();
        call.resolve();
    }
}

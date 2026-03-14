/**
 * JNI Bridge for tonutils-proxy and tonnet-proxy (anonymous)
 *
 * This file provides JNI-compatible function names that call
 * the CGO-exported functions from libtonutils-proxy.so and libtonnet-proxy.so
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>

// Forward declarations of CGO-exported functions from libtonutils-proxy.so
extern char* StartProxy(unsigned short port);
extern char* StopProxy(void);

// Forward declarations of CGO-exported functions from libtonnet-proxy.so
extern char* StartAnonymousProxy(unsigned short port);
extern char* StartAnonymousProxyWithConfig(char* configJSON);
extern char* StopAnonymousProxy(void);
extern char* GetAnonymousProxyStatus(void);
extern char* GetProxyLogs(void);
extern void ClearProxyLogs(void);

// ============================================================================
// Standard Proxy (tonutils-proxy)
// ============================================================================

/**
 * JNI wrapper for StartProxy
 *
 * Java signature: private static native String StartProxy(short port);
 */
JNIEXPORT jstring JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_StartProxy(
    JNIEnv *env,
    jclass clazz,
    jshort port
) {
    char* result = StartProxy((unsigned short)port);
    jstring jresult = (*env)->NewStringUTF(env, result ? result : "ERROR");

    // Free CGO-allocated memory to prevent leak
    if (result != NULL) {
        free(result);
    }

    return jresult;
}

/**
 * JNI wrapper for StopProxy
 *
 * Java signature: private static native String StopProxy();
 */
JNIEXPORT jstring JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_StopProxy(
    JNIEnv *env,
    jclass clazz
) {
    char* result = StopProxy();
    jstring jresult = (*env)->NewStringUTF(env, result ? result : "ERROR");

    // Free CGO-allocated memory to prevent leak
    if (result != NULL) {
        free(result);
    }

    return jresult;
}

// ============================================================================
// Anonymous Proxy (tonnet-proxy)
// ============================================================================

/**
 * JNI wrapper for StartAnonymousProxy
 *
 * Java signature: private static native String StartAnonymousProxy(short port);
 */
JNIEXPORT jstring JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_StartAnonymousProxy(
    JNIEnv *env,
    jclass clazz,
    jshort port
) {
    char* result = StartAnonymousProxy((unsigned short)port);
    jstring jresult = (*env)->NewStringUTF(env, result ? result : "ERROR");

    // Free CGO-allocated memory to prevent leak
    if (result != NULL) {
        free(result);
    }

    return jresult;
}

/**
 * JNI wrapper for StartAnonymousProxyWithConfig
 *
 * Java signature: private static native String StartAnonymousProxyWithConfig(String configJSON);
 */
JNIEXPORT jstring JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_StartAnonymousProxyWithConfig(
    JNIEnv *env,
    jclass clazz,
    jstring configJSON
) {
    if (configJSON == NULL) {
        return (*env)->NewStringUTF(env, "ERROR: null config");
    }
    const char* configStr = (*env)->GetStringUTFChars(env, configJSON, NULL);
    if (configStr == NULL) {
        return (*env)->NewStringUTF(env, "ERROR: GetStringUTFChars failed");
    }
    char* configCopy = strdup(configStr);
    (*env)->ReleaseStringUTFChars(env, configJSON, configStr);
    if (configCopy == NULL) {
        return (*env)->NewStringUTF(env, "ERROR: OOM");
    }

    char* result = StartAnonymousProxyWithConfig(configCopy);
    free(configCopy);

    jstring jresult = (*env)->NewStringUTF(env, result ? result : "ERROR");

    // Free CGO-allocated memory to prevent leak
    if (result != NULL) {
        free(result);
    }

    return jresult;
}

/**
 * JNI wrapper for StopAnonymousProxy
 *
 * Java signature: private static native String StopAnonymousProxy();
 */
JNIEXPORT jstring JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_StopAnonymousProxy(
    JNIEnv *env,
    jclass clazz
) {
    char* result = StopAnonymousProxy();
    jstring jresult = (*env)->NewStringUTF(env, result ? result : "ERROR");

    // Free CGO-allocated memory to prevent leak
    if (result != NULL) {
        free(result);
    }

    return jresult;
}

/**
 * JNI wrapper for GetAnonymousProxyStatus
 *
 * Java signature: private static native String GetAnonymousProxyStatus();
 */
JNIEXPORT jstring JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_GetAnonymousProxyStatus(
    JNIEnv *env,
    jclass clazz
) {
    char* result = GetAnonymousProxyStatus();
    jstring jresult = (*env)->NewStringUTF(env, result ? result : "ERROR");

    // Free CGO-allocated memory to prevent leak
    if (result != NULL) {
        free(result);
    }

    return jresult;
}

/**
 * JNI wrapper for GetProxyLogs
 *
 * Java signature: private static native String GetProxyLogs();
 */
JNIEXPORT jstring JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_GetProxyLogs(
    JNIEnv *env,
    jclass clazz
) {
    char* result = GetProxyLogs();
    jstring jresult = (*env)->NewStringUTF(env, result ? result : "");

    // Free CGO-allocated memory to prevent leak
    if (result != NULL) {
        free(result);
    }

    return jresult;
}

/**
 * JNI wrapper for ClearProxyLogs
 *
 * Java signature: private static native void ClearProxyLogs();
 */
JNIEXPORT void JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_ClearProxyLogs(
    JNIEnv *env,
    jclass clazz
) {
    ClearProxyLogs();
}

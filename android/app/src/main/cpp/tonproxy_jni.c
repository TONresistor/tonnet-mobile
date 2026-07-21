/**
 * JNI Bridge for tonutils-proxy
 *
 * This file provides JNI-compatible function names that call
 * the CGO-exported functions from libtonutils-proxy.so.
 *
 * tonutils-proxy reads config.json from CWD. Use StartProxyInDir
 * to chdir() to the directory containing the generated config.json
 * before starting the proxy (handles both direct and anonymous mode).
 */

#include <jni.h>
#include <stdlib.h>
#include <unistd.h>

// Forward declarations of CGO-exported functions from libtonutils-proxy.so
extern unsigned long long ReserveProxyStart(void);
extern char* StartProxyReserved(unsigned short port, unsigned long long token);
extern char* StopProxy(void);
extern int IsProxyActive(void);

/** Reserve a native startup token that a later StopProxy call can invalidate atomically. */
JNIEXPORT jlong JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_ReserveProxyStart(
    JNIEnv *env,
    jclass clazz
) {
    (void)env;
    (void)clazz;
    return (jlong)ReserveProxyStart();
}

/** Return whether the process-wide Go proxy is still active. */
JNIEXPORT jboolean JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_IsProxyActive(
    JNIEnv *env,
    jclass clazz
) {
    (void)env;
    (void)clazz;
    return IsProxyActive() != 0 ? JNI_TRUE : JNI_FALSE;
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
    (void)clazz;
    char* result = StopProxy();
    jstring jresult = (*env)->NewStringUTF(env, result ? result : "ERROR");

    if (result != NULL) {
        free(result);
    }

    return jresult;
}

/**
 * JNI wrapper that chdir()s to dirPath before calling StartProxy(port).
 * tonutils-proxy reads config.json from CWD, so placing the generated
 * config.json in dirPath allows the same binary to handle both direct
 * and anonymous mode (via TunnelSectionsNum in config.json).
 *
 * Java signature: private static native String StartProxyInDir(
 *     short port, String dirPath, long token);
 */
JNIEXPORT jstring JNICALL
Java_com_tonnet_browser_plugins_TonProxyPlugin_StartProxyInDir(
    JNIEnv *env,
    jclass clazz,
    jshort port,
    jstring dirPath,
    jlong token
) {
    (void)clazz;
    if (dirPath == NULL) {
        return (*env)->NewStringUTF(env, "ERROR: null dirPath");
    }

    const char* dirStr = (*env)->GetStringUTFChars(env, dirPath, NULL);
    if (dirStr == NULL) {
        return (*env)->NewStringUTF(env, "ERROR: GetStringUTFChars failed");
    }

    int rc = chdir(dirStr);
    (*env)->ReleaseStringUTFChars(env, dirPath, dirStr);

    if (rc != 0) {
        return (*env)->NewStringUTF(env, "ERROR: chdir failed");
    }

    char* result = StartProxyReserved((unsigned short)port, (unsigned long long)token);
    jstring jresult = (*env)->NewStringUTF(env, result ? result : "ERROR");

    if (result != NULL) {
        free(result);
    }

    return jresult;
}

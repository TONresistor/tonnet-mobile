#include <jni.h>

extern char *StartProxySession(char *config_text_json, int mode);
extern char *StopProxy(void);
extern int GetProxySessionState(void);
extern void FreeCString(char *value);

static jstring to_java_string(JNIEnv *env, char *value) {
    const char *safe_value = value == NULL ? "ERR:native returned null" : value;
    jstring result = (*env)->NewStringUTF(env, safe_value);
    if (value != NULL) {
        FreeCString(value);
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_tonnet_proxy_NativeTonProxy_nativeStartSession(
        JNIEnv *env,
        jclass clazz,
        jstring config_text,
        jint mode) {
    (void) clazz;
    if (config_text == NULL) {
        return (*env)->NewStringUTF(env, "ERR:missing TON config");
    }

    const char *config_chars = (*env)->GetStringUTFChars(env, config_text, NULL);
    if (config_chars == NULL) {
        return (*env)->NewStringUTF(env, "ERR:cannot read TON config");
    }

    char *result = StartProxySession((char *) config_chars, (int) mode);
    (*env)->ReleaseStringUTFChars(env, config_text, config_chars);
    return to_java_string(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_tonnet_proxy_NativeTonProxy_nativeStopSession(
        JNIEnv *env,
        jclass clazz) {
    (void) clazz;
    return to_java_string(env, StopProxy());
}

JNIEXPORT jint JNICALL
Java_com_tonnet_proxy_NativeTonProxy_nativeGetSessionState(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    return (jint) GetProxySessionState();
}

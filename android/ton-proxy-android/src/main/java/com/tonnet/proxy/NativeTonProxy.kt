package com.tonnet.proxy

internal object NativeTonProxy {
    private val loadFailure: Throwable? = runCatching {
        System.loadLibrary("tonproxy_jni")
    }.exceptionOrNull()

    val isAvailable: Boolean
        get() = loadFailure == null

    fun loadError(): String = loadFailure?.message ?: "Native TON proxy unavailable"

    @JvmStatic
    external fun nativeStartSession(globalConfigJson: String, mode: Int): String

    @JvmStatic
    external fun nativeStopSession(): String

    @JvmStatic
    external fun nativeGetSessionState(): Int
}

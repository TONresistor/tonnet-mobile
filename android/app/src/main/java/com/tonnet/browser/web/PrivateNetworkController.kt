package com.tonnet.browser.web

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.tonnet.browser.core.NetworkMode
import com.tonnet.browser.data.PrivacyResetCoordinator
import com.tonnet.browser.proxy.ProxySnapshot
import com.tonnet.browser.proxy.TonProxyConnection
import com.tonnet.proxy.ProxyContract
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

internal class PrivateNetworkController(
    context: Context,
    private val listener: Listener,
    private val sessionManager: WebViewSessionManager = WebViewSessionManager(),
    private val uiExecutor: Executor = ContextCompat.getMainExecutor(context),
) : AutoCloseable {
    private val privacyReset = PrivacyResetCoordinator()
    private val proxyConnection = TonProxyConnection(context) { snapshot ->
        uiExecutor.execute {
            if (!closed) onProxyState(snapshot)
        }
    }

    private var sessionStarted = false
    private var profileResetInFlight = false
    private var restartSessionAfterProfileReset = false
    private var startProxyWhenProfileReady = false
    private var proxyOverrideGeneration = 0L
    private var closed = false
    private val proxyPortSnapshot = AtomicInteger(NO_PROXY_PORT)

    var state = PrivateNetworkState()
        private set

    val profileName: String
        get() = sessionManager.profileName

    val fingerprintingSeed: String
        get() = sessionManager.fingerprintingSeed

    fun currentProxyPort(): Int? =
        proxyPortSnapshot.get().takeUnless { it == NO_PROXY_PORT }

    fun unsupportedFeatures(): List<String> = sessionManager.unsupportedFeatures()

    fun prepareSession(
        clearPersistedData: Boolean,
        after: (Result<Unit>) -> Unit,
    ) {
        if (sessionStarted) {
            after(Result.success(Unit))
            return
        }
        val started = sessionManager.startSession()
        if (started.isFailure) {
            after(started)
            return
        }
        sessionStarted = true
        if (clearPersistedData) {
            profileResetInFlight = true
            sessionManager.clearBrowsingData(uiExecutor) { result ->
                profileResetInFlight = false
                if (closed) {
                    sessionManager.releaseSession()
                    sessionStarted = false
                    return@clearBrowsingData
                }
                after(result)
            }
        } else {
            after(Result.success(Unit))
        }
    }

    fun start(mode: NetworkMode) {
        proxyConnection.switchTo(mode.wireValue)
    }

    fun switchTo(mode: NetworkMode) {
        if (!privacyReset.begin(mode)) return
        restartSessionAfterProfileReset = false
        proxyOverrideGeneration += 1
        updateState(PrivateNetworkState(resetting = true))
        listener.onNetworkResetStarted()

        sessionStarted = false
        profileResetInFlight = true
        sessionManager.clearAndEndSession(uiExecutor) { result ->
            profileResetInFlight = false
            if (closed) return@clearAndEndSession
            if (result.isFailure) {
                failPrivateData()
                return@clearAndEndSession
            }
            if (!privacyReset.inProgress) {
                resumeAfterCancelledReset(mode)
                return@clearAndEndSession
            }
            privacyReset.onDataCleared()?.let(::startNetworkSwitch)
        }
        runCatching {
            proxyController().clearProxyOverride(uiExecutor) {
                privacyReset.onProxyCleared()?.let(::startNetworkSwitch)
            }
        }.onFailure {
            failPrivateData()
        }
    }

    fun clearBrowsingData() {
        if (profileResetInFlight) return
        listener.onBrowsingDataResetStarted()
        sessionStarted = false
        profileResetInFlight = true
        sessionManager.clearAndEndSession(uiExecutor) { result ->
            profileResetInFlight = false
            if (closed) return@clearAndEndSession
            if (result.isFailure || sessionManager.startSession().isFailure) {
                failPrivateData()
                return@clearAndEndSession
            }
            sessionStarted = true
            if (startProxyWhenProfileReady) {
                startProxyWhenProfileReady = false
                proxyConnection.switchTo(listener.currentMode().wireValue)
            }
            listener.onBrowsingDataCleared()
        }
    }

    fun retry(mode: NetworkMode) {
        if (state.ready) {
            listener.onReadyRetryRequested()
            return
        }
        if (privacyReset.inProgress) {
            updateState(state.copy(failed = false))
            return
        }
        updateState(state.copy(failed = false))
        startProxySafely(mode)
    }

    fun close(clearBrowsingData: Boolean) {
        if (closed) return
        closed = true
        proxyPortSnapshot.set(NO_PROXY_PORT)
        proxyConnection.close()
        if (sessionStarted && !profileResetInFlight) {
            if (clearBrowsingData) {
                sessionManager.clearAndEndSession(uiExecutor)
            } else {
                sessionManager.releaseSession()
            }
            sessionStarted = false
        }
        proxyOverrideGeneration += 1
        runCatching { proxyController().clearProxyOverride(uiExecutor) {} }
    }

    override fun close() = close(clearBrowsingData = false)

    private fun onProxyState(snapshot: ProxySnapshot) {
        when (snapshot.state) {
            ProxyContract.STATE_STARTING,
            ProxyContract.STATE_RECOVERING,
            -> updateState(
                state.copy(
                    ready = false,
                    failed = false,
                    proxyPort = null,
                ),
            )
            ProxyContract.STATE_READY -> {
                if (!privacyReset.inProgress || privacyReset.switchRequested) {
                    configureWebViewProxy(snapshot.port)
                }
            }
            ProxyContract.STATE_FAILED -> failClosed()
            ProxyContract.STATE_STOPPED -> updateState(
                state.copy(
                    ready = false,
                    proxyPort = null,
                ),
            )
        }
    }

    private fun configureWebViewProxy(port: Int) {
        val overrideGeneration = ++proxyOverrideGeneration
        val config = ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:$port")
            .removeImplicitRules()
            .build()
        runCatching {
            proxyController().setProxyOverride(config, uiExecutor) {
                if (closed || proxyOverrideGeneration != overrideGeneration) {
                    return@setProxyOverride
                }
                if (privacyReset.inProgress && privacyReset.switchRequested) {
                    privacyReset.complete()
                }
                updateState(
                    PrivateNetworkState(
                        ready = true,
                        proxyPort = port,
                    ),
                )
                listener.onNetworkReady()
            }
        }.onFailure {
            failClosed()
        }
    }

    private fun startNetworkSwitch(mode: NetworkMode) {
        if (sessionManager.startSession().isFailure) {
            privacyReset.cancel()
            failPrivateData()
            return
        }
        sessionStarted = true
        proxyConnection.switchTo(mode.wireValue)
    }

    private fun resumeAfterCancelledReset(mode: NetworkMode) {
        if (restartSessionAfterProfileReset && !sessionStarted) {
            if (sessionManager.startSession().isFailure) {
                failPrivateData()
                return
            }
            sessionStarted = true
            restartSessionAfterProfileReset = false
        }
        if (startProxyWhenProfileReady) {
            startProxyWhenProfileReady = false
            proxyConnection.switchTo(mode.wireValue)
        }
    }

    private fun startProxySafely(mode: NetworkMode) {
        when {
            sessionStarted -> proxyConnection.switchTo(mode.wireValue)
            profileResetInFlight -> {
                restartSessionAfterProfileReset = true
                startProxyWhenProfileReady = true
            }
            sessionManager.startSession().isFailure -> failPrivateData()
            else -> {
                sessionStarted = true
                proxyConnection.switchTo(mode.wireValue)
            }
        }
    }

    private fun failClosed() {
        proxyOverrideGeneration += 1
        privacyReset.cancel()
        if (!sessionStarted) {
            if (profileResetInFlight) {
                restartSessionAfterProfileReset = true
            } else if (sessionManager.startSession().isFailure) {
                failPrivateData()
                return
            } else {
                sessionStarted = true
            }
        }
        updateState(PrivateNetworkState(failed = true))
        runCatching { proxyConnection.stop() }
        runCatching { proxyController().clearProxyOverride(uiExecutor) {} }
        listener.onNetworkUnavailable()
    }

    private fun failPrivateData() {
        profileResetInFlight = false
        sessionStarted = false
        privacyReset.cancel()
        restartSessionAfterProfileReset = false
        startProxyWhenProfileReady = false
        proxyOverrideGeneration += 1
        updateState(PrivateNetworkState(failed = true))
        runCatching { proxyConnection.stop() }
        runCatching { proxyController().clearProxyOverride(uiExecutor) {} }
        listener.onPrivateDataFailure()
    }

    private fun updateState(updated: PrivateNetworkState) {
        state = updated.copy(resetting = privacyReset.inProgress)
        proxyPortSnapshot.set(state.proxyPort ?: NO_PROXY_PORT)
        listener.onNetworkStateChanged(state)
    }

    @SuppressLint("RequiresFeature")
    private fun proxyController(): ProxyController {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))
        return ProxyController.getInstance()
    }

    interface Listener {
        fun currentMode(): NetworkMode
        fun onNetworkStateChanged(state: PrivateNetworkState)
        fun onNetworkResetStarted()
        fun onBrowsingDataResetStarted()
        fun onNetworkReady()
        fun onReadyRetryRequested()
        fun onNetworkUnavailable()
        fun onPrivateDataFailure()
        fun onBrowsingDataCleared()
    }

    private companion object {
        const val NO_PROXY_PORT = 0
    }
}

data class PrivateNetworkState(
    val ready: Boolean = false,
    val failed: Boolean = false,
    val proxyPort: Int? = null,
    val resetting: Boolean = false,
)

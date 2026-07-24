package com.tonnet.browser.data

import com.tonnet.browser.core.NetworkMode

internal class PrivacyResetCoordinator {
    private data class PendingReset(
        val mode: NetworkMode,
        var dataCleared: Boolean = false,
        var proxyCleared: Boolean = false,
        var switchRequested: Boolean = false,
    )

    private var pending: PendingReset? = null

    val inProgress: Boolean
        get() = pending != null

    val pendingMode: NetworkMode?
        get() = pending?.mode

    val switchRequested: Boolean
        get() = pending?.switchRequested == true

    fun begin(mode: NetworkMode): Boolean {
        if (pending != null) return false
        pending = PendingReset(mode)
        return true
    }

    fun onDataCleared(): NetworkMode? {
        pending?.dataCleared = true
        return modeReadyForSwitch()
    }

    fun onProxyCleared(): NetworkMode? {
        pending?.proxyCleared = true
        return modeReadyForSwitch()
    }

    fun complete() {
        check(pending?.switchRequested == true)
        pending = null
    }

    fun cancel() {
        pending = null
    }

    private fun modeReadyForSwitch(): NetworkMode? {
        val current = pending ?: return null
        if (!current.dataCleared || !current.proxyCleared || current.switchRequested) return null
        current.switchRequested = true
        return current.mode
    }
}

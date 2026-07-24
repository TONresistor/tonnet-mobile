package com.tonnet.browser.proxy

internal enum class BindingLossAction {
    IGNORE,
    RECOVER,
    REBIND,
    FAIL,
}

internal class ProxyBindingLifecycle {
    private var nextEpoch = 0L
    private var activeEpoch: Long? = null
    private var desiredMode: Int? = null
    private var closed = false

    fun request(mode: Int): Long? {
        if (closed) return null
        desiredMode = mode
        return beginBindingIfNeeded()
    }

    fun beginBindingIfNeeded(): Long? {
        if (closed || desiredMode == null || activeEpoch != null) return null
        return (++nextEpoch).also { activeEpoch = it }
    }

    fun desiredMode(epoch: Long): Int? =
        desiredMode.takeIf { !closed && activeEpoch == epoch }

    fun currentMode(): Int? = desiredMode.takeUnless { closed }

    fun accepts(epoch: Long): Boolean =
        !closed && activeEpoch == epoch && desiredMode != null

    fun onServiceDisconnected(epoch: Long): BindingLossAction =
        if (accepts(epoch)) BindingLossAction.RECOVER else BindingLossAction.IGNORE

    fun onBindingDied(epoch: Long): BindingLossAction {
        if (closed || activeEpoch != epoch) return BindingLossAction.IGNORE
        activeEpoch = null
        return if (desiredMode != null) BindingLossAction.REBIND else BindingLossAction.IGNORE
    }

    fun onNullBinding(epoch: Long): BindingLossAction {
        if (closed || activeEpoch != epoch) return BindingLossAction.IGNORE
        activeEpoch = null
        desiredMode = null
        return BindingLossAction.FAIL
    }

    fun onBindFailed(epoch: Long): BindingLossAction = onNullBinding(epoch)

    fun stop() {
        desiredMode = null
    }

    fun close() {
        closed = true
        desiredMode = null
        activeEpoch = null
    }
}

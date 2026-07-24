package com.tonnet.proxy

internal sealed interface ProxyHealthAction {
    data object Ready : ProxyHealthAction
    data object Recovering : ProxyHealthAction
    data class Failed(val reason: String) : ProxyHealthAction
}

internal class ProxyHealthSupervisor(
    private val recoveryTimeoutMs: Long = RECOVERY_TIMEOUT_MS,
) {
    private var recoveryStartedAtMs: Long? = null

    fun observe(nativeState: Int, nowMs: Long): ProxyHealthAction = when (nativeState) {
        ProxyContract.STATE_READY -> {
            recoveryStartedAtMs = null
            ProxyHealthAction.Ready
        }

        ProxyContract.STATE_RECOVERING -> {
            val startedAt = recoveryStartedAtMs ?: nowMs.also { recoveryStartedAtMs = it }
            if (nowMs - startedAt >= recoveryTimeoutMs) {
                ProxyHealthAction.Failed("TON tunnel recovery timed out")
            } else {
                ProxyHealthAction.Recovering
            }
        }

        ProxyContract.STATE_STOPPED -> ProxyHealthAction.Failed("TON proxy stopped unexpectedly")
        ProxyContract.STATE_FAILED -> ProxyHealthAction.Failed("TON proxy failed")
        ProxyContract.STATE_STARTING -> ProxyHealthAction.Failed("TON proxy returned to startup unexpectedly")
        else -> ProxyHealthAction.Failed("TON proxy returned an invalid state")
    }

    internal companion object {
        const val RECOVERY_TIMEOUT_MS = 90_000L
    }
}

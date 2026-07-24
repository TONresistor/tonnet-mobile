package com.tonnet.browser.proxy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.tonnet.proxy.IProxyStateCallback
import com.tonnet.proxy.ITonProxyService
import com.tonnet.proxy.ProxyContract
import com.tonnet.proxy.TonProxyService

data class ProxySnapshot(
    val state: Int = ProxyContract.STATE_STOPPED,
    val port: Int = 0,
    val message: String = "Stopped",
)

class TonProxyConnection(
    context: Context,
    private val onStateChanged: (ProxySnapshot) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val stateLock = Any()
    private val lifecycle = ProxyBindingLifecycle()
    private val handler = Handler(Looper.getMainLooper())

    private var service: ITonProxyService? = null
    private var activeConnection: EpochConnection? = null
    private var deadlineGeneration = 0L
    private var deadline: Runnable? = null
    private var closed = false

    fun switchTo(mode: Int) {
        require(mode == ProxyContract.MODE_DIRECT || mode == ProxyContract.MODE_TUNNEL_2_HOP)

        val connectedService: ITonProxyService?
        val bindingEpoch: Long?
        synchronized(stateLock) {
            if (closed) return
            bindingEpoch = lifecycle.request(mode)
            connectedService = service
            scheduleDeadlineLocked(mode)
        }

        onStateChanged(ProxySnapshot(ProxyContract.STATE_STARTING, 0, "Connecting to TON"))
        when {
            connectedService != null -> startRemoteSession(connectedService, mode, currentEpoch = null)
            bindingEpoch != null -> bind(bindingEpoch)
        }
    }

    fun stop() {
        val connectedService: ITonProxyService?
        synchronized(stateLock) {
            lifecycle.stop()
            cancelDeadlineLocked()
            connectedService = service
        }
        runCatching { connectedService?.stopSession() }
    }

    override fun close() {
        val connectedService: ITonProxyService?
        val connection: EpochConnection?
        synchronized(stateLock) {
            if (closed) return
            closed = true
            lifecycle.close()
            cancelDeadlineLocked()
            connectedService = service
            connection = activeConnection
            service = null
            activeConnection = null
        }

        if (connection != null) {
            runCatching { connectedService?.unregisterCallback(connection.callback) }
        }
        runCatching { connectedService?.stopSession() }
        if (connection != null) runCatching { appContext.unbindService(connection) }
    }

    private fun bind(epoch: Long) {
        val connection = EpochConnection(epoch)
        synchronized(stateLock) {
            if (closed || lifecycle.desiredMode(epoch) == null) return
            activeConnection = connection
        }

        val bound = runCatching {
            appContext.bindService(
                Intent(appContext, TonProxyService::class.java),
                connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
            )
        }.getOrDefault(false)
        if (bound) return

        val shouldFail = synchronized(stateLock) {
            if (activeConnection === connection) activeConnection = null
            lifecycle.onBindFailed(epoch) == BindingLossAction.FAIL
        }
        if (shouldFail) {
            cancelDeadline()
            onStateChanged(ProxySnapshot(ProxyContract.STATE_FAILED, 0, "TON proxy service unavailable"))
        }
    }

    private fun startRemoteSession(remote: ITonProxyService, mode: Int, currentEpoch: Long?) {
        runCatching { remote.startSession(mode) }
            .onFailure {
                val epoch = currentEpoch ?: synchronized(stateLock) { activeConnection?.epoch }
                if (epoch != null) handleRemoteFailure(epoch, "TON proxy process unavailable")
            }
    }

    private fun handleRemoteFailure(epoch: Long, reason: String) {
        val shouldRecover = synchronized(stateLock) {
            if (!lifecycle.accepts(epoch)) return@synchronized false
            service = null
            lifecycle.currentMode()?.let(::ensureDeadlineLocked)
            true
        }
        if (shouldRecover) {
            onStateChanged(ProxySnapshot(ProxyContract.STATE_RECOVERING, 0, reason))
        }
    }

    private fun handleServiceDisconnected(epoch: Long) {
        val shouldRecover = synchronized(stateLock) {
            if (lifecycle.onServiceDisconnected(epoch) != BindingLossAction.RECOVER) return@synchronized false
            service = null
            lifecycle.currentMode()?.let(::ensureDeadlineLocked)
            true
        }
        if (shouldRecover) {
            onStateChanged(ProxySnapshot(ProxyContract.STATE_RECOVERING, 0, "Recovering TON proxy"))
        }
    }

    private fun handleBindingDied(connection: EpochConnection) {
        val newEpoch = synchronized(stateLock) {
            if (lifecycle.onBindingDied(connection.epoch) != BindingLossAction.REBIND) return@synchronized null
            service = null
            if (activeConnection === connection) activeConnection = null
            lifecycle.currentMode()?.let(::ensureDeadlineLocked)
            lifecycle.beginBindingIfNeeded()
        }
        if (newEpoch == null) return

        runCatching { appContext.unbindService(connection) }
        onStateChanged(ProxySnapshot(ProxyContract.STATE_RECOVERING, 0, "Recovering TON proxy"))
        bind(newEpoch)
    }

    private fun handleNullBinding(connection: EpochConnection) {
        val shouldFail = synchronized(stateLock) {
            if (lifecycle.onNullBinding(connection.epoch) != BindingLossAction.FAIL) return@synchronized false
            service = null
            if (activeConnection === connection) activeConnection = null
            cancelDeadlineLocked()
            true
        }
        if (!shouldFail) return

        runCatching { appContext.unbindService(connection) }
        onStateChanged(ProxySnapshot(ProxyContract.STATE_FAILED, 0, "TON proxy service unavailable"))
    }

    private fun onRemoteState(epoch: Long, state: Int, port: Int, message: String?) {
        val accepted = synchronized(stateLock) {
            if (!lifecycle.accepts(epoch)) return@synchronized false
            when (state) {
                ProxyContract.STATE_READY -> cancelDeadlineLocked()
                ProxyContract.STATE_FAILED -> cancelDeadlineLocked()
                ProxyContract.STATE_RECOVERING -> lifecycle.currentMode()?.let(::ensureDeadlineLocked)
            }
            true
        }
        if (accepted) onStateChanged(ProxySnapshot(state, port, message.orEmpty()))
    }

    private fun scheduleDeadlineLocked(mode: Int) {
        cancelDeadlineLocked()
        val generation = ++deadlineGeneration
        val timeout = Runnable {
            var connectedService: ITonProxyService? = null
            val failed = synchronized(stateLock) {
                if (closed || generation != deadlineGeneration || lifecycle.currentMode() != mode) {
                    return@synchronized false
                }
                lifecycle.stop()
                deadline = null
                connectedService = service
                true
            }
            if (!failed) return@Runnable
            runCatching { connectedService?.stopSession() }
            onStateChanged(
                ProxySnapshot(
                    ProxyContract.STATE_FAILED,
                    0,
                    if (mode == ProxyContract.MODE_TUNNEL_2_HOP) {
                        "TON tunnel startup timed out"
                    } else {
                        "TON proxy startup timed out"
                    },
                ),
            )
        }
        deadline = timeout
        handler.postDelayed(timeout, TRANSITION_TIMEOUT_MS)
    }

    private fun ensureDeadlineLocked(mode: Int) {
        if (deadline == null) scheduleDeadlineLocked(mode)
    }

    private fun cancelDeadline() = synchronized(stateLock) { cancelDeadlineLocked() }

    private fun cancelDeadlineLocked() {
        deadline?.let(handler::removeCallbacks)
        deadline = null
        deadlineGeneration += 1
    }

    private inner class EpochConnection(val epoch: Long) : ServiceConnection {
        val callback = object : IProxyStateCallback.Stub() {
            override fun onStateChanged(state: Int, port: Int, message: String?) {
                onRemoteState(epoch, state, port, message)
            }
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null) {
                handleNullBinding(this)
                return
            }

            val remote = ITonProxyService.Stub.asInterface(binder)
            val mode = synchronized(stateLock) {
                if (closed || activeConnection !== this) {
                    return@synchronized null
                }
                if (lifecycle.desiredMode(epoch) == null) {
                    lifecycle.onBindingDied(epoch)
                    activeConnection = null
                    return@synchronized null
                }
                service = remote
                lifecycle.desiredMode(epoch)
            }
            if (mode == null) {
                runCatching { appContext.unbindService(this) }
                return
            }

            val registered = runCatching { remote.registerCallback(callback) }.isSuccess
            if (!registered) {
                handleRemoteFailure(epoch, "TON proxy process unavailable")
                return
            }
            startRemoteSession(remote, mode, epoch)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            handleServiceDisconnected(epoch)
        }

        override fun onBindingDied(name: ComponentName?) {
            handleBindingDied(this)
        }

        override fun onNullBinding(name: ComponentName?) {
            handleNullBinding(this)
        }
    }

    private companion object {
        const val TRANSITION_TIMEOUT_MS = 105_000L
    }
}

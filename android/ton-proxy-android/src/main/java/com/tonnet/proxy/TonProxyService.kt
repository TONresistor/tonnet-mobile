package com.tonnet.proxy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.RemoteCallbackList
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class TonProxyService : Service() {
    private val callbacks = RemoteCallbackList<IProxyStateCallback>()
    private val startExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "tonnet-proxy-start").apply { isDaemon = true }
    }
    private val monitorExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "tonnet-proxy-monitor").apply { isDaemon = true }
    }
    private val generation = AtomicLong(0)
    private val stateLock = Any()
    private val monitorLock = Any()
    private val nativeStartLock = Object()
    private var monitorFuture: ScheduledFuture<*>? = null
    @Volatile private var nativeStartInFlight = false

    @Volatile private var state = ProxyContract.STATE_STOPPED
    @Volatile private var port = 0
    @Volatile private var message = "Stopped"

    private val binder = object : ITonProxyService.Stub() {
        override fun startSession(mode: Int) {
            require(mode == ProxyContract.MODE_DIRECT || mode == ProxyContract.MODE_TUNNEL_2_HOP)
            start(mode)
        }

        override fun stopSession() = stop()
        override fun getState(): Int = state
        override fun getPort(): Int = port
        override fun getMessage(): String = message

        override fun registerCallback(callback: IProxyStateCallback?) {
            callback ?: return
            callbacks.register(callback)
            synchronized(stateLock) {
                runCatching { callback.onStateChanged(state, port, message) }
            }
        }

        override fun unregisterCallback(callback: IProxyStateCallback?) {
            callback ?: return
            callbacks.unregister(callback)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        stopSelf()
        return false
    }

    override fun onDestroy() {
        stop()
        cancelHealthMonitor()
        callbacks.kill()
        startExecutor.shutdownNow()
        monitorExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun start(mode: Int) {
        val currentGeneration = nextGeneration()
        cancelHealthMonitor()
        if (!NativeTonProxy.isAvailable) {
            publishIfCurrent(currentGeneration, ProxyContract.STATE_FAILED, 0, NativeTonProxy.loadError())
            return
        }

        val previousState = state
        publishIfCurrent(currentGeneration, ProxyContract.STATE_STARTING, 0, "Connecting to TON")

        startExecutor.execute {
            if (!isCurrent(currentGeneration)) return@execute
            if (previousState != ProxyContract.STATE_STOPPED) {
                if (!stopNative()) {
                    publishIfCurrent(currentGeneration, ProxyContract.STATE_FAILED, 0, "Previous TON proxy did not stop cleanly")
                    terminateProxyProcess()
                    return@execute
                }
            }
            if (!isCurrent(currentGeneration)) return@execute

            val config = runCatching {
                resources.openRawResource(R.raw.ton_global_config).bufferedReader().use { it.readText() }
            }.getOrElse { error ->
                publishIfCurrent(currentGeneration, ProxyContract.STATE_FAILED, 0, "Bundled TON config unavailable: ${error.message}")
                return@execute
            }
            if (!isCurrent(currentGeneration)) return@execute

            val result = startNativeIfCurrent(config, mode, currentGeneration) ?: return@execute

            if (!isCurrent(currentGeneration)) {
                if (!stopNative()) terminateProxyProcess()
                return@execute
            }
            if (result.startsWith("OK:")) {
                val resolvedPort = result.substringAfter(':').toIntOrNull()
                if (resolvedPort != null && resolvedPort in 1..65535) {
                    publishIfCurrent(
                        currentGeneration,
                        ProxyContract.STATE_READY,
                        resolvedPort,
                        if (mode == ProxyContract.MODE_TUNNEL_2_HOP) "2-hop tunnel" else "Direct TON",
                    )
                    startHealthMonitor(currentGeneration, mode)
                    return@execute
                }
            }
            val cleanupSucceeded = stopNative()
            publishIfCurrent(currentGeneration, ProxyContract.STATE_FAILED, 0, result.removePrefix("ERR:").take(240))
            if (!cleanupSucceeded) terminateProxyProcess()
        }
    }

    private fun stop() {
        val stopGeneration = nextGeneration()
        cancelHealthMonitor()
        if (state == ProxyContract.STATE_STOPPED) {
            publishIfCurrent(stopGeneration, ProxyContract.STATE_STOPPED, 0, "Stopped")
            return
        }
        Thread({
            if (stopNative()) {
                publishIfCurrent(stopGeneration, ProxyContract.STATE_STOPPED, 0, "Stopped")
            } else {
                publishIfCurrent(stopGeneration, ProxyContract.STATE_FAILED, 0, "TON proxy did not stop cleanly")
                terminateProxyProcess()
            }
        }, "tonnet-proxy-stop").apply { isDaemon = true }.start()
    }

    private fun startHealthMonitor(expectedGeneration: Long, mode: Int) {
        val supervisor = ProxyHealthSupervisor()
        synchronized(monitorLock) {
            monitorFuture?.cancel(false)
            monitorFuture = monitorExecutor.scheduleWithFixedDelay(
                {
                    if (!isCurrent(expectedGeneration) ||
                        (state != ProxyContract.STATE_READY && state != ProxyContract.STATE_RECOVERING)
                    ) {
                        return@scheduleWithFixedDelay
                    }
                    val nativeState = runCatching { NativeTonProxy.nativeGetSessionState() }
                        .getOrElse {
                            failHealthMonitor(expectedGeneration, "TON proxy health check failed")
                            return@scheduleWithFixedDelay
                        }
                    when (val action = supervisor.observe(nativeState, SystemClock.elapsedRealtime())) {
                        ProxyHealthAction.Ready -> {
                            if (state == ProxyContract.STATE_RECOVERING) {
                                publishIfCurrent(
                                    expectedGeneration,
                                    ProxyContract.STATE_READY,
                                    port,
                                    if (mode == ProxyContract.MODE_TUNNEL_2_HOP) "2-hop tunnel" else "Direct TON",
                                )
                            }
                        }

                        ProxyHealthAction.Recovering -> {
                            if (mode != ProxyContract.MODE_TUNNEL_2_HOP) {
                                failHealthMonitor(expectedGeneration, "Direct TON proxy entered an invalid state")
                                return@scheduleWithFixedDelay
                            }
                            if (state == ProxyContract.STATE_READY) {
                                publishIfCurrent(
                                    expectedGeneration,
                                    ProxyContract.STATE_RECOVERING,
                                    port,
                                    "Recovering TON tunnel",
                                )
                            }
                        }

                        is ProxyHealthAction.Failed -> {
                            failHealthMonitor(expectedGeneration, action.reason)
                        }
                    }
                },
                HEALTH_CHECK_INTERVAL_MS,
                HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun failHealthMonitor(expectedGeneration: Long, reason: String) {
        val failureGeneration = synchronized(stateLock) {
            if (generation.get() != expectedGeneration ||
                (state != ProxyContract.STATE_READY && state != ProxyContract.STATE_RECOVERING)
            ) {
                return
            }
            generation.incrementAndGet()
        }
        cancelHealthMonitor()
        publishIfCurrent(failureGeneration, ProxyContract.STATE_FAILED, 0, reason)
        if (!stopNative()) terminateProxyProcess()
    }

    private fun cancelHealthMonitor() {
        synchronized(monitorLock) {
            monitorFuture?.cancel(false)
            monitorFuture = null
        }
    }

    private fun stopNative(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + NATIVE_STOP_COORDINATION_TIMEOUT_MS
        while (true) {
            val result = runCatching {
                if (NativeTonProxy.isAvailable) NativeTonProxy.nativeStopSession() else "OK"
            }.getOrElse { "ERR:${it.javaClass.simpleName}" }
            if (result != "OK") return false

            synchronized(nativeStartLock) {
                if (!nativeStartInFlight) return true
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) return false
                runCatching { nativeStartLock.wait(remaining.coerceAtMost(NATIVE_START_POLL_MS)) }
            }
        }
    }

    private fun startNativeIfCurrent(config: String, mode: Int, expectedGeneration: Long): String? {
        synchronized(nativeStartLock) {
            if (!isCurrent(expectedGeneration)) return null
            nativeStartInFlight = true
        }
        return try {
            runCatching { NativeTonProxy.nativeStartSession(config, mode) }
                .getOrElse { error -> "ERR:${error.javaClass.simpleName}" }
        } finally {
            synchronized(nativeStartLock) {
                nativeStartInFlight = false
                nativeStartLock.notifyAll()
            }
        }
    }

    private fun terminateProxyProcess(): Nothing {
        Process.killProcess(Process.myPid())
        throw IllegalStateException("Dedicated TON proxy process did not terminate")
    }

    private fun nextGeneration(): Long = synchronized(stateLock) { generation.incrementAndGet() }
    private fun isCurrent(expectedGeneration: Long): Boolean = generation.get() == expectedGeneration

    private fun publishIfCurrent(expectedGeneration: Long, newState: Int, newPort: Int, newMessage: String) {
        synchronized(stateLock) {
            if (generation.get() == expectedGeneration) publishLocked(newState, newPort, newMessage)
        }
    }

    private fun publishLocked(newState: Int, newPort: Int, newMessage: String) {
        state = newState
        port = newPort
        message = newMessage
        val count = callbacks.beginBroadcast()
        try {
            for (index in 0 until count) {
                runCatching { callbacks.getBroadcastItem(index).onStateChanged(newState, newPort, newMessage) }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private companion object {
        const val HEALTH_CHECK_INTERVAL_MS = 500L
        const val NATIVE_START_POLL_MS = 25L
        const val NATIVE_STOP_COORDINATION_TIMEOUT_MS = 8_000L
    }
}

package com.tonnet.proxy

object ProxyContract {
    const val MODE_DIRECT = 0
    const val MODE_TUNNEL_2_HOP = 1

    const val STATE_STOPPED = 0
    const val STATE_STARTING = 1
    const val STATE_READY = 2
    const val STATE_FAILED = 3
    const val STATE_RECOVERING = 4
}

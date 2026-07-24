package com.tonnet.browser.core

enum class NetworkMode(val wireValue: Int) {
    DIRECT(0),
    TUNNEL_2_HOP(1);

    companion object {
        fun fromWire(value: Int): NetworkMode = entries.firstOrNull { it.wireValue == value } ?: DIRECT
    }
}

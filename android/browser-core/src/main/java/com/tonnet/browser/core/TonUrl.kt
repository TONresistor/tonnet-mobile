package com.tonnet.browser.core

import java.net.URI

data class TonUrl(
    val value: String,
    val host: String,
) {
    val uri: URI = URI(value)
}

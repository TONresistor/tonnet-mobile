package com.tonnet.browser.core

data class BrowserTab(
    val id: Long,
    var title: String = "New tab",
    var currentUrl: String? = null,
    var lastActivatedAt: Long = System.nanoTime(),
)

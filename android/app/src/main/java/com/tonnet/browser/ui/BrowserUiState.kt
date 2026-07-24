package com.tonnet.browser.ui

import com.tonnet.browser.core.TonUrlPolicy

data class BrowserChromeState(
    val address: String?,
    val addressHasText: Boolean,
    val addressFocused: Boolean,
    val isBookmarked: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val proxyReady: Boolean,
    val tabCount: Int,
    val overlay: BrowserOverlay,
) {
    val hasPage: Boolean
        get() = !address.isNullOrBlank()
    val showClearAddress: Boolean
        get() = addressFocused && addressHasText
    val showBookmarkToggle: Boolean
        get() = !addressFocused && hasPage
    val canReload: Boolean
        get() = proxyReady && hasPage && overlay == BrowserOverlay.NONE
}

data class BrowserChromeVisibility(
    val showTop: Boolean,
    val showBottom: Boolean,
    val showSettings: Boolean,
)

enum class BrowserOverlay {
    NONE,
    TABS,
    SETTINGS,
}

object StartupNavigation {
    fun firstAddress(intentAddress: String?, homepage: String?): String? =
        intentAddress ?: homepage
}

object BrowserChromePolicy {
    fun visibility(overlay: BrowserOverlay, imeVisible: Boolean): BrowserChromeVisibility {
        val settingsVisible = overlay == BrowserOverlay.SETTINGS
        return BrowserChromeVisibility(
            showTop = !settingsVisible,
            showBottom = !imeVisible,
            showSettings = settingsVisible,
        )
    }
}

object TabBarBlurPolicy {
    const val MIN_SDK = 31
    const val SCALE_FACTOR = 1f
    const val RADIUS_PX = 25f

    fun isSupported(sdkInt: Int): Boolean = sdkInt >= MIN_SDK
}

object BrowserSheetSizing {
    fun listHeight(
        itemCount: Int,
        rowHeight: Int,
        availableHeight: Int,
        fixedHeight: Int,
    ): Int {
        if (itemCount <= 0 || rowHeight <= 0) return 0
        val maximum = (availableHeight - fixedHeight).coerceAtLeast(rowHeight)
        return (itemCount * rowHeight).coerceAtMost(maximum)
    }
}

object TabClosePolicy {
    fun nextActiveId(
        tabIds: List<Long>,
        closedId: Long,
        activeId: Long?,
    ): Long? {
        if (closedId != activeId) return activeId
        val closedIndex = tabIds.indexOf(closedId)
        if (closedIndex < 0) return activeId
        val remaining = tabIds.filterNot { it == closedId }
        if (remaining.isEmpty()) return null
        return remaining[closedIndex.coerceAtMost(remaining.lastIndex)]
    }
}

enum class BrowserSwipeAction {
    NONE,
    BACK,
    FORWARD,
}

object BrowserSwipePolicy {
    fun action(
        deltaX: Float,
        deltaY: Float,
        threshold: Float,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ): BrowserSwipeAction {
        if (kotlin.math.abs(deltaX) < threshold || kotlin.math.abs(deltaX) <= kotlin.math.abs(deltaY)) {
            return BrowserSwipeAction.NONE
        }
        return when {
            deltaX > 0f && canGoBack -> BrowserSwipeAction.BACK
            deltaX < 0f && canGoForward -> BrowserSwipeAction.FORWARD
            else -> BrowserSwipeAction.NONE
        }
    }
}

object AddressPresentation {
    fun shouldShowTonSiteBadge(canonicalUrl: String?, focused: Boolean): Boolean =
        !focused && TonUrlPolicy.isAllowed(canonicalUrl)

    fun display(canonicalUrl: String?, focused: Boolean): String {
        if (canonicalUrl.isNullOrBlank()) return ""
        if (focused) {
            return if (canonicalUrl.startsWith("http://")) {
                "tonsite://${canonicalUrl.substring("http://".length)}"
            } else {
                canonicalUrl
            }
        }
        val compact = canonicalUrl.removePrefix("http://")
        return if ('?' !in compact && '#' !in compact && compact.count { it == '/' } == 1) {
            compact.removeSuffix("/")
        } else {
            compact
        }
    }
}

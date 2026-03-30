/**
 * Browser page - displays external URLs (.ton sites).
 * Uses iframe to load content through the TON proxy.
 * Supports swipe gestures for back/forward navigation.
 */

import { useState, useEffect, useRef } from 'react'
import { Globe, AlertCircle, Loader2, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react'
import { useSettingsStore } from '@/stores/settings'
import { useShallow } from 'zustand/react/shallow'
import { useProxyStore } from '@/stores/proxy'
import { usePreferences } from '@/stores/preferences'
import { Button } from '@/components/ui/button'

const SWIPE_THRESHOLD = 80

export function BrowserPage() {
  const { currentUrl, reloadCounter, canGoBack, canGoForward, goBack, goForward } =
    useSettingsStore(
      useShallow((s) => ({
        currentUrl: s.currentUrl,
        reloadCounter: s.reloadCounter,
        canGoBack: s.canGoBack,
        canGoForward: s.canGoForward,
        goBack: s.goBack,
        goForward: s.goForward,
      }))
    )
  const proxyStatus = useProxyStore((state) => state.status)
  const isProxyConnected = proxyStatus === 'connected'
  const { javaScriptEnabled } = usePreferences()

  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [iframeKey, setIframeKey] = useState(0)

  // Swipe navigation
  const [swipeX, setSwipeX] = useState(0)
  const startX = useRef(0)
  const isSwiping = useRef(false)

  // React to reload requests from header
  useEffect(() => {
    if (reloadCounter > 0) {
      setIsLoading(true)
      setError(null)
      setIframeKey((k) => k + 1)
    }
  }, [reloadCounter])

  // The WebView proxy is configured to route through localhost:port
  // So we just load the URL directly - the proxy handles the routing
  const targetUrl = isProxyConnected ? currentUrl : null

  const handleRefresh = () => {
    setIsLoading(true)
    setError(null)
    setIframeKey((k) => k + 1)
  }

  const handleIframeLoad = () => {
    setIsLoading(false)
  }

  const handleIframeError = () => {
    setIsLoading(false)
    setError('Failed to load page')
  }

  // Reset state when URL changes
  useEffect(() => {
    setIsLoading(true)
    setError(null)
  }, [currentUrl])

  // Swipe handlers
  const handleTouchStart = (e: React.TouchEvent) => {
    startX.current = e.touches[0].clientX
    isSwiping.current = false
  }

  const handleTouchMove = (e: React.TouchEvent) => {
    const deltaX = e.touches[0].clientX - startX.current
    if (Math.abs(deltaX) > 20) {
      isSwiping.current = true
      // Limit swipe distance and check if action is possible
      if (deltaX > 0 && canGoBack) {
        setSwipeX(Math.min(deltaX, 150))
      } else if (deltaX < 0 && canGoForward) {
        setSwipeX(Math.max(deltaX, -150))
      }
    }
  }

  const handleTouchEnd = () => {
    // Let Android system gesture handle edge swipes
    const isEdgeSwipe = startX.current < 40 || startX.current > window.innerWidth - 40
    if (isEdgeSwipe) {
      setSwipeX(0)
      return
    }

    if (swipeX > SWIPE_THRESHOLD && canGoBack) {
      goBack()
    } else if (swipeX < -SWIPE_THRESHOLD && canGoForward) {
      goForward()
    }
    setSwipeX(0)
  }

  // Display URL nicely
  const displayUrl = currentUrl.replace(/^https?:\/\//, '')

  // If proxy is not connected, show error
  if (!isProxyConnected) {
    return (
      <div className="flex flex-col items-center justify-center h-full bg-background-secondary p-8">
        <AlertCircle className="h-16 w-16 text-destructive mb-4" />
        <h2 className="text-xl font-semibold text-foreground mb-2">Proxy Not Connected</h2>
        <p className="text-muted-foreground text-center mb-4">
          Connect to the TON network to browse .ton sites.
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Progress Bar */}
      {isLoading && (
        <div className="absolute top-0 left-0 right-0 h-[3px] bg-transparent z-50 overflow-hidden">
          <div className="h-full bg-primary progress-bar-animation" />
        </div>
      )}

      {/* URL Bar Info */}
      <div className="flex items-center gap-2 px-4 py-2 bg-background-secondary border-b border-border">
        <Globe className="h-4 w-4 text-primary flex-shrink-0" />
        <span className="text-sm text-foreground truncate flex-1">{displayUrl}</span>
        <Button
          variant="ghost"
          size="sm"
          onClick={handleRefresh}
          className="p-1 h-8 w-8"
        >
          <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
        </Button>
      </div>

      {/* Content Area with Swipe */}
      <div
        className="flex-1 relative"
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
      >
        {/* Swipe Indicators */}
        {swipeX > 20 && (
          <div
            className="absolute left-0 top-1/2 -translate-y-1/2 z-20 bg-white/20 rounded-r-full p-3"
            style={{ opacity: Math.min(swipeX / SWIPE_THRESHOLD, 1) }}
          >
            <ChevronLeft className="h-6 w-6 text-white" />
          </div>
        )}
        {swipeX < -20 && (
          <div
            className="absolute right-0 top-1/2 -translate-y-1/2 z-20 bg-white/20 rounded-l-full p-3"
            style={{ opacity: Math.min(Math.abs(swipeX) / SWIPE_THRESHOLD, 1) }}
          >
            <ChevronRight className="h-6 w-6 text-white" />
          </div>
        )}
        {/* Loading Overlay */}
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center bg-background z-10">
            <div className="flex flex-col items-center gap-4">
              <Loader2 className="h-10 w-10 text-primary animate-spin" />
              <p className="text-muted-foreground">Loading {displayUrl}...</p>
            </div>
          </div>
        )}

        {/* Error State */}
        {error && !isLoading && (
          <div className="absolute inset-0 flex items-center justify-center bg-background z-10">
            <div className="flex flex-col items-center gap-4 p-8 text-center">
              <AlertCircle className="h-12 w-12 text-destructive" />
              <h3 className="text-lg font-medium text-foreground">Unable to Load Page</h3>
              <p className="text-muted-foreground">{error}</p>
              <Button onClick={handleRefresh} className="mt-2">
                Try Again
              </Button>
            </div>
          </div>
        )}

        {/* Iframe for actual content */}
        {targetUrl && (
          <iframe
            key={iframeKey}
            src={targetUrl}
            className="w-full h-full border-0"
            onLoad={handleIframeLoad}
            onError={handleIframeError}
            sandbox={`${javaScriptEnabled ? 'allow-scripts ' : ''}allow-same-origin allow-forms allow-popups`}
            referrerPolicy="no-referrer"
            allow=""
            title={displayUrl}
          />
        )}

        {/* Placeholder when no URL */}
        {!targetUrl && !error && (
          <div className="flex flex-col items-center justify-center h-full p-8">
            <Globe className="h-16 w-16 text-muted-foreground mb-4 opacity-50" />
            <p className="text-muted-foreground text-center">
              Enter a .ton address to browse
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

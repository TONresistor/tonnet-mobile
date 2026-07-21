/**
 * App - Main application component.
 * Handles layout selection (mobile/desktop) and page routing.
 */

import { App as CapacitorApp } from '@capacitor/app'
import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { useShallow } from 'zustand/react/shallow'
import { BookmarksSheet } from '@/components/mobile/BookmarksSheet'
import { MobileHeader } from '@/components/mobile/MobileHeader'
import { closeTopModal } from '@/components/mobile/modalStack'
import { TabsSheet } from '@/components/mobile/TabsSheet'
import { TelegramTabBar } from '@/components/mobile/TelegramTabBar'
import { LandingPage } from '@/components/pages/LandingPage'
import { useIsMobile } from '@/hooks/useIsMobile'
import { useProxy } from '@/hooks/useProxy'
import { createLogger } from '@/lib/logger'
import { normalizeUrl } from '@/lib/url'
import { platform } from '@/platform'
import { INTERNAL_ROUTES } from '@/shared/constants'
import { useNavigationStore } from '@/stores/navigation'
import { usePreferences, usePreferencesStore } from '@/stores/preferences'
import { useProxyStore } from '@/stores/proxy'

const StartPage = lazy(() =>
  import('@/components/pages/StartPage').then((m) => ({ default: m.StartPage })),
)
const SettingsPage = lazy(() =>
  import('@/components/pages/SettingsPage').then((m) => ({ default: m.SettingsPage })),
)
const BrowserPage = lazy(() =>
  import('@/components/pages/BrowserPage').then((m) => ({ default: m.BrowserPage })),
)

const logger = createLogger('App')

function App() {
  const isMobile = useIsMobile()
  const { autoConnect, clearOnExit, thirdPartyCookies } = usePreferences()
  const isLoaded = usePreferencesStore((state) => state.isLoaded)
  const proxyStatus = useProxyStore((state) => state.status)
  const isProxyConnected = proxyStatus === 'connected'
  const { connect } = useProxy()
  const autoConnectAttempted = useRef(false)
  const appliedCookiePolicy = useRef<boolean | null>(null)
  const cookiePolicyRequest = useRef(0)

  // Tabs sheet state
  const [tabsSheetOpen, setTabsSheetOpen] = useState(false)
  // Bookmarks sheet state
  const [showBookmarks, setShowBookmarks] = useState(false)

  const {
    activeView,
    currentUrl,
    canGoBack,
    canGoForward,
    tabs,
    activeTabId,
    navigate,
    goBack,
    goForward,
    reload,
    createTab,
    closeTab,
    switchTab,
  } = useNavigationStore(
    useShallow((s) => ({
      activeView: s.activeView,
      currentUrl: s.currentUrl,
      canGoBack: s.canGoBack,
      canGoForward: s.canGoForward,
      tabs: s.tabs,
      activeTabId: s.activeTabId,
      navigate: s.navigate,
      goBack: s.goBack,
      goForward: s.goForward,
      reload: s.reload,
      createTab: s.createTab,
      closeTab: s.closeTab,
      switchTab: s.switchTab,
    })),
  )

  // Keep the native cookie policy aligned with the rehydrated preference.
  useEffect(() => {
    if (!isLoaded || appliedCookiePolicy.current === thirdPartyCookies) return

    const requestId = cookiePolicyRequest.current + 1
    cookiePolicyRequest.current = requestId
    platform
      .setThirdPartyCookies(thirdPartyCookies)
      .then(() => {
        if (cookiePolicyRequest.current === requestId) {
          appliedCookiePolicy.current = thirdPartyCookies
        }
      })
      .catch((error) => {
        if (cookiePolicyRequest.current !== requestId) return

        logger.error('Failed to apply the cookie policy', error)
        const lastAppliedPolicy = appliedCookiePolicy.current ?? false
        if (lastAppliedPolicy !== thirdPartyCookies) {
          usePreferencesStore.getState().setPreference('thirdPartyCookies', lastAppliedPolicy)
        }
      })
  }, [isLoaded, thirdPartyCookies])

  // Auto-connect on startup if enabled
  useEffect(() => {
    if (
      isLoaded &&
      autoConnect &&
      proxyStatus === 'disconnected' &&
      !autoConnectAttempted.current
    ) {
      autoConnectAttempted.current = true
      void connect().catch((error) => {
        logger.warn('Automatic proxy connection failed', error)
      })
    }
  }, [isLoaded, autoConnect, proxyStatus, connect])

  // Handle Android back button/gesture
  useEffect(() => {
    if (!platform.isAndroid) return

    let backSub: { remove: () => Promise<void> } | null = null
    let disposed = false

    CapacitorApp.addListener('backButton', () => {
      // Overlays own the first Back press, including sheets rendered by child pages.
      if (closeTopModal()) return

      const proxy = useProxyStore.getState()
      const navigation = useNavigationStore.getState()

      // Block back entirely while connecting
      if (proxy.status === 'connecting') return

      // If we can go back in navigation history, do that
      if (navigation.canGoBack) {
        navigation.goBack()
        return
      }

      // Otherwise minimize the app (don't close it)
      CapacitorApp.minimizeApp()
    }).then(async (sub) => {
      if (disposed) await sub.remove()
      else backSub = sub
    })

    return () => {
      disposed = true
      void backSub?.remove()
    }
  }, [])

  // Clear browsing data when app goes to background (if clearOnExit is enabled)
  const clearOnExitRef = useRef(clearOnExit)
  clearOnExitRef.current = clearOnExit

  useEffect(() => {
    if (!platform.isAndroid) return

    let subscription: { remove: () => Promise<void> } | null = null
    let disposed = false

    CapacitorApp.addListener('appStateChange', async ({ isActive }) => {
      if (!isActive && clearOnExitRef.current) {
        try {
          await platform.clearBrowsingData({
            cache: true,
            localStorage: true,
            sessionStorage: true,
          })
          useNavigationStore.getState().resetSession()
        } catch (error) {
          logger.error('Failed to clear browsing data on exit', error)
        }
      }
    }).then(async (sub) => {
      if (disposed) await sub.remove()
      else subscription = sub
    })

    return () => {
      disposed = true
      void subscription?.remove()
    }
  }, [])

  // Handle URL submission from header
  const handleUrlSubmit = (url: string) => {
    const finalUrl = normalizeUrl(url)
    if (finalUrl) {
      navigate(finalUrl)
    }
  }

  // Render the active page based on current view
  // Always show LandingPage if proxy is not connected
  const renderPage = () => {
    if (!isProxyConnected) {
      return <LandingPage />
    }

    switch (activeView) {
      case 'landing':
        return <LandingPage />
      case 'settings':
        return (
          <Suspense fallback={null}>
            <SettingsPage />
          </Suspense>
        )
      case 'web':
        return (
          <Suspense fallback={null}>
            <BrowserPage />
          </Suspense>
        )
      case 'start':
        return (
          <Suspense fallback={null}>
            <StartPage />
          </Suspense>
        )
    }
  }

  // Determine if we should show header/nav (only when connected and not on landing)
  const showUI = isProxyConnected && activeView !== 'landing'
  // Don't show address bar header on settings page (it has its own header)
  const showHeader = showUI && activeView !== 'settings'

  // Mobile layout with header and Opera-style nav bar
  if (isMobile) {
    return (
      <div className="h-screen w-screen flex flex-col bg-background overflow-hidden">
        {/* Mobile Header - only show when connected and not on settings */}
        {showHeader && (
          <MobileHeader
            url={currentUrl}
            onUrlSubmit={handleUrlSubmit}
            onRefresh={reload}
            onOpenBookmarks={() => setShowBookmarks(true)}
          />
        )}

        {/* Main content area */}
        <main className={`flex-1 overflow-auto ${showHeader ? 'pt-14' : ''}`}>{renderPage()}</main>

        {/* Telegram-style tab bar - only show when connected */}
        {showUI && (
          <TelegramTabBar
            canGoBack={canGoBack}
            canGoForward={canGoForward}
            tabCount={tabs.length}
            activeView={activeView}
            onBack={goBack}
            onForward={goForward}
            onNewTab={() => createTab()}
            onOpenTabs={() => setTabsSheetOpen(true)}
            onSettings={() => navigate(INTERNAL_ROUTES.settings)}
          />
        )}

        {/* Tabs Sheet */}
        <TabsSheet
          open={tabsSheetOpen}
          onClose={() => setTabsSheetOpen(false)}
          tabs={tabs}
          activeTabId={activeTabId}
          onSwitchTab={switchTab}
          onCloseTab={closeTab}
        />

        {/* Bookmarks Sheet */}
        <BookmarksSheet
          open={showBookmarks}
          onClose={() => setShowBookmarks(false)}
          onNavigate={handleUrlSubmit}
        />
      </div>
    )
  }

  // Desktop layout (simplified for mobile-first project)
  return (
    <div className="h-screen w-screen flex flex-col bg-background overflow-hidden">
      <main className="flex-1 overflow-auto">{renderPage()}</main>
    </div>
  )
}

export default App

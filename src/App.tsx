/**
 * App - Main application component.
 * Handles layout selection (mobile/desktop) and page routing.
 */

import { useEffect, useRef, useState } from 'react'
import { App as CapacitorApp } from '@capacitor/app'
import { useIsMobile } from '@/hooks/useIsMobile'
import { useSettingsStore } from '@/stores/settings'
import { usePreferences, usePreferencesStore } from '@/stores/preferences'
import { useProxyStore } from '@/stores/proxy'
import { useProxy } from '@/hooks/useProxy'
import { platform } from '@/platform'
import { normalizeUrl } from '@/lib/url'

// Pages
import { LandingPage } from '@/components/pages/LandingPage'
import { StartPage } from '@/components/pages/StartPage'
import { SettingsPage } from '@/components/pages/SettingsPage'
import { BrowserPage } from '@/components/pages/BrowserPage'

// Mobile components
import { MobileHeader } from '@/components/mobile/MobileHeader'
import { OperaNavBar } from '@/components/mobile/OperaNavBar'
import { TabsSheet } from '@/components/mobile/TabsSheet'
import { BookmarksSheet } from '@/components/mobile/BookmarksSheet'

function App() {
  const isMobile = useIsMobile()
  const { theme, autoConnect, clearOnExit } = usePreferences()
  const isLoaded = usePreferencesStore((state) => state.isLoaded)
  const proxyStatus = useProxyStore((state) => state.status)
  const isProxyConnected = proxyStatus === 'connected'
  const { connect } = useProxy()
  const autoConnectAttempted = useRef(false)

  // Tabs sheet state
  const [tabsSheetOpen, setTabsSheetOpen] = useState(false)
  // Bookmarks sheet state
  const [showBookmarks, setShowBookmarks] = useState(false)

  const activeView = useSettingsStore((s) => s.activeView)
  const currentUrl = useSettingsStore((s) => s.currentUrl)
  const canGoBack = useSettingsStore((s) => s.canGoBack)
  const canGoForward = useSettingsStore((s) => s.canGoForward)
  const tabs = useSettingsStore((s) => s.tabs)
  const activeTabId = useSettingsStore((s) => s.activeTabId)
  const navigate = useSettingsStore((s) => s.navigate)
  const goBack = useSettingsStore((s) => s.goBack)
  const goForward = useSettingsStore((s) => s.goForward)
  const reload = useSettingsStore((s) => s.reload)
  const createTab = useSettingsStore((s) => s.createTab)
  const closeTab = useSettingsStore((s) => s.closeTab)
  const switchTab = useSettingsStore((s) => s.switchTab)

  // Apply theme to document
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
  }, [theme])

  // Auto-connect on startup if enabled
  useEffect(() => {
    if (
      isLoaded &&
      autoConnect &&
      proxyStatus === 'disconnected' &&
      !autoConnectAttempted.current
    ) {
      autoConnectAttempted.current = true
      connect()
    }
  }, [isLoaded, autoConnect, proxyStatus, connect])

  // Clear browsing data when app goes to background (if clearOnExit is enabled)
  const clearOnExitRef = useRef(clearOnExit)
  clearOnExitRef.current = clearOnExit

  useEffect(() => {
    if (!platform.isAndroid) return

    let subscription: { remove: () => void } | null = null

    CapacitorApp.addListener('appStateChange', async ({ isActive }) => {
      if (!isActive && clearOnExitRef.current) {
        try {
          await platform.clearBrowsingData({
            cache: true,
            localStorage: true,
            sessionStorage: true,
          })
        } catch (err) {
          console.error('[App] Failed to clear browsing data:', err)
        }
      }
    }).then((sub) => {
      subscription = sub
    })

    return () => {
      subscription?.remove()
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
        return <SettingsPage />
      case 'web':
        return <BrowserPage />
      case 'start':
      default:
        return <StartPage />
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
            onUrlChange={() => {}}
            onUrlSubmit={handleUrlSubmit}
            onRefresh={reload}
            onOpenBookmarks={() => setShowBookmarks(true)}
          />
        )}

        {/* Main content area */}
        <main className={`flex-1 overflow-auto ${showHeader ? 'pt-14' : ''} ${showUI ? 'pb-14' : ''}`}>
          {renderPage()}
        </main>

        {/* Opera-style bottom navigation - only show when connected */}
        {showUI && (
          <OperaNavBar
            canGoBack={canGoBack}
            canGoForward={canGoForward}
            tabCount={tabs.length}
            onBack={goBack}
            onForward={goForward}
            onNewTab={() => createTab()}
            onOpenTabs={() => setTabsSheetOpen(true)}
            onSettings={() => navigate('ton://settings')}
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
      <main className="flex-1 overflow-auto">
        {renderPage()}
      </main>
    </div>
  )
}

export default App

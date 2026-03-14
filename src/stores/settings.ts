/**
 * Settings store - Navigation and UI state management.
 * Handles multi-tab navigation, active views, and UI toggles.
 * Adapted for mobile: uses localStorage for persistence.
 */

import { create } from 'zustand'

// View types: 'start' = search page, 'web' = external URL, 'settings', 'landing'
type ActiveView = 'start' | 'web' | 'settings' | 'landing'

// Tab interface
interface Tab {
  id: string
  url: string
  title: string
  history: string[]
  historyIndex: number
}

interface SettingsState {
  // Multi-tab state
  tabs: Tab[]
  activeTabId: string

  // Current tab computed values (derived from active tab)
  currentUrl: string
  canGoBack: boolean
  canGoForward: boolean

  // UI state
  activeView: ActiveView
  reloadCounter: number

  // Actions (operate on active tab)
  navigate: (url: string) => void
  goBack: () => void
  goForward: () => void
  reload: () => void

  // Tab actions
  createTab: (url?: string) => void
  closeTab: (tabId: string) => void
  switchTab: (tabId: string) => void
  updateTabTitle: (tabId: string, title: string) => void
}

// Helper to determine view from URL
function getViewFromUrl(url: string): ActiveView {
  if (url === 'ton://landing' || url === '') return 'landing'
  if (url === 'ton://start') return 'start'
  if (url === 'ton://settings') return 'settings'
  if (url.startsWith('ton://')) return 'start' // Unknown ton:// URLs go to start
  return 'web' // External URLs (http://, https://)
}

// Helper to generate unique tab ID
function generateTabId(): string {
  return `tab_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`
}

// Helper to get title from URL
function getTitleFromUrl(url: string): string {
  if (url === 'ton://landing') return 'Welcome'
  if (url === 'ton://start') return 'Start'
  if (url === 'ton://settings') return 'Settings'
  if (url.startsWith('http://') || url.startsWith('https://')) {
    try {
      const urlObj = new URL(url)
      return urlObj.hostname
    } catch {
      return url
    }
  }
  return url
}

// Create initial tab
function createInitialTab(): Tab {
  return {
    id: generateTabId(),
    url: 'ton://landing',
    title: 'Welcome',
    history: ['ton://landing'],
    historyIndex: 0,
  }
}

export const useSettingsStore = create<SettingsState>()((set, get) => {
  const initialTab = createInitialTab()

  return {
    // Initial state with one tab
    tabs: [initialTab],
    activeTabId: initialTab.id,
    currentUrl: 'ton://landing',
    canGoBack: false,
    canGoForward: false,
    activeView: 'landing',
    reloadCounter: 0,

    navigate: (url) => {
          const state = get()
          const activeTab = state.tabs.find((t) => t.id === state.activeTabId)
          if (!activeTab) return

          
          // Don't navigate to the same URL
          if (url === activeTab.url) {
                        return
          }

          const activeView = getViewFromUrl(url)
          const title = getTitleFromUrl(url)
          
          // Update tab history
          const MAX_HISTORY = 100
          const fullHistory = [...activeTab.history.slice(0, activeTab.historyIndex + 1), url]
          // Trim oldest entries if history exceeds limit
          const newHistory = fullHistory.length > MAX_HISTORY
            ? fullHistory.slice(fullHistory.length - MAX_HISTORY)
            : fullHistory
          const newHistoryIndex = newHistory.length - 1

          // Update the tab
          const updatedTabs = state.tabs.map((t) =>
            t.id === state.activeTabId
              ? {
                  ...t,
                  url,
                  title,
                  history: newHistory,
                  historyIndex: newHistoryIndex,
                }
              : t
          )

          set({
            tabs: updatedTabs,
            currentUrl: url,
            activeView,
            canGoBack: newHistoryIndex > 0,
            canGoForward: false,
          })
        },

        goBack: () => {
          const state = get()
          const activeTab = state.tabs.find((t) => t.id === state.activeTabId)
          if (!activeTab || activeTab.historyIndex <= 0) return

          const newIndex = activeTab.historyIndex - 1
          const prevUrl = activeTab.history[newIndex]
          
          const updatedTabs = state.tabs.map((t) =>
            t.id === state.activeTabId
              ? {
                  ...t,
                  url: prevUrl,
                  title: getTitleFromUrl(prevUrl),
                  historyIndex: newIndex,
                }
              : t
          )

          set({
            tabs: updatedTabs,
            currentUrl: prevUrl,
            activeView: getViewFromUrl(prevUrl),
            canGoBack: newIndex > 0,
            canGoForward: true,
          })
        },

        goForward: () => {
          const state = get()
          const activeTab = state.tabs.find((t) => t.id === state.activeTabId)
          if (!activeTab || activeTab.historyIndex >= activeTab.history.length - 1) return

          const newIndex = activeTab.historyIndex + 1
          const nextUrl = activeTab.history[newIndex]
          
          const updatedTabs = state.tabs.map((t) =>
            t.id === state.activeTabId
              ? {
                  ...t,
                  url: nextUrl,
                  title: getTitleFromUrl(nextUrl),
                  historyIndex: newIndex,
                }
              : t
          )

          set({
            tabs: updatedTabs,
            currentUrl: nextUrl,
            activeView: getViewFromUrl(nextUrl),
            canGoBack: true,
            canGoForward: newIndex < activeTab.history.length - 1,
          })
        },

        reload: () => {
          const state = get()
                    // Increment counter to trigger re-render in BrowserPage
          set({ reloadCounter: state.reloadCounter + 1 })
        },

        // Tab actions
        createTab: (url = 'ton://start') => {
          const state = get()
          const newTab: Tab = {
            id: generateTabId(),
            url,
            title: getTitleFromUrl(url),
            history: [url],
            historyIndex: 0,
          }

          
          set({
            tabs: [...state.tabs, newTab],
            activeTabId: newTab.id,
            currentUrl: url,
            activeView: getViewFromUrl(url),
            canGoBack: false,
            canGoForward: false,
          })
        },

        closeTab: (tabId) => {
          const state = get()
          if (state.tabs.length <= 1) {
            // Don't close the last tab, reset it instead
            const resetTab = createInitialTab()
            set({
              tabs: [resetTab],
              activeTabId: resetTab.id,
              currentUrl: 'ton://landing',
              activeView: 'landing',
              canGoBack: false,
              canGoForward: false,
            })
            return
          }

          const tabIndex = state.tabs.findIndex((t) => t.id === tabId)
          const newTabs = state.tabs.filter((t) => t.id !== tabId)

          // If closing active tab, switch to adjacent tab
          let newActiveTabId = state.activeTabId
          if (tabId === state.activeTabId) {
            // Prefer previous tab, or next if closing first
            const newIndex = Math.min(tabIndex, newTabs.length - 1)
            newActiveTabId = newTabs[newIndex].id
          }

          const newActiveTab = newTabs.find((t) => t.id === newActiveTabId)!

          
          set({
            tabs: newTabs,
            activeTabId: newActiveTabId,
            currentUrl: newActiveTab.url,
            activeView: getViewFromUrl(newActiveTab.url),
            canGoBack: newActiveTab.historyIndex > 0,
            canGoForward: newActiveTab.historyIndex < newActiveTab.history.length - 1,
          })
        },

        switchTab: (tabId) => {
          const state = get()
          const tab = state.tabs.find((t) => t.id === tabId)
          if (!tab || tabId === state.activeTabId) return

          
          set({
            activeTabId: tabId,
            currentUrl: tab.url,
            activeView: getViewFromUrl(tab.url),
            canGoBack: tab.historyIndex > 0,
            canGoForward: tab.historyIndex < tab.history.length - 1,
          })
        },

        updateTabTitle: (tabId, title) => {
          const state = get()
          const updatedTabs = state.tabs.map((t) =>
            t.id === tabId ? { ...t, title } : t
          )
          set({ tabs: updatedTabs })
        },
      }
})

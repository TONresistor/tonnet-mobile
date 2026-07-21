import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { appendHistory, createBrowserTab, getTitleFromUrl, getViewFromUrl } from '@/lib/navigation'
import { INTERNAL_ROUTES, NAVIGATION_HISTORY_LIMIT, STORAGE_KEYS } from '@/shared/constants'
import type { ActiveView, BrowserTab } from '@/shared/types'

interface NavigationState {
  tabs: BrowserTab[]
  activeTabId: string
  currentUrl: string
  canGoBack: boolean
  canGoForward: boolean
  activeView: ActiveView
  reloadCounter: number
  navigate: (url: string) => void
  goBack: () => void
  goForward: () => void
  reload: () => void
  createTab: (url?: string) => void
  closeTab: (tabId: string) => void
  switchTab: (tabId: string) => void
  resetSession: () => void
}

type NavigationSnapshot = Pick<
  NavigationState,
  'activeView' | 'canGoBack' | 'canGoForward' | 'currentUrl'
>

function getNavigationSnapshot(tab: BrowserTab): NavigationSnapshot {
  return {
    currentUrl: tab.url,
    activeView: getViewFromUrl(tab.url),
    canGoBack: tab.historyIndex > 0,
    canGoForward: tab.historyIndex < tab.history.length - 1,
  }
}

function createSessionState(): Pick<
  NavigationState,
  | 'activeTabId'
  | 'activeView'
  | 'canGoBack'
  | 'canGoForward'
  | 'currentUrl'
  | 'reloadCounter'
  | 'tabs'
> {
  const tab = createBrowserTab()

  return {
    tabs: [tab],
    activeTabId: tab.id,
    ...getNavigationSnapshot(tab),
    reloadCounter: 0,
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function sanitizeTab(value: unknown): BrowserTab | null {
  if (!isRecord(value) || typeof value.id !== 'string' || !value.id) return null

  const storedUrl = typeof value.url === 'string' && value.url ? value.url : INTERNAL_ROUTES.landing
  const storedHistory = Array.isArray(value.history)
    ? value.history.filter((item): item is string => typeof item === 'string' && item.length > 0)
    : []
  const fullHistory = storedHistory.length > 0 ? storedHistory : [storedUrl]
  const historyOffset = Math.max(0, fullHistory.length - NAVIGATION_HISTORY_LIMIT)
  const history = fullHistory.slice(historyOffset)
  const storedIndex =
    typeof value.historyIndex === 'number' && Number.isInteger(value.historyIndex)
      ? value.historyIndex
      : fullHistory.length - 1
  const historyIndex = Math.max(0, Math.min(history.length - 1, storedIndex - historyOffset))
  const url = history[historyIndex]
  const title =
    url === storedUrl && typeof value.title === 'string' && value.title
      ? value.title
      : getTitleFromUrl(url)

  return { id: value.id, url, title, history, historyIndex }
}

function mergePersistedNavigation(
  persistedState: unknown,
  currentState: NavigationState,
): NavigationState {
  if (!isRecord(persistedState) || !Array.isArray(persistedState.tabs)) return currentState

  const seenIds = new Set<string>()
  const tabs = persistedState.tabs.map(sanitizeTab).filter((tab): tab is BrowserTab => {
    if (!tab || seenIds.has(tab.id)) return false
    seenIds.add(tab.id)
    return true
  })

  if (tabs.length === 0) return currentState

  const activeTabId =
    typeof persistedState.activeTabId === 'string' &&
    tabs.some((tab) => tab.id === persistedState.activeTabId)
      ? persistedState.activeTabId
      : tabs[0].id
  const activeTab = tabs.find((tab) => tab.id === activeTabId) ?? tabs[0]

  return {
    ...currentState,
    tabs,
    activeTabId,
    ...getNavigationSnapshot(activeTab),
  }
}

export const useNavigationStore = create<NavigationState>()(
  persist(
    (set, get) => ({
      ...createSessionState(),

      navigate: (url) => {
        const state = get()
        const activeTab = state.tabs.find((tab) => tab.id === state.activeTabId)
        if (!activeTab) return

        if (url === activeTab.url) {
          const activeView = getViewFromUrl(url)
          if (state.activeView !== activeView) set({ activeView })
          return
        }

        const nextHistory = appendHistory(activeTab, url)
        const updatedTab: BrowserTab = {
          ...activeTab,
          ...nextHistory,
          url,
          title: getTitleFromUrl(url),
        }

        set({
          tabs: state.tabs.map((tab) => (tab.id === activeTab.id ? updatedTab : tab)),
          ...getNavigationSnapshot(updatedTab),
        })
      },

      goBack: () => {
        const state = get()
        const activeTab = state.tabs.find((tab) => tab.id === state.activeTabId)
        if (!activeTab || activeTab.historyIndex <= 0) return

        const historyIndex = activeTab.historyIndex - 1
        const url = activeTab.history[historyIndex]
        const updatedTab = {
          ...activeTab,
          url,
          title: getTitleFromUrl(url),
          historyIndex,
        }

        set({
          tabs: state.tabs.map((tab) => (tab.id === activeTab.id ? updatedTab : tab)),
          ...getNavigationSnapshot(updatedTab),
        })
      },

      goForward: () => {
        const state = get()
        const activeTab = state.tabs.find((tab) => tab.id === state.activeTabId)
        if (!activeTab || activeTab.historyIndex >= activeTab.history.length - 1) return

        const historyIndex = activeTab.historyIndex + 1
        const url = activeTab.history[historyIndex]
        const updatedTab = {
          ...activeTab,
          url,
          title: getTitleFromUrl(url),
          historyIndex,
        }

        set({
          tabs: state.tabs.map((tab) => (tab.id === activeTab.id ? updatedTab : tab)),
          ...getNavigationSnapshot(updatedTab),
        })
      },

      reload: () => set((state) => ({ reloadCounter: state.reloadCounter + 1 })),

      createTab: (url = INTERNAL_ROUTES.start) => {
        const tab = createBrowserTab(url)
        set((state) => ({
          tabs: [...state.tabs, tab],
          activeTabId: tab.id,
          ...getNavigationSnapshot(tab),
        }))
      },

      closeTab: (tabId) => {
        const state = get()
        const tabIndex = state.tabs.findIndex((tab) => tab.id === tabId)
        if (tabIndex < 0) return

        if (state.tabs.length === 1) {
          set(createSessionState())
          return
        }

        const tabs = state.tabs.filter((tab) => tab.id !== tabId)
        const activeTabId =
          tabId === state.activeTabId
            ? tabs[Math.min(tabIndex, tabs.length - 1)].id
            : state.activeTabId
        const activeTab = tabs.find((tab) => tab.id === activeTabId) ?? tabs[0]

        set({ tabs, activeTabId, ...getNavigationSnapshot(activeTab) })
      },

      switchTab: (tabId) => {
        const state = get()
        if (tabId === state.activeTabId) return

        const tab = state.tabs.find((candidate) => candidate.id === tabId)
        if (!tab) return

        set({ activeTabId: tabId, ...getNavigationSnapshot(tab) })
      },

      resetSession: () => set(createSessionState()),
    }),
    {
      name: STORAGE_KEYS.navigation,
      partialize: (state) => ({ tabs: state.tabs, activeTabId: state.activeTabId }),
      merge: mergePersistedNavigation,
    },
  ),
)

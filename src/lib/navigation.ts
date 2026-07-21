import { INTERNAL_ROUTES, NAVIGATION_HISTORY_LIMIT } from '@/shared/constants'
import type { ActiveView, BrowserTab } from '@/shared/types'

export function getViewFromUrl(url: string): ActiveView {
  if (!url || url === INTERNAL_ROUTES.landing) return 'landing'
  if (url === INTERNAL_ROUTES.start) return 'start'
  if (url === INTERNAL_ROUTES.settings) return 'settings'
  return url.startsWith('ton://') ? 'start' : 'web'
}

export function getTitleFromUrl(url: string): string {
  if (url === INTERNAL_ROUTES.landing) return 'Welcome'
  if (url === INTERNAL_ROUTES.start) return 'Start'
  if (url === INTERNAL_ROUTES.settings) return 'Settings'

  try {
    const parsedUrl = new URL(url)
    return parsedUrl.hostname || url
  } catch {
    return url
  }
}

function createTabId(): string {
  return `tab_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`
}

export function createBrowserTab(url: string = INTERNAL_ROUTES.landing): BrowserTab {
  return {
    id: createTabId(),
    url,
    title: getTitleFromUrl(url),
    history: [url],
    historyIndex: 0,
  }
}

export function appendHistory(
  tab: BrowserTab,
  url: string,
): Pick<BrowserTab, 'history' | 'historyIndex'> {
  const fullHistory = [...tab.history.slice(0, tab.historyIndex + 1), url]
  const history = fullHistory.slice(-NAVIGATION_HISTORY_LIMIT)

  return {
    history,
    historyIndex: history.length - 1,
  }
}

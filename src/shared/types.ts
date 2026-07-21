export interface Bookmark {
  id: string
  url: string
  title: string
  favicon?: string
  createdAt: number
}

export type ActiveView = 'start' | 'web' | 'settings' | 'landing'

export interface BrowserTab {
  id: string
  url: string
  title: string
  history: string[]
  historyIndex: number
}

export type ProxyConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

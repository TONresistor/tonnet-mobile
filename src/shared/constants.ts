declare const __APP_NAME__: string
declare const __APP_VERSION__: string

export const APP_NAME = __APP_NAME__
export const APP_VERSION = __APP_VERSION__

export const INTERNAL_ROUTES = {
  landing: 'ton://landing',
  start: 'ton://start',
  settings: 'ton://settings',
} as const

export const STORAGE_KEYS = {
  preferences: 'tonnet-preferences',
  bookmarks: 'tonnet-bookmarks',
  navigation: 'tonnet-settings',
} as const

export const PRESERVED_STORAGE_KEYS: readonly string[] = [
  STORAGE_KEYS.preferences,
  STORAGE_KEYS.bookmarks,
]

export const DEFAULT_LANGUAGE = 'en'
export const DEFAULT_PROXY_PORT = 8080
export const NAVIGATION_HISTORY_LIMIT = 100

/** Content used by the bookmarks store to create fresh default entities. */
export const DEFAULT_BOOKMARKS = [
  { url: 'http://tonnet-sync-check.ton', title: 'Sync Check' },
  { url: 'http://boards.ton', title: 'Boards' },
  { url: 'http://piracy.ton', title: 'Piracy' },
  { url: 'http://dnslookup.ton', title: 'DNS Lookup' },
] as const

/**
 * Shared constants.
 */

declare const __APP_NAME__: string
declare const __APP_VERSION__: string

export const APP_NAME = __APP_NAME__
export const APP_VERSION = __APP_VERSION__

export const DEFAULT_BOOKMARKS = [
  { id: '1', url: 'http://tonnet-sync-check.ton', title: 'Sync Check', createdAt: Date.now() },
  { id: '2', url: 'http://boards.ton', title: 'Boards', createdAt: Date.now() },
  { id: '3', url: 'http://piracy.ton', title: 'Piracy', createdAt: Date.now() },
  { id: '4', url: 'http://dnslookup.ton', title: 'DNS Lookup', createdAt: Date.now() },
]

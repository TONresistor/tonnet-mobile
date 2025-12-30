/**
 * Shared types.
 */

export interface Bookmark {
  id: string
  url: string
  title: string
  favicon?: string
  createdAt: number
}

/** Connection status type for hooks */
export type ProxyConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

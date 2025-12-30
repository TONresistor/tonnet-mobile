/**
 * Proxy store - Global TON proxy connection state.
 * Uses Zustand for shared state across all components.
 */

import { create } from 'zustand'
import { platform } from '@/platform'
import { usePreferencesStore } from '@/stores/preferences'
import type { ProxyConnectionStatus } from '@/shared/types'

interface ProxyState {
  // State
  status: ProxyConnectionStatus
  error: string | null
  port: number
  isAnonymous: boolean

  // Computed
  isConnecting: boolean
  isConnected: boolean

  // Actions
  connect: () => Promise<void>
  disconnect: () => Promise<void>
  checkStatus: () => Promise<void>
}

export const useProxyStore = create<ProxyState>()((set, get) => ({
  // Initial state
  status: 'disconnected',
  error: null,
  port: 8080,
  isAnonymous: false,

  // Computed getters (recalculated on access)
  get isConnecting() {
    return get().status === 'connecting'
  },
  get isConnected() {
    return get().status === 'connected'
  },

  // Check initial status - only updates if we're in disconnected/error state
  // This prevents overriding 'connected' or 'connecting' states
  checkStatus: async () => {
    const currentStatus = get().status
    // Don't check if we're already connected or connecting
    if (currentStatus === 'connected' || currentStatus === 'connecting') {
      console.log('[ProxyStore] Status check skipped - already:', currentStatus)
      return
    }

    try {
      const result = await platform.proxy.getStatus()
      console.log('[ProxyStore] Status check:', result)
      if (result.running) {
        set({ status: 'connected', port: result.port || 8080 })
      }
      // Don't set to disconnected - trust our own state
    } catch (err) {
      console.debug('[ProxyStore] Status check error:', err)
    }
  },

  // Connect to TON network
  connect: async () => {
    const state = get()
    if (state.status === 'connecting' || state.status === 'connected') {
      console.log('[ProxyStore] Already connecting/connected')
      return
    }

    // Read settings from preferences
    const { anonymousMode, circuitRotation, rotateInterval } = usePreferencesStore.getState().preferences

    set({ error: null, status: 'connecting' })
    console.log('[ProxyStore] Connecting... anonymous:', anonymousMode, 'rotation:', circuitRotation, 'interval:', rotateInterval)

    try {
      const result = await platform.proxy.connect({
        anonymous: anonymousMode,
        circuitRotation: anonymousMode ? circuitRotation : false,
        rotateInterval: circuitRotation ? rotateInterval : undefined,
      })
      console.log('[ProxyStore] Connect result:', result)

      if (result.success) {
        set({ status: 'connected', port: result.port || 8080, isAnonymous: anonymousMode })
        console.log('[ProxyStore] Connected! Anonymous:', anonymousMode)
      } else {
        set({ status: 'error', error: 'Failed to connect to TON network' })
        console.log('[ProxyStore] Connection failed')
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      set({ status: 'error', error: message })
      console.error('[ProxyStore] Connection error:', err)
    }
  },

  // Disconnect from TON network
  disconnect: async () => {
    const state = get()
    if (state.status === 'disconnected') {
      return
    }

    console.log('[ProxyStore] Disconnecting...')

    try {
      await platform.proxy.disconnect()
      set({ status: 'disconnected', error: null, isAnonymous: false })
      console.log('[ProxyStore] Disconnected')
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      set({ error: message })
      console.error('[ProxyStore] Disconnect error:', err)
    }
  },
}))

/**
 * Platform Bridge
 * Unified API for Android (Capacitor) and Desktop (Electron)
 *
 * This module provides a single interface for platform-specific functionality,
 * abstracting away the differences between Capacitor plugins on Android
 * and Electron IPC on Desktop.
 */
import { Capacitor } from '@capacitor/core'
import { TonProxy } from '../plugins/ton-proxy'
import type {
  Platform,
  PlatformEventType,
  PlatformEventListener,
  EventSubscription,
  ProxyConnectOptions,
  ProxyConnectResult,
  ProxyStatusResult,
  ClearBrowsingDataOptions,
} from './types'

// ============================================================================
// Platform Detection
// ============================================================================

const capacitorPlatform = Capacitor.getPlatform()
const isAndroid = capacitorPlatform === 'android'
const isDesktop = typeof window !== 'undefined' && 'electron' in window && window.electron !== undefined
const isWeb = !isAndroid && !isDesktop

// ============================================================================
// Event Management
// ============================================================================

type EventCallback = (...args: unknown[]) => void
const eventListeners = new Map<string, Set<EventCallback>>()

function addEventSubscription(event: string, callback: EventCallback): EventSubscription {
  if (!eventListeners.has(event)) {
    eventListeners.set(event, new Set())
  }
  eventListeners.get(event)!.add(callback)

  return {
    remove: () => {
      eventListeners.get(event)?.delete(callback)
    },
  }
}

function emitEvent(event: string, data: unknown): void {
  eventListeners.get(event)?.forEach((callback) => {
    try {
      callback(data)
    } catch (error) {
      console.error(`Error in event listener for ${event}:`, error)
    }
  })
}

// ============================================================================
// Setup Native Event Listeners (Android)
// ============================================================================

let androidListenersInitialized = false

async function initAndroidListeners(): Promise<void> {
  if (androidListenersInitialized || !isAndroid) return
  androidListenersInitialized = true

  try {
    // Proxy events
    await TonProxy.addListener('proxyStarted', (event) => {
      emitEvent('proxy:started', { port: event.port })
    })

    await TonProxy.addListener('proxyStopped', () => {
      emitEvent('proxy:stopped', {})
    })

    await TonProxy.addListener('proxyError', (event) => {
      emitEvent('proxy:error', { error: event.error || 'Unknown error' })
    })
  } catch (error) {
    console.error('Failed to initialize Android listeners:', error)
  }
}

// Initialize listeners on Android
if (isAndroid) {
  initAndroidListeners()
}

// ============================================================================
// Proxy Implementation
// ============================================================================

const proxyApi = {
  connect: async (options: ProxyConnectOptions = {}): Promise<ProxyConnectResult> => {
    const { port = 8080, anonymous = false } = options

    if (isAndroid) {
      // Emit progress events for UI feedback
      emitEvent('proxy:progress', { step: 0, message: anonymous ? 'Initializing tunnel...' : 'Starting proxy...' })

      try {
        let timedOut = false
        const timeout = new Promise<{ success: false; port: 0 }>((resolve) =>
          setTimeout(() => { timedOut = true; resolve({ success: false, port: 0 }) }, 60000)
        )
        const result = await Promise.race([TonProxy.start({ port, anonymous }), timeout])
        if (timedOut) {
          TonProxy.stop().catch(() => {})
        }

        if (!result.success) {
          emitEvent('proxy:progress', { step: -1, message: 'Failed to start proxy' })
          return { success: false, port: 0 }
        }

        // On Android, the Go library handles circuit building internally
        // and reports success only when the circuit is ready.
        // We skip the fetch-based sync check because fetch() doesn't route
        // through our local proxy on Android (WebView uses PrivacyWebViewClient instead)
        emitEvent('proxy:progress', { step: 1, message: 'Syncing with network...' })

        // Small delay for UI feedback
        await new Promise(r => setTimeout(r, 500))

        emitEvent('proxy:progress', { step: 2, message: 'Connected!' })
        return { success: true, port: result.port }
      } catch (error) {
        emitEvent('proxy:progress', { step: -1, message: 'Connection failed' })
        throw error
      }
    }

    if (isDesktop && window.electron) {
      return window.electron.proxy.connect(options)
    }

    // Web fallback - no proxy available
    console.warn('Proxy not available on web platform')
    return { success: false, port: 0 }
  },

  disconnect: async (): Promise<void> => {
    if (isAndroid) {
      await TonProxy.stop()
      return
    }

    if (isDesktop && window.electron) {
      await window.electron.proxy.disconnect()
      return
    }

    console.warn('Proxy not available on web platform')
  },

  getStatus: async (): Promise<ProxyStatusResult> => {
    if (isAndroid) {
      const status = await TonProxy.getStatus()
      return {
        running: status.running,
        port: status.port,
        anonymous: status.anonymous,
        error: status.error,
      }
    }

    if (isDesktop && window.electron) {
      return window.electron.proxy.status()
    }

    return { running: false }
  },
}

// ============================================================================
// Navigation
// ============================================================================

function navigate(url: string): void {
  if (isAndroid) {
    // On Android with Capacitor, navigate within the WebView
    // For ton:// URLs, the native side will handle routing
    window.location.href = url
    return
  }

  if (isDesktop && window.electron) {
    window.electron.navigate(url)
    return
  }

  // Web fallback
  window.location.href = url
}

// ============================================================================
// Event Subscription
// ============================================================================

function on<T extends PlatformEventType>(
  event: T,
  listener: PlatformEventListener<T>
): EventSubscription {
  // For Desktop/Electron, also subscribe to IPC events
  if (isDesktop && window.electron) {
    const electronChannel = event.replace(':', '-')
    const unsubscribe = window.electron.on(electronChannel, listener as (...args: unknown[]) => void)

    // Create a combined subscription
    const internalSub = addEventSubscription(event, listener as EventCallback)
    return {
      remove: () => {
        internalSub.remove()
        unsubscribe()
      },
    }
  }

  return addEventSubscription(event, listener as EventCallback)
}

// ============================================================================
// Clear Browsing Data
// ============================================================================

// Keys to preserve when clearing localStorage (app data, not browsing data)
const PRESERVE_STORAGE_KEYS = ['tonnet-preferences', 'tonnet-bookmarks', 'tonnet-settings']

async function clearBrowsingData(options: ClearBrowsingDataOptions = {}): Promise<void> {
  const {
    cache = true,
    cookies = true,
    history = true,
    localStorage: clearLocalStorage = true,
    sessionStorage: clearSessionStorage = true,
  } = options

  if (isDesktop && window.electron) {
    await window.electron.clearBrowsingData(options)
    return
  }

  // For Android/Web, clear what we can from JavaScript
  try {
    if (clearLocalStorage) {
      // Save app data before clearing
      const preserved: Record<string, string | null> = {}
      PRESERVE_STORAGE_KEYS.forEach((key) => {
        preserved[key] = window.localStorage.getItem(key)
      })

      window.localStorage.clear()

      // Restore app data
      Object.entries(preserved).forEach(([key, value]) => {
        if (value !== null) {
          window.localStorage.setItem(key, value)
        }
      })
    }

    if (clearSessionStorage) {
      window.sessionStorage.clear()
    }

    if (cache && 'caches' in window) {
      const cacheNames = await window.caches.keys()
      await Promise.all(cacheNames.map((name) => window.caches.delete(name)))
    }

    // Cookies and history require native implementation on Android
    if (isAndroid && (cookies || history)) {
      console.info('Cookie and history clearing requires native Android implementation')
    }
  } catch (error) {
    console.error('Failed to clear browsing data:', error)
    throw error
  }
}

// ============================================================================
// Platform Export
// ============================================================================

export const platform: Platform = {
  // Platform detection
  isAndroid,
  isDesktop,
  isWeb,

  // APIs
  proxy: proxyApi,

  // Functions
  navigate,
  on,
  clearBrowsingData,
}

// Default export for convenience
export default platform

// Re-export types
export type {
  Platform,
  PlatformEventType,
  PlatformEventListener,
  EventSubscription,
  ProxyConnectOptions,
  ProxyConnectResult,
  ProxyStatusResult,
  ClearBrowsingDataOptions,
} from './types'

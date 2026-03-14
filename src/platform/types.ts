/**
 * Platform Bridge Types
 * Type definitions for the unified platform API
 */

// ============================================================================
// Proxy Types
// ============================================================================

export interface ProxyConnectOptions {
  port?: number
  anonymous?: boolean
  rotateInterval?: string  // e.g., "5m", "10m", "15m", "30m"
  circuitRotation?: boolean
}

export interface ProxyConnectResult {
  success: boolean
  port: number
}

export interface ProxyStatusResult {
  running: boolean
  port?: number
  anonymous?: boolean
  error?: string
}

// ============================================================================
// Event Types
// ============================================================================

export type PlatformEventType =
  | 'proxy:started'
  | 'proxy:stopped'
  | 'proxy:error'
  | 'proxy:progress'

export interface PlatformEventData {
  'proxy:started': { port: number }
  'proxy:stopped': Record<string, never>
  'proxy:error': { error: string }
  'proxy:progress': { step: number; message: string }
}

export type PlatformEventListener<T extends PlatformEventType> = (
  data: PlatformEventData[T]
) => void

export interface EventSubscription {
  remove: () => void
}

// ============================================================================
// Clear Browsing Data Types
// ============================================================================

export interface ClearBrowsingDataOptions {
  cache?: boolean
  cookies?: boolean
  history?: boolean
  localStorage?: boolean
  sessionStorage?: boolean
}

// ============================================================================
// Platform API Interface
// ============================================================================

export interface PlatformProxy {
  connect(options?: ProxyConnectOptions): Promise<ProxyConnectResult>
  disconnect(): Promise<void>
  getStatus(): Promise<ProxyStatusResult>
}

export interface Platform {
  /** True if running on Android (Capacitor) */
  isAndroid: boolean
  /** True if running on Desktop (Electron) */
  isDesktop: boolean
  /** True if running in a web browser (not native) */
  isWeb: boolean

  /** Proxy management */
  proxy: PlatformProxy

  /** Navigate to a URL */
  navigate(url: string): void

  /** Subscribe to platform events */
  on<T extends PlatformEventType>(
    event: T,
    listener: PlatformEventListener<T>
  ): EventSubscription

  /** Clear browsing data */
  clearBrowsingData(options?: ClearBrowsingDataOptions): Promise<void>
}

// ============================================================================
// Electron Window Interface (for Desktop compatibility)
// ============================================================================

export interface ElectronAPI {
  proxy: {
    connect(options?: ProxyConnectOptions): Promise<ProxyConnectResult>
    disconnect(): Promise<void>
    status(): Promise<ProxyStatusResult>
  }
  navigate(url: string): void
  clearBrowsingData(options?: ClearBrowsingDataOptions): Promise<void>
  getAppVersion(): Promise<string>
  on(channel: string, callback: (...args: unknown[]) => void): () => void
}

// Augment Window interface for Electron
declare global {
  interface Window {
    electron?: ElectronAPI
  }
}

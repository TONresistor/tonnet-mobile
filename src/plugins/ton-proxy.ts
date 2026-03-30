/**
 * TonProxy Capacitor Plugin
 * Wraps the native Android TonProxyPlugin for use in TypeScript
 */
import { registerPlugin } from '@capacitor/core'

export interface TonProxyStartOptions {
  port?: number
  anonymous?: boolean
}

export interface TonProxyStartResult {
  success: boolean
  port: number
}

export interface TonProxyStatusResult {
  running: boolean
  port?: number
  anonymous?: boolean
  libraryLoaded?: boolean
  error?: string
}

export interface TonProxyLibraryResult {
  available: boolean
  error?: string
}

export interface TonProxyPlugin {
  /**
   * Start the TON proxy
   * @param options - Configuration options
   * @returns Promise with start result
   */
  start(options?: TonProxyStartOptions): Promise<TonProxyStartResult>

  /**
   * Stop the TON proxy
   */
  stop(): Promise<void>

  /**
   * Get the current proxy status
   */
  getStatus(): Promise<TonProxyStatusResult>

  /**
   * Check if the native library is available
   */
  isLibraryAvailable(): Promise<TonProxyLibraryResult>

  /**
   * Get proxy logs
   */
  getLogs(): Promise<{ logs: string }>

  /**
   * Clear proxy logs
   */
  clearLogs(): Promise<void>

  /**
   * Set third-party cookie policy
   */
  setThirdPartyCookies(options: { enabled: boolean }): Promise<void>

  /**
   * Add a listener for proxy events
   */
  addListener(
    eventName: 'proxyStarted' | 'proxyStopped' | 'proxyError',
    listenerFunc: (event: { port?: number; error?: string }) => void
  ): Promise<{ remove: () => Promise<void> }>

  /**
   * Remove all listeners
   */
  removeAllListeners(): Promise<void>
}

export const TonProxy = registerPlugin<TonProxyPlugin>('TonProxy')

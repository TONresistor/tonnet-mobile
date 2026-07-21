import { Capacitor } from '@capacitor/core'
import { createLogger } from '@/lib/logger'
import { TonProxy } from '@/plugins/ton-proxy'
import { DEFAULT_PROXY_PORT, PRESERVED_STORAGE_KEYS } from '@/shared/constants'
import {
  type ClearBrowsingDataOptions,
  type EventSubscription,
  type Platform,
  PlatformError,
  type PlatformEventListener,
  type PlatformEventType,
  type ProxyConnectOptions,
  type ProxyConnectResult,
  type ProxyStatusResult,
} from './types'

const PROXY_CONNECT_TIMEOUT_MS = 120_000
const PROXY_STOP_TIMEOUT_MS = 15_000
const ANONYMOUS_RETRY_COUNT = 3
const STANDARD_RETRY_COUNT = 1
const RETRY_DELAY_MS = 3_000
const CONNECTED_FEEDBACK_DELAY_MS = 500

const logger = createLogger('Platform')
const isAndroid = Capacitor.getPlatform() === 'android'

class NonRetryableProxyError extends PlatformError {
  constructor(message: string, originalError?: unknown) {
    super('NATIVE_FAILURE', message, originalError)
  }
}

type EventCallback = (data: { step: number; message: string }) => void
const eventListeners = new Map<PlatformEventType, Set<EventCallback>>()

function emitProgress(step: number, message: string): void {
  eventListeners.get('proxy:progress')?.forEach((callback) => {
    try {
      callback({ step, message })
    } catch (error) {
      logger.error('Proxy progress listener failed', error)
    }
  })
}

function on<T extends PlatformEventType>(
  event: T,
  listener: PlatformEventListener<T>,
): EventSubscription {
  const listeners = eventListeners.get(event) ?? new Set<EventCallback>()
  listeners.add(listener as EventCallback)
  eventListeners.set(event, listeners)

  return {
    remove: () => {
      listeners.delete(listener as EventCallback)
      if (listeners.size === 0) eventListeners.delete(event)
    },
  }
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

function asPlatformError(error: unknown, fallbackMessage: string): PlatformError {
  if (error instanceof PlatformError) return error

  if (typeof error === 'object' && error !== null && 'code' in error && error.code === 'OFFLINE') {
    return new PlatformError('OFFLINE', 'An active Internet connection is required', error)
  }

  return new PlatformError('NATIVE_FAILURE', fallbackMessage, error)
}

async function startWithTimeout(
  options: Required<ProxyConnectOptions>,
  timeoutMilliseconds: number,
): Promise<ProxyConnectResult> {
  let timeoutId: number | undefined

  const timeout = new Promise<never>((_, reject) => {
    timeoutId = window.setTimeout(() => {
      reject(new PlatformError('TIMEOUT', 'The TON proxy did not start in time'))
    }, timeoutMilliseconds)
  })

  try {
    return await Promise.race([TonProxy.start(options), timeout])
  } finally {
    if (timeoutId !== undefined) window.clearTimeout(timeoutId)
  }
}

async function stopNativeProxyWithTimeout(): Promise<void> {
  let timeoutId: number | undefined
  const timeout = new Promise<never>((_, reject) => {
    timeoutId = window.setTimeout(() => {
      reject(new PlatformError('TIMEOUT', 'The TON proxy did not stop in time'))
    }, PROXY_STOP_TIMEOUT_MS)
  })

  try {
    await Promise.race([TonProxy.stop(), timeout])
  } finally {
    if (timeoutId !== undefined) window.clearTimeout(timeoutId)
  }
}

function normalizeConnectOptions(options: ProxyConnectOptions): Required<ProxyConnectOptions> {
  const normalized = {
    port: options.port ?? DEFAULT_PROXY_PORT,
    anonymous: options.anonymous ?? false,
  }

  if (!Number.isInteger(normalized.port) || normalized.port <= 1024 || normalized.port > 65_535) {
    throw new PlatformError('INVALID_CONFIG', 'Proxy port must be between 1025 and 65535')
  }

  return normalized
}

async function stopAfterTimeout(error: unknown): Promise<void> {
  if (!(error instanceof PlatformError) || error.code !== 'TIMEOUT') return

  try {
    await stopNativeProxyWithTimeout()
  } catch (stopError) {
    logger.warn('Timed-out proxy could not be stopped cleanly', stopError)
  }
}

async function startProxyAttempt(
  options: Required<ProxyConnectOptions>,
  timeoutMilliseconds: number,
): Promise<ProxyConnectResult> {
  try {
    const result = await startWithTimeout(options, timeoutMilliseconds)
    if (!result.success) {
      throw new PlatformError('NATIVE_FAILURE', 'The native proxy rejected the request')
    }
    const anonymous = result.anonymous ?? options.anonymous
    if (anonymous !== options.anonymous) {
      try {
        await stopNativeProxyWithTimeout()
      } catch (error) {
        throw new NonRetryableProxyError(
          'The running proxy mode does not match the request and cleanup failed',
          error,
        )
      }
      throw new NonRetryableProxyError('The running proxy mode does not match the request')
    }
    return { success: true, port: result.port || options.port, anonymous }
  } catch (error) {
    await stopAfterTimeout(error)
    throw error
  }
}

async function waitForRetry(
  attempt: number,
  maxAttempts: number,
  deadline: number,
): Promise<boolean> {
  if (attempt <= 1) return true

  emitProgress(0, `Retrying tunnel discovery (${attempt}/${maxAttempts})...`)
  const retryDelay = Math.min(RETRY_DELAY_MS, Math.max(0, deadline - Date.now()))
  if (retryDelay === 0) return false

  await delay(retryDelay)
  return true
}

function normalizeRetryableStartError(error: unknown): PlatformError {
  const platformError = asPlatformError(error, 'Failed to start the TON proxy')
  if (platformError.code === 'OFFLINE') {
    emitProgress(-1, 'Internet connection required')
    throw platformError
  }
  if (platformError instanceof NonRetryableProxyError) {
    emitProgress(-1, 'Failed to start proxy')
    throw platformError
  }
  return platformError
}

async function connectProxy(options: ProxyConnectOptions = {}): Promise<ProxyConnectResult> {
  const normalized = normalizeConnectOptions(options)

  if (!isAndroid) {
    throw new PlatformError('UNAVAILABLE', 'The TON proxy is only available on Android')
  }

  emitProgress(0, normalized.anonymous ? 'Initializing tunnel...' : 'Starting proxy...')
  const maxAttempts = normalized.anonymous ? ANONYMOUS_RETRY_COUNT : STANDARD_RETRY_COUNT
  const deadline = Date.now() + PROXY_CONNECT_TIMEOUT_MS
  let lastError: unknown

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    if (!(await waitForRetry(attempt, maxAttempts, deadline))) break

    const remainingTime = deadline - Date.now()
    if (remainingTime <= 0) {
      lastError = new PlatformError('TIMEOUT', 'The TON proxy did not start in time')
      break
    }

    try {
      const result = await startProxyAttempt(normalized, remainingTime)
      emitProgress(1, 'Syncing with network...')
      await delay(CONNECTED_FEEDBACK_DELAY_MS)
      emitProgress(2, 'Connected!')
      return result
    } catch (error) {
      lastError = normalizeRetryableStartError(error)
    }
  }

  emitProgress(-1, 'Failed to start proxy')
  throw asPlatformError(lastError, 'Failed to start the TON proxy')
}

const proxyApi: Platform['proxy'] = {
  connect: connectProxy,

  async disconnect() {
    if (!isAndroid) return
    try {
      await stopNativeProxyWithTimeout()
    } catch (error) {
      throw asPlatformError(error, 'Failed to stop the TON proxy')
    }
  },

  async getStatus(): Promise<ProxyStatusResult> {
    if (!isAndroid) return { running: false }
    try {
      const status = await TonProxy.getStatus()
      return {
        running: status.running,
        port: status.port,
        anonymous: status.anonymous,
      }
    } catch (error) {
      throw asPlatformError(error, 'Failed to read the TON proxy status')
    }
  },

  async getLogs() {
    if (!isAndroid) return []
    try {
      const result = await TonProxy.getLogs()
      return result.logs.split('\n').filter(Boolean)
    } catch (error) {
      throw asPlatformError(error, 'Failed to read the TON proxy logs')
    }
  },
}

async function setThirdPartyCookies(enabled: boolean): Promise<void> {
  if (!isAndroid) return
  try {
    await TonProxy.setThirdPartyCookies({ enabled })
  } catch (error) {
    throw asPlatformError(error, 'Failed to update the cookie policy')
  }
}

async function clearBrowsingData(options: ClearBrowsingDataOptions = {}): Promise<void> {
  const {
    cache = true,
    cookies = true,
    history = true,
    localStorage: clearLocalStorage = true,
    sessionStorage: clearSessionStorage = true,
  } = options

  try {
    if (clearLocalStorage) {
      const preserved = new Map<string, string>()
      for (const key of PRESERVED_STORAGE_KEYS) {
        const value = window.localStorage.getItem(key)
        if (value !== null) preserved.set(key, value)
      }

      window.localStorage.clear()
      preserved.forEach((value, key) => {
        window.localStorage.setItem(key, value)
      })
    }

    if (clearSessionStorage) window.sessionStorage.clear()

    if (cache && 'caches' in window) {
      const cacheNames = await window.caches.keys()
      await Promise.all(cacheNames.map((name) => window.caches.delete(name)))
    }

    if (isAndroid && (cache || cookies || history)) {
      await TonProxy.clearBrowsingData({ cache, cookies, history })
    }
  } catch (error) {
    throw asPlatformError(error, 'Failed to clear browsing data')
  }
}

export const platform: Platform = {
  isAndroid,
  proxy: proxyApi,
  setThirdPartyCookies,
  on,
  clearBrowsingData,
}

export type {
  ClearBrowsingDataOptions,
  EventSubscription,
  Platform,
  PlatformEventListener,
  PlatformEventType,
  ProxyConnectOptions,
  ProxyConnectResult,
  ProxyStatusResult,
} from './types'

export { PlatformError } from './types'

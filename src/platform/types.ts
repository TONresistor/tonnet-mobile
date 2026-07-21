export interface ProxyConnectOptions {
  port?: number
  anonymous?: boolean
}

export interface ProxyConnectResult {
  success: boolean
  port: number
  anonymous?: boolean
}

export interface ProxyStatusResult {
  running: boolean
  port?: number
  anonymous?: boolean
}

type PlatformErrorCode = 'INVALID_CONFIG' | 'NATIVE_FAILURE' | 'OFFLINE' | 'TIMEOUT' | 'UNAVAILABLE'

export class PlatformError extends Error {
  readonly code: PlatformErrorCode
  readonly originalError?: unknown

  constructor(code: PlatformErrorCode, message: string, originalError?: unknown) {
    super(message)
    this.name = 'PlatformError'
    this.code = code
    this.originalError = originalError
  }
}

export type PlatformEventType = 'proxy:progress'

export interface PlatformEventData {
  'proxy:progress': { step: number; message: string }
}

export type PlatformEventListener<T extends PlatformEventType> = (
  data: PlatformEventData[T],
) => void

export interface EventSubscription {
  remove: () => void
}

export interface ClearBrowsingDataOptions {
  cache?: boolean
  cookies?: boolean
  history?: boolean
  localStorage?: boolean
  sessionStorage?: boolean
}

export interface PlatformProxy {
  connect(options?: ProxyConnectOptions): Promise<ProxyConnectResult>
  disconnect(): Promise<void>
  getStatus(): Promise<ProxyStatusResult>
  getLogs(): Promise<string[]>
}

export interface Platform {
  isAndroid: boolean
  proxy: PlatformProxy
  setThirdPartyCookies(enabled: boolean): Promise<void>
  on<T extends PlatformEventType>(event: T, listener: PlatformEventListener<T>): EventSubscription
  clearBrowsingData(options?: ClearBrowsingDataOptions): Promise<void>
}

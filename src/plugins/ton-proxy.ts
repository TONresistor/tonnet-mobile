import { registerPlugin } from '@capacitor/core'

interface TonProxyStartOptions {
  port?: number
  anonymous?: boolean
}

interface TonProxyStartResult {
  success: boolean
  port: number
  anonymous?: boolean
}

interface TonProxyStatusResult {
  running: boolean
  port?: number
  anonymous?: boolean
}

interface TonProxyClearBrowsingDataOptions {
  cache?: boolean
  cookies?: boolean
  history?: boolean
}

interface TonProxyPlugin {
  start(options?: TonProxyStartOptions): Promise<TonProxyStartResult>
  stop(): Promise<void>
  getStatus(): Promise<TonProxyStatusResult>
  getLogs(): Promise<{ logs: string }>
  setThirdPartyCookies(options: { enabled: boolean }): Promise<void>
  clearBrowsingData(options?: TonProxyClearBrowsingDataOptions): Promise<void>
}

export const TonProxy = registerPlugin<TonProxyPlugin>('TonProxy')

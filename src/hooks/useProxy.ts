/**
 * useProxy hook - manages TON proxy connection state.
 * Now uses the global proxy store for shared state across components.
 */

import { useEffect } from 'react'
import type { ProxyConnectionStatus } from '@/shared/types'
import { useProxyStore } from '@/stores/proxy'

interface UseProxyReturn {
  /** Whether the proxy is currently connecting */
  isConnecting: boolean
  /** Whether the proxy is connected */
  isConnected: boolean
  /** Current connection status */
  status: ProxyConnectionStatus
  /** Error message if connection failed */
  error: string | null
  /** Connect to the TON network (uses anonymousMode from preferences) */
  connect: () => Promise<void>
}

export function useProxy(): UseProxyReturn {
  const status = useProxyStore((state) => state.status)
  const error = useProxyStore((state) => state.error)
  const connect = useProxyStore((state) => state.connect)
  const checkStatus = useProxyStore((state) => state.checkStatus)

  const isConnecting = status === 'connecting'
  const isConnected = status === 'connected'

  // Check initial status on mount
  useEffect(() => {
    checkStatus()
  }, [checkStatus])

  return {
    isConnecting,
    isConnected,
    status,
    error,
    connect,
  }
}

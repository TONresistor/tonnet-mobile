import { create } from 'zustand'
import { createLogger } from '@/lib/logger'
import { platform } from '@/platform'
import type { ProxyConnectionStatus } from '@/shared/types'
import { usePreferencesStore } from '@/stores/preferences'

const logger = createLogger('ProxyStore')

let operationGeneration = 0
let activeMutationGeneration: number | null = null
let disconnectInFlight: Promise<void> | null = null

function beginOperation(): number {
  operationGeneration += 1
  return operationGeneration
}

function beginMutation(): number {
  const generation = beginOperation()
  activeMutationGeneration = generation
  return generation
}

function finishMutation(generation: number): void {
  if (activeMutationGeneration === generation) activeMutationGeneration = null
}

function isCurrentOperation(generation: number): boolean {
  return generation === operationGeneration
}

function applyIfCurrent(generation: number, apply: () => void): void {
  if (isCurrentOperation(generation)) apply()
}

interface ProxyState {
  status: ProxyConnectionStatus
  error: string | null
  isAnonymous: boolean
  connect: () => Promise<void>
  disconnect: () => Promise<void>
  checkStatus: () => Promise<void>
}

type ProxyRuntimeState = Pick<ProxyState, 'error' | 'isAnonymous' | 'status'>

async function getStateAfterFailedDisconnect(
  previousState: ProxyState,
  error: string,
): Promise<ProxyRuntimeState> {
  try {
    const nativeStatus = await platform.proxy.getStatus()
    if (!nativeStatus.running) {
      return { status: 'disconnected', error, isAnonymous: false }
    }

    return {
      status: 'connected',
      error,
      isAnonymous: nativeStatus.anonymous ?? previousState.isAnonymous,
    }
  } catch {
    logger.debug('Unable to verify proxy state after a failed stop')
    return {
      status: previousState.status === 'connected' ? 'connected' : 'error',
      error,
      isAnonymous: previousState.isAnonymous,
    }
  }
}

async function reconcileFailedDisconnect(
  generation: number,
  previousState: ProxyState,
  error: string,
  apply: (state: ProxyRuntimeState) => void,
): Promise<void> {
  if (!isCurrentOperation(generation)) return

  const nextState = await getStateAfterFailedDisconnect(previousState, error)
  applyIfCurrent(generation, () => apply(nextState))
}

export const useProxyStore = create<ProxyState>()((set, get) => ({
  status: 'disconnected',
  error: null,
  isAnonymous: false,

  checkStatus: async () => {
    const observedStatus = get().status
    if (
      activeMutationGeneration !== null ||
      observedStatus === 'connected' ||
      observedStatus === 'connecting'
    ) {
      return
    }

    const generation = beginOperation()

    try {
      const result = await platform.proxy.getStatus()
      if (!isCurrentOperation(generation) || get().status !== observedStatus) return

      if (result.running) {
        set({
          status: 'connected',
          error: null,
          isAnonymous: result.anonymous ?? false,
        })
      } else {
        set({ status: 'disconnected', error: null, isAnonymous: false })
      }
    } catch {
      logger.debug('Unable to read the native proxy status')
    }
  },

  connect: async () => {
    const state = get()
    if (state.status === 'connecting' || state.status === 'connected') {
      return
    }

    const { anonymousMode, proxyPort } = usePreferencesStore.getState().preferences
    const generation = beginMutation()

    set({ error: null, status: 'connecting' })

    try {
      const result = await platform.proxy.connect({
        anonymous: anonymousMode,
        port: proxyPort,
      })

      if (!result.success) {
        throw new Error('Failed to connect to TON network')
      }
      applyIfCurrent(generation, () => {
        set({
          status: 'connected',
          error: null,
          isAnonymous: result.anonymous ?? anonymousMode,
        })
      })
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error'
      applyIfCurrent(generation, () => set({ status: 'error', error: message }))
      logger.error('Connection failed', error)
      throw error
    } finally {
      finishMutation(generation)
    }
  },

  disconnect: () => {
    if (disconnectInFlight) return disconnectInFlight

    const state = get()
    if (state.status === 'disconnected') {
      return Promise.resolve()
    }

    const generation = beginMutation()

    disconnectInFlight = (async () => {
      try {
        await platform.proxy.disconnect()
        applyIfCurrent(generation, () => {
          set({ status: 'disconnected', error: null, isAnonymous: false })
        })
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Unknown error'
        await reconcileFailedDisconnect(generation, state, message, (nextState) => set(nextState))
        logger.error('Disconnect failed', error)
        throw error
      } finally {
        finishMutation(generation)
        disconnectInFlight = null
      }
    })()

    return disconnectInFlight
  },
}))

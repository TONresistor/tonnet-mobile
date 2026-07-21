import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defaultPreferences, usePreferencesStore } from '../preferences'

const proxyApi = vi.hoisted(() => ({
  connect: vi.fn(),
  disconnect: vi.fn(),
  getStatus: vi.fn(),
}))

vi.mock('@/platform', () => ({
  platform: { proxy: proxyApi },
}))

import { useProxyStore } from '../proxy'

function createDeferred<Value>() {
  let resolve!: (value: Value | PromiseLike<Value>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<Value>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })

  return { promise, reject, resolve }
}

describe('Proxy Store', () => {
  beforeEach(() => {
    proxyApi.connect.mockReset()
    proxyApi.disconnect.mockReset()
    proxyApi.getStatus.mockReset()
    usePreferencesStore.setState({
      preferences: { ...defaultPreferences, proxyPort: 9090, anonymousMode: true },
      isLoaded: true,
    })
    useProxyStore.setState({
      status: 'disconnected',
      error: null,
      isAnonymous: false,
    })
  })

  it('connects with current preferences and records anonymous mode', async () => {
    proxyApi.connect.mockResolvedValue({ success: true, port: 9090, anonymous: true })

    await useProxyStore.getState().connect()

    expect(proxyApi.connect).toHaveBeenCalledWith({ anonymous: true, port: 9090 })
    expect(useProxyStore.getState()).toMatchObject({
      status: 'connected',
      error: null,
      isAnonymous: true,
    })
  })

  it('does not start a second connection while busy or connected', async () => {
    useProxyStore.setState({ status: 'connecting' })
    await useProxyStore.getState().connect()
    useProxyStore.setState({ status: 'connected' })
    await useProxyStore.getState().connect()

    expect(proxyApi.connect).not.toHaveBeenCalled()
  })

  it('exposes failed results and normalized thrown errors', async () => {
    proxyApi.connect.mockResolvedValueOnce({ success: false })
    await expect(useProxyStore.getState().connect()).rejects.toThrow(
      'Failed to connect to TON network',
    )
    expect(useProxyStore.getState()).toMatchObject({
      status: 'error',
      error: 'Failed to connect to TON network',
    })

    useProxyStore.setState({ status: 'disconnected' })
    proxyApi.connect.mockRejectedValueOnce(new Error('Native unavailable'))
    await expect(useProxyStore.getState().connect()).rejects.toThrow('Native unavailable')
    expect(useProxyStore.getState()).toMatchObject({
      status: 'error',
      error: 'Native unavailable',
    })
  })

  it('propagates a cancelled start without overwriting a newer disconnect', async () => {
    const pendingStart = createDeferred<{ success: boolean; port: number; anonymous: boolean }>()
    proxyApi.connect.mockReturnValue(pendingStart.promise)
    proxyApi.disconnect.mockResolvedValue(undefined)

    const connection = useProxyStore.getState().connect()
    const connectionFailure = expect(connection).rejects.toThrow('Start cancelled')
    await useProxyStore.getState().disconnect()

    pendingStart.reject(new Error('Start cancelled'))
    await connectionFailure

    expect(useProxyStore.getState()).toMatchObject({
      status: 'disconnected',
      error: null,
      isAnonymous: false,
    })
  })

  it('ignores a late successful start after a newer connection wins', async () => {
    const firstStart = createDeferred<{ success: boolean; port: number; anonymous: boolean }>()
    const secondStart = createDeferred<{ success: boolean; port: number; anonymous: boolean }>()
    proxyApi.connect
      .mockReturnValueOnce(firstStart.promise)
      .mockReturnValueOnce(secondStart.promise)
    proxyApi.disconnect.mockResolvedValue(undefined)

    const firstConnection = useProxyStore.getState().connect()
    await useProxyStore.getState().disconnect()
    usePreferencesStore.getState().setPreference('anonymousMode', false)
    const secondConnection = useProxyStore.getState().connect()

    secondStart.resolve({ success: true, port: 9090, anonymous: false })
    await secondConnection
    firstStart.resolve({ success: true, port: 9090, anonymous: true })
    await firstConnection

    expect(useProxyStore.getState()).toMatchObject({
      status: 'connected',
      error: null,
      isAnonymous: false,
    })
  })

  it('disconnects and clears anonymous state', async () => {
    useProxyStore.setState({ status: 'connected', isAnonymous: true })
    proxyApi.disconnect.mockResolvedValue(undefined)

    await useProxyStore.getState().disconnect()

    expect(proxyApi.disconnect).toHaveBeenCalledOnce()
    expect(useProxyStore.getState()).toMatchObject({
      status: 'disconnected',
      error: null,
      isAnonymous: false,
    })
  })

  it('keeps native state and allows a retry when disconnect throws', async () => {
    useProxyStore.setState({ status: 'connected', isAnonymous: true })
    proxyApi.disconnect
      .mockRejectedValueOnce(new Error('Stop failed'))
      .mockResolvedValueOnce(undefined)
    proxyApi.getStatus.mockResolvedValue({ running: true, anonymous: true })

    await expect(useProxyStore.getState().disconnect()).rejects.toThrow('Stop failed')

    expect(useProxyStore.getState()).toMatchObject({
      status: 'connected',
      error: 'Stop failed',
      isAnonymous: true,
    })
    await useProxyStore.getState().disconnect()
    expect(proxyApi.disconnect).toHaveBeenCalledTimes(2)
    expect(useProxyStore.getState()).toMatchObject({
      status: 'disconnected',
      error: null,
      isAnonymous: false,
    })
  })

  it('records a stopped native proxy when disconnect reports an error', async () => {
    useProxyStore.setState({ status: 'connected', isAnonymous: true })
    proxyApi.disconnect.mockRejectedValue(new Error('Late stop failure'))
    proxyApi.getStatus.mockResolvedValue({ running: false })

    await expect(useProxyStore.getState().disconnect()).rejects.toThrow('Late stop failure')

    expect(useProxyStore.getState()).toMatchObject({
      status: 'disconnected',
      error: 'Late stop failure',
      isAnonymous: false,
    })
    proxyApi.disconnect.mockClear()
    await useProxyStore.getState().disconnect()
    expect(proxyApi.disconnect).not.toHaveBeenCalled()
  })

  it('preserves the prior safe state when failed disconnect status cannot be verified', async () => {
    useProxyStore.setState({ status: 'connected', isAnonymous: true })
    proxyApi.disconnect.mockRejectedValue(new Error('Stop failed'))
    proxyApi.getStatus.mockRejectedValue(new Error('Status failed'))

    await expect(useProxyStore.getState().disconnect()).rejects.toThrow('Stop failed')

    expect(useProxyStore.getState()).toMatchObject({
      status: 'connected',
      error: 'Stop failed',
      isAnonymous: true,
    })
  })

  it('coalesces concurrent stops and reconciles their shared failure once', async () => {
    const pendingStop = createDeferred<void>()
    useProxyStore.setState({ status: 'connected', isAnonymous: true })
    proxyApi.disconnect.mockReturnValue(pendingStop.promise)
    proxyApi.getStatus.mockResolvedValue({ running: true, anonymous: true })

    const firstDisconnect = useProxyStore.getState().disconnect()
    const secondDisconnect = useProxyStore.getState().disconnect()
    const firstFailure = expect(firstDisconnect).rejects.toThrow('Stop failed')
    const secondFailure = expect(secondDisconnect).rejects.toThrow('Stop failed')

    expect(secondDisconnect).toBe(firstDisconnect)
    expect(proxyApi.disconnect).toHaveBeenCalledOnce()
    pendingStop.reject(new Error('Stop failed'))
    await Promise.all([firstFailure, secondFailure])

    expect(proxyApi.getStatus).toHaveBeenCalledOnce()
    expect(useProxyStore.getState()).toMatchObject({
      status: 'connected',
      error: 'Stop failed',
      isAnonymous: true,
    })
  })

  it('keeps a failed stop authoritative over a concurrent status check from an error state', async () => {
    const pendingStop = createDeferred<void>()
    let stopHasFailed = false
    useProxyStore.setState({ status: 'error', error: 'Previous failure', isAnonymous: true })
    proxyApi.disconnect.mockReturnValue(pendingStop.promise)
    proxyApi.getStatus.mockImplementation(async () => ({
      running: stopHasFailed,
      anonymous: stopHasFailed,
    }))

    const disconnection = useProxyStore.getState().disconnect()
    const stopFailure = expect(disconnection).rejects.toThrow('Stop failed')
    await useProxyStore.getState().checkStatus()

    expect(proxyApi.getStatus).not.toHaveBeenCalled()
    stopHasFailed = true
    pendingStop.reject(new Error('Stop failed'))
    await stopFailure

    expect(proxyApi.getStatus).toHaveBeenCalledOnce()
    expect(useProxyStore.getState()).toMatchObject({
      status: 'connected',
      error: 'Stop failed',
      isAnonymous: true,
    })
  })

  it('adopts a running native proxy without overriding active states', async () => {
    proxyApi.getStatus.mockResolvedValue({ running: true, port: 9090, anonymous: true })
    await useProxyStore.getState().checkStatus()
    expect(useProxyStore.getState()).toMatchObject({
      status: 'connected',
      isAnonymous: true,
    })

    await useProxyStore.getState().checkStatus()
    expect(proxyApi.getStatus).toHaveBeenCalledOnce()
  })

  it('tolerates native status failures', async () => {
    proxyApi.getStatus.mockRejectedValue(new Error('Unavailable'))

    await useProxyStore.getState().checkStatus()

    expect(useProxyStore.getState().status).toBe('disconnected')
  })

  it('does not let a stale status response overwrite an active connection attempt', async () => {
    let resolveStatus: ((value: { running: boolean }) => void) | undefined
    proxyApi.getStatus.mockReturnValue(
      new Promise((resolve) => {
        resolveStatus = resolve
      }),
    )

    const statusCheck = useProxyStore.getState().checkStatus()
    useProxyStore.setState({ status: 'connecting' })
    resolveStatus?.({ running: true })
    await statusCheck

    expect(useProxyStore.getState().status).toBe('connecting')
  })

  it('lets only the newest concurrent status check update the store', async () => {
    const firstStatus = createDeferred<{ running: boolean; anonymous?: boolean }>()
    const secondStatus = createDeferred<{ running: boolean; anonymous?: boolean }>()
    proxyApi.getStatus
      .mockReturnValueOnce(firstStatus.promise)
      .mockReturnValueOnce(secondStatus.promise)

    const staleCheck = useProxyStore.getState().checkStatus()
    const currentCheck = useProxyStore.getState().checkStatus()

    secondStatus.resolve({ running: false })
    await currentCheck
    firstStatus.resolve({ running: true, anonymous: true })
    await staleCheck

    expect(proxyApi.getStatus).toHaveBeenCalledTimes(2)
    expect(useProxyStore.getState()).toMatchObject({
      status: 'disconnected',
      error: null,
      isAnonymous: false,
    })
  })

  it('recovers an error state when native status confirms the proxy is stopped', async () => {
    useProxyStore.setState({ status: 'error', error: 'Previous failure', isAnonymous: true })
    proxyApi.getStatus.mockResolvedValue({ running: false })

    await useProxyStore.getState().checkStatus()

    expect(useProxyStore.getState()).toMatchObject({
      status: 'disconnected',
      error: null,
      isAnonymous: false,
    })
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const runtime = vi.hoisted(() => ({ platform: 'android' }))
const tonProxy = vi.hoisted(() => ({
  start: vi.fn(),
  stop: vi.fn(),
  getStatus: vi.fn(),
  getLogs: vi.fn(),
  setThirdPartyCookies: vi.fn(),
  clearBrowsingData: vi.fn(),
}))

vi.mock('@capacitor/core', () => ({
  Capacitor: { getPlatform: () => runtime.platform },
}))

vi.mock('@/plugins/ton-proxy', () => ({ TonProxy: tonProxy }))

async function loadPlatform() {
  return import('../index')
}

describe('platform bridge', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    runtime.platform = 'android'
    tonProxy.start.mockResolvedValue({ success: true, port: 8080, anonymous: false })
    tonProxy.stop.mockResolvedValue(undefined)
    tonProxy.getStatus.mockResolvedValue({ running: false })
    tonProxy.getLogs.mockResolvedValue({ logs: '' })
    tonProxy.setThirdPartyCookies.mockResolvedValue(undefined)
    tonProxy.clearBrowsingData.mockResolvedValue(undefined)
    localStorage.clear()
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('connects through the native plugin and emits progress', async () => {
    vi.useFakeTimers()
    const { platform } = await loadPlatform()
    const progress: number[] = []
    const subscription = platform.on('proxy:progress', ({ step }) => progress.push(step))

    const connection = platform.proxy.connect({ port: 9090, anonymous: false })
    await vi.runAllTimersAsync()

    await expect(connection).resolves.toEqual({ success: true, port: 8080, anonymous: false })
    expect(tonProxy.start).toHaveBeenCalledWith({ port: 9090, anonymous: false })
    expect(progress).toEqual([0, 1, 2])

    subscription.remove()
  })

  it('rejects invalid ports before calling native code', async () => {
    const { platform, PlatformError } = await loadPlatform()

    await expect(platform.proxy.connect({ port: 80 })).rejects.toMatchObject({
      name: PlatformError.name,
      code: 'INVALID_CONFIG',
    })
    expect(tonProxy.start).not.toHaveBeenCalled()
  })

  it('retries anonymous startup and reports the effective native mode', async () => {
    vi.useFakeTimers()
    tonProxy.start
      .mockResolvedValueOnce({ success: false, port: 9090, anonymous: true })
      .mockResolvedValueOnce({ success: true, port: 0, anonymous: true })
    const { platform } = await loadPlatform()

    const connection = platform.proxy.connect({ port: 9090, anonymous: true })
    await vi.runAllTimersAsync()

    await expect(connection).resolves.toEqual({ success: true, port: 9090, anonymous: true })
    expect(tonProxy.start).toHaveBeenCalledTimes(2)
  })

  it('stops and rejects a proxy already running in a different privacy mode without retrying', async () => {
    vi.useFakeTimers()
    tonProxy.start.mockResolvedValue({ success: true, port: 8080, anonymous: false })
    const { platform } = await loadPlatform()

    const connection = platform.proxy.connect({ anonymous: true })
    const expectation = expect(connection).rejects.toMatchObject({ code: 'NATIVE_FAILURE' })
    await vi.runAllTimersAsync()

    await expectation
    expect(tonProxy.start).toHaveBeenCalledOnce()
    expect(tonProxy.stop).toHaveBeenCalledOnce()
  })

  it('stops a native start that times out', async () => {
    vi.useFakeTimers()
    tonProxy.start.mockReturnValue(new Promise(() => undefined))
    const { platform } = await loadPlatform()

    const connection = platform.proxy.connect()
    const expectation = expect(connection).rejects.toMatchObject({ code: 'TIMEOUT' })
    await vi.advanceTimersByTimeAsync(120_000)

    await expectation
    expect(tonProxy.stop).toHaveBeenCalledOnce()
  })

  it('bounds timeout cleanup when the native stop callback never arrives', async () => {
    vi.useFakeTimers()
    tonProxy.start.mockReturnValue(new Promise(() => undefined))
    tonProxy.stop.mockReturnValue(new Promise(() => undefined))
    const { platform } = await loadPlatform()

    const connection = platform.proxy.connect()
    const expectation = expect(connection).rejects.toMatchObject({ code: 'TIMEOUT' })
    await vi.advanceTimersByTimeAsync(135_000)

    await expectation
    expect(tonProxy.start).toHaveBeenCalledOnce()
    expect(tonProxy.stop).toHaveBeenCalledOnce()
  })

  it('reports offline native failures immediately without retrying', async () => {
    vi.useFakeTimers()
    tonProxy.start.mockRejectedValue(
      Object.assign(new Error('No validated network'), { code: 'OFFLINE' }),
    )
    const { platform } = await loadPlatform()
    const progress: Array<{ message: string; step: number }> = []
    platform.on('proxy:progress', (event) => progress.push(event))

    await expect(platform.proxy.connect({ anonymous: true })).rejects.toMatchObject({
      code: 'OFFLINE',
      message: 'An active Internet connection is required',
    })

    expect(tonProxy.start).toHaveBeenCalledOnce()
    expect(tonProxy.stop).not.toHaveBeenCalled()
    expect(progress[progress.length - 1]).toEqual({
      message: 'Internet connection required',
      step: -1,
    })
  })

  it('normalizes native logs into non-empty lines', async () => {
    tonProxy.getLogs.mockResolvedValue({ logs: 'first\nsecond\n' })
    const { platform } = await loadPlatform()

    await expect(platform.proxy.getLogs()).resolves.toEqual(['first', 'second'])
  })

  it('maps native status and forwards native policy operations', async () => {
    tonProxy.getStatus.mockResolvedValue({
      running: true,
      port: 9090,
      anonymous: true,
    })
    const { platform } = await loadPlatform()

    await expect(platform.proxy.getStatus()).resolves.toEqual({
      running: true,
      port: 9090,
      anonymous: true,
    })
    await platform.setThirdPartyCookies(true)
    await platform.proxy.disconnect()

    expect(tonProxy.setThirdPartyCookies).toHaveBeenCalledWith({ enabled: true })
    expect(tonProxy.stop).toHaveBeenCalledOnce()
  })

  it('normalizes native boundary failures', async () => {
    const { platform } = await loadPlatform()

    tonProxy.stop.mockRejectedValueOnce(new Error('stop'))
    await expect(platform.proxy.disconnect()).rejects.toMatchObject({ code: 'NATIVE_FAILURE' })

    tonProxy.getStatus.mockRejectedValueOnce(new Error('status'))
    await expect(platform.proxy.getStatus()).rejects.toMatchObject({ code: 'NATIVE_FAILURE' })

    tonProxy.getLogs.mockRejectedValueOnce(new Error('logs'))
    await expect(platform.proxy.getLogs()).rejects.toMatchObject({ code: 'NATIVE_FAILURE' })

    tonProxy.setThirdPartyCookies.mockRejectedValueOnce(new Error('cookies'))
    await expect(platform.setThirdPartyCookies(false)).rejects.toMatchObject({
      code: 'NATIVE_FAILURE',
    })

    tonProxy.clearBrowsingData.mockRejectedValueOnce(new Error('clear'))
    await expect(
      platform.clearBrowsingData({
        cache: false,
        cookies: true,
        history: false,
        localStorage: false,
        sessionStorage: false,
      }),
    ).rejects.toMatchObject({ code: 'NATIVE_FAILURE' })
  })

  it('preserves app preferences and bookmarks when clearing browser storage', async () => {
    localStorage.setItem('tonnet-preferences', 'preferences')
    localStorage.setItem('tonnet-bookmarks', 'bookmarks')
    localStorage.setItem('tonnet-settings', 'navigation')
    localStorage.setItem('site-data', 'remove-me')
    sessionStorage.setItem('session', 'remove-me')
    const { platform } = await loadPlatform()

    await platform.clearBrowsingData({ cache: false })

    expect(localStorage.getItem('tonnet-preferences')).toBe('preferences')
    expect(localStorage.getItem('tonnet-bookmarks')).toBe('bookmarks')
    expect(localStorage.getItem('tonnet-settings')).toBeNull()
    expect(localStorage.getItem('site-data')).toBeNull()
    expect(sessionStorage.getItem('session')).toBeNull()
    expect(tonProxy.clearBrowsingData).toHaveBeenCalledWith({
      cache: false,
      cookies: true,
      history: true,
    })
  })

  it('clears named browser caches while honoring storage opt-outs', async () => {
    localStorage.setItem('site-data', 'keep-me')
    sessionStorage.setItem('session', 'keep-me')
    const originalCaches = window.caches
    const cacheStorage = {
      keys: vi.fn().mockResolvedValue(['pages', 'assets']),
      delete: vi.fn().mockResolvedValue(true),
    }
    Object.defineProperty(window, 'caches', { configurable: true, value: cacheStorage })

    try {
      const { platform } = await loadPlatform()
      await platform.clearBrowsingData({
        cache: true,
        cookies: false,
        history: false,
        localStorage: false,
        sessionStorage: false,
      })

      expect(localStorage.getItem('site-data')).toBe('keep-me')
      expect(sessionStorage.getItem('session')).toBe('keep-me')
      expect(cacheStorage.delete).toHaveBeenCalledTimes(2)
      expect(tonProxy.clearBrowsingData).toHaveBeenCalledWith({
        cache: true,
        cookies: false,
        history: false,
      })
    } finally {
      if (originalCaches) {
        Object.defineProperty(window, 'caches', { configurable: true, value: originalCaches })
      } else {
        Reflect.deleteProperty(window, 'caches')
      }
    }
  })

  it('uses a deterministic web fallback', async () => {
    runtime.platform = 'web'
    const { platform } = await loadPlatform()

    expect(platform.isAndroid).toBe(false)
    await expect(platform.proxy.getStatus()).resolves.toEqual({ running: false })
    await expect(platform.proxy.getLogs()).resolves.toEqual([])
    await expect(platform.proxy.connect()).rejects.toMatchObject({ code: 'UNAVAILABLE' })
    await expect(platform.proxy.disconnect()).resolves.toBeUndefined()
    await expect(platform.setThirdPartyCookies(true)).resolves.toBeUndefined()
    expect(tonProxy.start).not.toHaveBeenCalled()
    expect(tonProxy.stop).not.toHaveBeenCalled()
    expect(tonProxy.setThirdPartyCookies).not.toHaveBeenCalled()
  })
})

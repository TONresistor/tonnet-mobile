import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useProxyStore } from '@/stores/proxy'
import { useProxy } from '../useProxy'

const initialState = useProxyStore.getState()

describe('useProxy', () => {
  const connect = vi.fn(async () => undefined)
  const checkStatus = vi.fn(async () => undefined)

  beforeEach(() => {
    connect.mockClear()
    checkStatus.mockClear()
    useProxyStore.setState(
      {
        ...initialState,
        status: 'connecting',
        error: null,
        connect,
        checkStatus,
      },
      true,
    )
  })

  afterEach(() => {
    useProxyStore.setState(initialState, true)
  })

  it('derives connection flags and checks native status on mount', async () => {
    const { result } = renderHook(() => useProxy())

    expect(result.current).toMatchObject({
      status: 'connecting',
      isConnecting: true,
      isConnected: false,
      error: null,
    })
    await waitFor(() => expect(checkStatus).toHaveBeenCalledOnce())

    await act(async () => {
      await result.current.connect()
    })
    expect(connect).toHaveBeenCalledOnce()
  })

  it('reacts to connected and error states from the shared store', () => {
    const { result } = renderHook(() => useProxy())

    act(() => {
      useProxyStore.setState({ status: 'connected' })
    })
    expect(result.current).toMatchObject({
      status: 'connected',
      isConnecting: false,
      isConnected: true,
    })

    act(() => {
      useProxyStore.setState({ status: 'error', error: 'Native failure' })
    })
    expect(result.current).toMatchObject({
      status: 'error',
      isConnecting: false,
      isConnected: false,
      error: 'Native failure',
    })
  })
})

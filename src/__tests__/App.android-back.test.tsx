import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'
import { INTERNAL_ROUTES } from '@/shared/constants'
import { useNavigationStore } from '@/stores/navigation'
import { defaultPreferences, usePreferencesStore } from '@/stores/preferences'
import { useProxyStore } from '@/stores/proxy'
import App from '../App'

const capacitorMocks = vi.hoisted(() => ({
  addListener: vi.fn(),
  backHandler: null as (() => void) | null,
  minimizeApp: vi.fn(),
  removeListener: vi.fn(async () => {}),
}))

vi.mock('@capacitor/app', () => ({
  App: {
    addListener: capacitorMocks.addListener,
    minimizeApp: capacitorMocks.minimizeApp,
  },
}))

vi.mock('@/hooks/useIsMobile', () => ({
  useIsMobile: () => true,
}))

vi.mock('@/platform', () => ({
  platform: {
    isAndroid: true,
    clearBrowsingData: vi.fn(async () => {}),
    setThirdPartyCookies: vi.fn(async () => {}),
    on: vi.fn(() => ({ remove: vi.fn() })),
    proxy: {
      connect: vi.fn(async () => ({ success: true, port: 8080, anonymous: false })),
      disconnect: vi.fn(async () => {}),
      getLogs: vi.fn(async () => []),
      getStatus: vi.fn(async () => ({ running: true, port: 8080, anonymous: false })),
    },
  },
}))

describe('App Android Back handling', () => {
  beforeEach(() => {
    capacitorMocks.addListener.mockReset()
    capacitorMocks.addListener.mockImplementation((event: string, handler: () => void) => {
      if (event === 'backButton') capacitorMocks.backHandler = handler
      return Promise.resolve({ remove: capacitorMocks.removeListener })
    })
    capacitorMocks.backHandler = null
    capacitorMocks.minimizeApp.mockClear()
    usePreferencesStore.setState({
      preferences: { ...defaultPreferences },
      isLoaded: true,
    })
    useProxyStore.setState({ status: 'connected', error: null, isAnonymous: false })
    useNavigationStore.getState().resetSession()
    useNavigationStore.getState().navigate(INTERNAL_ROUTES.settings)
  })

  it('closes a child-page overlay before changing navigation history', async () => {
    render(<App />)
    await screen.findByRole('heading', { name: 'Settings' })
    await waitFor(() => expect(capacitorMocks.backHandler).not.toBeNull())

    fireEvent.click(screen.getByRole('button', { name: /Language/ }))
    expect(screen.getByRole('dialog', { name: 'Language' })).toBeInTheDocument()

    act(() => capacitorMocks.backHandler?.())

    expect(screen.queryByRole('dialog', { name: 'Language' })).not.toBeInTheDocument()
    expect(useNavigationStore.getState().activeView).toBe('settings')
    expect(capacitorMocks.minimizeApp).not.toHaveBeenCalled()

    act(() => capacitorMocks.backHandler?.())
    expect(useNavigationStore.getState().activeView).toBe('start')
    expect(capacitorMocks.minimizeApp).not.toHaveBeenCalled()
    expect(
      capacitorMocks.addListener.mock.calls.filter(([event]) => event === 'backButton'),
    ).toHaveLength(1)
  })
})

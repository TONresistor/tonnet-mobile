import { render, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { LandingPage } from '@/components/pages/LandingPage'
import { INTERNAL_ROUTES } from '@/shared/constants'
import { useNavigationStore } from '@/stores/navigation'
import { defaultPreferences, usePreferencesStore } from '@/stores/preferences'
import { useProxyStore } from '@/stores/proxy'

describe('LandingPage', () => {
  beforeEach(() => {
    usePreferencesStore.setState({
      preferences: { ...defaultPreferences },
      isLoaded: true,
    })
    useNavigationStore.getState().resetSession()
    useProxyStore.setState({
      status: 'disconnected',
      error: null,
      isAnonymous: false,
    })
  })

  it('leaves a reset landing session when the proxy is already connected', async () => {
    useProxyStore.setState({ status: 'connected' })

    render(<LandingPage />)

    await waitFor(() => {
      expect(useNavigationStore.getState().currentUrl).toBe(INTERNAL_ROUTES.start)
    })
  })
})

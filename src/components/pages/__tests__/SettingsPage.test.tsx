import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { defaultPreferences, usePreferencesStore } from '@/stores/preferences'
import { SettingsPage } from '../SettingsPage'

describe('SettingsPage', () => {
  beforeEach(() => {
    usePreferencesStore.setState({
      preferences: { ...defaultPreferences },
      isLoaded: true,
    })
  })

  it('renders the settings screen and updates a preference through the public action', () => {
    render(<SettingsPage />)

    expect(screen.getByRole('heading', { name: 'Settings' })).toBeInTheDocument()
    const autoConnect = screen.getByRole('switch', { name: 'Auto-connect' })
    expect(autoConnect).not.toBeChecked()

    fireEvent.click(autoConnect)

    expect(usePreferencesStore.getState().preferences.autoConnect).toBe(true)
    expect(autoConnect).toBeChecked()
  })
})

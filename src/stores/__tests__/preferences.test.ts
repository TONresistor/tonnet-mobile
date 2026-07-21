import { beforeEach, describe, expect, it } from 'vitest'
import { DEFAULT_PROXY_PORT, INTERNAL_ROUTES, STORAGE_KEYS } from '@/shared/constants'
import { defaultPreferences, usePreferencesStore } from '../preferences'

describe('Preferences Store', () => {
  beforeEach(() => {
    usePreferencesStore.setState({
      preferences: { ...defaultPreferences },
      isLoaded: true,
    })
  })

  it('exposes the mobile defaults without persisting a single-option theme', () => {
    expect(defaultPreferences).toEqual({
      homepage: INTERNAL_ROUTES.start,
      language: 'en',
      proxyPort: DEFAULT_PROXY_PORT,
      autoConnect: false,
      anonymousMode: false,
      clearOnExit: false,
      javaScriptEnabled: true,
      thirdPartyCookies: false,
    })
    expect(defaultPreferences).not.toHaveProperty('theme')
  })

  it('updates preferences immediately with type-safe values', () => {
    const { setPreference } = usePreferencesStore.getState()

    setPreference('anonymousMode', true)
    setPreference('proxyPort', 9000)
    setPreference('language', 'fr')

    expect(usePreferencesStore.getState().preferences).toMatchObject({
      anonymousMode: true,
      proxyPort: 9000,
      language: 'fr',
    })
  })

  it('resets every preference to a fresh copy of the defaults', () => {
    const store = usePreferencesStore.getState()
    store.setPreference('autoConnect', true)
    store.setPreference('homepage', 'http://custom.ton')

    store.resetPreferences()

    const preferences = usePreferencesStore.getState().preferences
    expect(preferences).toEqual(defaultPreferences)
    expect(preferences).not.toBe(defaultPreferences)
  })

  it('migrates legacy state, removes unknown fields and fills new defaults', async () => {
    localStorage.setItem(
      STORAGE_KEYS.preferences,
      JSON.stringify({
        version: 0,
        state: {
          preferences: {
            homepage: 'http://legacy.ton',
            language: 'fr',
            proxyPort: 9090,
            autoConnect: true,
            anonymousMode: true,
            clearOnExit: true,
            javaScriptEnabled: false,
            theme: 'resistance-dog',
            obsoleteSetting: true,
          },
        },
      }),
    )

    await usePreferencesStore.persist.rehydrate()

    const state = usePreferencesStore.getState()
    expect(state.isLoaded).toBe(true)
    expect(state.preferences).toEqual({
      homepage: 'http://legacy.ton',
      language: 'fr',
      proxyPort: 9090,
      autoConnect: true,
      anonymousMode: true,
      clearOnExit: true,
      javaScriptEnabled: false,
      thirdPartyCookies: false,
    })
    expect(state.preferences).not.toHaveProperty('theme')
    expect(state.preferences).not.toHaveProperty('obsoleteSetting')
  })

  it('falls back to safe defaults for invalid persisted values', async () => {
    localStorage.setItem(
      STORAGE_KEYS.preferences,
      JSON.stringify({
        version: 0,
        state: {
          preferences: {
            homepage: '',
            language: 'unsupported',
            proxyPort: '8080',
            autoConnect: 'yes',
          },
        },
      }),
    )

    await usePreferencesStore.persist.rehydrate()

    expect(usePreferencesStore.getState().preferences).toEqual(defaultPreferences)
  })

  it.each([80, 65_536])('rejects an out-of-range persisted proxy port: %s', async (proxyPort) => {
    localStorage.setItem(
      STORAGE_KEYS.preferences,
      JSON.stringify({
        version: 0,
        state: { preferences: { ...defaultPreferences, proxyPort } },
      }),
    )

    await usePreferencesStore.persist.rehydrate()

    expect(usePreferencesStore.getState().preferences.proxyPort).toBe(DEFAULT_PROXY_PORT)
  })
})

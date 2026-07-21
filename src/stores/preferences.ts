import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { isLanguageCode, type LanguageCode } from '@/i18n/registry'
import { normalizeUrl } from '@/lib/url'
import {
  DEFAULT_LANGUAGE,
  DEFAULT_PROXY_PORT,
  INTERNAL_ROUTES,
  STORAGE_KEYS,
} from '@/shared/constants'

interface AppPreferences {
  homepage: string
  language: LanguageCode
  proxyPort: number
  autoConnect: boolean
  anonymousMode: boolean
  clearOnExit: boolean
  javaScriptEnabled: boolean
  thirdPartyCookies: boolean
}

export const defaultPreferences: AppPreferences = {
  homepage: INTERNAL_ROUTES.start,
  language: DEFAULT_LANGUAGE,
  proxyPort: DEFAULT_PROXY_PORT,
  autoConnect: false,
  anonymousMode: false,
  clearOnExit: false,
  javaScriptEnabled: true,
  thirdPartyCookies: false,
}

interface PreferencesState {
  preferences: AppPreferences
  isLoaded: boolean
  setPreference: <Key extends keyof AppPreferences>(key: Key, value: AppPreferences[Key]) => void
  resetPreferences: () => void
}

const PREFERENCES_STORAGE_VERSION = 1

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function booleanOrDefault(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback
}

function sanitizePreferences(value: unknown): AppPreferences {
  const candidate = isRecord(value) ? value : {}
  const proxyPort =
    typeof candidate.proxyPort === 'number' &&
    Number.isInteger(candidate.proxyPort) &&
    candidate.proxyPort > 1024 &&
    candidate.proxyPort <= 65_535
      ? candidate.proxyPort
      : defaultPreferences.proxyPort

  return {
    homepage:
      typeof candidate.homepage === 'string'
        ? normalizeUrl(candidate.homepage) || defaultPreferences.homepage
        : defaultPreferences.homepage,
    language: isLanguageCode(candidate.language) ? candidate.language : defaultPreferences.language,
    proxyPort,
    autoConnect: booleanOrDefault(candidate.autoConnect, defaultPreferences.autoConnect),
    anonymousMode: booleanOrDefault(candidate.anonymousMode, defaultPreferences.anonymousMode),
    clearOnExit: booleanOrDefault(candidate.clearOnExit, defaultPreferences.clearOnExit),
    javaScriptEnabled: booleanOrDefault(
      candidate.javaScriptEnabled,
      defaultPreferences.javaScriptEnabled,
    ),
    thirdPartyCookies: booleanOrDefault(
      candidate.thirdPartyCookies,
      defaultPreferences.thirdPartyCookies,
    ),
  }
}

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      preferences: { ...defaultPreferences },
      isLoaded: false,

      setPreference: (key, value) =>
        set((state) => ({
          preferences: { ...state.preferences, [key]: value },
        })),

      resetPreferences: () => set({ preferences: { ...defaultPreferences } }),
    }),
    {
      name: STORAGE_KEYS.preferences,
      version: PREFERENCES_STORAGE_VERSION,
      partialize: (state) => ({ preferences: state.preferences }),
      migrate: (persistedState) => {
        const persisted = isRecord(persistedState) ? persistedState : {}
        return {
          ...persisted,
          preferences: sanitizePreferences(persisted.preferences),
        } as PreferencesState
      },
      merge: (persistedState, currentState) => {
        const persisted = isRecord(persistedState) ? persistedState : {}
        return {
          ...currentState,
          preferences: sanitizePreferences(persisted.preferences),
          isLoaded: true,
        }
      },
      onRehydrateStorage: () => (state) => {
        if (state) state.isLoaded = true
      },
    },
  ),
)

export function usePreferences(): AppPreferences {
  return usePreferencesStore((state) => state.preferences)
}

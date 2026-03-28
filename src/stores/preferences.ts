/**
 * Preferences store - User preferences management.
 * Handles theme, homepage, network settings, and other app preferences.
 * Adapted for mobile: uses localStorage instead of Electron IPC.
 */

import { create } from 'zustand'
import { persist } from 'zustand/middleware'

// AppPreferences interface - all user-configurable settings (mobile-optimized)
export interface AppPreferences {
  // General
  homepage: string

  // Network
  proxyPort: number
  autoConnect: boolean
  anonymousMode: boolean

  // Appearance
  theme: 'resistance-dog'

  // Privacy
  clearOnExit: boolean
}

// Default preferences (mobile-optimized)
export const defaultPreferences: AppPreferences = {
  // General
  homepage: 'ton://start',

  // Network
  proxyPort: 8080,
  autoConnect: false,
  anonymousMode: false,

  // Appearance
  theme: 'resistance-dog',

  // Privacy
  clearOnExit: false,
}

interface PreferencesState {
  // Current saved preferences
  preferences: AppPreferences

  // Draft for editing (unsaved changes)
  draft: AppPreferences

  // State flags
  isLoaded: boolean
  hasChanges: boolean
  isSaving: boolean

  // Actions
  loadFromMain: () => Promise<void>
  setDraft: <K extends keyof AppPreferences>(key: K, value: AppPreferences[K]) => void
  save: () => Promise<void>
  discard: () => void
  resetToDefaults: () => void

  // Direct preference getters (for components that just need to read)
  getPreference: <K extends keyof AppPreferences>(key: K) => AppPreferences[K]
}

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set, get) => ({
      preferences: { ...defaultPreferences },
      draft: { ...defaultPreferences },
      isLoaded: false,
      hasChanges: false,
      isSaving: false,

      loadFromMain: async () => {
        // On mobile, we just mark as loaded since persist middleware handles loading
        const state = get()
        set({
          draft: { ...state.preferences },
          isLoaded: true,
          hasChanges: false,
        })
      },

      setDraft: (key, value) => {
        const state = get()
        const newDraft = { ...state.draft, [key]: value }

        // Check if there are any changes compared to saved preferences
        const hasChanges = Object.keys(newDraft).some(
          (k) => newDraft[k as keyof AppPreferences] !== state.preferences[k as keyof AppPreferences]
        )

        set({
          draft: newDraft,
          hasChanges,
        })
      },

      save: async () => {
        set({ isSaving: true })
        try {
          const state = get()
          const newPreferences = { ...state.draft }

          // Apply theme change immediately
          if (newPreferences.theme !== state.preferences.theme) {
            document.documentElement.setAttribute('data-theme', newPreferences.theme)
          }

          set({
            preferences: newPreferences,
            hasChanges: false,
            isSaving: false,
          })
        } catch (error) {
          console.error('Failed to save preferences:', error)
          set({ isSaving: false })
        }
      },

      discard: () => {
        const state = get()
        set({
          draft: { ...state.preferences },
          hasChanges: false,
        })
      },

      resetToDefaults: () => {
        set({
          preferences: { ...defaultPreferences },
          draft: { ...defaultPreferences },
          hasChanges: false,
        })
        // Apply default theme
        document.documentElement.setAttribute('data-theme', defaultPreferences.theme)
      },

      getPreference: (key) => {
        return get().preferences[key]
      },
    }),
    {
      name: 'tonnet-preferences',
      partialize: (state) => ({
        preferences: state.preferences,
      }),
      onRehydrateStorage: () => (state) => {
        if (state) {
          // After rehydration, sync draft with preferences and mark as loaded
          state.draft = { ...state.preferences }
          state.isLoaded = true

          // Apply saved theme
          if (state.preferences.theme) {
            document.documentElement.setAttribute('data-theme', state.preferences.theme)
          }
        }
      },
    }
  )
)

// Hook for components that only need to read preferences (not edit)
export function usePreferences(): AppPreferences {
  return usePreferencesStore((state) => state.preferences)
}

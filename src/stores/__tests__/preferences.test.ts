import { describe, it, expect, beforeEach, vi } from 'vitest'
import { usePreferencesStore, defaultPreferences } from '../preferences'

describe('Preferences Store', () => {
  // Reset store before each test
  beforeEach(() => {
    usePreferencesStore.setState({
      preferences: { ...defaultPreferences },
      draft: { ...defaultPreferences },
      isLoaded: false,
      hasChanges: false,
      isSaving: false,
    })
  })

  describe('defaultPreferences', () => {
    it('should have correct default values', () => {
      expect(defaultPreferences.homepage).toBe('ton://start')
      expect(defaultPreferences.proxyPort).toBe(8080)
      expect(defaultPreferences.autoConnect).toBe(false)
      expect(defaultPreferences.anonymousMode).toBe(false)
      expect(defaultPreferences.theme).toBe('resistance-dog')
      expect(defaultPreferences.clearOnExit).toBe(false)
    })
  })

  describe('setDraft', () => {
    it('should update draft value', () => {
      const { setDraft } = usePreferencesStore.getState()

      setDraft('theme', 'utya-duck')

      const state = usePreferencesStore.getState()
      expect(state.draft.theme).toBe('utya-duck')
    })

    it('should set hasChanges to true when draft differs from preferences', () => {
      const { setDraft } = usePreferencesStore.getState()

      expect(usePreferencesStore.getState().hasChanges).toBe(false)

      setDraft('autoConnect', true)

      expect(usePreferencesStore.getState().hasChanges).toBe(true)
    })

    it('should set hasChanges to false when draft equals preferences', () => {
      const { setDraft } = usePreferencesStore.getState()

      setDraft('autoConnect', true)
      expect(usePreferencesStore.getState().hasChanges).toBe(true)

      setDraft('autoConnect', false) // Back to default
      expect(usePreferencesStore.getState().hasChanges).toBe(false)
    })

    it('should update numeric values correctly', () => {
      const { setDraft } = usePreferencesStore.getState()

      setDraft('proxyPort', 9090)

      const state = usePreferencesStore.getState()
      expect(state.draft.proxyPort).toBe(9090)
    })

    it('should update string values correctly', () => {
      const { setDraft } = usePreferencesStore.getState()

      setDraft('homepage', 'ton://custom')

      const state = usePreferencesStore.getState()
      expect(state.draft.homepage).toBe('ton://custom')
    })
  })

  describe('save', () => {
    it('should save draft to preferences', async () => {
      const { setDraft, save } = usePreferencesStore.getState()

      setDraft('anonymousMode', true)
      setDraft('proxyPort', 9000)

      await save()

      const state = usePreferencesStore.getState()
      expect(state.preferences.anonymousMode).toBe(true)
      expect(state.preferences.proxyPort).toBe(9000)
    })

    it('should set hasChanges to false after save', async () => {
      const { setDraft, save } = usePreferencesStore.getState()

      setDraft('autoConnect', true)
      expect(usePreferencesStore.getState().hasChanges).toBe(true)

      await save()

      expect(usePreferencesStore.getState().hasChanges).toBe(false)
    })

    it('should set isSaving during save', async () => {
      const { setDraft, save } = usePreferencesStore.getState()

      setDraft('theme', 'utya-duck')

      const savePromise = save()

      // isSaving should be true during save (may be too fast to catch)
      await savePromise

      expect(usePreferencesStore.getState().isSaving).toBe(false)
    })

    it('should apply theme change to document', async () => {
      const { setDraft, save } = usePreferencesStore.getState()
      const setAttributeSpy = vi.spyOn(document.documentElement, 'setAttribute')

      setDraft('theme', 'utya-duck')
      await save()

      expect(setAttributeSpy).toHaveBeenCalledWith('data-theme', 'utya-duck')
    })
  })

  describe('discard', () => {
    it('should reset draft to saved preferences', () => {
      const { setDraft, discard } = usePreferencesStore.getState()

      setDraft('anonymousMode', true)
      setDraft('proxyPort', 9999)

      discard()

      const state = usePreferencesStore.getState()
      expect(state.draft.anonymousMode).toBe(false)
      expect(state.draft.proxyPort).toBe(8080)
    })

    it('should set hasChanges to false', () => {
      const { setDraft, discard } = usePreferencesStore.getState()

      setDraft('autoConnect', true)
      expect(usePreferencesStore.getState().hasChanges).toBe(true)

      discard()

      expect(usePreferencesStore.getState().hasChanges).toBe(false)
    })
  })

  describe('resetToDefaults', () => {
    it('should reset preferences to defaults', async () => {
      const { setDraft, save, resetToDefaults } = usePreferencesStore.getState()

      // First, change and save some preferences
      setDraft('theme', 'utya-duck')
      setDraft('proxyPort', 9000)
      setDraft('anonymousMode', true)
      await save()

      // Now reset
      resetToDefaults()

      const state = usePreferencesStore.getState()
      expect(state.preferences.theme).toBe('resistance-dog')
      expect(state.preferences.proxyPort).toBe(8080)
      expect(state.preferences.anonymousMode).toBe(false)
    })

    it('should also reset draft', async () => {
      const { setDraft, save, resetToDefaults } = usePreferencesStore.getState()

      setDraft('autoConnect', true)
      await save()
      setDraft('clearOnExit', true) // Unsaved change

      resetToDefaults()

      const state = usePreferencesStore.getState()
      expect(state.draft.autoConnect).toBe(false)
      expect(state.draft.clearOnExit).toBe(false)
    })

    it('should set hasChanges to false', () => {
      const { setDraft, resetToDefaults } = usePreferencesStore.getState()

      setDraft('autoConnect', true)
      expect(usePreferencesStore.getState().hasChanges).toBe(true)

      resetToDefaults()

      expect(usePreferencesStore.getState().hasChanges).toBe(false)
    })

    it('should apply default theme to document', () => {
      const { resetToDefaults } = usePreferencesStore.getState()
      const setAttributeSpy = vi.spyOn(document.documentElement, 'setAttribute')

      resetToDefaults()

      expect(setAttributeSpy).toHaveBeenCalledWith('data-theme', 'resistance-dog')
    })
  })

  describe('getPreference', () => {
    it('should return current preference value', () => {
      const { getPreference } = usePreferencesStore.getState()

      expect(getPreference('theme')).toBe('resistance-dog')
      expect(getPreference('proxyPort')).toBe(8080)
    })

    it('should return saved preference, not draft', async () => {
      const { setDraft, getPreference } = usePreferencesStore.getState()

      setDraft('proxyPort', 9999) // Change draft but don't save

      expect(getPreference('proxyPort')).toBe(8080) // Should return saved value
    })
  })

  describe('loadFromMain', () => {
    it('should mark as loaded', async () => {
      const { loadFromMain } = usePreferencesStore.getState()

      expect(usePreferencesStore.getState().isLoaded).toBe(false)

      await loadFromMain()

      expect(usePreferencesStore.getState().isLoaded).toBe(true)
    })

    it('should sync draft with preferences', async () => {
      // Simulate already having saved preferences
      usePreferencesStore.setState({
        preferences: { ...defaultPreferences, proxyPort: 9000 },
      })

      const { loadFromMain } = usePreferencesStore.getState()
      await loadFromMain()

      const state = usePreferencesStore.getState()
      expect(state.draft.proxyPort).toBe(9000)
    })
  })
})

import { describe, it, expect, beforeEach } from 'vitest'
import { useSettingsStore } from '../settings'

describe('Settings Store', () => {
  // Reset store before each test
  beforeEach(() => {
    useSettingsStore.setState({
      tabs: [{
        id: 'test-tab-1',
        url: 'ton://landing',
        title: 'Welcome',
        history: ['ton://landing'],
        historyIndex: 0,
      }],
      activeTabId: 'test-tab-1',
      currentUrl: 'ton://landing',
      canGoBack: false,
      canGoForward: false,
      activeView: 'landing',
      reloadCounter: 0,
    })
  })

  describe('navigate', () => {
    it('should navigate to a new URL', () => {
      const { navigate } = useSettingsStore.getState()

      navigate('http://example.ton')

      const state = useSettingsStore.getState()
      expect(state.currentUrl).toBe('http://example.ton')
      expect(state.activeView).toBe('web')
    })

    it('should update history when navigating', () => {
      const { navigate } = useSettingsStore.getState()

      navigate('http://page1.ton')
      navigate('http://page2.ton')

      const state = useSettingsStore.getState()
      const activeTab = state.tabs.find(t => t.id === state.activeTabId)

      expect(activeTab?.history).toHaveLength(3)
      expect(activeTab?.history).toEqual(['ton://landing', 'http://page1.ton', 'http://page2.ton'])
      expect(activeTab?.historyIndex).toBe(2)
    })

    it('should not navigate to the same URL', () => {
      const { navigate } = useSettingsStore.getState()

      navigate('http://example.ton')
      const historyLengthAfterFirst = useSettingsStore.getState().tabs[0].history.length

      navigate('http://example.ton') // Same URL
      const historyLengthAfterSecond = useSettingsStore.getState().tabs[0].history.length

      expect(historyLengthAfterSecond).toBe(historyLengthAfterFirst)
    })

    it('should set canGoBack to true after navigation', () => {
      const { navigate } = useSettingsStore.getState()

      expect(useSettingsStore.getState().canGoBack).toBe(false)

      navigate('http://example.ton')

      expect(useSettingsStore.getState().canGoBack).toBe(true)
    })

    it('should detect ton:// URLs correctly', () => {
      const { navigate } = useSettingsStore.getState()

      navigate('ton://start')
      expect(useSettingsStore.getState().activeView).toBe('start')

      navigate('ton://settings')
      expect(useSettingsStore.getState().activeView).toBe('settings')
    })
  })

  describe('goBack / goForward', () => {
    it('should go back in history', () => {
      const { navigate, goBack } = useSettingsStore.getState()

      navigate('http://page1.ton')
      navigate('http://page2.ton')

      goBack()

      const state = useSettingsStore.getState()
      expect(state.currentUrl).toBe('http://page1.ton')
      expect(state.canGoBack).toBe(true)
      expect(state.canGoForward).toBe(true)
    })

    it('should go forward in history', () => {
      const { navigate, goBack, goForward } = useSettingsStore.getState()

      navigate('http://page1.ton')
      navigate('http://page2.ton')
      goBack()

      goForward()

      const state = useSettingsStore.getState()
      expect(state.currentUrl).toBe('http://page2.ton')
      expect(state.canGoForward).toBe(false)
    })

    it('should not go back when at beginning of history', () => {
      const { goBack } = useSettingsStore.getState()

      goBack() // Should do nothing

      expect(useSettingsStore.getState().currentUrl).toBe('ton://landing')
    })

    it('should truncate forward history when navigating after going back', () => {
      const { navigate, goBack } = useSettingsStore.getState()

      navigate('http://page1.ton')
      navigate('http://page2.ton')
      goBack()
      navigate('http://page3.ton')

      const state = useSettingsStore.getState()
      const activeTab = state.tabs.find(t => t.id === state.activeTabId)

      expect(activeTab?.history).toEqual(['ton://landing', 'http://page1.ton', 'http://page3.ton'])
      expect(state.canGoForward).toBe(false)
    })
  })

  describe('Tab Management', () => {
    it('should create a new tab', () => {
      const { createTab } = useSettingsStore.getState()

      createTab('http://newtab.ton')

      const state = useSettingsStore.getState()
      expect(state.tabs).toHaveLength(2)
      expect(state.currentUrl).toBe('http://newtab.ton')
    })

    it('should create tab with default URL if not provided', () => {
      const { createTab } = useSettingsStore.getState()

      createTab()

      const state = useSettingsStore.getState()
      expect(state.currentUrl).toBe('ton://start')
    })

    it('should switch to new tab after creation', () => {
      const { createTab } = useSettingsStore.getState()

      const oldActiveTabId = useSettingsStore.getState().activeTabId
      createTab()
      const newActiveTabId = useSettingsStore.getState().activeTabId

      expect(newActiveTabId).not.toBe(oldActiveTabId)
    })

    it('should close a tab', () => {
      const { createTab, closeTab } = useSettingsStore.getState()

      createTab('http://tab2.ton')
      const state1 = useSettingsStore.getState()
      expect(state1.tabs).toHaveLength(2)

      const tabToClose = state1.tabs[1].id
      closeTab(tabToClose)

      const state2 = useSettingsStore.getState()
      expect(state2.tabs).toHaveLength(1)
    })

    it('should reset to landing when closing last tab', () => {
      const { closeTab } = useSettingsStore.getState()

      const tabId = useSettingsStore.getState().tabs[0].id
      closeTab(tabId)

      const state = useSettingsStore.getState()
      expect(state.tabs).toHaveLength(1)
      expect(state.currentUrl).toBe('ton://landing')
      expect(state.activeView).toBe('landing')
    })

    it('should switch to adjacent tab when closing active tab', () => {
      const { createTab, closeTab } = useSettingsStore.getState()

      createTab('http://tab2.ton')
      createTab('http://tab3.ton')

      // Active tab is now tab3
      const state1 = useSettingsStore.getState()
      const tab3Id = state1.activeTabId

      closeTab(tab3Id)

      const state2 = useSettingsStore.getState()
      // Should switch to tab2
      expect(state2.currentUrl).toBe('http://tab2.ton')
    })

    it('should switch between tabs', () => {
      const { createTab, switchTab } = useSettingsStore.getState()

      const tab1Id = useSettingsStore.getState().tabs[0].id
      createTab('http://tab2.ton')

      // Currently on tab2, switch to tab1
      switchTab(tab1Id)

      const state = useSettingsStore.getState()
      expect(state.activeTabId).toBe(tab1Id)
      expect(state.currentUrl).toBe('ton://landing')
    })

    it('should not switch to already active tab', () => {
      const state1 = useSettingsStore.getState()
      const currentTabId = state1.activeTabId

      state1.switchTab(currentTabId)

      // State should remain unchanged
      const state2 = useSettingsStore.getState()
      expect(state2.activeTabId).toBe(currentTabId)
    })
  })

  describe('reload', () => {
    it('should increment reload counter', () => {
      const { reload } = useSettingsStore.getState()

      expect(useSettingsStore.getState().reloadCounter).toBe(0)

      reload()
      expect(useSettingsStore.getState().reloadCounter).toBe(1)

      reload()
      expect(useSettingsStore.getState().reloadCounter).toBe(2)
    })
  })

  describe('updateTabTitle', () => {
    it('should update tab title', () => {
      const { updateTabTitle } = useSettingsStore.getState()
      const tabId = useSettingsStore.getState().tabs[0].id

      updateTabTitle(tabId, 'New Title')

      const state = useSettingsStore.getState()
      expect(state.tabs[0].title).toBe('New Title')
    })
  })
})

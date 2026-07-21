import { beforeEach, describe, expect, it } from 'vitest'
import { INTERNAL_ROUTES, NAVIGATION_HISTORY_LIMIT, STORAGE_KEYS } from '@/shared/constants'
import { useNavigationStore } from '../navigation'

const initialTab = {
  id: 'test-tab-1',
  url: INTERNAL_ROUTES.landing,
  title: 'Welcome',
  history: [INTERNAL_ROUTES.landing],
  historyIndex: 0,
}

describe('Navigation Store', () => {
  beforeEach(() => {
    useNavigationStore.setState({
      tabs: [{ ...initialTab, history: [...initialTab.history] }],
      activeTabId: initialTab.id,
      currentUrl: initialTab.url,
      canGoBack: false,
      canGoForward: false,
      activeView: 'landing',
      reloadCounter: 0,
    })
  })

  describe('history navigation', () => {
    it('navigates, updates the view and avoids duplicate history entries', () => {
      const { navigate } = useNavigationStore.getState()

      navigate('http://example.ton')
      navigate('http://example.ton')

      const state = useNavigationStore.getState()
      expect(state.currentUrl).toBe('http://example.ton')
      expect(state.activeView).toBe('web')
      expect(state.canGoBack).toBe(true)
      expect(state.tabs[0].history).toEqual([INTERNAL_ROUTES.landing, 'http://example.ton'])
    })

    it('maps internal routes and unknown internal URLs to application views', () => {
      const { navigate } = useNavigationStore.getState()

      navigate(INTERNAL_ROUTES.start)
      expect(useNavigationStore.getState().activeView).toBe('start')
      navigate(INTERNAL_ROUTES.settings)
      expect(useNavigationStore.getState().activeView).toBe('settings')
      navigate('ton://unknown')
      expect(useNavigationStore.getState().activeView).toBe('start')
    })

    it('moves backward and forward without crossing history boundaries', () => {
      const store = useNavigationStore.getState()
      store.goBack()
      store.navigate('http://page-1.ton')
      store.navigate('http://page-2.ton')
      store.goBack()

      expect(useNavigationStore.getState()).toMatchObject({
        currentUrl: 'http://page-1.ton',
        canGoBack: true,
        canGoForward: true,
      })

      store.goForward()
      store.goForward()
      expect(useNavigationStore.getState()).toMatchObject({
        currentUrl: 'http://page-2.ton',
        canGoForward: false,
      })
    })

    it('drops forward history after navigating from an older entry', () => {
      const store = useNavigationStore.getState()
      store.navigate('http://page-1.ton')
      store.navigate('http://page-2.ton')
      store.goBack()
      store.navigate('http://page-3.ton')

      expect(useNavigationStore.getState().tabs[0].history).toEqual([
        INTERNAL_ROUTES.landing,
        'http://page-1.ton',
        'http://page-3.ton',
      ])
    })

    it('caps each tab history at the configured limit', () => {
      const { navigate } = useNavigationStore.getState()
      for (let index = 0; index <= NAVIGATION_HISTORY_LIMIT; index += 1) {
        navigate(`http://page-${index}.ton`)
      }

      const state = useNavigationStore.getState()
      expect(state.tabs[0].history).toHaveLength(NAVIGATION_HISTORY_LIMIT)
      expect(state.tabs[0].history[0]).toBe('http://page-1.ton')
      expect(state.tabs[0].historyIndex).toBe(NAVIGATION_HISTORY_LIMIT - 1)
    })
  })

  describe('tab management', () => {
    it('creates a selected start tab by default and supports switching', () => {
      const firstTabId = useNavigationStore.getState().activeTabId

      useNavigationStore.getState().createTab()
      const secondTabId = useNavigationStore.getState().activeTabId

      expect(secondTabId).not.toBe(firstTabId)
      expect(useNavigationStore.getState().currentUrl).toBe(INTERNAL_ROUTES.start)

      useNavigationStore.getState().switchTab(firstTabId)
      expect(useNavigationStore.getState().currentUrl).toBe(INTERNAL_ROUTES.landing)

      useNavigationStore.getState().switchTab('missing-tab')
      expect(useNavigationStore.getState().activeTabId).toBe(firstTabId)
    })

    it('selects an adjacent tab when the active tab closes', () => {
      const store = useNavigationStore.getState()
      store.createTab('http://second.ton')
      store.createTab('http://third.ton')
      const thirdTabId = useNavigationStore.getState().activeTabId

      store.closeTab(thirdTabId)

      expect(useNavigationStore.getState()).toMatchObject({
        currentUrl: 'http://second.ton',
        activeView: 'web',
      })
    })

    it('ignores an unknown close request and resets the final tab', () => {
      const store = useNavigationStore.getState()
      store.closeTab('missing-tab')
      expect(useNavigationStore.getState().tabs).toHaveLength(1)

      store.closeTab(initialTab.id)
      const state = useNavigationStore.getState()
      expect(state.tabs).toHaveLength(1)
      expect(state.currentUrl).toBe(INTERNAL_ROUTES.landing)
      expect(state.tabs[0].id).not.toBe(initialTab.id)
    })

    it('increments reload requests', () => {
      const store = useNavigationStore.getState()
      store.reload()
      store.reload()

      expect(useNavigationStore.getState().reloadCounter).toBe(2)
    })

    it('resets the complete browsing session', () => {
      const store = useNavigationStore.getState()
      store.navigate('http://example.ton')
      store.createTab('http://second.ton')
      store.reload()

      store.resetSession()

      expect(useNavigationStore.getState()).toMatchObject({
        currentUrl: INTERNAL_ROUTES.landing,
        activeView: 'landing',
        canGoBack: false,
        canGoForward: false,
        reloadCounter: 0,
      })
      expect(useNavigationStore.getState().tabs).toHaveLength(1)
    })
  })

  it('rehydrates valid legacy tabs and derives computed navigation state', async () => {
    localStorage.setItem(
      STORAGE_KEYS.navigation,
      JSON.stringify({
        version: 0,
        state: {
          activeTabId: 'persisted-tab',
          tabs: [
            {
              id: 'persisted-tab',
              url: 'http://two.ton',
              title: 'Custom title',
              history: [INTERNAL_ROUTES.start, 'http://one.ton', 'http://two.ton'],
              historyIndex: 2,
            },
          ],
        },
      }),
    )

    await useNavigationStore.persist.rehydrate()

    expect(useNavigationStore.getState()).toMatchObject({
      activeTabId: 'persisted-tab',
      currentUrl: 'http://two.ton',
      activeView: 'web',
      canGoBack: true,
      canGoForward: false,
    })
    expect(useNavigationStore.getState().tabs[0].title).toBe('Custom title')
  })
})

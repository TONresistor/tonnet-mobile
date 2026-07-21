import { describe, expect, it } from 'vitest'
import { INTERNAL_ROUTES, NAVIGATION_HISTORY_LIMIT } from '@/shared/constants'
import { appendHistory, createBrowserTab, getTitleFromUrl, getViewFromUrl } from '../navigation'

describe('navigation helpers', () => {
  it.each([
    ['', 'landing'],
    [INTERNAL_ROUTES.landing, 'landing'],
    [INTERNAL_ROUTES.start, 'start'],
    [INTERNAL_ROUTES.settings, 'settings'],
    ['ton://unknown', 'start'],
    ['http://example.ton', 'web'],
  ] as const)('maps %s to the %s view', (url, view) => {
    expect(getViewFromUrl(url)).toBe(view)
  })

  it('derives internal labels and external hostnames', () => {
    expect(getTitleFromUrl(INTERNAL_ROUTES.landing)).toBe('Welcome')
    expect(getTitleFromUrl(INTERNAL_ROUTES.start)).toBe('Start')
    expect(getTitleFromUrl(INTERNAL_ROUTES.settings)).toBe('Settings')
    expect(getTitleFromUrl('https://docs.example.ton/path')).toBe('docs.example.ton')
    expect(getTitleFromUrl('not a valid url')).toBe('not a valid url')
  })

  it('creates an invariant browser tab for the requested URL', () => {
    const tab = createBrowserTab('http://example.ton')

    expect(tab.id).toMatch(/^tab_/)
    expect(tab).toMatchObject({
      url: 'http://example.ton',
      title: 'example.ton',
      history: ['http://example.ton'],
      historyIndex: 0,
    })
  })

  it('truncates forward entries and enforces the history limit', () => {
    const tab = createBrowserTab(INTERNAL_ROUTES.start)
    tab.history = Array.from({ length: NAVIGATION_HISTORY_LIMIT }, (_, index) => `page-${index}`)
    tab.historyIndex = tab.history.length - 2

    const result = appendHistory(tab, 'replacement')

    expect(result.history).toHaveLength(NAVIGATION_HISTORY_LIMIT)
    expect(result.history[result.history.length - 1]).toBe('replacement')
    expect(result.history).not.toContain(`page-${NAVIGATION_HISTORY_LIMIT - 1}`)
    expect(result.historyIndex).toBe(NAVIGATION_HISTORY_LIMIT - 1)
  })
})

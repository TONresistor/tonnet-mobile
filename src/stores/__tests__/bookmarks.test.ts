import { beforeEach, describe, expect, it } from 'vitest'
import { DEFAULT_BOOKMARKS } from '@/shared/constants'
import { useBookmarksStore } from '../bookmarks'

describe('Bookmarks Store', () => {
  beforeEach(() => {
    useBookmarksStore.setState({ bookmarks: [] })
  })

  it('adds a complete bookmark and prevents duplicate URLs', () => {
    const { addBookmark } = useBookmarksStore.getState()
    const before = Date.now()

    addBookmark('http://example.ton', 'Example', 'http://example.ton/favicon.ico')
    addBookmark('http://example.ton', 'Duplicate')

    const [bookmark] = useBookmarksStore.getState().bookmarks
    expect(useBookmarksStore.getState().bookmarks).toHaveLength(1)
    expect(bookmark).toMatchObject({
      url: 'http://example.ton',
      title: 'Example',
      favicon: 'http://example.ton/favicon.ico',
    })
    expect(bookmark.id).toMatch(/^bm_/)
    expect(bookmark.createdAt).toBeGreaterThanOrEqual(before)
  })

  it('removes only the requested bookmark and ignores unknown IDs', () => {
    const store = useBookmarksStore.getState()
    store.addBookmark('http://first.ton', 'First')
    store.addBookmark('http://second.ton', 'Second')
    const firstId = useBookmarksStore.getState().bookmarks[0].id

    store.removeBookmark('missing')
    store.removeBookmark(firstId)

    expect(useBookmarksStore.getState().bookmarks).toEqual([
      expect.objectContaining({ url: 'http://second.ton' }),
    ])
  })

  it('updates editable fields while preserving identity and creation time', () => {
    const store = useBookmarksStore.getState()
    store.addBookmark('http://old.ton', 'Old title')
    const original = useBookmarksStore.getState().bookmarks[0]

    store.updateBookmark(original.id, { url: 'http://new.ton', title: 'New title' })

    expect(useBookmarksStore.getState().bookmarks[0]).toEqual({
      ...original,
      url: 'http://new.ton',
      title: 'New title',
    })
  })

  it('creates fresh default entities on every reset', () => {
    const { resetBookmarks } = useBookmarksStore.getState()

    resetBookmarks()
    const firstDefaults = useBookmarksStore.getState().bookmarks
    resetBookmarks()
    const secondDefaults = useBookmarksStore.getState().bookmarks

    expect(secondDefaults.map(({ url, title }) => ({ url, title }))).toEqual(DEFAULT_BOOKMARKS)
    expect(new Set(secondDefaults.map((bookmark) => bookmark.id)).size).toBe(
      DEFAULT_BOOKMARKS.length,
    )
    expect(secondDefaults.every((bookmark) => bookmark.id.startsWith('bm_'))).toBe(true)
    expect(secondDefaults.every((bookmark) => Number.isFinite(bookmark.createdAt))).toBe(true)
    expect(secondDefaults.map((bookmark) => bookmark.id)).not.toEqual(
      firstDefaults.map((bookmark) => bookmark.id),
    )
  })
})

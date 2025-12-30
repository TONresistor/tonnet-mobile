import { describe, it, expect, beforeEach } from 'vitest'
import { useBookmarksStore } from '../bookmarks'

describe('Bookmarks Store', () => {
  // Reset store before each test
  beforeEach(() => {
    useBookmarksStore.setState({
      bookmarks: [],
    })
  })

  describe('addBookmark', () => {
    it('should add a new bookmark', () => {
      const { addBookmark } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Example Site')

      const state = useBookmarksStore.getState()
      expect(state.bookmarks).toHaveLength(1)
      expect(state.bookmarks[0].url).toBe('http://example.ton')
      expect(state.bookmarks[0].title).toBe('Example Site')
    })

    it('should add bookmark with favicon', () => {
      const { addBookmark } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Example', 'http://example.ton/favicon.ico')

      const state = useBookmarksStore.getState()
      expect(state.bookmarks[0].favicon).toBe('http://example.ton/favicon.ico')
    })

    it('should generate unique id for bookmark', () => {
      const { addBookmark } = useBookmarksStore.getState()

      addBookmark('http://site1.ton', 'Site 1')
      addBookmark('http://site2.ton', 'Site 2')

      const state = useBookmarksStore.getState()
      expect(state.bookmarks[0].id).not.toBe(state.bookmarks[1].id)
      expect(state.bookmarks[0].id).toMatch(/^bm_/)
    })

    it('should set createdAt timestamp', () => {
      const { addBookmark } = useBookmarksStore.getState()
      const before = Date.now()

      addBookmark('http://example.ton', 'Example')

      const state = useBookmarksStore.getState()
      const after = Date.now()

      expect(state.bookmarks[0].createdAt).toBeGreaterThanOrEqual(before)
      expect(state.bookmarks[0].createdAt).toBeLessThanOrEqual(after)
    })

    it('should not add duplicate bookmark', () => {
      const { addBookmark } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Example 1')
      addBookmark('http://example.ton', 'Example 2') // Same URL

      const state = useBookmarksStore.getState()
      expect(state.bookmarks).toHaveLength(1)
      expect(state.bookmarks[0].title).toBe('Example 1')
    })
  })

  describe('removeBookmark', () => {
    it('should remove a bookmark by id', () => {
      const { addBookmark, removeBookmark } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Example')
      const bookmarkId = useBookmarksStore.getState().bookmarks[0].id

      removeBookmark(bookmarkId)

      const state = useBookmarksStore.getState()
      expect(state.bookmarks).toHaveLength(0)
    })

    it('should only remove the specified bookmark', () => {
      const { addBookmark, removeBookmark } = useBookmarksStore.getState()

      addBookmark('http://site1.ton', 'Site 1')
      addBookmark('http://site2.ton', 'Site 2')
      const bookmarkIdToRemove = useBookmarksStore.getState().bookmarks[0].id

      removeBookmark(bookmarkIdToRemove)

      const state = useBookmarksStore.getState()
      expect(state.bookmarks).toHaveLength(1)
      expect(state.bookmarks[0].url).toBe('http://site2.ton')
    })

    it('should do nothing if id does not exist', () => {
      const { addBookmark, removeBookmark } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Example')

      removeBookmark('non-existent-id')

      const state = useBookmarksStore.getState()
      expect(state.bookmarks).toHaveLength(1)
    })
  })

  describe('updateBookmark', () => {
    it('should update bookmark title', () => {
      const { addBookmark, updateBookmark } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Old Title')
      const bookmarkId = useBookmarksStore.getState().bookmarks[0].id

      updateBookmark(bookmarkId, { title: 'New Title' })

      const state = useBookmarksStore.getState()
      expect(state.bookmarks[0].title).toBe('New Title')
    })

    it('should update bookmark url', () => {
      const { addBookmark, updateBookmark } = useBookmarksStore.getState()

      addBookmark('http://old.ton', 'Site')
      const bookmarkId = useBookmarksStore.getState().bookmarks[0].id

      updateBookmark(bookmarkId, { url: 'http://new.ton' })

      const state = useBookmarksStore.getState()
      expect(state.bookmarks[0].url).toBe('http://new.ton')
    })

    it('should preserve other fields when updating', () => {
      const { addBookmark, updateBookmark } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Original Title', 'http://favicon.ico')
      const bookmark = useBookmarksStore.getState().bookmarks[0]

      updateBookmark(bookmark.id, { title: 'Updated Title' })

      const state = useBookmarksStore.getState()
      expect(state.bookmarks[0].url).toBe('http://example.ton')
      expect(state.bookmarks[0].favicon).toBe('http://favicon.ico')
      expect(state.bookmarks[0].createdAt).toBe(bookmark.createdAt)
    })
  })

  describe('isBookmarked', () => {
    it('should return true for bookmarked URL', () => {
      const { addBookmark, isBookmarked } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Example')

      expect(isBookmarked('http://example.ton')).toBe(true)
    })

    it('should return false for non-bookmarked URL', () => {
      const { isBookmarked } = useBookmarksStore.getState()

      expect(isBookmarked('http://notbookmarked.ton')).toBe(false)
    })
  })

  describe('getBookmarkByUrl', () => {
    it('should return bookmark for existing URL', () => {
      const { addBookmark, getBookmarkByUrl } = useBookmarksStore.getState()

      addBookmark('http://example.ton', 'Example')

      const bookmark = getBookmarkByUrl('http://example.ton')
      expect(bookmark).toBeDefined()
      expect(bookmark?.title).toBe('Example')
    })

    it('should return undefined for non-existing URL', () => {
      const { getBookmarkByUrl } = useBookmarksStore.getState()

      const bookmark = getBookmarkByUrl('http://notfound.ton')
      expect(bookmark).toBeUndefined()
    })
  })

  describe('importBookmarks', () => {
    it('should import new bookmarks', () => {
      const { importBookmarks } = useBookmarksStore.getState()

      importBookmarks([
        { id: 'imp1', url: 'http://import1.ton', title: 'Import 1', createdAt: Date.now() },
        { id: 'imp2', url: 'http://import2.ton', title: 'Import 2', createdAt: Date.now() },
      ])

      const state = useBookmarksStore.getState()
      expect(state.bookmarks).toHaveLength(2)
    })

    it('should not import duplicates', () => {
      const { addBookmark, importBookmarks } = useBookmarksStore.getState()

      addBookmark('http://existing.ton', 'Existing')

      importBookmarks([
        { id: 'imp1', url: 'http://existing.ton', title: 'Duplicate', createdAt: Date.now() },
        { id: 'imp2', url: 'http://new.ton', title: 'New', createdAt: Date.now() },
      ])

      const state = useBookmarksStore.getState()
      expect(state.bookmarks).toHaveLength(2)
      // Original title should be preserved
      expect(state.bookmarks.find(b => b.url === 'http://existing.ton')?.title).toBe('Existing')
    })

    it('should generate ids for bookmarks without ids', () => {
      const { importBookmarks } = useBookmarksStore.getState()

      importBookmarks([
        { id: '', url: 'http://noid.ton', title: 'No ID', createdAt: Date.now() },
      ])

      const state = useBookmarksStore.getState()
      expect(state.bookmarks[0].id).toMatch(/^bm_/)
    })
  })

  describe('reorderBookmarks', () => {
    it('should reorder bookmarks', () => {
      const { addBookmark, reorderBookmarks } = useBookmarksStore.getState()

      addBookmark('http://first.ton', 'First')
      addBookmark('http://second.ton', 'Second')
      addBookmark('http://third.ton', 'Third')

      // Move first to last
      reorderBookmarks(0, 2)

      const state = useBookmarksStore.getState()
      expect(state.bookmarks[0].url).toBe('http://second.ton')
      expect(state.bookmarks[1].url).toBe('http://third.ton')
      expect(state.bookmarks[2].url).toBe('http://first.ton')
    })

    it('should move item forward correctly', () => {
      const { addBookmark, reorderBookmarks } = useBookmarksStore.getState()

      addBookmark('http://a.ton', 'A')
      addBookmark('http://b.ton', 'B')
      addBookmark('http://c.ton', 'C')

      // Move last to first
      reorderBookmarks(2, 0)

      const state = useBookmarksStore.getState()
      expect(state.bookmarks.map(b => b.title)).toEqual(['C', 'A', 'B'])
    })
  })

  describe('resetBookmarks', () => {
    it('should reset to default bookmarks', () => {
      const { addBookmark, resetBookmarks } = useBookmarksStore.getState()

      addBookmark('http://custom.ton', 'Custom')
      addBookmark('http://another.ton', 'Another')

      resetBookmarks()

      const state = useBookmarksStore.getState()
      // Should have default bookmarks from DEFAULT_BOOKMARKS constant
      expect(state.bookmarks.length).toBeGreaterThanOrEqual(1)
      // Custom bookmarks should be gone
      expect(state.bookmarks.find(b => b.url === 'http://custom.ton')).toBeUndefined()
    })
  })
})

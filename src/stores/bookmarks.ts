/**
 * Bookmarks store - Bookmark management.
 * Handles adding, removing, and organizing bookmarks.
 * Adapted for mobile: uses localStorage for persistence.
 */

import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Bookmark } from '@shared/types'
import { DEFAULT_BOOKMARKS } from '@shared/constants'

interface BookmarksState {
  // Bookmarks list
  bookmarks: Bookmark[]

  // Actions
  addBookmark: (url: string, title: string, favicon?: string) => void
  removeBookmark: (id: string) => void
  updateBookmark: (id: string, updates: Partial<Omit<Bookmark, 'id' | 'createdAt'>>) => void
  isBookmarked: (url: string) => boolean
  getBookmarkByUrl: (url: string) => Bookmark | undefined
  resetBookmarks: () => void
  importBookmarks: (bookmarks: Bookmark[]) => void
  reorderBookmarks: (fromIndex: number, toIndex: number) => void
}

// Generate unique ID for bookmarks
function generateId(): string {
  return `bm_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
}

export const useBookmarksStore = create<BookmarksState>()(
  persist(
    (set, get) => ({
      // Initialize with default bookmarks
      bookmarks: DEFAULT_BOOKMARKS.map((b) => ({
        ...b,
        id: b.id || generateId(),
        createdAt: b.createdAt || Date.now(),
      })),

      addBookmark: (url, title, favicon) => {
        const state = get()

        // Check if already bookmarked
        if (state.bookmarks.some((b) => b.url === url)) {
          return
        }

        const newBookmark: Bookmark = {
          id: generateId(),
          url,
          title,
          favicon,
          createdAt: Date.now(),
        }

        set({
          bookmarks: [...state.bookmarks, newBookmark],
        })
      },

      removeBookmark: (id) => {
        const state = get()
        set({
          bookmarks: state.bookmarks.filter((b) => b.id !== id),
        })
      },

      updateBookmark: (id, updates) => {
        const state = get()
        set({
          bookmarks: state.bookmarks.map((b) =>
            b.id === id ? { ...b, ...updates } : b
          ),
        })
      },

      isBookmarked: (url) => {
        const state = get()
        return state.bookmarks.some((b) => b.url === url)
      },

      getBookmarkByUrl: (url) => {
        const state = get()
        return state.bookmarks.find((b) => b.url === url)
      },

      resetBookmarks: () => {
        set({
          bookmarks: DEFAULT_BOOKMARKS.map((b) => ({
            ...b,
            id: b.id || generateId(),
            createdAt: b.createdAt || Date.now(),
          })),
        })
      },

      importBookmarks: (bookmarks) => {
        const state = get()
        const existingUrls = new Set(state.bookmarks.map((b) => b.url))

        // Filter out duplicates and ensure all have valid IDs
        const newBookmarks = bookmarks
          .filter((b) => !existingUrls.has(b.url))
          .map((b) => ({
            ...b,
            id: b.id || generateId(),
            createdAt: b.createdAt || Date.now(),
          }))

        set({
          bookmarks: [...state.bookmarks, ...newBookmarks],
        })
      },

      reorderBookmarks: (fromIndex, toIndex) => {
        const state = get()
        const bookmarks = [...state.bookmarks]
        const [removed] = bookmarks.splice(fromIndex, 1)
        bookmarks.splice(toIndex, 0, removed)

        set({ bookmarks })
      },
    }),
    {
      name: 'tonnet-bookmarks',
      partialize: (state) => ({
        bookmarks: state.bookmarks,
      }),
    }
  )
)

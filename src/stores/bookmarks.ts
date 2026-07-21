import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { DEFAULT_BOOKMARKS, STORAGE_KEYS } from '@/shared/constants'
import type { Bookmark } from '@/shared/types'

interface BookmarksState {
  bookmarks: Bookmark[]
  addBookmark: (url: string, title: string, favicon?: string) => void
  removeBookmark: (id: string) => void
  updateBookmark: (id: string, updates: Partial<Omit<Bookmark, 'id' | 'createdAt'>>) => void
  resetBookmarks: () => void
}

function createBookmarkId(): string {
  return `bm_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
}

function createDefaultBookmarks(): Bookmark[] {
  return DEFAULT_BOOKMARKS.map((bookmark) => ({
    ...bookmark,
    id: createBookmarkId(),
    createdAt: Date.now(),
  }))
}

export const useBookmarksStore = create<BookmarksState>()(
  persist(
    (set) => ({
      bookmarks: createDefaultBookmarks(),

      addBookmark: (url, title, favicon) =>
        set((state) => {
          if (state.bookmarks.some((bookmark) => bookmark.url === url)) return state

          return {
            bookmarks: [
              ...state.bookmarks,
              {
                id: createBookmarkId(),
                url,
                title,
                favicon,
                createdAt: Date.now(),
              },
            ],
          }
        }),

      removeBookmark: (id) =>
        set((state) => ({
          bookmarks: state.bookmarks.filter((bookmark) => bookmark.id !== id),
        })),

      updateBookmark: (id, updates) =>
        set((state) => ({
          bookmarks: state.bookmarks.map((bookmark) =>
            bookmark.id === id ? { ...bookmark, ...updates } : bookmark,
          ),
        })),

      resetBookmarks: () => set({ bookmarks: createDefaultBookmarks() }),
    }),
    {
      name: STORAGE_KEYS.bookmarks,
      partialize: (state) => ({ bookmarks: state.bookmarks }),
    },
  ),
)

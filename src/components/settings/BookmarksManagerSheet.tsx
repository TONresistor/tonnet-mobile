import { Check, Globe, Pencil, Plus, Trash2, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { BottomSheet } from '@/components/mobile/BottomSheet'
import { useBookmarksStore } from '@/stores/bookmarks'

interface BookmarksManagerSheetProps {
  open: boolean
  onClose: () => void
}

export function BookmarksManagerSheet({ open, onClose }: BookmarksManagerSheetProps) {
  const { t } = useTranslation('settings')
  const { t: tc } = useTranslation('common')
  const { bookmarks, addBookmark, removeBookmark, updateBookmark } = useBookmarksStore()
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editUrl, setEditUrl] = useState('')
  const [adding, setAdding] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newUrl, setNewUrl] = useState('')

  const close = () => {
    setEditingId(null)
    setAdding(false)
    onClose()
  }

  const cancelAdd = () => {
    setAdding(false)
    setNewTitle('')
    setNewUrl('')
  }

  const add = () => {
    const title = newTitle.trim()
    const url = newUrl.trim()
    if (!title || !url) return
    addBookmark(url, title)
    cancelAdd()
  }

  return (
    <BottomSheet
      open={open}
      onClose={close}
      title={t('bookmarks')}
      showHandle
      showCloseButton={false}
      maxHeight="70vh"
    >
      <div className="space-y-1">
        {bookmarks.length === 0 && !adding && (
          <p className="text-muted-foreground text-center py-8 text-[13px]">
            {t('bookmarks_empty')}
          </p>
        )}

        {bookmarks.map((bookmark) => (
          <div key={bookmark.id} className="px-2">
            {editingId === bookmark.id ? (
              <div className="bg-background-secondary rounded-lg p-3 space-y-2">
                <input
                  className="w-full bg-background rounded-lg px-3 py-2 text-sm text-foreground outline-none border border-border"
                  value={editTitle}
                  onChange={(event) => setEditTitle(event.target.value)}
                  placeholder={tc('title')}
                />
                <input
                  className="w-full bg-background rounded-lg px-3 py-2 text-sm text-foreground outline-none border border-border"
                  value={editUrl}
                  onChange={(event) => setEditUrl(event.target.value)}
                  placeholder="URL"
                />
                <div className="flex gap-2 justify-end">
                  <button
                    type="button"
                    onClick={() => setEditingId(null)}
                    aria-label={tc('cancel')}
                    className="p-2 rounded-lg text-muted-foreground active:bg-muted/50"
                  >
                    <X className="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      const title = editTitle.trim()
                      const url = editUrl.trim()
                      if (!title || !url) return
                      updateBookmark(bookmark.id, { title, url })
                      setEditingId(null)
                    }}
                    aria-label={tc('save')}
                    className="p-2 rounded-lg text-primary active:bg-primary/10"
                  >
                    <Check className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ) : (
              <div className="flex items-center gap-3 px-2 py-2.5 rounded-xl active:bg-muted/50 transition-colors">
                {bookmark.favicon ? (
                  <img src={bookmark.favicon} className="w-5 h-5 rounded shrink-0" alt="" />
                ) : (
                  <Globe className="w-5 h-5 text-muted-foreground shrink-0" />
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">{bookmark.title}</p>
                  <p className="text-[12px] text-muted-foreground truncate">{bookmark.url}</p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setEditingId(bookmark.id)
                    setEditTitle(bookmark.title)
                    setEditUrl(bookmark.url)
                  }}
                  aria-label={`Edit ${bookmark.title}`}
                  className="p-2 rounded-lg text-muted-foreground active:bg-muted/50 shrink-0"
                >
                  <Pencil className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => removeBookmark(bookmark.id)}
                  aria-label={`Delete ${bookmark.title}`}
                  className="p-2 rounded-lg text-red-400 active:bg-red-500/10 shrink-0"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            )}
          </div>
        ))}

        {adding ? (
          <div className="px-2">
            <div className="bg-background-secondary rounded-lg p-3 space-y-2">
              <input
                className="w-full bg-background rounded-lg px-3 py-2 text-sm text-foreground outline-none border border-border"
                value={newTitle}
                onChange={(event) => setNewTitle(event.target.value)}
                placeholder={tc('title')}
              />
              <input
                className="w-full bg-background rounded-lg px-3 py-2 text-sm text-foreground outline-none border border-border"
                value={newUrl}
                onChange={(event) => setNewUrl(event.target.value)}
                placeholder="URL"
              />
              <div className="flex gap-2 justify-end">
                <button
                  type="button"
                  onClick={cancelAdd}
                  aria-label={tc('cancel')}
                  className="p-2 rounded-lg text-muted-foreground active:bg-muted/50"
                >
                  <X className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={add}
                  aria-label={tc('save')}
                  className="p-2 rounded-lg text-primary active:bg-primary/10"
                >
                  <Check className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="w-full flex items-center gap-2 px-4 py-3 rounded-xl text-primary active:bg-primary/10 transition-colors"
          >
            <Plus className="h-4 w-4" />
            <span className="text-sm font-medium">{t('bookmarks_add')}</span>
          </button>
        )}
      </div>
    </BottomSheet>
  )
}

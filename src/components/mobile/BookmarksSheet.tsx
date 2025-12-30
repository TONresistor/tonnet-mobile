/**
 * Bookmarks bottom sheet component.
 * Shows list of bookmarks with tap-to-navigate and long-press-to-edit.
 */
import { useState, useRef } from 'react'
import { Globe, Trash2, Check, X } from 'lucide-react'
import { BottomSheet } from './BottomSheet'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useBookmarksStore } from '@/stores/bookmarks'

interface BookmarksSheetProps {
  open: boolean
  onClose: () => void
  onNavigate: (url: string) => void
}

export function BookmarksSheet({ open, onClose, onNavigate }: BookmarksSheetProps) {
  // State for editing
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editUrl, setEditUrl] = useState('')
  const pressTimerRef = useRef<NodeJS.Timeout | null>(null)

  const { bookmarks, updateBookmark, removeBookmark } = useBookmarksStore()

  const handleTouchStart = (id: string, title: string, url: string) => {
    pressTimerRef.current = setTimeout(() => {
      setEditingId(id)
      setEditTitle(title)
      setEditUrl(url)
    }, 500)
  }

  const handleTouchEnd = () => {
    if (pressTimerRef.current) {
      clearTimeout(pressTimerRef.current)
      pressTimerRef.current = null
    }
  }

  const handleTap = (url: string) => {
    if (!editingId) {
      onNavigate(url)
      onClose()
    }
  }

  const handleSave = () => {
    if (editingId && editTitle.trim() && editUrl.trim()) {
      updateBookmark(editingId, { title: editTitle.trim(), url: editUrl.trim() })
      setEditingId(null)
    }
  }

  const handleDelete = () => {
    if (editingId) {
      removeBookmark(editingId)
      setEditingId(null)
    }
  }

  const handleCancel = () => {
    setEditingId(null)
  }

  return (
    <BottomSheet open={open} onClose={onClose} title="Bookmarks">
      {bookmarks.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          <p>No bookmarks yet</p>
          <p className="text-sm mt-1">Tap the star icon to add one</p>
        </div>
      ) : (
        <div className="space-y-1">
          {bookmarks.map((bookmark) => (
            <div key={bookmark.id}>
              {editingId === bookmark.id ? (
                // Edit mode
                <div className="p-3 bg-background-secondary rounded-lg space-y-3">
                  <Input
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    placeholder="Title"
                    className="h-9"
                  />
                  <Input
                    value={editUrl}
                    onChange={(e) => setEditUrl(e.target.value)}
                    placeholder="URL"
                    className="h-9"
                  />
                  <div className="flex gap-2">
                    <Button size="sm" onClick={handleSave} className="flex-1">
                      <Check className="h-4 w-4 mr-1" /> Save
                    </Button>
                    <Button size="sm" variant="ghost" onClick={handleCancel}>
                      <X className="h-4 w-4" />
                    </Button>
                    <Button size="sm" variant="destructive" onClick={handleDelete}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ) : (
                // Normal mode
                <button
                  className="w-full flex items-center gap-3 p-3 rounded-lg active:bg-muted transition-colors text-left"
                  onClick={() => handleTap(bookmark.url)}
                  onTouchStart={() => handleTouchStart(bookmark.id, bookmark.title, bookmark.url)}
                  onTouchEnd={handleTouchEnd}
                  onTouchCancel={handleTouchEnd}
                  onMouseDown={() => handleTouchStart(bookmark.id, bookmark.title, bookmark.url)}
                  onMouseUp={handleTouchEnd}
                  onMouseLeave={handleTouchEnd}
                >
                  {bookmark.favicon ? (
                    <img src={bookmark.favicon} alt="" className="w-5 h-5 rounded" />
                  ) : (
                    <Globe className="w-5 h-5 text-muted-foreground" />
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-foreground truncate">{bookmark.title}</p>
                    <p className="text-xs text-muted-foreground truncate">{bookmark.url}</p>
                  </div>
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </BottomSheet>
  )
}

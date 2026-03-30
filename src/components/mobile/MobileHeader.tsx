/**
 * Mobile header component.
 * Contains home button, address bar, and refresh button.
 */

import { useState, useEffect, useMemo, useRef } from 'react'
import { Home, X, RefreshCw, Star, Bookmark } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { useSettingsStore } from '@/stores/settings'
import { useProxyStore } from '@/stores/proxy'
import { useBookmarksStore } from '@/stores/bookmarks'
import { usePreferences } from '@/stores/preferences'
import { normalizeUrl } from '@/lib/url'

/**
 * Strip http:// prefix and format URL for display
 */
function formatDisplayUrl(url: string): string {
  if (!url) return ''
  return url.replace(/^https?:\/\//, '')
}

/**
 * Check if URL is a TON site (.ton, .adnl or .t.me domain)
 */
function isTonSiteUrl(url: string): boolean {
  if (!url) return false
  try {
    const urlObj = new URL(url.startsWith('http') ? url : `http://${url}`)
    const hostname = urlObj.hostname.toLowerCase()
    return hostname.endsWith('.ton') || hostname.endsWith('.adnl') || hostname.endsWith('.t.me')
  } catch {
    // Fallback for invalid URLs
    return url.includes('.ton') || url.includes('.adnl') || url.includes('.t.me')
  }
}

interface MobileHeaderProps {
  url: string
  onUrlSubmit: (url: string) => void
  onRefresh?: () => void
  onOpenBookmarks: () => void
  isLoading?: boolean
}

export function MobileHeader({
  url,
  onUrlSubmit,
  onRefresh,
  onOpenBookmarks,
  isLoading = false,
}: MobileHeaderProps) {
  const [inputValue, setInputValue] = useState(url)
  const [isFocused, setIsFocused] = useState(false)
  const lastSubmitTime = useRef(0)
  const { navigate } = useSettingsStore()
  const { homepage } = usePreferences()
  const isAnonymous = useProxyStore((state) => state.isAnonymous)
  const { bookmarks, addBookmark, removeBookmark } = useBookmarksStore()

  // Check if current URL is a TON site
  const isTonSite = useMemo(() => isTonSiteUrl(url), [url])

  // Check if current URL is bookmarked (depends on bookmarks array for reactivity)
  const urlIsBookmarked = useMemo(() => {
    return bookmarks.some((b) => b.url === url)
  }, [url, bookmarks])

  // Toggle bookmark for current URL
  const handleToggleBookmark = () => {
    if (!url) return
    if (urlIsBookmarked) {
      const bookmark = bookmarks.find((b) => b.url === url)
      if (bookmark) {
        removeBookmark(bookmark.id)
      }
    } else {
      // Use URL as title for now (could be improved with page title)
      const title = formatDisplayUrl(url)
      addBookmark(url, title)
    }
  }

  // Format display URL (strip http://) when not focused
  const displayValue = useMemo(() => {
    if (isFocused) return inputValue
    return formatDisplayUrl(inputValue)
  }, [inputValue, isFocused])

  // Sync inputValue with url prop when it changes externally
  useEffect(() => {
    if (!isFocused) {
      setInputValue(url)
    }
  }, [url, isFocused])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const now = Date.now()
    if (now - lastSubmitTime.current < 300) return
    lastSubmitTime.current = now
    const trimmedUrl = inputValue.trim()
    if (trimmedUrl) {
      onUrlSubmit(trimmedUrl)
    }
  }

  const handleClear = () => {
    setInputValue('')
  }

  return (
    <header className="fixed top-0 left-0 right-0 bg-background border-b border-border z-50 safe-area-top">
      <div className="flex items-center h-14 px-1 gap-0">
        {/* Home Button */}
        <button
          onClick={() => navigate(normalizeUrl(homepage))}
          className="p-2 rounded-lg text-foreground active:bg-muted transition-colors"
          aria-label="Go home"
        >
          <Home className="h-5 w-5" />
        </button>

        {/* Address Bar */}
        <form onSubmit={handleSubmit} className="flex-1 mx-0.5">
          <div className="relative flex items-center">
            {/* TON Site Badge - shows anonymous mode when active */}
            {isTonSite && !isFocused && (
              <div className={cn(
                "absolute left-2 top-1/2 -translate-y-1/2 z-10 text-xs font-medium px-2 py-0.5 rounded-full",
                isAnonymous
                  ? "bg-[#229ED9] text-white"
                  : "bg-primary text-primary-foreground"
              )}>
                tonsite://
              </div>
            )}
            <Input
              type="text"
              value={isFocused ? inputValue : displayValue}
              onChange={(e) => {
                setInputValue(e.target.value)
              }}
              onFocus={(e) => {
                setIsFocused(true)
                e.target.select()
              }}
              onBlur={() => setIsFocused(false)}
              placeholder="Enter .ton address or URL"
              className={cn(
                "w-full h-9 pr-8 text-base bg-background-secondary rounded-full border-0 focus-visible:ring-1 focus-visible:ring-primary",
                isTonSite && !isFocused ? "pl-[80px]" : "px-4"
              )}
            />
            {inputValue && isFocused && (
              <button
                type="button"
                onClick={handleClear}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground active:text-foreground"
              >
                <X className="h-4 w-4" />
              </button>
            )}
            {/* Star icon for bookmarking - visible when not focused and URL is not empty */}
            {inputValue && !isFocused && (
              <button
                type="button"
                onClick={handleToggleBookmark}
                className="absolute right-3 top-1/2 -translate-y-1/2 active:scale-90 transition-transform"
              >
                <Star
                  className={cn(
                    "h-4 w-4",
                    urlIsBookmarked
                      ? "fill-yellow-400 text-yellow-400"
                      : "text-muted-foreground"
                  )}
                />
              </button>
            )}
          </div>
        </form>

        {/* Refresh Button */}
        <button
          onClick={onRefresh}
          className={cn(
            'p-2 rounded-lg text-foreground active:bg-muted transition-colors',
            isLoading && 'animate-spin'
          )}
          aria-label={isLoading ? 'Loading...' : 'Refresh'}
        >
          <RefreshCw className="h-5 w-5" />
        </button>

        {/* Bookmarks Button */}
        <button
          onClick={onOpenBookmarks}
          className="p-2 rounded-lg text-foreground active:bg-muted transition-colors"
          aria-label="Open bookmarks"
        >
          <Bookmark className="h-5 w-5" />
        </button>
      </div>
    </header>
  )
}

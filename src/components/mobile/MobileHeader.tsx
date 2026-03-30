/**
 * Mobile header component.
 * Contains home button, address bar, and refresh button.
 */

import { useState, useEffect, useMemo, useRef } from 'react'
import { X, RefreshCw, Star, Bookmark } from 'lucide-react'
import { useTranslation } from 'react-i18next'

function HomeIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 22 19" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M10.9996 2.58541L18.4349 9.88078V18.0881C18.4349 18.5903 18.0174 19 17.5055 19H14.2525C13.9948 19 13.7878 18.797 13.7878 18.544V13.0725C13.7878 12.8196 13.5809 12.6165 13.3231 12.6165H8.67611C8.41834 12.6165 8.21141 12.8196 8.21141 13.0725V18.544C8.21141 18.797 8.00447 19 7.7467 19H4.49378C3.98189 19 3.56438 18.5903 3.56438 18.0881V9.88078L10.9996 2.58541ZM11.468 0.155991L11.5478 0.223672L16.5761 5.15375V2.12945C16.5761 1.87653 16.783 1.67349 17.0408 1.67349H17.9702C18.2279 1.67349 18.4349 1.87653 18.4349 2.12945V6.97759L21.7713 10.2548C22.0762 10.5505 22.0762 11.0349 21.7713 11.3306C21.4954 11.6049 21.0597 11.6263 20.7547 11.3983L20.6749 11.3306L10.9996 1.83735L1.32437 11.3306C1.04845 11.6049 0.612792 11.6263 0.307831 11.3983L0.22796 11.3306C-0.0515871 11.0599 -0.0733696 10.6324 0.158982 10.3332L0.22796 10.2548L10.4514 0.223672C10.7273 -0.050617 11.163 -0.0719892 11.468 0.155991Z" fill="currentColor" />
    </svg>
  )
}
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
  const { t } = useTranslation('common')
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
          aria-label={t('go_home')}
        >
          <HomeIcon className="h-5 w-5" />
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
              placeholder={t('enter_ton_address')}
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
          aria-label={isLoading ? t('loading') : t('refresh')}
        >
          <RefreshCw className="h-5 w-5" />
        </button>

        {/* Bookmarks Button */}
        <button
          onClick={onOpenBookmarks}
          className="p-2 rounded-lg text-foreground active:bg-muted transition-colors"
          aria-label={t('open_bookmarks')}
        >
          <Bookmark className="h-5 w-5" />
        </button>
      </div>
    </header>
  )
}

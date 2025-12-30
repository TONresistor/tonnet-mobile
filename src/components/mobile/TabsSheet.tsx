/**
 * Tabs list sheet - displays all open tabs in a bottom sheet.
 * Allows switching between tabs and closing them.
 * Supports swipe-to-delete gesture.
 */

import { Globe, Home, Settings } from 'lucide-react'
import { useRef, useState } from 'react'
import { BottomSheet } from './BottomSheet'
import { cn } from '@/lib/utils'

interface Tab {
  id: string
  url: string
  title: string
}

interface TabsSheetProps {
  open: boolean
  onClose: () => void
  tabs: Tab[]
  activeTabId: string
  onSwitchTab: (tabId: string) => void
  onCloseTab: (tabId: string) => void
}

const SWIPE_THRESHOLD = 100

// Get icon for tab based on URL
function getTabIcon(url: string) {
  if (url === 'ton://start' || url === 'ton://landing') {
    return <Home className="h-4 w-4" />
  }
  if (url === 'ton://settings') {
    return <Settings className="h-4 w-4" />
  }
  return <Globe className="h-4 w-4" />
}

// Swipeable tab item component
function SwipeableTabItem({
  tab,
  isActive,
  onSwitch,
  onDelete,
}: {
  tab: Tab
  isActive: boolean
  onSwitch: () => void
  onDelete: () => void
}) {
  const [swipeX, setSwipeX] = useState(0)
  const [isDeleting, setIsDeleting] = useState(false)
  const startX = useRef(0)
  const startY = useRef(0)
  const isSwiping = useRef(false)

  const handleTouchStart = (e: React.TouchEvent) => {
    startX.current = e.touches[0].clientX
    startY.current = e.touches[0].clientY
    isSwiping.current = false
  }

  const handleTouchMove = (e: React.TouchEvent) => {
    const deltaX = e.touches[0].clientX - startX.current
    const deltaY = e.touches[0].clientY - startY.current

    // Detect horizontal swipe (ignore vertical scroll)
    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
      isSwiping.current = true
      // Only allow swipe left (negative)
      setSwipeX(Math.min(0, deltaX))
    }
  }

  const handleTouchEnd = () => {
    if (swipeX < -SWIPE_THRESHOLD) {
      // Trigger delete animation
      setIsDeleting(true)
      setSwipeX(-400)
      setTimeout(() => {
        onDelete()
      }, 200)
    } else {
      // Reset position
      setSwipeX(0)
    }
  }

  const handleClick = () => {
    // Only trigger click if not swiping
    if (!isSwiping.current && swipeX === 0) {
      onSwitch()
    }
  }

  return (
    <div
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      onClick={handleClick}
      style={{
        transform: `translateX(${swipeX}px)`,
        transition: isSwiping.current ? 'none' : 'transform 250ms cubic-bezier(0.34, 1.56, 0.64, 1)',
        opacity: isDeleting ? 0 : 1,
      }}
      className={cn(
        'flex items-center gap-3 p-3 rounded-xl cursor-pointer',
        isActive
          ? 'bg-[#2AABEE] text-white'
          : 'bg-white/10 active:bg-white/20'
      )}
    >
      {/* Tab Icon */}
      <span className="flex-shrink-0 text-current">
        {getTabIcon(tab.url)}
      </span>

      {/* Tab Title */}
      <span className="flex-1 text-left truncate text-sm text-current">
        {tab.title}
      </span>
    </div>
  )
}

export function TabsSheet({
  open,
  onClose,
  tabs,
  activeTabId,
  onSwitchTab,
  onCloseTab,
}: TabsSheetProps) {
  const handleSwitch = (tabId: string) => {
    onSwitchTab(tabId)
    onClose()
  }

  const handleDelete = (tabId: string) => {
    onCloseTab(tabId)
    if (tabs.length <= 1) {
      onClose()
    }
  }

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title={`Tabs (${tabs.length})`}
      maxHeight="60vh"
    >
      <div className="space-y-2">
        {tabs.map((tab) => (
          <SwipeableTabItem
            key={tab.id}
            tab={tab}
            isActive={tab.id === activeTabId}
            onSwitch={() => handleSwitch(tab.id)}
            onDelete={() => handleDelete(tab.id)}
          />
        ))}
      </div>
    </BottomSheet>
  )
}

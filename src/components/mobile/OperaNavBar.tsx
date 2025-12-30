/**
 * Opera-style bottom navigation bar.
 * Contains: Back, Forward, New Tab, Tabs, Settings
 */

import { ChevronLeft, ChevronRight, Plus, MoreVertical } from 'lucide-react'
import { cn } from '@/lib/utils'

interface OperaNavBarProps {
  canGoBack: boolean
  canGoForward: boolean
  tabCount: number
  onBack: () => void
  onForward: () => void
  onNewTab: () => void
  onOpenTabs: () => void
  onSettings: () => void
}

export function OperaNavBar({
  canGoBack,
  canGoForward,
  tabCount,
  onBack,
  onForward,
  onNewTab,
  onOpenTabs,
  onSettings,
}: OperaNavBarProps) {
  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-background border-t border-border z-50 pb-[env(safe-area-inset-bottom)]">
      <div className="flex items-center justify-around h-14 px-4">
        {/* Back Button */}
        <NavButton
          onClick={onBack}
          disabled={!canGoBack}
          aria-label="Go back"
        >
          <ChevronLeft className="h-6 w-6" />
        </NavButton>

        {/* Forward Button */}
        <NavButton
          onClick={onForward}
          disabled={!canGoForward}
          aria-label="Go forward"
        >
          <ChevronRight className="h-6 w-6" />
        </NavButton>

        {/* New Tab Button */}
        <NavButton
          onClick={onNewTab}
          aria-label="New tab"
        >
          <Plus className="h-6 w-6" />
        </NavButton>

        {/* Tabs Button - rounded square with count */}
        <NavButton
          onClick={onOpenTabs}
          aria-label={`${tabCount} tabs open`}
        >
          <div className="w-6 h-6 border-2 border-current rounded-md flex items-center justify-center">
            <span className="text-xs font-semibold">
              {tabCount > 99 ? '99' : tabCount}
            </span>
          </div>
        </NavButton>

        {/* Settings Button - three dots */}
        <NavButton
          onClick={onSettings}
          aria-label="Settings"
        >
          <MoreVertical className="h-6 w-6" />
        </NavButton>
      </div>
    </nav>
  )
}

// Internal button component
function NavButton({
  children,
  onClick,
  disabled = false,
  'aria-label': ariaLabel,
}: {
  children: React.ReactNode
  onClick: () => void
  disabled?: boolean
  'aria-label': string
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      aria-label={ariaLabel}
      className={cn(
        'w-12 h-12 flex items-center justify-center rounded-xl transition-transform duration-200 spring active:scale-90',
        disabled
          ? 'text-muted-foreground/40 cursor-not-allowed'
          : 'text-foreground active:bg-muted'
      )}
    >
      {children}
    </button>
  )
}

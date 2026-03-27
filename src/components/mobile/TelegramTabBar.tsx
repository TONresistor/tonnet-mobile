/**
 * Telegram iOS-style bottom navigation bar.
 * Pill-shaped glassmorphism container with icon + label tabs.
 */

import { ChevronLeft, ChevronRight, Plus, Layers, Settings } from 'lucide-react'
import { cn } from '@/lib/utils'

interface TelegramTabBarProps {
  canGoBack: boolean
  canGoForward: boolean
  tabCount: number
  activeView: string
  onBack: () => void
  onForward: () => void
  onNewTab: () => void
  onOpenTabs: () => void
  onSettings: () => void
}

export function TelegramTabBar({
  canGoBack,
  canGoForward,
  tabCount,
  activeView,
  onBack,
  onForward,
  onNewTab,
  onOpenTabs,
  onSettings,
}: TelegramTabBarProps) {
  const isSettings = activeView === 'settings'

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-50 px-5 pt-4 pb-[25px]"
      style={{ paddingBottom: 'max(25px, env(safe-area-inset-bottom))' }}
    >
      {/* Pill container — glassmorphism */}
      <div className="relative flex items-center rounded-[296px] pl-1 pr-4 overflow-hidden">
        {/* Glass layers (Figma: Fill + Shadow + Glass Effect) */}
        <div className="absolute inset-0 rounded-[296px] shadow-[0px_8px_40px_0px_rgba(0,0,0,0.12)]">
          <div className="absolute inset-0 rounded-[296px] bg-[rgba(255,255,255,0.08)]" />
          <div className="absolute inset-0 rounded-[296px] bg-background/70 backdrop-blur-2xl" />
          <div className="absolute inset-0 rounded-[296px] border border-white/10" />
        </div>

        {/* Tab buttons */}
        <TabButton
          onClick={onBack}
          disabled={!canGoBack}
          icon={<ChevronLeft className="h-[22px] w-[22px]" strokeWidth={2.5} />}
          label="Back"
        />
        <TabButton
          onClick={onForward}
          disabled={!canGoForward}
          icon={<ChevronRight className="h-[22px] w-[22px]" strokeWidth={2.5} />}
          label="Forward"
        />
        <TabButton
          onClick={onNewTab}
          icon={<Plus className="h-[22px] w-[22px]" strokeWidth={2.5} />}
          label="New"
        />
        <TabButton
          onClick={onOpenTabs}
          icon={
            <div className="relative">
              <Layers className="h-[22px] w-[22px]" strokeWidth={2} />
              <span className="absolute -top-1.5 -right-2.5 min-w-[16px] h-[14px] flex items-center justify-center bg-primary text-primary-foreground text-[9px] font-bold rounded-full px-0.5">
                {tabCount > 99 ? '99' : tabCount}
              </span>
            </div>
          }
          label="Tabs"
        />
        <TabButton
          onClick={onSettings}
          icon={<Settings className="h-[22px] w-[22px]" strokeWidth={2} />}
          label="Settings"
          active={isSettings}
        />
      </div>
    </nav>
  )
}

function TabButton({
  icon,
  label,
  onClick,
  disabled = false,
  active = false,
}: {
  icon: React.ReactNode
  label: string
  onClick: () => void
  disabled?: boolean
  active?: boolean
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className={cn(
        'relative flex-1 flex flex-col items-center justify-center gap-px min-w-[66px] py-[6px] pb-[7px] px-2 -mr-3 rounded-[100px] transition-all duration-200 active:scale-95',
        disabled && 'opacity-30 cursor-not-allowed',
        active && 'bg-[var(--muted)]',
      )}
    >
      <div className={cn(
        'transition-colors',
        active ? 'text-primary' : disabled ? 'text-muted-foreground' : 'text-muted-foreground'
      )}>
        {icon}
      </div>
      <span className={cn(
        'text-[10px] leading-[12px] tracking-tight',
        active ? 'font-bold text-primary' : 'font-medium text-muted-foreground',
        disabled && 'text-muted-foreground'
      )}>
        {label}
      </span>
    </button>
  )
}

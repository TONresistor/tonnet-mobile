/**
 * Bottom sheet component for mobile.
 * Modal sheet that slides up from the bottom of the screen.
 */

import { X } from 'lucide-react'
import { useId, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useModalDialog } from './useModalDialog'

interface BottomSheetProps {
  open: boolean
  onClose: () => void
  title: string
  children: React.ReactNode
  showHandle?: boolean
  showCloseButton?: boolean
  maxHeight?: string
}

export function BottomSheet({
  open,
  onClose,
  title,
  children,
  showHandle = true,
  showCloseButton = true,
  maxHeight = '70vh',
}: BottomSheetProps) {
  const { t } = useTranslation('common')
  const sheetRef = useRef<HTMLDivElement>(null)
  const startY = useRef<number>(0)
  const currentY = useRef<number>(0)
  const titleId = useId()
  const dialogBehavior = useModalDialog(open, onClose, sheetRef)

  if (!open) return null

  // Handle touch gestures for swipe-to-close
  const handleTouchStart = (e: React.TouchEvent) => {
    startY.current = e.touches[0].clientY
    currentY.current = startY.current
  }

  const handleTouchMove = (e: React.TouchEvent) => {
    currentY.current = e.touches[0].clientY
    const deltaY = currentY.current - startY.current

    if (deltaY > 0 && sheetRef.current) {
      // Only allow dragging down
      sheetRef.current.style.transform = `translateY(${deltaY}px)`
    }
  }

  const resetTouchPosition = () => {
    startY.current = 0
    currentY.current = 0

    if (sheetRef.current) sheetRef.current.style.transform = ''
  }

  const handleTouchEnd = () => {
    const deltaY = currentY.current - startY.current
    resetTouchPosition()

    // Close if dragged down more than 100px
    if (deltaY > 100) {
      onClose()
    }
  }

  return (
    <div className="fixed inset-0 z-[60]" data-modal-root>
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden="true" />

      {/* Sheet */}
      <div
        ref={sheetRef}
        className="absolute bottom-0 left-0 right-0 bg-background rounded-t-2xl transition-transform duration-300 spring-smooth safe-area-bottom translate-y-0"
        style={{ maxHeight }}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={dialogBehavior.onKeyDown}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onTouchCancel={resetTouchPosition}
      >
        {/* Handle */}
        {showHandle && (
          <div className="flex justify-center py-3">
            <div className="w-10 h-1 bg-muted-foreground/30 rounded-full" />
          </div>
        )}

        {/* Header */}
        <div className="flex items-center justify-between px-4 pb-3 border-b border-border">
          <h2 id={titleId} className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          {showCloseButton && (
            <button
              type="button"
              onClick={onClose}
              className="p-2 -mr-2 rounded-lg text-muted-foreground active:bg-muted transition-colors"
              aria-label={t('cancel')}
            >
              <X aria-hidden="true" className="h-5 w-5" />
            </button>
          )}
        </div>

        {/* Content */}
        <div className="overflow-auto px-4 py-4" style={{ maxHeight: `calc(${maxHeight} - 80px)` }}>
          {children}
        </div>
      </div>
    </div>
  )
}

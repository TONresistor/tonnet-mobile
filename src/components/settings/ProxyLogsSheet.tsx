import { RefreshCw, X } from 'lucide-react'
import { useId, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useModalDialog } from '@/components/mobile/useModalDialog'
import { cn } from '@/lib/utils'

interface ProxyLogsSheetProps {
  open: boolean
  logs: string[]
  loading: boolean
  onClose: () => void
  onRefresh: () => void
}

function createLogEntries(logs: string[]): Array<{ id: string; line: string }> {
  const occurrences = new Map<string, number>()

  return logs.map((line) => {
    const occurrence = (occurrences.get(line) ?? 0) + 1
    occurrences.set(line, occurrence)
    return { id: `${line}\u0000${occurrence}`, line }
  })
}

export function ProxyLogsSheet({ open, logs, loading, onClose, onRefresh }: ProxyLogsSheetProps) {
  const { t } = useTranslation('settings')
  const { t: tc } = useTranslation('common')
  const dialogRef = useRef<HTMLDivElement>(null)
  const titleId = useId()
  const dialogBehavior = useModalDialog(open, onClose, dialogRef)

  if (!open) return null

  return (
    <div
      ref={dialogRef}
      className="fixed inset-0 z-[60] bg-black/90 flex flex-col"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      tabIndex={-1}
      data-modal-root
      onKeyDown={dialogBehavior.onKeyDown}
    >
      <div className="flex items-center justify-between px-5 py-3 pt-12 border-b border-border bg-background">
        <h2 id={titleId} className="text-lg font-medium text-foreground">
          {t('proxy_logs')}
        </h2>
        <div className="flex gap-1">
          <button
            type="button"
            onClick={onRefresh}
            disabled={loading}
            aria-label={tc('refresh')}
            className="p-3 rounded-xl text-muted-foreground active:bg-muted/50 transition-colors duration-200"
          >
            <RefreshCw aria-hidden="true" className={cn('h-5 w-5', loading && 'animate-spin')} />
          </button>
          <button
            type="button"
            onClick={onClose}
            aria-label={tc('cancel')}
            className="p-3 rounded-xl text-muted-foreground active:bg-muted/50 transition-colors duration-200"
          >
            <X aria-hidden="true" className="h-5 w-5" />
          </button>
        </div>
      </div>
      <div className="flex-1 overflow-auto p-3 font-mono text-xs">
        {logs.length === 0 ? (
          <p className="text-muted-foreground text-center py-8">
            {loading ? t('logs_loading') : t('logs_empty')}
          </p>
        ) : (
          createLogEntries(logs).map(({ id, line }) => (
            <div
              key={id}
              className={cn(
                'py-0.5 text-muted-foreground',
                line.includes('[PROXY]') && 'text-green-400',
                line.includes('[tonnet-proxy]') && 'text-purple-400',
                line.includes('Error') && 'text-red-400',
              )}
            >
              {line}
            </div>
          ))
        )}
      </div>
    </div>
  )
}
